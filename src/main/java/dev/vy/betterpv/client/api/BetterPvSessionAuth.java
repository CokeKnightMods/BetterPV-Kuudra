package dev.vy.betterpv.client.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import dev.vy.betterpv.BetterPV;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * BetterPV → api.vyriv.dev session proof (JWT only).
 *
 * <p>Minecraft access tokens are used only for the official Mojang/authlib
 * {@link MinecraftSessionService#joinServer} call. They are never sent to
 * api.vyriv.dev / Vyriv / Cloudflare / logging.
 */
public final class BetterPvSessionAuth {
	private static final URI AUTH_URI = URI.create("https://api.vyriv.dev/hypixel/auth");
	private static final Duration TIMEOUT = Duration.ofSeconds(12);
	private static final long REFRESH_SKEW_MILLIS = 60_000L;
	private static final HttpClient HTTP = HypixelApiClient.http();
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Object LOCK = new Object();
	private static final ExecutorService AUTH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-SessionAuth");
		t.setDaemon(true);
		return t;
	});

	private static final long REFRESH_WHILE_USED_MILLIS = 30L * 60L * 1000L;
	private static final ScheduledExecutorService REFRESH_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-SessionAuthRefresh");
		t.setDaemon(true);
		return t;
	});

	private static volatile String cachedJwt;
	private static volatile long expiresAtMillis;
	private static volatile long lastUsedAtMillis;
	private static CompletableFuture<Optional<String>> inFlight;
	private static volatile Failure lastFailure = Failure.NONE;
	private static volatile String lastFailureDetail = "";
	private static volatile long lastChatNoticeAtMillis;
	private static volatile String lastChatNotice;

	public enum Failure {
		NONE(""),
		MISSING_SESSION("BetterPV could not authenticate /pv (missing Minecraft session)"),
		OFFLINE_SESSION("BetterPV could not authenticate /pv (offline / Fabric session)"),
		JOIN_SERVER_FAILED("BetterPV could not authenticate /pv (Minecraft session rejected). Fully quit Minecraft, reopen Prism, and re-login to Microsoft if it keeps failing."),
		SERVER_AUTH_UNAVAILABLE("BetterPV could not authenticate /pv (auth service unavailable)"),
		AUTH_REJECTED("BetterPV could not authenticate /pv (session proof rejected)"),
		AUTH_HTTP("BetterPV could not authenticate /pv (auth HTTP error)"),
		MISSING_JWT("BetterPV could not authenticate /pv (missing JWT)");

		private final String userMessage;

		Failure(String userMessage) {
			this.userMessage = userMessage;
		}

		public String userMessage() {
			return userMessage;
		}
	}

	private BetterPvSessionAuth() {
	}

	public static void invalidate() {
		synchronized (LOCK) {
			cachedJwt = null;
			expiresAtMillis = 0L;
			inFlight = null;
		}
	}

	private static void setFailure(Failure failure, String detail) {
		lastFailure = failure == null ? Failure.NONE : failure;
		lastFailureDetail = detail == null ? "" : detail.trim();
	}

	public static Failure lastFailure() {
		return lastFailure == null ? Failure.NONE : lastFailure;
	}

	/** User-facing reason when JWT auth is unavailable; empty when OK. */
	public static Optional<String> userFacingFailure() {
		Failure failure = lastFailure();
		if (failure == Failure.NONE || failure.userMessage().isBlank()) {
			return Optional.empty();
		}
		return Optional.of(failure.userMessage());
	}

	/** Applies {@code Authorization: Bearer} JWT. Returns false when JWT cannot be obtained. */
	public static boolean applyAuthHeaders(HttpRequest.Builder builder) {
		Optional<String> bearer = ensureBearerToken();
		if (bearer.isPresent()) {
			builder.header("Authorization", "Bearer " + bearer.get());
			return true;
		}
		if (lastFailure == Failure.NONE) {
			lastFailure = Failure.MISSING_JWT;
		}
		return false;
	}

	/** Posts a throttled in-game chat line for the current auth failure. */
	public static void notifyPlayerIfNeeded() {
		Failure failure = lastFailure();
		if (failure == Failure.NONE) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.gui == null) {
			return;
		}
		long now = System.currentTimeMillis();
		String noticeKey = failure.name();
		if (noticeKey.equals(lastChatNotice) && now - lastChatNoticeAtMillis < 15_000L) {
			return;
		}
		lastChatNotice = noticeKey;
		lastChatNoticeAtMillis = now;
		String report = copyableReport(mc, failure);
		mc.execute(() -> {
			if (mc.gui == null) {
				return;
			}
			mc.gui.getChat().addClientSystemMessage(authFailChat(report));
		});
	}

	private static Component authFailChat(String report) {
		Component copyHint = Component.literal("Click to copy a report to DM Vyriv")
			.setStyle(Style.EMPTY
				.withColor(0xFFD36A)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.CopyToClipboard(report))
				.withHoverEvent(new HoverEvent.ShowText(
					Component.literal("Click to copy, then paste it to Vyriv on Discord")
				)));
		return Component.literal("BetterPV: /pv could not authenticate. ")
			.setStyle(Style.EMPTY.withColor(0xFFAAAAAA))
			.append(copyHint);
	}

	private static String copyableReport(Minecraft mc, Failure failure) {
		User user = mc.getUser();
		String name = user != null && user.getName() != null ? user.getName() : "?";
		String uuid = user != null && user.getProfileId() != null ? user.getProfileId().toString() : "?";
		String detail = lastFailureDetail == null || lastFailureDetail.isBlank() ? "" : "\ndetail: " + lastFailureDetail;
		return "BetterPV /pv auth failed"
			+ "\nname: " + name
			+ "\nuuid: " + uuid
			+ "\nreason: " + failure.name()
			+ detail
			+ "\nmod: " + modVersion();
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(BetterPV.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}

	/**
	 * Non-blocking warm of the session JWT once a real Minecraft login is present.
	 * Reuses in-flight auth; no-ops when a usable JWT is already cached.
	 */
	public static void prefetchAsync() {
		if (isUsable()) {
			lastFailure = Failure.NONE;
			return;
		}
		synchronized (LOCK) {
			if (isUsable()) {
				lastFailure = Failure.NONE;
				return;
			}
			if (inFlight == null || inFlight.isDone()) {
				inFlight = CompletableFuture.supplyAsync(BetterPvSessionAuth::authenticateOnce, AUTH_EXECUTOR);
			}
		}
	}

	/** Non-blocking; starts auth if needed so it can overlap other /pv requests. */
	public static CompletableFuture<Optional<String>> bearerTokenAsync() {
		lastUsedAtMillis = System.currentTimeMillis();
		if (isUsable()) {
			lastFailure = Failure.NONE;
			return CompletableFuture.completedFuture(Optional.of(cachedJwt));
		}
		synchronized (LOCK) {
			if (isUsable()) {
				lastFailure = Failure.NONE;
				return CompletableFuture.completedFuture(Optional.of(cachedJwt));
			}
			if (inFlight == null || inFlight.isDone()) {
				inFlight = CompletableFuture.supplyAsync(BetterPvSessionAuth::authenticateOnce, AUTH_EXECUTOR);
			}
			return inFlight.exceptionally(error -> Optional.empty());
		}
	}

	/** Blocking; call only from worker threads, never the render thread. */
	public static Optional<String> ensureBearerToken() {
		lastUsedAtMillis = System.currentTimeMillis();
		if (isUsable()) {
			lastFailure = Failure.NONE;
			return Optional.of(cachedJwt);
		}

		CompletableFuture<Optional<String>> future;
		synchronized (LOCK) {
			if (isUsable()) {
				lastFailure = Failure.NONE;
				return Optional.of(cachedJwt);
			}
			if (inFlight == null || inFlight.isDone()) {
				inFlight = CompletableFuture.supplyAsync(BetterPvSessionAuth::authenticateOnce, AUTH_EXECUTOR);
			}
			future = inFlight;
		}

		try {
			Optional<String> token = future.join();
			return token == null ? Optional.empty() : token;
		} catch (java.util.concurrent.CompletionException exception) {
			BetterPV.LOGGER.warn("BetterPV session auth failed", exception);
			invalidate();
			lastFailure = Failure.AUTH_HTTP;
			return Optional.empty();
		}
	}

	// Renew shortly before the token goes stale so the next /pv doesn't wait on joinServer,
	// but only while /pv is actually in use; idle clients shouldn't keep hitting Mojang.
	private static void scheduleRefresh(long expiresInSeconds) {
		long delayMillis = Math.max(30_000L, expiresInSeconds * 1000L - REFRESH_SKEW_MILLIS - 30_000L);
		REFRESH_SCHEDULER.schedule(() -> {
			if (System.currentTimeMillis() - lastUsedAtMillis > REFRESH_WHILE_USED_MILLIS) {
				return;
			}
			synchronized (LOCK) {
				if (inFlight == null || inFlight.isDone()) {
					inFlight = CompletableFuture.supplyAsync(BetterPvSessionAuth::authenticateOnce, AUTH_EXECUTOR);
				}
			}
		}, delayMillis, TimeUnit.MILLISECONDS);
	}

	private static boolean isUsable() {
		String token = cachedJwt;
		return token != null && !token.isBlank() && System.currentTimeMillis() < (expiresAtMillis - REFRESH_SKEW_MILLIS);
	}

	private static Optional<String> authenticateOnce() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			setFailure(Failure.MISSING_SESSION, "minecraft instance null");
			return Optional.empty();
		}

		User user = mc.getUser();
		if (user == null) {
			setFailure(Failure.MISSING_SESSION, "user null");
			return Optional.empty();
		}

		String username = user.getName();
		UUID profileId = user.getProfileId();
		String accessToken = user.getAccessToken();
		if (username == null || username.isBlank() || profileId == null || accessToken == null || accessToken.isBlank()) {
			BetterPV.LOGGER.warn("BetterPV session auth skipped: missing Minecraft user session");
			setFailure(Failure.MISSING_SESSION, "blank username/uuid/token");
			return Optional.empty();
		}

		if (looksLikeOfflineDevSession(accessToken)) {
			BetterPV.LOGGER.warn(
				"BetterPV session JWT skipped: Fabric offline token user={}",
				username
			);
			setFailure(Failure.OFFLINE_SESSION, "FabricMC offline token");
			return Optional.empty();
		}

		String serverId = randomServerId();
		Optional<String> joinError = joinMinecraftSession(mc, profileId, accessToken, serverId, username);
		if (joinError.isPresent()) {
			// One quick retry for transient Mojang blips; expired tokens stay failed.
			if (!joinError.get().startsWith("InvalidCredentials")) {
				try {
					Thread.sleep(400L);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					setFailure(Failure.JOIN_SERVER_FAILED, joinError.get());
					return Optional.empty();
				}
				serverId = randomServerId();
				joinError = joinMinecraftSession(mc, profileId, accessToken, serverId, username);
			}
			if (joinError.isPresent()) {
				setFailure(Failure.JOIN_SERVER_FAILED, joinError.get());
				return Optional.empty();
			}
		}

		try {
			String body = "{\"username\":\"" + escapeJson(username) + "\",\"serverId\":\"" + serverId + "\"}";
			HttpRequest request = HttpRequest.newBuilder(AUTH_URI)
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			String responseBody = response.body() == null ? "" : response.body();
			if (status == 503 || causeEquals(responseBody, "session_auth_unavailable")) {
				BetterPV.LOGGER.warn("BetterPV /hypixel/auth unavailable status={} (signing secret missing?)", status);
				setFailure(Failure.SERVER_AUTH_UNAVAILABLE, "status=" + status);
				return Optional.empty();
			}
			if (status < 200 || status >= 300 || responseBody.isBlank()) {
				BetterPV.LOGGER.warn("BetterPV /hypixel/auth failed status={}", status);
				setFailure(
					status == 401 || status == 403 ? Failure.AUTH_REJECTED : Failure.AUTH_HTTP,
					"status=" + status
				);
				return Optional.empty();
			}

			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			if (root.has("success") && root.get("success").isJsonPrimitive() && !root.get("success").getAsBoolean()) {
				setFailure(Failure.AUTH_REJECTED, "success=false");
				return Optional.empty();
			}
			if (!root.has("token") || !root.get("token").isJsonPrimitive()) {
				setFailure(Failure.AUTH_REJECTED, "missing token field");
				return Optional.empty();
			}

			String token = root.get("token").getAsString();
			long expiresInSeconds = root.has("expiresIn") && root.get("expiresIn").isJsonPrimitive()
				? Math.max(60L, root.get("expiresIn").getAsLong())
				: 900L;
			synchronized (LOCK) {
				cachedJwt = token;
				expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L);
			}
			scheduleRefresh(expiresInSeconds);
			setFailure(Failure.NONE, "");
			return Optional.of(token);
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn("BetterPV /hypixel/auth request failed", exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			setFailure(Failure.AUTH_HTTP, exception.toString());
			return Optional.empty();
		} catch (RuntimeException exception) {
			BetterPV.LOGGER.warn("BetterPV /hypixel/auth parse failed", exception);
			setFailure(Failure.AUTH_HTTP, exception.toString());
			return Optional.empty();
		}
	}

	/**
	 * Registers this client session with Mojang for {@code serverId}.
	 * Returns empty on success, or a short error tag on failure.
	 */
	private static Optional<String> joinMinecraftSession(
		Minecraft mc,
		UUID profileId,
		String accessToken,
		String serverId,
		String username
	) {
		try {
			// Access token is ONLY for official Minecraft session-service join; never sent to Vyriv.
			MinecraftSessionService sessionService = mc.services().sessionService();
			sessionService.joinServer(profileId, accessToken, serverId);
			return Optional.empty();
		} catch (InvalidCredentialsException exception) {
			BetterPV.LOGGER.warn(
				"Minecraft joinServer rejected credentials for BetterPV auth user={}",
				username
			);
			return Optional.of("InvalidCredentials: " + exception.toString());
		} catch (AuthenticationException exception) {
			BetterPV.LOGGER.warn("Minecraft joinServer failed for BetterPV auth: {}", exception.toString());
			return Optional.of(exception.getClass().getSimpleName() + ": " + exception.toString());
		} catch (RuntimeException exception) {
			BetterPV.LOGGER.warn("Minecraft joinServer failed for BetterPV auth: {}", exception.toString());
			return Optional.of(exception.getClass().getSimpleName() + ": " + exception.toString());
		}
	}

	/** Loom {@code runClient} default token. Real Microsoft sessions must still attempt joinServer. */
	static boolean looksLikeOfflineDevSession(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			return true;
		}
		return "FabricMC".equalsIgnoreCase(accessToken.trim());
	}

	private static boolean causeEquals(String body, String cause) {
		if (body == null || body.isBlank() || cause == null) {
			return false;
		}
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			return root.has("cause")
				&& root.get("cause").isJsonPrimitive()
				&& cause.equalsIgnoreCase(root.get("cause").getAsString());
		} catch (RuntimeException ignored) {
			return body.contains(cause);
		}
	}

	private static String randomServerId() {
		byte[] bytes = new byte[20];
		RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private static String escapeJson(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
