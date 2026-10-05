package dev.vy.betterpv.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import dev.vy.betterpv.client.networth.NbtAttrs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Inventory evidence for Kuudra; not a rating of a player's skill or live equipped build. */
public record KuudraGearSnapshot(boolean loaded, Coverage coverage, List<Section> sections) {
	public enum State { FOUND, PARTIAL, NOT_SEEN, UNKNOWN }
	public record Coverage(boolean inventory, boolean armor, boolean equipment, boolean pets) {}
	public record Section(String title, List<Check> checks) {
		public Section { checks = List.copyOf(checks); }
	}
	public record Check(String label, State state, String summary, List<GearItem> items, List<String> notes) {
		public Check { items = List.copyOf(items); notes = List.copyOf(notes); }
	}
	/** Plain data keeps gear matching independent of Minecraft's rendering and API requests. */
	public record GearItem(String id, String name, String location, String reforge, int stars,
		Map<String, Integer> enchantments, Map<String, Integer> attributes, List<String> scrolls, List<String> lore) {
		public GearItem {
			id = id.toUpperCase(Locale.ROOT);
			name = name == null || name.isBlank() ? pretty(id) : name.replaceAll("§.", "");
			reforge = reforge == null ? "" : reforge;
			enchantments = Map.copyOf(enchantments);
			attributes = Map.copyOf(attributes);
			scrolls = List.copyOf(scrolls);
			lore = lore == null ? List.of() : List.copyOf(lore);
		}

		public int enchant(String... keys) {
			int level = 0;
			for (String key : keys) level = Math.max(level, this.enchantments.getOrDefault(key, 0));
			return level;
		}

		public List<String> details() {
			List<String> out = new ArrayList<>();
			if (!this.reforge.isBlank()) out.add(pretty(this.reforge));
			if (this.stars > 0) out.add(this.stars + " stars");
			addEnchant(out, "Rend", "ultimate_rend", "rend");
			addEnchant(out, "Duplex", "ultimate_reiterate", "ultimate_duplex", "duplex");
			addEnchant(out, "Fatal Tempo", "ultimate_fatal_tempo", "fatal_tempo");
			addEnchant(out, "Chimera", "ultimate_chimera", "chimera");
			addEnchant(out, "Power", "power");
			addEnchant(out, "Cubism", "cubism");
			addEnchant(out, "Overload", "overload");
			addEnchant(out, "Legion", "ultimate_legion", "legion");
			addEnchant(out, "Ferocious Mana", "ferocious_mana");
			addEnchant(out, "Strong Mana", "strong_mana");
			addEnchant(out, "Hardened Mana", "hardened_mana");
			this.attributes.entrySet().stream().filter(e -> e.getValue() > 0).sorted(Map.Entry.comparingByKey())
				.forEach(e -> out.add(pretty(e.getKey()) + " " + e.getValue()));
			return List.copyOf(out);
		}

		private void addEnchant(List<String> out, String label, String... keys) {
			int level = enchant(keys);
			if (level > 0) out.add(label + " " + level);
		}
	}

	public record Pet(String type, int level, String heldItem, boolean active) {}

	public KuudraGearSnapshot { sections = List.copyOf(sections); }

	public static KuudraGearSnapshot empty() {
		return new KuudraGearSnapshot(false, new Coverage(false, false, false, false), List.of());
	}

	public static KuudraGearSnapshot from(JsonObject member, Map<String, List<InventoryDecoder.Stack>> categories, PetSnapshot pets) {
		List<GearItem> items = new ArrayList<>();
		// Do not count museum donations, sacks or an unowned recipe preview as usable gear.
		Set<String> locations = Set.of("inventory", "armor", "equipment", "wardrobe", "enderchest", "storage", "personal_vault");
		for (var category : categories.entrySet()) {
			if (!locations.contains(category.getKey())) continue;
			for (InventoryDecoder.Stack stack : category.getValue()) {
				if (stack == null || stack.id() == null || stack.id().isBlank()) continue;
				var nbt = stack.extraAttributes();
				items.add(new GearItem(stack.id(), stack.displayName(), category.getKey(), NbtAttrs.string(nbt, "modifier"),
					Math.max(NbtAttrs.intValue(nbt, "upgrade_level", 0), NbtAttrs.intValue(nbt, "dungeon_item_level", 0)),
					NbtAttrs.intMap(nbt, "enchantments"), NbtAttrs.intMap(nbt, "attributes"),
					NbtAttrs.stringList(nbt, "ability_scroll"), stack.lore()));
			}
		}
		List<Pet> petItems = pets.pets().stream()
			.map(p -> new Pet(p.type(), p.level(), p.heldItem(), p.active())).toList();
		return analyze(items, petItems, coverage(member));
	}

	public static Coverage coverage(JsonObject member) {
		JsonObject inv = object(member, "inventory");
		if (inv == null) inv = object(member, "inventories");
		JsonObject loadout = object(member, "loadout");
		JsonObject pets = object(member, "pets_data");
		return new Coverage(
			hasData(inv, "inv_contents"),
			hasData(inv, "inv_armor") || hasData(inv, "wardrobe_contents") || hasData(loadout, "armor"),
			hasData(inv, "equipment_contents") || hasData(loadout, "equipment"),
			isArray(pets, "pets") || isArray(member, "pets")
		);
	}

