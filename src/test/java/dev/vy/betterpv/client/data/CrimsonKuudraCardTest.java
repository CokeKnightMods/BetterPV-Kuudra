package dev.vy.betterpv.client.data;

import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrimsonKuudraCardTest {
	@Test void tuxedoHasThreeDistinctVisibleSlots() {
		var pieces = CrimsonKuudraCard.scanTuxedoArmor(List.of(
			item("ELEGANT_TUXEDO_CHESTPLATE", "ancient", 0, 0),
			item("ELEGANT_TUXEDO_LEGGINGS", "ancient", 0, 0),
			item("ELEGANT_TUXEDO_BOOTS", "ancient", 0, 0)));
		assertEquals(List.of("CHESTPLATE", "LEGGINGS", "BOOTS"), pieces.stream().map(CrimsonKuudraCard.ArmorPiece::slot).toList());
		assertTrue(pieces.stream().allMatch(CrimsonKuudraCard.ArmorPiece::owned));
	}

	@Test void repeatedOrCheaperTuxPiecesDoNotCompleteElegantTuxedo() {
		var chest = item("ELEGANT_TUXEDO_CHESTPLATE", "ancient", 0, 0);
		var pieces = CrimsonKuudraCard.scanTuxedoArmor(List.of(chest, chest,
			item("FANCY_TUXEDO_LEGGINGS", "ancient", 0, 0), item("CHEAP_TUXEDO_BOOTS", "ancient", 0, 0)));
		assertEquals(1, pieces.stream().filter(CrimsonKuudraCard.ArmorPiece::owned).count());
		assertEquals("ELEGANT_TUXEDO_BOOTS", pieces.get(2).iconId());
	}

	@Test void rcmTerrorOnlyUsesMageReforgedTerror() {
		var pieces = CrimsonKuudraCard.scanRcmTerrorArmor(List.of(
			item("INFERNAL_TERROR_CHESTPLATE", "loving", 10, 0),
			item("INFERNAL_TERROR_LEGGINGS", "necrotic", 10, 0),
			item("INFERNAL_TERROR_BOOTS", "ancient", 10, 0),
			item("INFERNAL_AURORA_BOOTS", "necrotic", 10, 0)));
		assertEquals(2, pieces.stream().filter(CrimsonKuudraCard.ArmorPiece::owned).count());
		assertFalse(pieces.get(2).owned());
		assertTrue(pieces.getFirst().label().contains("Loving"));
	}

	@Test void rcmPrefersPrestigeBeforeStarsAndKeepsItemAppearance() {
		var pieces = CrimsonKuudraCard.scanRcmTerrorArmor(List.of(
			item("INFERNAL_TERROR_CHESTPLATE", "loving", 0, 0),
			item("FIERY_TERROR_CHESTPLATE", "loving", 10, 0)));
		assertEquals("INFERNAL_TERROR_CHESTPLATE", pieces.getFirst().iconId());
		assertEquals(0x123456, pieces.getFirst().dyeColor());
		assertEquals(List.of("Item lore"), pieces.getFirst().lore());
	}

	@Test void normalBonemerangDoesNotCountAsRendAndHighestRendWins() {
		assertFalse(CrimsonKuudraCard.rendBonemerang(List.of(item("BONE_BOOMERANG", "spiritual", 10, 0))).owned());
		var rend = CrimsonKuudraCard.rendBonemerang(List.of(
			item("BONE_BOOMERANG", "spiritual", 10, 3), item("BONE_BOOMERANG", "precise", 0, 5)));
		assertTrue(rend.owned());
		assertEquals(List.of("Rend 5"), rend.details());
	}

	@Test void aSingleDragonCannotBeCountedTwice() {
		var first = pet("first", 200, "PET_ITEM_SHELMET");
		assertNull(CrimsonKuudraCard.secondGoldenDragonEntry(List.of(first)));
		assertNull(CrimsonKuudraCard.secondGoldenDragonEntry(List.of(first, first)));
	}

	@Test void secondDragonPrefersAnotherHeldItemAndKeepsActualLevel() {
		var alternate = pet("third", 150, "PET_ITEM_ANTIQUE_REMEDIES");
		assertSame(alternate, CrimsonKuudraCard.secondGoldenDragonEntry(List.of(
			pet("first", 200, "PET_ITEM_SHELMET"), pet("second", 200, "PET_ITEM_SHELMET"), alternate)));
	}

	@Test void secondDragonCanHaveTheSameHeldItemWhenItIsAnotherPet() {
		var second = pet("second", 199, "PET_ITEM_SHELMET");
		assertSame(second, CrimsonKuudraCard.secondGoldenDragonEntry(List.of(pet("first", 200, "PET_ITEM_SHELMET"), second)));
	}

	private static InventoryDecoder.Stack item(String id, String reforge, int stars, int rend) {
		CompoundTag nbt = new CompoundTag();
		nbt.putString("modifier", reforge);
		nbt.putInt("upgrade_level", stars);
		CompoundTag enchants = new CompoundTag();
		if (rend > 0) enchants.putInt("ultimate_rend", rend);
		nbt.put("enchantments", enchants);
		return new InventoryDecoder.Stack(id, 1, nbt, List.of("Item lore"), false, "§6" + id, 0x123456, "", "");
	}

	private static PetSnapshot.Entry pet(String uuid, int level, String heldItem) {
		return new PetSnapshot.Entry("GOLDEN_DRAGON", "Golden Dragon", "LEGENDARY", 4, "GOLDEN_DRAGON;4",
			level, 200, 0, 0, 0, 0, 0, false, heldItem, 0, "", uuid, 0);
	}
}
