package dev.vy.betterpv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Participants of completed runs, never an assertion about a player's live party. */
public record CurrentPartySnapshot(Run dungeon, Run kuudra) {
	private static final Pattern DISPLAY_NAME = Pattern.compile("^([A-Za-z0-9_]{1,16})(?::\\s*(.*))?$");
	private static final Pattern FORMATTING = Pattern.compile("\u00a7.");

	public record Participant(UUID uuid, String name, String detail) {}

	public record Run(String type, long completedAt, String label, List<Participant> participants) {
		public Run {
			participants = List.copyOf(participants);
		}
	}

	public static CurrentPartySnapshot empty() {
		return new CurrentPartySnapshot(null, null);
	}

	public Run latest() {
		if (this.dungeon == null) return this.kuudra;
		if (this.kuudra == null) return this.dungeon;
		return this.dungeon.completedAt() >= this.kuudra.completedAt() ? this.dungeon : this.kuudra;
	}

	/** Uses the displayed profile exactly; never falls back to another co-op or profile. */
	public static CurrentPartySnapshot fromProfiles(JsonObject root, String profileId, UUID playerUuid) {
		if (root == null || profileId == null || profileId.isBlank() || playerUuid == null) return empty();
		for (JsonElement entry : array(root, "profiles")) {
			if (!entry.isJsonObject()) continue;
			JsonObject profile = entry.getAsJsonObject();
			if (!normalizedId(profileId).equals(normalizedId(string(profile, "profile_id")))) continue;
			JsonObject members = object(profile, "members");
			if (members == null) return empty();
			for (var member : members.entrySet()) {
				if (normalizedId(member.getKey()).equals(normalizedId(playerUuid.toString()))
					&& member.getValue().isJsonObject()) {
					return fromMember(member.getValue().getAsJsonObject());
				}
			}
			return empty();
		}
		return empty();
	}

	public static CurrentPartySnapshot fromMember(JsonObject member) {
		JsonObject treasures = object(object(member, "dungeons"), "treasures");
		JsonObject dungeon = null;
		JsonObject kuudra = null;
		long dungeonTime = 0L;
		long kuudraTime = 0L;
		// Hypixel does not guarantee chronological array order.
		for (JsonElement entry : array(treasures, "runs")) {
			if (!entry.isJsonObject()) continue;
			JsonObject run = entry.getAsJsonObject();
			long completed = number(run, "completion_ts");
			String type = string(run, "type");
			if ("DUNGEON".equals(type) && completed > dungeonTime) {
				dungeon = run;
				dungeonTime = completed;
			} else if ("KUUDRA".equals(type) && completed > kuudraTime) {
				kuudra = run;
				kuudraTime = completed;
			}
		}
		return new CurrentPartySnapshot(parseRun(dungeon), parseRun(kuudra));
	}

	private static Run parseRun(JsonObject run) {
		if (run == null) return null;
		var participants = new LinkedHashMap<UUID, Participant>();
		for (JsonElement entry : array(run, "participants")) {
			if (!entry.isJsonObject()) continue;
			JsonObject participant = entry.getAsJsonObject();
			UUID uuid = parseUuid(string(participant, "player_uuid"));
			if (uuid == null) continue;
			String display = FORMATTING.matcher(string(participant, "display_name")).replaceAll("").trim();
			var match = DISPLAY_NAME.matcher(display);
			String name = "";
			String detail = "";
			if (match.matches()) {
				name = match.group(1);
				detail = match.group(2) == null ? "" : match.group(2);
			}
			participants.putIfAbsent(uuid, new Participant(uuid, name, detail));
		}
		return new Run(string(run, "type"), number(run, "completion_ts"), runLabel(run), List.copyOf(participants.values()));
	}

	private static String runLabel(JsonObject run) {
		if ("KUUDRA".equals(string(run, "type"))) {
			String tier = switch (string(run, "tier_id").toLowerCase(Locale.ROOT)) {
				case "none", "basic" -> "Basic (T1)";
				case "hot" -> "Hot (T2)";
				case "burning" -> "Burning (T3)";
				case "fiery" -> "Fiery (T4)";
				case "infernal" -> "Infernal (T5)";
				default -> "Unknown tier";
			};
			return "Kuudra - " + tier;
		}
		String type = string(run, "dungeon_type");
		String floor = string(run, "dungeon_tier");
		if (!floor.matches("[0-7]")) return "Dungeons - Unknown floor";
		return switch (type) {
			case "catacombs" -> "Catacombs - " + ("0".equals(floor) ? "Entrance" : "F" + floor);
			case "master_catacombs" -> "Catacombs - M" + floor;
			default -> "Dungeons - Unknown floor";
		};
	}

	private static UUID parseUuid(String raw) {
		String id = normalizedId(raw);
		if (!id.matches("[0-9a-f]{32}")) return null;
		return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
			+ "-" + id.substring(16, 20) + "-" + id.substring(20));
	}

	private static String normalizedId(String id) {
		return id.replace("-", "").toLowerCase(Locale.ROOT);
	}

	private static JsonObject object(JsonObject parent, String key) {
		return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
	}

	private static JsonArray array(JsonObject parent, String key) {
		return parent != null && parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : new JsonArray();
	}

	private static String string(JsonObject parent, String key) {
		if (parent == null || !parent.has(key) || !parent.get(key).isJsonPrimitive()) return "";
		return parent.get(key).getAsString();
	}

	private static long number(JsonObject parent, String key) {
		try {
			return Long.parseLong(string(parent, key));
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}
}