	public static KuudraGearSnapshot analyze(List<GearItem> items, List<Pet> pets, Coverage coverage) {
		List<Section> sections = new ArrayList<>();
		sections.add(new Section("Rend & swaps", List.of(
			weapon(items, coverage, "Rend Terminator", Set.of("TERMINATOR"), "ultimate_rend", "rend"),
			weapon(items, coverage, "Rend Bonemerang", Set.of("BONE_BOOMERANG", "BONEMERANG"), "ultimate_rend", "rend"),
			weapon(items, coverage, "Ragnarock Axe", Set.of("RAGNAROCK_AXE")),
			weapon(items, coverage, "Atomsplit Katana", Set.of("ATOMSPLIT_KATANA")),
			armorSet(items, coverage, "Elegant Tuxedo", "ELEGANT_TUXEDO", List.of("CHESTPLATE", "LEGGINGS", "BOOTS")),
			armorSet(items, coverage, "Reaper set", "REAPER", List.of("CHESTPLATE", "LEGGINGS", "BOOTS"))
		)));
		sections.add(new Section("DPS & clear", List.of(
			weapon(items, coverage, "Duplex Terminator", Set.of("TERMINATOR"), "ultimate_reiterate", "ultimate_duplex", "duplex"),
			weapon(items, coverage, "Fatal Tempo Terminator", Set.of("TERMINATOR"), "ultimate_fatal_tempo", "fatal_tempo"),
			impact(items, coverage),
			weapon(items, coverage, "Warden Helmet", Set.of("WARDEN_HELMET")),
			armorSet(items, coverage, "Terror armor", "TERROR", List.of("CHESTPLATE", "LEGGINGS", "BOOTS")),
			armorSet(items, coverage, "Aurora armor", "AURORA", List.of("CHESTPLATE", "LEGGINGS", "BOOTS")),
			weapon(items, coverage, "Wither Goggles", Set.of("WITHER_GOGGLES"))
		)));
		sections.add(new Section("Golden Dragon variants", dragonChecks(pets, coverage)));
		sections.add(new Section("Equipment & utility", List.of(
			weapon(items, coverage, "Lava Shell Necklace", Set.of("LAVA_SHELL_NECKLACE")),
			weapon(items, coverage, "Molten Necklace", Set.of("MOLTEN_NECKLACE")),
			weapon(items, coverage, "Molten Cloak", Set.of("MOLTEN_CLOAK")),
			weapon(items, coverage, "Molten Belt", Set.of("MOLTEN_BELT")),
			weapon(items, coverage, "Molten Bracelet", Set.of("MOLTEN_BRACELET")),
			weapon(items, coverage, "Gauntlet of Contagion", Set.of("GAUNTLET_OF_CONTAGION")),
			weapon(items, coverage, "SOS Flare", Set.of("SOS_FLARE")),
			weapon(items, coverage, "Plasmaflux / Overflux", Set.of("PLASMAFLUX_POWER_ORB", "OVERFLUX_POWER_ORB")),
			weapon(items, coverage, "Wand of Strength", Set.of("WAND_OF_STRENGTH")),
			weapon(items, coverage, "End Stone Sword", Set.of("END_STONE_SWORD"))
		)));
		return new KuudraGearSnapshot(true, coverage, sections);
	}

	private static Check weapon(List<GearItem> items, Coverage coverage, String label, Set<String> ids, String... enchant) {
		List<GearItem> matches = items.stream().filter(i -> ids.contains(i.id())).toList();
		Comparator<GearItem> order = Comparator.comparingInt((GearItem i) -> i.enchant(enchant))
			.thenComparingInt(KuudraGearSnapshot::quality);
		GearItem best = matches.stream().max(order).orElse(null);
		if (best == null) return unseen(label, coverage);
		boolean hasEnchant = enchant.length == 0 || best.enchant(enchant) > 0;
		List<String> notes = hasEnchant ? List.of() : List.of("Item found, but the requested ultimate enchant is absent.");
		String detail = hasEnchant ? summary(best) : "Item found; enchant missing";
		return new Check(label, hasEnchant ? State.FOUND : State.PARTIAL, detail, List.of(best), notes);
	}

	private static Check impact(List<GearItem> items, Coverage coverage) {
		Set<String> blades = Set.of("HYPERION", "SCYLLA", "ASTRAEA", "VALKYRIE", "NECRON_BLADE");
		GearItem best = items.stream().filter(i -> blades.contains(i.id()))
			.max(Comparator.comparingInt(KuudraGearSnapshot::impactScrolls).thenComparingInt(KuudraGearSnapshot::quality)).orElse(null);
		if (best == null) return unseen("Wither Impact", coverage);
		int scrolls = impactScrolls(best);
		return new Check("Wither Impact", scrolls == 3 ? State.FOUND : State.PARTIAL,
			pretty(best.id()) + " · " + scrolls + "/3 scrolls", List.of(best),
			List.of("Implosion + Shadow Warp + Wither Shield are checked separately."));
	}

