package dev.vy.betterpv.client.gui.party;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.api.HypixelApiClient;
import dev.vy.betterpv.client.data.CurrentPartySnapshot;
import dev.vy.betterpv.client.data.CurrentPartySnapshot.Participant;
import dev.vy.betterpv.client.data.CurrentPartySnapshot.Run;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.gui.ProfileViewerScreen;
import dev.vy.betterpv.client.gui.PvDraw;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small run viewer using the profile request already made by BetterPV. */
public final class CurrentPartyPage {
	private static final int ROW_H = 24;
	private static final long REFRESH_DELAY_MS = 30_000L;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		.withZone(ZoneId.systemDefault());
	private final Screen owner;
	private final Map<UUID, String> names = new HashMap<>();
	private final Set<UUID> requestedNames = new HashSet<>();
	private final List<Hit> hits = new ArrayList<>();
	private CurrentPartySnapshot snapshot = CurrentPartySnapshot.empty();
	private JsonObject sourceRoot;
	private UUID playerUuid;
	private String profileId;
	private int generation;
	private boolean refreshing;
	private long refreshReadyAt;
	private String error = "";
	private int filter;
	private int scroll;
	private int maxScroll;
	private int listX, listY, listW, listH;

	public CurrentPartyPage(Screen owner) {
		this.owner = owner;
	}

	public void apply(JsonObject root, String profileId, UUID uuid, String playerName) {
		boolean sameIdentity = Objects.equals(this.profileId, profileId) && Objects.equals(this.playerUuid, uuid);
		if (sameIdentity && this.sourceRoot == root) return;
		this.generation++;
		this.sourceRoot = root;
		this.profileId = profileId;
		this.playerUuid = uuid;
		this.snapshot = CurrentPartySnapshot.fromProfiles(root, profileId, uuid);
		this.refreshing = false;
		this.error = "";
		this.scroll = 0;
		this.hits.clear();
		this.requestedNames.clear();
		if (!sameIdentity) {
			this.filter = 0;
			this.refreshReadyAt = 0L;
		}
		if (uuid != null && playerName != null && !playerName.isBlank()) this.names.put(uuid, playerName);
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		this.hits.clear();
		this.listH = 0;
		PvDraw.textBold(g, font, text("title"), x + 4, y + 3, PvDraw.COLOR_TEXT);
		PvDraw.text(g, font, text("note"), x + 4, y + 17, PvDraw.COLOR_MUTED);
		String[] labels = { text("latest"), text("dungeons"), text("kuudra") };
		int buttonX = x + 4;
		for (int i = 0; i < labels.length; i++) {
			int selected = i;
			int width = font.width(labels[i]) + 16;
			button(g, font, labels[i], buttonX, y + 33, width, this.filter == i, true, mouseX, mouseY, () -> {
				this.filter = selected;
				this.scroll = 0;
				this.hits.clear();
			});
			buttonX += width + 4;
		}
		long now = System.currentTimeMillis();
		String refreshLabel = this.refreshing ? text("refreshing") : this.refreshReadyAt > now
			? text("cooldown", (this.refreshReadyAt - now + 999L) / 1000L) : text("refresh");
		int refreshW = Math.max(72, font.width(refreshLabel) + 12);
		button(g, font, refreshLabel, x + w - refreshW - 4, y + 33, refreshW, false,
			!this.refreshing && now >= this.refreshReadyAt && this.playerUuid != null,
			mouseX, mouseY, this::refresh);

		int cardY = y + 58;
		int cardH = h - 75;
		PvDraw.innerPanel(g, x, cardY, w, cardH);
		Run run = switch (this.filter) {
			case 1 -> this.snapshot.dungeon();
			case 2 -> this.snapshot.kuudra();
			default -> this.snapshot.latest();
		};
		if (run == null) {
			PvDraw.textCentered(g, font, text("empty"), x + w / 2, cardY + 24, PvDraw.COLOR_TEXT);
			PvDraw.textCentered(g, font, text("empty_hint"), x + w / 2, cardY + 40, PvDraw.COLOR_MUTED);
		} else {
			resolveNames(run);
			PvDraw.textBold(g, font, run.label(), x + 8, cardY + 7, PvDraw.COLOR_ACCENT);
			String age = run.completedAt() > now ? text("clock_difference") : FormatUtil.ago(run.completedAt());
			PvDraw.textRight(g, font, age, x + w - 8, cardY + 7, PvDraw.COLOR_GOLD);
			PvDraw.text(g, font, text("completed", DATE.format(Instant.ofEpochMilli(run.completedAt()))),
				x + 8, cardY + 21, PvDraw.COLOR_MUTED);
			PvDraw.fill(g, x + 8, cardY + 35, w - 16, 1, PvDraw.COLOR_DIVIDER);
			this.listX = x + 6;
			this.listY = cardY + 40;
			this.listW = w - 12;
			int visibleRows = Math.max(1, (cardH - 46) / ROW_H);
			this.listH = visibleRows * ROW_H;
			this.maxScroll = Math.max(0, run.participants().size() - visibleRows);
			this.scroll = Math.min(this.scroll, this.maxScroll);
			if (run.participants().isEmpty()) {
				PvDraw.text(g, font, text("no_participants"), x + 8, this.listY + 6, PvDraw.COLOR_MUTED);
			}
			for (int i = this.scroll; i < Math.min(run.participants().size(), this.scroll + visibleRows); i++) {
				Participant participant = run.participants().get(i);
				drawParticipant(g, font, participant, this.listY + (i - this.scroll) * ROW_H, mouseX, mouseY);
			}
			if (this.maxScroll > 0) {
				int trackH = this.listH;
				int thumbH = Math.max(10, trackH * visibleRows / run.participants().size());
				PvDraw.fill(g, x + w - 4, this.listY, 2, trackH, PvDraw.COLOR_DIVIDER);
				PvDraw.fill(g, x + w - 4, this.listY + (trackH - thumbH) * this.scroll / this.maxScroll,
					2, thumbH, PvDraw.COLOR_ACCENT);
			}
		}
		PvDraw.text(g, font, this.error.isBlank() ? text("footer") : this.error,
			x + 4, y + h - 11, this.error.isBlank() ? PvDraw.COLOR_MUTED : 0xFFFFAA55);
	}

