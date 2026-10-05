package dev.vy.betterpv.client.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KuudraProfileFactsTest {
	private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
	@Test void hiddenBankIsDifferentFromAnEmptyBank() {
		assertNull(read(root("", "{}"), "profile").bank());
		assertEquals(0.0, read(root(",\"banking\":{\"balance\":0}", "{}"), "profile").bank());
		assertNull(read(root(",\"banking\":{\"balance\":\"NaN\"}", "{}"), "profile").bank());
	}
	@Test void cannotBorrowAnotherProfileOrCoopMembersData() {
		var root = root(",\"banking\":{\"balance\":1000000000}", "{}");
		assertNull(read(root, "different").bank());
		assertNull(KuudraProfileFacts.fromProfiles(root, "profile", UUID.randomUUID()).bank());
		assertEquals(1000000000.0, read(root, "profile").bank());
	}
	@Test void zeroXpIsAvailableButOmittedXpIsUnknown() {
		var facts = read(root("", """
			{"player_data":{"experience":{"SKILL_COMBAT":0}},
			 "nether_island_player_data":{"kuudra_completed_tiers":{}},
			 "accessory_bag_storage":{"highest_magical_power":1200}}
			"""), "profile");
		assertTrue(facts.combat());
		assertFalse(facts.foraging());
		assertFalse(facts.catacombs());
		assertTrue(facts.runs());
		assertFalse(facts.accessories());
		assertEquals(1200.0, facts.highestMagicalPower());
	}
	private static KuudraProfileFacts read(JsonObject root, String profile) {
		return KuudraProfileFacts.fromProfiles(root, profile, PLAYER);
	}
	private static JsonObject root(String banking, String member) {
		return JsonParser.parseString("{\"profiles\":[{\"profile_id\":\"profile\"" + banking
			+ ",\"members\":{\"" + PLAYER.toString().replace("-", "") + "\":" + member + "}}]}").getAsJsonObject();
	}
}