	private static int impactScrolls(GearItem item) {
		return (int) Set.of("IMPLOSION_SCROLL", "SHADOW_WARP_SCROLL", "WITHER_SHIELD_SCROLL").stream()
			.filter(item.scrolls()::contains).count();
	}

	private static Check armorSet(List<GearItem> items, Coverage coverage, String label, String set, List<String> slots) {
		List<GearItem> found = new ArrayList<>();
		for (String slot : slots) {
			items.stream().filter(i -> withoutPrestige(i.id()).equals(set + "_" + slot))
				.max(Comparator.comparingInt(KuudraGearSnapshot::quality)).ifPresent(found::add);
		}
		if (found.isEmpty()) return unseen(label, coverage);
		String levels = found.stream().map(i -> prestige(i.id()) + " " + i.stars() + "*").distinct()
			.collect(java.util.stream.Collectors.joining(", "));
		return new Check(label, found.size() == slots.size() ? State.FOUND : State.PARTIAL,
			found.size() + "/" + slots.size() + " pieces · " + levels, found,
			List.of("Best visible piece per slot; these pieces may be in different loadouts."));
	}

	private static List<Check> dragonChecks(List<Pet> pets, Coverage coverage) {
		List<Check> checks = new ArrayList<>();
		// Keep held-item variants separate instead of silently choosing one Golden Dragon for both roles.
		Map<String, Pet> variants = new java.util.TreeMap<>();
		for (Pet pet : pets) {
			if (!"GOLDEN_DRAGON".equalsIgnoreCase(pet.type())) continue;
			variants.merge(pet.heldItem(), pet, (a, b) -> a.level() >= b.level() ? a : b);
		}
		for (Pet pet : variants.values()) {
			String held = pet.heldItem().isBlank() ? "No held item" : pretty(pet.heldItem().replaceFirst("^PET_ITEM_", ""));
			checks.add(new Check("Golden Dragon · Lv " + pet.level(), State.FOUND, held + (pet.active() ? " · Active" : ""),
				List.of(), List.of("Held item: " + held, "Level: " + pet.level() + "/200",
					"Pet ownership does not establish which pet was used in a run.")));
		}
		if (checks.isEmpty()) checks.add(new Check("Golden Dragon", coverage.pets() ? State.NOT_SEEN : State.UNKNOWN,
			coverage.pets() ? "Not seen in pet data" : "Pet data unavailable", List.of(), List.of()));
		return List.copyOf(checks);
	}

	private static Check unseen(String label, Coverage coverage) {
		boolean any = coverage.inventory() || coverage.armor() || coverage.equipment();
		return new Check(label, any ? State.NOT_SEEN : State.UNKNOWN,
			any ? "Not seen in available data" : "Inventory data unavailable", List.of(),
			List.of("Only exposed inventory, storage, armor and equipment are checked.",
				"Not seen does not prove that this player does not own the item."));
	}

	private static String summary(GearItem item) {
		List<String> details = item.details();
		return details.isEmpty() ? pretty(item.id()) : String.join(" · ", details);
	}

	private static int quality(GearItem item) {
		int tier = switch (prestige(item.id())) { case "Infernal" -> 4; case "Fiery" -> 3; case "Burning" -> 2; case "Hot" -> 1; default -> 0; };
		return tier * 100_000 + item.stars() * 1000 + item.enchant("ultimate_chimera", "chimera") * 100
			+ item.enchant("power") * 10 + item.enchant("cubism");
	}

	private static String withoutPrestige(String id) {
		return id.replaceFirst("^(INFERNAL_|FIERY_|BURNING_|HOT_)", "");
	}

	private static String prestige(String id) {
		for (String prefix : List.of("INFERNAL_", "FIERY_", "BURNING_", "HOT_")) {
			if (id.startsWith(prefix)) return pretty(prefix.substring(0, prefix.length() - 1));
		}
		return "Base";
	}

	private static boolean isArray(JsonObject obj, String key) {
		return obj != null && obj.has(key) && obj.get(key).isJsonArray();
	}

	private static boolean hasData(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return false;
		JsonElement field = obj.get(key);
		if (field.isJsonObject()) {
			JsonObject value = field.getAsJsonObject();
			if (value.has("data")) return value.get("data").isJsonPrimitive() && !value.get("data").getAsString().isBlank();
			return !value.isEmpty();
		}
		return field.isJsonPrimitive() && !field.getAsString().isBlank();
	}

	private static JsonObject object(JsonObject obj, String key) {
		return obj != null && obj.has(key) && obj.get(key).isJsonObject() ? obj.getAsJsonObject(key) : null;
	}

	public static String pretty(String id) {
		StringBuilder out = new StringBuilder();
		for (String part : id.toLowerCase(Locale.ROOT).split("_")) {
			if (part.isBlank()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out.toString();
	}
}
