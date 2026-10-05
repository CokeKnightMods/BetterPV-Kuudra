# BetterPV — Kuudra & Current Party

[![Join the Discord](https://img.shields.io/discord/1487928162636533871?label=Join%20the%20Discord&logo=discord&color=5865F2&logoColor=white)](https://discord.gg/R5NdTVRDpb)

An unofficial custom build of [Vyriv/BetterPV](https://github.com/Vyriv/BetterPV), a profile viewer for Hypixel SkyBlock.
Original author: Vy. Custom Kuudra and Current Party additions: CokeKnightMods. Licensed under MPL-2.0.

Use `/pv <player>` to open it.

## Custom build for Minecraft 26.1.2

This custom build targets Minecraft **26.1.2**, Fabric Loader **0.19.3+** and Java **25+**.
Replace the previous BetterPV jar in your instance's `mods` folder; keep Fabric API installed.
Restart Minecraft after replacing the jar. This is a replacement for BetterPV, not a second mod to install alongside it.
If another mod owns `/pv`, use `/betterpv pv <player> kuudra` or `/betterpv pv <player> party`.

### Kuudra

Open `/pv <player> kuudra` (aliases `kudra` / `qdra`) or click the magma-cream tab.
The two panels scroll independently; hover over a row for item details and storage location.

- Rend Terminator, Rend Bonemerang, Ragnarock Axe, Atomsplit, Elegant Tuxedo and Reaper armor
- Duplex / Fatal Tempo Terminator, Wither Impact with all three scrolls, Warden Helmet, Terror, Aurora and Wither Goggles
- Golden Dragon variants grouped by held item, including actual level and active status
- Molten equipment, Lava Shell Necklace, Contagion, SOS Flare, Plasmaflux / Overflux and utility swaps
- Relevant reforges, stars, ultimate enchants, Power, Cubism, Overload, mana enchants and item attributes in tooltips
- Magical Power, selected power, profile bank, Combat, Foraging, Catacombs, SkyBlock level and T1–T5 completions

The selection of checks is informed by [Phantom Addons' public Kuudra profile feature](https://github.com/PhantomOrigin/Phantom-Addons).
The matcher is implemented here and uses BetterPV's existing profile response; it does not depend on a Phantom service.
Items are searched in exposed inventory, equipment, armor, wardrobe, ender chest, backpacks and personal vault.
The best visible armor piece is chosen separately per slot, so pieces may come from different loadouts.
`Found` describes inventory evidence, not a readiness rating or proof of what a player used in a run.
`Partial` means an item lacks the requested enchant/scrolls or a set lacks a distinct slot.
`Not seen` only refers to available data. Hidden bank, skill, inventory and pet data remain `Unknown`.
MP is labeled as an estimate from accessories or as the API's historical peak. Golden Dragon variants are not assigned speculative DPS/Rend roles.

### Current Party

Open `/pv <player> party`, or select the player-head tab next to Dungeons.

The tab reads `members[uuid].dungeons.treasures.runs` from the displayed profile using BetterPV's
existing API connection. It shows the newest completed run (or newest Dungeon/Kuudra run), its
participants, floor/tier and completion time. Names are resolved asynchronously; clicking a name
opens that player's profile. Refresh has a 30-second cooldown and remains subject to API caching.
This is historical run data: membership changes after the run are not visible. If run data is
missing, the tab shows an empty state instead of guessing a party. No additional API key is needed
by this feature; BetterPV's normal signed-in Minecraft session is still required.

Build and run the parser regression tests with JDK 25:

```sh
./gradlew :26.1.2:build
```

The 26.1.2 build and 20 automated tests were verified. In-game rendering and authenticated Hypixel
responses have not been tested in this environment.

## Supported versions

- This custom release: Minecraft 26.1.2
- The upstream project also contains a 26.2 target; no custom 26.2 release is provided here.

## Features

- 17 profile tabs covering pretty much every part of SkyBlock
- Kuudra: Rend and DPS gear, armor, equipment, pet variants and essential profile stats in a standalone tab. Open with `/pv <player> kuudra`.
- Current Party: participants from the latest saved Dungeon or Kuudra run, with completion time, mode filters, profile links and manual refresh. These are completed-run records, not a live party roster. Open directly with `/pv <player> party`.
- Home overview with skills, slayers, SkyBlock level, networth, weight and stats
- Dungeon stats, floor times, classes, essence shops and XP calculators
- Full inventory viewer with backpacks, wardrobe, sacks, accessory bag, carnival masks and more
- Pets, auctions, collections and minions
- Garden, Mining, Foraging, Fishing and Crimson Isle progression
- Rift, Museum and Bestiary
- Bingo and Chocolate Factory stats
- Profile switching with co-op member info
- Search for players, inventories and bestiary entries
- Senither + Lily weight
- Networth with multiple calculation modes
- Estimated item values on tooltips, with a clickable value breakdown
- Skill, Slayer and Dungeon XP calculators
- Custom item textures and proper SkyBlock item tooltips

## Commands

`/pv`

`/pv <player>`

`/pv <player> <page>`

Running `/pv` without a name opens your own profile.

## Credits

BetterPV uses data, APIs and formulas from a few community projects:

- [NotEnoughUpdates / NEU-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) - items, textures, pets, bestiary data and other SkyBlock data
- [Athen](https://athen.aerii.xyz/prices) - prices
- [SkyHelper Prices](https://github.com/SkyHelperBot/Prices) - price data and fallback pricing
- [SkyCofl](https://sky.coflnet.com) - auction history
- [EliteBot](https://api.elitebot.dev) - Jacob contest history and farming weight
- [SkyCrypt](https://skycrypt.co) - networth/application worth references
- Senither - weight formula
- Lily - weight formula
- ninjune / Vinxey - ColeWeight display thresholds

## Links

- [Website](https://vyriv.dev)
- [GitHub](https://github.com/Vyriv)
