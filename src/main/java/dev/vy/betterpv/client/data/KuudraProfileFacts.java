package dev.vy.betterpv.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.UUID;

/** Availability is separate from a zero value, especially for hidden banking and skills. */
public record KuudraProfileFacts(Double bank, boolean combat, boolean foraging, boolean catacombs,
	boolean skyBlock, boolean runs, Double highestMagicalPower, boolean accessories) {
	public static KuudraProfileFacts empty() {
		return new KuudraProfileFacts(null, false, false, false, false, false, null, false);
	}

	public static KuudraProfileFacts fromProfiles(JsonObject root, String profileId, UUID player) {
		if (root == null || profileId == null || profileId.isBlank() || player == null
			|| !root.has("profiles") || !root.get("profiles").isJsonArray()) return empty();
		for (JsonElement entry : root.getAsJsonArray("profiles")) {
			if (!entry.isJsonObject()) continue;
			JsonObject profile = entry.getAsJsonObject();
			JsonElement id = profile.get("profile_id");
			if (id == null || !id.isJsonPrimitive() || !normalize(profileId).equals(normalize(id.getAsString()))) continue;
			JsonObject members = object(profile, "members");
			if (members == null) return empty();
			for (var member : members.entrySet()) {
				if (normalize(member.getKey()).equals(normalize(player.toString())) && member.getValue().isJsonObject()) {
					return from(profile, member.getValue().getAsJsonObject());
				}
			}
			return empty();
		}
		return empty();
	}

	private static KuudraProfileFacts from(JsonObject profile, JsonObject member) {
		JsonObject inventory = object(member, "inventory");
		if (inventory == null) inventory = object(member, "inventories");
		JsonObject bags = object(inventory, "bag_contents");
		JsonObject talismans = object(bags, "talisman_bag");
		boolean accessories = talismans != null && talismans.has("data")
			&& talismans.get("data").isJsonPrimitive() && !talismans.get("data").getAsString().isBlank();
		return new KuudraProfileFacts(number(object(profile, "banking"), "balance"),
			hasSkill(member, "COMBAT"), hasSkill(member, "FORAGING"),
			number(object(object(object(member, "dungeons"), "dungeon_types"), "catacombs"), "experience") != null,
			number(object(member, "leveling"), "experience") != null,
			object(object(member, "nether_island_player_data"), "kuudra_completed_tiers") != null,
			number(object(member, "accessory_bag_storage"), "highest_magical_power"), accessories);
	}

	private static boolean hasSkill(JsonObject member, String skill) {
		JsonObject experience = object(object(member, "player_data"), "experience");
		return number(experience, "SKILL_" + skill) != null || number(experience, skill) != null
			|| number(member, "experience_skill_" + skill.toLowerCase(Locale.ROOT)) != null;
	}

	private static Double number(JsonObject object, String key) {
		if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return null;
		try {
			double value = object.get(key).getAsDouble();
			return Double.isFinite(value) && value >= 0 ? value : null;
		} catch (NumberFormatException | UnsupportedOperationException ignored) { return null; }
	}

	private static JsonObject object(JsonObject object, String key) {
		return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
	}

	private static String normalize(String id) { return id.replace("-", "").toLowerCase(Locale.ROOT); }
}