	private void drawParticipant(GuiGraphicsExtractor g, Font font, Participant participant, int y, int mx, int my) {
		String name = this.names.getOrDefault(participant.uuid(), participant.name());
		boolean named = !name.isBlank();
		if (!named) name = participant.uuid().toString().substring(0, 8) + "...";
		boolean viewed = participant.uuid().equals(this.playerUuid);
		boolean hovered = contains(mx, my, this.listX, y, this.listW, ROW_H - 2);
		if (hovered && named) PvDraw.fill(g, this.listX, y, this.listW, ROW_H - 2, 0xFF26324B);
		PvDraw.text(g, font, name + (viewed ? " " + text("viewed") : ""), this.listX + 4, y + 2,
			viewed ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_TEXT);
		String detail = participant.detail().isBlank() ? text("participant") : participant.detail();
		PvDraw.text(g, font, font.plainSubstrByWidth(detail, this.listW - 16), this.listX + 4, y + 13, PvDraw.COLOR_MUTED);
		if (named) {
			String targetName = name;
			this.hits.add(new Hit(this.listX, y, this.listW, ROW_H - 2,
				() -> Minecraft.getInstance().setScreen(new ProfileViewerScreen(targetName))));
		}
	}

	private void resolveNames(Run run) {
		int token = this.generation;
		for (Participant participant : run.participants()) {
			UUID uuid = participant.uuid();
			if (this.names.containsKey(uuid) || !this.requestedNames.add(uuid)) continue;
			HypixelApiClient.resolveName(uuid).whenComplete((result, failure) -> {
				Minecraft client = Minecraft.getInstance();
				if (client == null) return;
				client.execute(() -> {
					if (!stillCurrent(client, token)) return;
					if (failure == null && result != null && result.isPresent()) this.names.put(uuid, result.get().name());
				});
			});
		}
	}

	private void refresh() {
		long now = System.currentTimeMillis();
		if (this.playerUuid == null || this.refreshing || now < this.refreshReadyAt) return;
		this.refreshing = true;
		this.refreshReadyAt = now + REFRESH_DELAY_MS;
		this.error = "";
		int token = this.generation;
		UUID uuid = this.playerUuid;
		String id = this.profileId;
		HypixelApiClient.skyblockProfiles(uuid).whenComplete((result, failure) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) return;
			client.execute(() -> {
				if (!stillCurrent(client, token)) return;
				this.refreshing = false;
				if (failure != null || result == null || result.isEmpty()
					|| !result.get().has("profiles") || !result.get().get("profiles").isJsonArray()) {
					this.error = text("refresh_failed");
					return;
				}
				this.snapshot = CurrentPartySnapshot.fromProfiles(result.get(), id, uuid);
				this.requestedNames.clear();
				this.scroll = 0;
				this.hits.clear();
			});
		});
	}

	private boolean stillCurrent(Minecraft client, int token) {
		return client.screen == this.owner && token == this.generation;
	}

	public boolean mouseClicked(double mx, double my) {
		for (Hit hit : this.hits) {
			if (contains(mx, my, hit.x, hit.y, hit.w, hit.h)) {
				hit.action.run();
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!contains(mx, my, this.listX, this.listY, this.listW, this.listH) || this.maxScroll == 0) return false;
		this.scroll = Math.clamp(this.scroll - (int) Math.signum(amount), 0, this.maxScroll);
		return true;
	}

	private void button(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w,
		boolean selected, boolean enabled, int mx, int my, Runnable action) {
		boolean hovered = contains(mx, my, x, y, w, 18);
		PvDraw.fill(g, x, y, w, 18, selected ? 0xFF2A3A55 : hovered && enabled ? 0xFF222230 : 0xFF16161E);
		g.outline(x, y, w, 18, selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER);
		PvDraw.textCentered(g, font, label, x + w / 2, y + 5, enabled ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
		if (enabled) this.hits.add(new Hit(x, y, w, 18, action));
	}

	private static boolean contains(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static String text(String key, Object... args) {
		return Component.translatable("betterpv.party." + key, args).getString();
	}

	private record Hit(int x, int y, int w, int h, Runnable action) {}
}
