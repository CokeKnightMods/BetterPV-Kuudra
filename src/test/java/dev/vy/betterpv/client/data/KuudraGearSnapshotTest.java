package dev.vy.betterpv.client.data;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.vy.betterpv.client.data.KuudraGearSnapshot.*;

class KuudraGearSnapshotTest {
	private static final Coverage VISIBLE = new Coverage(true, true, true, true);

	@Test void reforgeIsNotDuplexAndTheEnchantIsRecognized() {
		var spiritual = gear("TERMINATOR", "spiritual", Map.of("power", 7), List.of());
		assertEquals(State.PARTIAL, check(List.of(spiritual), "Duplex Terminator").state());
		var duplex = gear("TERMINATOR", "precise", Map.of("ultimate_reiterate", 5), List.of());
		assertEquals(State.FOUND, check(List.of(spiritual, duplex), "Duplex Terminator").state());
		assertSame(duplex, check(List.of(spiritual, duplex), "Duplex Terminator").items().getFirst());
	}

	@Test void rendUsesItsOwnBowAndTheActualBonemerangId() {
		var rend = gear("TERMINATOR", "", Map.of("ultimate_rend", 5), List.of());
		var duplex = gear("TERMINATOR", "", Map.of("ultimate_reiterate", 5, "power", 7), List.of());
		var bone = gear("BONE_BOOMERANG", "", Map.of("rend", 5), List.of());
		assertSame(rend, check(List.of(rend, duplex), "Rend Terminator").items().getFirst());
		assertEquals(State.FOUND, check(List.of(bone), "Rend Bonemerang").state());
	}

	@Test void impactRequiresThreeDifferentScrolls() {
		var partial = gear("HYPERION", "", Map.of(), List.of("IMPLOSION_SCROLL", "IMPLOSION_SCROLL", "SHADOW_WARP_SCROLL"));
		var full = gear("SCYLLA", "", Map.of(), List.of("IMPLOSION_SCROLL", "WITHER_SHIELD_SCROLL", "SHADOW_WARP_SCROLL"));
		assertEquals(State.PARTIAL, check(List.of(partial), "Wither Impact").state());
		assertEquals(State.FOUND, check(List.of(partial, full), "Wither Impact").state());
		assertSame(full, check(List.of(partial, full), "Wither Impact").items().getFirst());
	}

	@Test void duplicatesCannotCompleteAnArmorSet() {
		var chest = gear("ELEGANT_TUXEDO_CHESTPLATE", "", Map.of(), List.of());
		var partial = check(List.of(chest, chest, chest), "Elegant Tuxedo");
		assertEquals(State.PARTIAL, partial.state());
		assertEquals(1, partial.items().size());
		assertEquals(State.FOUND, check(List.of(chest,
			gear("ELEGANT_TUXEDO_LEGGINGS", "", Map.of(), List.of()),
			gear("ELEGANT_TUXEDO_BOOTS", "", Map.of(), List.of())), "Elegant Tuxedo").state());
	}

	@Test void highestPrestigeArmorIsSelectedPerSlot() {
		var base = gear("TERROR_CHESTPLATE", "", Map.of(), List.of());
		var infernal = gear("INFERNAL_TERROR_CHESTPLATE", "", Map.of(), List.of());
		var check = check(List.of(base, infernal), "Terror armor");
		assertSame(infernal, check.items().getFirst());
		assertEquals(State.PARTIAL, check.state());
	}

	@Test void lesserFlaresAreNotSos() {
		assertEquals(State.NOT_SEEN, check(List.of(gear("WARNING_FLARE", "", Map.of(), List.of())), "SOS Flare").state());
		assertEquals(State.FOUND, check(List.of(gear("SOS_FLARE", "", Map.of(), List.of())), "SOS Flare").state());
	}

	@Test void hiddenApiDataStaysUnknown() {
		var hidden = coverage(JsonParser.parseString("{\"inventory\":{\"inv_contents\":null},\"pets_data\":{}}").getAsJsonObject());
		assertFalse(hidden.inventory());
		var snapshot = analyze(List.of(), List.of(), hidden);
		assertTrue(snapshot.sections().stream().flatMap(s -> s.checks().stream()).allMatch(c -> c.state() == State.UNKNOWN));
		var exposed = coverage(JsonParser.parseString("{\"inventory\":{\"inv_contents\":{\"data\":\"encoded\"}},\"pets_data\":{\"pets\":[]}}").getAsJsonObject());
		assertTrue(exposed.inventory());
		assertTrue(exposed.pets());
	}

	@Test void dragonVariantsAndLevelsAreKeptWithoutInventingRoles() {
		var snapshot = analyze(List.of(), List.of(new Pet("GOLDEN_DRAGON", 150, "PET_ITEM_SHELMET", true),
			new Pet("GOLDEN_DRAGON", 200, "PET_ITEM_TIGER_CROCHET_PLUSHIE", false),
			new Pet("GOLDEN_DRAGON", 100, "PET_ITEM_SHELMET", false)), VISIBLE);
		var checks = snapshot.sections().get(2).checks();
		assertEquals(2, checks.size());
		assertTrue(checks.stream().anyMatch(c -> c.label().contains("150") && c.summary().contains("Active")));
		assertTrue(checks.stream().allMatch(c -> c.state() == State.FOUND));
	}

	@Test void enchantmentsAndAttributesRemainSeparateAndVisible() {
		var item = new GearItem("INFERNAL_TERROR_BOOTS", "Boots", "wardrobe", "ancient", 10,
			Map.of("ultimate_legion", 5), Map.of("lifeline", 10, "mana_pool", 8), List.of(), List.of());
		assertTrue(item.details().containsAll(List.of("Legion 5", "Lifeline 10", "Mana Pool 8", "10 stars")));
	}

	private static GearItem gear(String id, String reforge, Map<String, Integer> enchants, List<String> scrolls) {
		return new GearItem(id, id, "inventory", reforge, 0, enchants, Map.of(), scrolls, List.of());
	}
	private static Check check(List<GearItem> items, String label) {
		return analyze(items, List.of(), VISIBLE).sections().stream().flatMap(s -> s.checks().stream())
			.filter(c -> c.label().equals(label)).findFirst().orElseThrow();
	}
}
