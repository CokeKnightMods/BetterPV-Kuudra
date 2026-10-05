package dev.vy.betterpv.client.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrentPartySnapshotTest {
	private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID TEAMMATE = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Test
	void selectsNewestCompletedRunPerModeRegardlessOfArrayOrder() {
		var snapshot = fromRuns("""
			{"type":"DUNGEON","completion_ts":5000,"dungeon_type":"master_catacombs","dungeon_tier":7},
			{"type":"KUUDRA","completion_ts":8000,"tier_id":"infernal"},
			{"type":"DUNGEON","completion_ts":1000},
			{"type":"KUUDRA","completion_ts":2000},
			{"type":"OTHER","completion_ts":9000}
			""");
		assertEquals(5000, snapshot.dungeon().completedAt());
		assertEquals("Catacombs - M7", snapshot.dungeon().label());
		assertEquals("Kuudra - Infernal (T5)", snapshot.kuudra().label());
		assertSame(snapshot.kuudra(), snapshot.latest());
	}

	@Test
	void handlesNullMissingAndMalformedFieldsWithoutCrashing() {
		assertNull(CurrentPartySnapshot.fromMember(null).latest());
		assertNull(CurrentPartySnapshot.fromMember(json("{}")).latest());
		assertNull(CurrentPartySnapshot.fromMember(json("{\"dungeons\":{\"treasures\":{\"runs\":{}}}}")).latest());
		assertNull(fromRuns("""
			null, 7, {}, {"type":"DUNGEON","completion_ts":"invalid"},
			{"type":"KUUDRA","completion_ts":null}, {"type":"DUNGEON","completion_ts":-1}
			""").latest());
	}

	@Test
	void normalizesUuidsStripsFormattingAndKeepsClassDetails() {
		var snapshot = fromRuns("""
			{"type":"DUNGEON","completion_ts":1000,"participants":[
			  {"player_uuid":"11111111111111111111111111111111","display_name":"\u00a7aAlice\u00a7r: Mage (50)"},
			  {"player_uuid":"22222222-2222-2222-2222-222222222222","display_name":"Bob"},
			  {"player_uuid":"22222222222222222222222222222222"},
			  {"player_uuid":"broken"}, {"player_uuid":{}}, null
			]}
			""");
		assertEquals(2, snapshot.latest().participants().size());
		var alice = snapshot.latest().participants().getFirst();
		assertEquals(PLAYER, alice.uuid());
		assertEquals("Alice", alice.name());
		assertEquals("Mage (50)", alice.detail());
		assertEquals(TEAMMATE, snapshot.latest().participants().get(1).uuid());
		assertEquals("Bob", snapshot.latest().participants().get(1).name());
	}

	@Test
	void retainsUuidWhenDisplayNameIsMissingSoItCanBeResolved() {
		var snapshot = fromRuns("""
			{"type":"KUUDRA","completion_ts":1000,"participants":[
			  {"player_uuid":"22222222222222222222222222222222"}
			]}
			""");
		assertEquals(TEAMMATE, snapshot.latest().participants().getFirst().uuid());
		assertEquals("", snapshot.latest().participants().getFirst().name());
	}

	@Test
	void neverSubstitutesOlderParticipantsForAnIncompleteNewestRun() {
		var snapshot = fromRuns("""
			{"type":"DUNGEON","completion_ts":1000,"participants":[
			  {"player_uuid":"22222222222222222222222222222222","display_name":"Bob"}
			]},
			{"type":"DUNGEON","completion_ts":2000,"participants":null}
			""");
		assertEquals(2000, snapshot.latest().completedAt());
		assertTrue(snapshot.latest().participants().isEmpty());
	}

	@Test
	void usesTheViewedProfileAndMemberEvenIfAnotherProfileIsSelected() {
		JsonObject root = json("""
			{"profiles":[
			  {"profile_id":"other","selected":true,"members":{}},
			  {"profile_id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","members":{
			    "11111111-1111-1111-1111-111111111111":{"dungeons":{"treasures":{"runs":[
			      {"type":"KUUDRA","completion_ts":4000,"tier_id":"hot"}
			    ]}}},
			    "22222222222222222222222222222222":{"dungeons":{"treasures":{"runs":[
			      {"type":"DUNGEON","completion_ts":8000}
			    ]}}}
			  }}
			]}
			""");
		var snapshot = CurrentPartySnapshot.fromProfiles(root, "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA", PLAYER);
		assertEquals("Kuudra - Hot (T2)", snapshot.latest().label());
		assertNull(snapshot.dungeon());
		assertNull(CurrentPartySnapshot.fromProfiles(root, "missing", PLAYER).latest());
		assertNull(CurrentPartySnapshot.fromProfiles(root, "other", PLAYER).latest());
		assertNull(CurrentPartySnapshot.fromProfiles(root, null, PLAYER).latest());
	}

	@Test
	void supportsEntranceNormalFloorsAndBasicKuudra() {
		assertEquals("Catacombs - Entrance", fromRuns("""
			{"type":"DUNGEON","completion_ts":1000,"dungeon_type":"catacombs","dungeon_tier":0}
			""").latest().label());
		assertEquals("Catacombs - F7", fromRuns("""
			{"type":"DUNGEON","completion_ts":1000,"dungeon_type":"catacombs","dungeon_tier":7}
			""").latest().label());
		assertEquals("Kuudra - Basic (T1)", fromRuns("""
			{"type":"KUUDRA","completion_ts":1000,"tier_id":"none"}
			""").latest().label());
	}

	@Test
	void futureOrOldTimestampsAreRetainedAndUnknownFloorsAreNotGuessed() {
		var snapshot = fromRuns("""
			{"type":"DUNGEON","completion_ts":4102444800000,"dungeon_type":"new_mode","dungeon_tier":7}
			""");
		assertEquals(4102444800000L, snapshot.latest().completedAt());
		assertEquals("Dungeons - Unknown floor", snapshot.latest().label());
	}

	private static CurrentPartySnapshot fromRuns(String runs) {
		return CurrentPartySnapshot.fromMember(json("{\"dungeons\":{\"treasures\":{\"runs\":[" + runs + "]}}}"));
	}

	private static JsonObject json(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}
}
