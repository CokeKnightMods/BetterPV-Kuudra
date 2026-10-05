package dev.vy.betterpv.client.gui;

import com.google.gson.JsonObject;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.api.BetterPvSessionAuth;
import dev.vy.betterpv.client.api.HypixelApiClient;
import dev.vy.betterpv.client.api.ProfileFetcher;
import dev.vy.betterpv.client.data.MiscStatsSnapshot;
import dev.vy.betterpv.client.data.ProfileSnapshot;
import dev.vy.betterpv.client.gui.auctions.AuctionPage;
import dev.vy.betterpv.client.gui.bestiary.BestiaryPage;
import dev.vy.betterpv.client.gui.collections.CollectionsPage;
import dev.vy.betterpv.client.gui.crimson.CrimsonPage;
import dev.vy.betterpv.client.gui.dungeons.DungeonPage;
import dev.vy.betterpv.client.gui.events.EventsPage;
import dev.vy.betterpv.client.gui.fishing.FishingPage;
import dev.vy.betterpv.client.gui.foraging.ForagingPage;
import dev.vy.betterpv.client.gui.garden.GardenPage;
import dev.vy.betterpv.client.gui.home.HomePage;
import dev.vy.betterpv.client.gui.home.page.MiscStatsPage;
import dev.vy.betterpv.client.gui.inventories.InventoryPage;
import dev.vy.betterpv.client.gui.mining.MiningPage;
import dev.vy.betterpv.client.gui.museum.MuseumPage;
import dev.vy.betterpv.client.gui.nav.IconButtonBar;
import dev.vy.betterpv.client.gui.nav.MuseumSort;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.gui.nav.PvTab;
import dev.vy.betterpv.client.gui.pets.PetsPage;
import dev.vy.betterpv.client.gui.party.CurrentPartyPage;
import dev.vy.betterpv.client.gui.rift.RiftPage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ProfileViewerScreen extends Screen {
	private static final int PAD = 8;
	private static final long OPEN_ANIM_MS = 260L;
	private static final float OPEN_SCALE_START = 0.12F;

	private final String requestedName;
	private final HomePage homePage;
	private final MiscStatsPage homeMiscPage = new MiscStatsPage();
	private final DungeonPage dungeonPage = new DungeonPage();
	private final CurrentPartyPage currentPartyPage = new CurrentPartyPage(this);
	private final dev.vy.betterpv.client.gui.kuudra.KuudraGearPage kuudraGearPage = new dev.vy.betterpv.client.gui.kuudra.KuudraGearPage();
	private final InventoryPage inventoryPage = new InventoryPage();
	private final PetsPage petsPage = new PetsPage();
	private final AuctionPage auctionPage = new AuctionPage();
	private final CollectionsPage collectionsPage = new CollectionsPage();
	private final GardenPage gardenPage = new GardenPage();
	private final MiningPage miningPage = new MiningPage();
	private final ForagingPage foragingPage = new ForagingPage();
	private final FishingPage fishingPage = new FishingPage();
	private final CrimsonPage crimsonPage = new CrimsonPage();
	private final RiftPage riftPage = new RiftPage();
	private final MuseumPage museumPage = new MuseumPage();
	private final BestiaryPage bestiaryPage = new BestiaryPage();
	private final EventsPage eventsPage = new EventsPage();
	private final IconButtonBar topBar = new IconButtonBar();
	private final IconButtonBar sideBar = new IconButtonBar();
	private final IconButtonBar inventoryBar = new IconButtonBar();
	private final Map<PvTab, PvSubTab> subSelection = new EnumMap<>(PvTab.class);
	private final long openAnimStartMs = System.currentTimeMillis();
	private final ProfileSelectorController profileSelector = new ProfileSelectorController(this);
	private final ProfileScreenDataLoader dataLoader = new ProfileScreenDataLoader(this);
	private final InventorySplitLayout inventoryLayout = new InventorySplitLayout();
	private final BestiarySplitLayout bestiaryLayout = new BestiarySplitLayout();
	private float openPivotX;
	private float openPivotY;

	private PvTab tab = PvTab.HOME;
	private MuseumSort museumSort = MuseumSort.ALL;
	private boolean fetchStarted;
	private boolean dataReady;
	/** Set when fetch fails (e.g. unknown IGN). Shown instead of infinite loading. */
	private String loadError;
	/** Bumps on each fetch/switch so stale async results cannot overwrite newer state. */
	private int loadGeneration;
	private final Consumer<ProfileFetcher.LoadedProfile> networthListener = this::onDeferredNetworth;
	private boolean breakScheduled;
	private String profileId;
	private UUID playerUuid;
	private EditBox inventorySearch;
	private String inventorySearchQuery = "";
	private List<IconButtonBar.Entry> topTabEntries;
	private PvTab cachedSideTab;
	private List<IconButtonBar.Entry> sideTabEntries = List.of();

	private JsonObject profilesRoot;
	private int panelXCache;
	private int panelYCache;
	private int panelWCache;
	private int panelHCache;
	private EditBox playerSearch;
	private String playerSearchError = "";
	private long playerSearchErrorUntilMs;

	public ProfileViewerScreen(String playerName) {
		this(playerName, PvTab.HOME);
	}

	public ProfileViewerScreen(String playerName, PvTab initialTab) {
		super(Component.translatable("betterpv.screen.title"));
		this.requestedName = playerName == null || playerName.isBlank() ? "?" : playerName.trim();
		this.homePage = new HomePage(ProfileSnapshot.loading(this.requestedName));
		this.tab = initialTab == null ? PvTab.HOME : initialTab;
		for (PvTab t : PvTab.values()) {
			PvSubTab[] subs = t.subTabs();
			if (subs.length > 0) {
				this.subSelection.put(t, subs[0]);
			}
		}
	}

	@Override
	protected void init() {
		super.init();
		this.inventorySearch = new EditBox(
			this.font,
			0,
			0,
			100,
			InventorySplitLayout.SEARCH_H,
			Component.translatable("betterpv.inv.search")
		);
		this.inventorySearch.setMaxLength(64);
		this.inventorySearch.setBordered(false);
		// EditBox colours are ARGB - stripping alpha makes typed text invisible.
		this.inventorySearch.setTextColor(PvDraw.COLOR_TEXT);
		this.inventorySearch.setTextColorUneditable(PvDraw.COLOR_MUTED);
		this.inventorySearch.setValue(this.inventorySearchQuery);
		this.inventorySearch.setResponder(value -> {
			this.inventorySearchQuery = value;
			this.inventoryPage.setSearchQuery(value);
			this.bestiaryPage.setSearchQuery(value);
		});
		this.inventorySearch.setVisible(false);
		this.addWidget(this.inventorySearch);

		this.playerSearch = new EditBox(
			this.font,
			0,
			0,
			120,
			InventorySplitLayout.SEARCH_H,
			Component.translatable("betterpv.home.search_hint")
		);
		this.playerSearch.setMaxLength(16);
		this.playerSearch.setBordered(false);
		this.playerSearch.setTextColor(PvDraw.COLOR_TEXT);
		this.playerSearch.setTextColorUneditable(PvDraw.COLOR_MUTED);
		this.playerSearch.setHint(Component.translatable("betterpv.home.search_hint"));
		this.playerSearch.setVisible(false);
		this.addWidget(this.playerSearch);

		if (this.fetchStarted) {
			return;
		}
		this.fetchStarted = true;
		int generation = ++this.loadGeneration;
		ProfileFetcher.prioritizeTab(this.tab);
		ProfileFetcher.addNetworthListener(this.networthListener);
		ProfileFetcher.fetch(this.requestedName, updated -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.screen != this || generation != this.loadGeneration) {
					return;
				}
				if (updated == null || !updated.ok()) {
					return;
				}
				applyLoadedProfile(updated);
			});
		}).whenComplete((loaded, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.screen != this || generation != this.loadGeneration) {
					return;
				}
				ProfileFetcher.LoadedProfile displayed = loaded;
				if (error != null || loaded == null) {
					BetterPV.LOGGER.warn(
						"Profile fetch failed for {}",
						this.requestedName,
						error
					);
					String message = error != null && error.getMessage() != null
						? error.getMessage()
						: "Profile fetch failed";
					displayed = ProfileFetcher.failed(this.requestedName, message);
				}
				if (!displayed.ok()) {
					BetterPV.LOGGER.warn("Profile fetch failed for {}: {}", this.requestedName, displayed.error());
					BetterPvSessionAuth.notifyPlayerIfNeeded();
					this.loadError = friendlyLoadError(displayed.error());
					return;
				}
				this.loadError = null;
				applyLoadedProfile(displayed);
				this.dataReady = true;
			});
		});
	}

	private void onDeferredNetworth(ProfileFetcher.LoadedProfile updated) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || updated == null || !updated.ok()) {
			return;
		}
		client.execute(() -> {
			if (client.screen != this || !this.dataReady) {
				return;
			}
			if (this.playerUuid != null && updated.snapshot() != null
				&& this.playerUuid.equals(updated.snapshot().playerUuid())
				&& (this.profileId == null || this.profileId.equals(updated.profileId()))) {
				applyLoadedProfile(updated);
			}
		});
	}

	@Override
	public void removed() {
		ProfileFetcher.removeNetworthListener(this.networthListener);
		super.removed();
	}

	void applyLoadedProfile(ProfileFetcher.LoadedProfile loaded) {
		if (loaded == null) {
			return;
		}
		UUID prevUuid = this.playerUuid;
		String prevProfileId = this.profileId;
		UUID nextUuid = loaded.snapshot() == null ? null : loaded.snapshot().playerUuid();
		String nextProfileId = loaded.profileId();
		boolean sameIdentity = prevUuid != null
			&& prevUuid.equals(nextUuid)
			&& prevProfileId != null
			&& prevProfileId.equals(nextProfileId);

		this.profileSelector.clearFooterNameCache();
		this.homePage.applyLoaded(
			loaded.snapshot(),
			loaded.senither(),
			loaded.lily(),
			loaded.networthNormal(),
			loaded.networthNonCosmetic(),
			loaded.networthUnsoulbound(),
			loaded.networthUnsoulboundNonCosmetic(),
			loaded.armor(),
			loaded.playerStats(),
			loaded.error()
		);
		this.homeMiscPage.reset();
		this.homeMiscPage.apply(loaded.misc() == null ? MiscStatsSnapshot.empty() : loaded.misc());
		this.homeMiscPage.setGuildClickHandler(this.dataLoader::ensureGuild);
		this.dungeonPage.apply(loaded.dungeons());
		this.inventoryPage.apply(loaded.inventories());
		this.petsPage.apply(loaded.pets());
		this.auctionPage.apply(loaded.auctions());
		this.collectionsPage.apply(loaded.collections());
		this.gardenPage.apply(loaded.garden());
		this.miningPage.apply(loaded.mining());
		this.foragingPage.apply(loaded.foraging());
		this.fishingPage.apply(loaded.fishing());
		this.crimsonPage.apply(loaded.crimson());
		this.riftPage.apply(loaded.rift());
		this.bestiaryPage.apply(loaded.bestiary());
		this.eventsPage.apply(loaded.events());
		if (loaded.museumMember() != null) {
			this.museumPage.applyMuseum(loaded.museumMember());
		} else if (!sameIdentity) {
			this.museumPage.reset();
		}
		this.profileId = loaded.profileId();
		this.playerUuid = nextUuid;
		if (loaded.profilesRoot() != null) {
			this.profilesRoot = loaded.profilesRoot();
		}
		String viewed = this.playerUuid == null ? "" : HypixelApiClient.undashed(this.playerUuid);
		this.currentPartyPage.apply(this.profilesRoot, this.profileId, this.playerUuid, this.homePage.playerName());
		this.kuudraGearPage.apply(loaded.crimson(), this.profilesRoot, this.profileId, this.playerUuid, sameIdentity);
		this.profileSelector.applyChoices(loaded.profiles(), viewed, this.homePage.playerName());
		if (!sameIdentity) {
			this.dataLoader.resetForNewLoad();
			if (loaded.museumMember() != null) {
				this.dataLoader.markMuseumLoaded();
			}
			this.eventsPage.resetBingoFetch();
			this.dataLoader.ensurePlayerRank();
		} else if (loaded.museumMember() != null) {
			this.dataLoader.markMuseumLoaded();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		this.topBar.clearHits();
		this.sideBar.clearHits();
		this.inventoryBar.clearHits();

		int topRoom = IconButtonBar.TAB + 4;
		int leftRoom = IconButtonBar.TAB + 4;

		int tabWidth = 8 + PvTab.values().length * (IconButtonBar.TAB + IconButtonBar.GAP) - IconButtonBar.GAP;
		int panelW = Math.min(520, Math.max(tabWidth, this.width - 80 - leftRoom));
		int contentH = this.homePage.preferredHeight(this.font, panelW - PAD * 2);
		if (this.tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_MISC) {
			contentH = Math.max(contentH, 220);
		}
		int maxPanelH = Math.max(200, this.height - topRoom - 24);
		int panelH = Math.min(maxPanelH, Math.max(200, contentH + PAD * 2));

		int panelX = (this.width - panelW) / 2 + leftRoom / 2;
		int panelY = (this.height - panelH - topRoom) / 2 + topRoom;
		this.panelXCache = panelX;
		this.panelYCache = panelY;
		this.panelWCache = panelW;
		this.panelHCache = panelH;

		PvDraw.fill(graphics, 0, 0, this.width, this.height, 0x99000000);

		float scale = openScale();
		this.openPivotX = panelX + panelW / 2.0F;
		this.openPivotY = panelY + panelH / 2.0F;
		boolean scaled = scale < 0.999F;
		if (scaled) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.openPivotX, this.openPivotY);
			graphics.pose().scale(scale, scale);
			graphics.pose().translate(-this.openPivotX, -this.openPivotY);
		}

		PvDraw.panel(graphics, panelX, panelY, panelW, panelH);

		if (!this.dataReady) {
			InventorySplitLayout.hideInventorySearch(this.inventorySearch);
			hidePlayerSearch();
			drawLoadingFace(graphics, panelX, panelY, panelW, panelH);
		} else {
			this.topBar.drawTopFrameTabs(
				graphics, this.font, panelX, panelY, mouseX, mouseY, topTabEntries(), this.tab
			);

			Object[] left = this.tab.leftTabs();
			if (left.length > 0) {
				List<IconButtonBar.Entry> side = sideTabEntries();
				Object selectedLeft = selectedLeftKey();
				this.sideBar.drawLeftFrameTabs(
					graphics, this.font, panelX, panelY, panelH, mouseX, mouseY, side, selectedLeft
				);
			}

			// Draw before body tooltips so item / pet tips are not covered by the search box.
			positionPlayerSearch(graphics, panelX, panelY, panelW, panelH);

			renderBody(graphics, panelX + PAD, panelY + PAD, panelW - PAD * 2, panelH - PAD * 2, mouseX, mouseY, delta);

			this.profileSelector.renderFooter(graphics, this.font, panelX, panelY, panelW, panelH, mouseX, mouseY);
			if (this.playerSearchErrorUntilMs > System.currentTimeMillis() && !this.playerSearchError.isBlank()) {
				PvDraw.text(
					graphics, this.font, this.playerSearchError,
					panelX + panelW - 2 - this.font.width(this.playerSearchError),
					panelY + panelH + 3 + InventorySplitLayout.SEARCH_H + 2,
					0xFFFF5555
				);
			}

			// Higher stratum than EditBox / search chrome so tall item tips are not covered
			// by the footer "Search player" field (vanilla deferred tips use the same pattern).
			graphics.nextStratum();
			DeferredTooltipLayer.render(
				graphics,
				this.font,
				this.width,
				this.height,
				panelX,
				panelY,
				panelW,
				panelH,
				mouseX,
				mouseY,
				this.tab,
				activeSub(PvSubTab.HOME_OVERVIEW),
				this.profileSelector,
				this.inventoryLayout,
				this.bestiaryLayout,
				this.inventoryBar,
				this.homePage,
				this.homeMiscPage,
				this.dungeonPage,
				this.petsPage,
				this.auctionPage,
				this.collectionsPage,
				this.gardenPage,
				this.miningPage,
				this.foragingPage,
				this.fishingPage,
				this.crimsonPage,
				this.kuudraGearPage,
				this.riftPage,
				this.museumPage,
				this.eventsPage,
				this.inventoryPage,
				this.bestiaryPage
			);

			if (this.tab == PvTab.DUNGEONS) {
				this.dungeonPage.renderOverlay(graphics, this.font, this.width, this.height, mouseX, mouseY);
			} else if (this.tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
				this.homePage.renderSlayerOverlay(graphics, this.font, this.width, this.height, mouseX, mouseY);
			} else if (this.tab.isInventorySplit()) {
				this.inventoryPage.renderOverlay(graphics, this.font, this.width, this.height, mouseX, mouseY);
			}
		}

		if (scaled) {
			graphics.pose().popMatrix();
		}

		PvDraw.text(
			graphics,
			this.font,
			Component.translatable("betterpv.screen.flip_tip").getString(),
			4,
			this.height - this.font.lineHeight - 4,
			0xFFFFFF55
		);
	}

	private float openScale() {
		long elapsed = System.currentTimeMillis() - this.openAnimStartMs;
		if (elapsed >= OPEN_ANIM_MS) {
			return 1.0F;
		}
		float t = Math.max(0.0F, Math.min(1.0F, elapsed / (float) OPEN_ANIM_MS));
		// Cubic ease-out: snappy expand from center.
		float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
		return OPEN_SCALE_START + (1.0F - OPEN_SCALE_START) * eased;
	}

	private boolean uiInteractive() {
		return this.dataReady && openScale() >= 0.98F;
	}

	private static String friendlyLoadError(String raw) {
		if (raw == null || raw.isBlank()) {
			return "Player not found";
		}
		String lower = raw.toLowerCase(java.util.Locale.ROOT);
		if (lower.contains("not found") || lower.contains("unknown") || lower.contains("no player")) {
			return "Player not found";
		}
		return raw;
	}

	private void drawLoadingFace(GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH) {
		if (this.loadError != null && !this.loadError.isBlank()) {
			int cx = panelX + panelW / 2;
			int cy = panelY + panelH / 2 - this.font.lineHeight;
			PvDraw.textCentered(g, this.font, this.loadError, cx, cy, 0xFFFF5555);
			PvDraw.textCentered(
				g,
				this.font,
				"Press Esc to close",
				cx,
				cy + this.font.lineHeight + 6,
				PvDraw.COLOR_MUTED
			);
			return;
		}
		long elapsedMs = System.currentTimeMillis() - this.openAnimStartMs;
		List<LoadingEgg.Stage> stages = LoadingEgg.stagesUnlocked(elapsedMs);
		int cx = panelX + panelW / 2;
		int maxW = panelW - 24;
		int lineH = this.font.lineHeight + 3;

		long phase = (System.currentTimeMillis() / 400L) % 4L;
		String dots = switch ((int) phase) {
			case 1 -> ".";
			case 2 -> "..";
			case 3 -> "...";
			default -> "";
		};
		String loading = "Loading Data" + dots;

		if (elapsedMs >= LoadingEgg.BREAK_AT_MS) {
			scheduleBreakFinale();
			PvDraw.fill(g, panelX + 2, panelY + 2, panelW - 4, panelH - 4, 0xCC330000);
		} else if (!stages.isEmpty() && stages.get(stages.size() - 1).shout()) {
			PvDraw.fill(g, panelX + 4, panelY + 4, panelW - 8, panelH - 8, 0x33AA0000);
		}

		// Loading Data + every unlocked threat, stacked oldest → newest.
		int rows = 1 + stages.size();
		int blockH = rows * lineH - 3;
		int topY = panelY + Math.max(10, (panelH - blockH) / 2);
		PvDraw.textCentered(g, this.font, loading, cx, topY, PvDraw.COLOR_MUTED);
		int ty = topY + lineH;
		for (LoadingEgg.Stage stage : stages) {
			drawEggLine(g, stage.line(), cx, ty, maxW);
			ty += lineH;
		}
	}

	private void drawEggLine(GuiGraphicsExtractor g, Component line, int cx, int y, int maxW) {
		int tw = this.font.width(line);
		if (tw <= maxW || tw <= 0) {
			PvDraw.textCentered(g, this.font, line, cx, y);
			return;
		}
		float scale = maxW / (float) tw;
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		PvDraw.text(g, this.font, line, -tw / 2, 0);
		g.pose().popMatrix();
	}

	private void scheduleBreakFinale() {
		if (this.breakScheduled) {
			return;
		}
		this.breakScheduled = true;
		BetterPV.LOGGER.warn("Loading egg hit finale for {} - starting limbo + fake ban", this.requestedName);
		LoadingEggFinale.start();
	}

	private Object selectedLeftKey() {
		if (this.tab == PvTab.MUSEUM) {
			return this.museumSort;
		}
		return this.subSelection.get(this.tab);
	}

	private List<IconButtonBar.Entry> topTabEntries() {
		if (this.topTabEntries == null) {
			List<IconButtonBar.Entry> entries = new ArrayList<>();
			for (PvTab t : PvTab.values()) {
				entries.add(new IconButtonBar.Entry(t, t.icon(), t.label(), () -> {
					if (t != PvTab.DUNGEONS) {
						this.dungeonPage.blurField();
					}
					this.homePage.forceCloseSbLevelOverlay();
					this.tab = t;
					ProfileFetcher.prioritizeTab(t);
				}));
			}
			this.topTabEntries = List.copyOf(entries);
		}
		return this.topTabEntries;
	}

	private List<IconButtonBar.Entry> sideTabEntries() {
		if (this.cachedSideTab == this.tab && !this.sideTabEntries.isEmpty()) {
			return this.sideTabEntries;
		}
		Object[] left = this.tab.leftTabs();
		List<IconButtonBar.Entry> side = new ArrayList<>(left.length);
		for (Object entry : left) {
			if (entry instanceof PvSubTab sub) {
				side.add(new IconButtonBar.Entry(
					entry, sub.icon(), sub.label(),
					() -> {
						this.homePage.forceCloseSbLevelOverlay();
						this.subSelection.put(this.tab, sub);
					},
					sub.textureIcon(), sub.textureSize()
				));
			} else if (entry instanceof MuseumSort sort) {
				side.add(new IconButtonBar.Entry(entry, sort.icon(), sort.label(), () -> {
					this.museumSort = sort;
					this.museumPage.setSort(sort);
				}));
			}
		}
		this.cachedSideTab = this.tab;
		this.sideTabEntries = List.copyOf(side);
		return this.sideTabEntries;
	}

	private void prepareStandardBody() {
		InventorySplitLayout.hideInventorySearch(this.inventorySearch);
		this.dungeonPage.blurField();
	}

	private PvSubTab activeSub(PvSubTab fallback) {
		return this.subSelection.getOrDefault(this.tab, fallback);
	}

	private void renderBody(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
		switch (this.tab) {
			case HOME -> {
				prepareStandardBody();
				PvSubTab homeSub = activeSub(PvSubTab.HOME_OVERVIEW);
				if (homeSub == PvSubTab.HOME_MISC) {
					this.homeMiscPage.render(
						g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height
					);
				} else {
					this.homePage.render(
						g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height,
						openScale(), this.openPivotX, this.openPivotY
					);
				}
			}
			case DUNGEONS -> {
				InventorySplitLayout.hideInventorySearch(this.inventorySearch);
				this.dungeonPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case INVENTORIES -> {
				this.dungeonPage.blurField();
				this.inventoryLayout.render(
					g, this.font, this.inventoryPage, this.inventoryBar, this.inventorySearch,
					this.inventorySearchQuery, this.width, this.height,
					x, y, w, h, mouseX, mouseY, delta
				);
			}
			case CURRENT_PARTY -> {
				prepareStandardBody();
				this.currentPartyPage.render(g, this.font, x, y, w, h, mouseX, mouseY);
			}
			case KUUDRA -> {
				prepareStandardBody();
				this.kuudraGearPage.render(g, this.font, x, y, w, h, mouseX, mouseY);
			}
			case BESTIARY -> {
				this.dungeonPage.blurField();
				this.bestiaryLayout.render(
					g, this.font, this.bestiaryPage, this.inventoryBar, this.inventorySearch,
					this.inventorySearchQuery, this.width, this.height,
					x, y, w, h, mouseX, mouseY, delta
				);
			}
			case PETS -> {
				prepareStandardBody();
				this.petsPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case AUCTIONS -> {
				prepareStandardBody();
				this.auctionPage.render(
					g, this.font, activeSub(PvSubTab.AUCTION_STATS),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case COLLECTIONS -> {
				prepareStandardBody();
				this.collectionsPage.render(
					g, this.font, activeSub(PvSubTab.COLLECTIONS_LIST),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case GARDEN -> {
				prepareStandardBody();
				this.dataLoader.ensureGardenIsland();
				this.dataLoader.ensureGardenWeight();
				PvSubTab sub = activeSub(PvSubTab.GARDEN_OVERVIEW);
				if (sub == PvSubTab.GARDEN_JACOB) {
					this.dataLoader.ensureGardenContests();
				}
				this.gardenPage.render(g, this.font, sub, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case MINING -> {
				prepareStandardBody();
				this.miningPage.render(
					g, this.font, activeSub(PvSubTab.MINING_OVERVIEW),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case FORAGING -> {
				prepareStandardBody();
				this.foragingPage.render(
					g, this.font, activeSub(PvSubTab.FORAGING_OVERVIEW),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case FISHING -> {
				prepareStandardBody();
				this.fishingPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case CRIMSON -> {
				prepareStandardBody();
				this.crimsonPage.render(
					g, this.font, activeSub(PvSubTab.CRIMSON_OVERVIEW),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case RIFT -> {
				prepareStandardBody();
				this.riftPage.render(
					g, this.font, activeSub(PvSubTab.RIFT_OVERVIEW),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case MUSEUM -> {
				prepareStandardBody();
				this.museumPage.setSort(this.museumSort);
				this.dataLoader.ensureMuseum(false);
				this.museumPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case EVENTS -> {
				prepareStandardBody();
				this.dataLoader.ensureBingo(false);
				this.eventsPage.render(
					g, this.font, activeSub(PvSubTab.EVENTS_BINGO),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
		}
	}

	private static boolean isDirectionalKey(int key) {
		return key == 262 || key == 263 || key == 264 || key == 265;
	}

	private void positionPlayerSearch(GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH) {
		if (this.playerSearch == null) {
			return;
		}
		int boxW = 110;
		int inset = 4;
		int textH = Math.max(8, this.font.lineHeight);
		int boxX = panelX + panelW - boxW;
		int boxY = panelY + panelH + 2;
		boolean focused = this.playerSearch.isFocused();
		PvDraw.fill(g, boxX, boxY, boxW, InventorySplitLayout.SEARCH_H, 0xAA101018);
		g.outline(boxX, boxY, boxW, InventorySplitLayout.SEARCH_H, focused ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER);
		this.playerSearch.setWidth(Math.max(20, boxW - inset * 2));
		this.playerSearch.setHeight(textH);
		this.playerSearch.setX(boxX + inset);
		this.playerSearch.setY(boxY + Math.max(0, (InventorySplitLayout.SEARCH_H - textH) / 2));
		this.playerSearch.setVisible(true);
		this.playerSearch.extractWidgetRenderState(g, 0, 0, 0f);
		if (this.playerSearch.getValue().isEmpty() && !focused) {
			String hint = Component.translatable("betterpv.home.search_hint").getString();
			PvDraw.text(
				g, this.font, hint,
				this.playerSearch.getX(),
				this.playerSearch.getY(),
				PvDraw.COLOR_MUTED
			);
		}
	}

	private void hidePlayerSearch() {
		if (this.playerSearch == null) {
			return;
		}
		this.playerSearch.setFocused(false);
		this.playerSearch.setVisible(false);
	}

	private void submitPlayerSearch() {
		if (this.playerSearch == null) {
			return;
		}
		String trimmed = this.playerSearch.getValue() == null ? "" : this.playerSearch.getValue().trim();
		if (trimmed.isEmpty()) {
			this.playerSearchError = Component.translatable("betterpv.home.search_empty").getString();
			this.playerSearchErrorUntilMs = System.currentTimeMillis() + 2_500L;
			return;
		}
		this.playerSearchError = "";
		Minecraft.getInstance().setScreen(new ProfileViewerScreen(trimmed));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click == null) {
			return super.mouseClicked(click, doubled);
		}
		if (!uiInteractive()) {
			return true;
		}
		double mx = click.x();
		double my = click.y();
		if (this.profileSelector.mouseClicked(mx, my)) {
			return true;
		}
		if (routePageClick(mx, my)) {
			return true;
		}
		if (this.topBar.click(mx, my) || this.sideBar.click(mx, my) || this.inventoryBar.click(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
			if (this.homePage.slayerMouseClicked(mx, my)) {
				return true;
			}
			if (this.homePage.clickSbLevelPanel(mx, my)) {
				return true;
			}
			if (this.homePage.isSbLevelOverlayActive()) {
				return true;
			}
			if (this.homePage.hitName(mx, my)) {
				this.dataLoader.ensureUsernameHistory();
				return true;
			}
			if (this.homePage.hitStatus(mx, my)) {
				this.dataLoader.ensurePlayerStatus();
				return true;
			}
			if (this.homePage.clickWeight(mx, my)) {
				return true;
			}
			if (this.homePage.clickNetworth(mx, my, click.button())) {
				return true;
			}
			if (this.homePage.clickLeftPanel(mx, my)) {
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	private boolean routePageClick(double mx, double my) {
		return switch (this.tab) {
			case HOME -> activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_MISC
				&& this.homeMiscPage.mouseClicked(mx, my);
			case DUNGEONS -> this.dungeonPage.mouseClicked(mx, my);
			case CURRENT_PARTY -> this.currentPartyPage.mouseClicked(mx, my);
			case MINING -> this.miningPage.mouseClicked(mx, my);
			case FORAGING -> this.foragingPage.mouseClicked(mx, my);
			case FISHING -> this.fishingPage.mouseClicked(mx, my);
			case CRIMSON -> this.crimsonPage.mouseClicked(mx, my);
			case RIFT -> this.riftPage.mouseClicked(mx, my, activeSub(PvSubTab.RIFT_OVERVIEW));
			case MUSEUM -> {
				if (this.museumPage.clickRefresh(mx, my)) {
					this.dataLoader.ensureMuseum(true);
					yield true;
				}
				yield false;
			}
			case GARDEN -> this.gardenPage.mouseClicked(mx, my);
			case PETS -> this.petsPage.mouseClicked(mx, my);
			case AUCTIONS -> this.auctionPage.mouseClicked(mx, my, activeSub(PvSubTab.AUCTION_STATS));
			case COLLECTIONS -> this.collectionsPage.mouseClicked(mx, my, activeSub(PvSubTab.COLLECTIONS_LIST));
			case INVENTORIES -> this.inventoryPage.mouseClicked(mx, my);
			default -> false;
		};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!uiInteractive()) {
			return true;
		}
		if (this.profileSelector.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		if (routePageScroll(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean routePageScroll(double mouseX, double mouseY, double scrollY) {
		return switch (this.tab) {
			case HOME -> {
				if (activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_MISC) {
					yield this.homeMiscPage.mouseScrolled(mouseX, mouseY, scrollY);
				}
				yield this.homePage.mouseScrolled(mouseX, mouseY, scrollY);
			}
			case MUSEUM -> this.museumPage.mouseScrolled(mouseX, mouseY, scrollY);
			case DUNGEONS -> this.dungeonPage.mouseScrolled(scrollY);
			case CURRENT_PARTY -> this.currentPartyPage.mouseScrolled(mouseX, mouseY, scrollY);
			case KUUDRA -> this.kuudraGearPage.mouseScrolled(mouseX, mouseY, scrollY);
			case PETS -> this.petsPage.mouseScrolled(scrollY);
			case AUCTIONS -> this.auctionPage.mouseScrolled(mouseX, mouseY, scrollY);
			case COLLECTIONS -> this.collectionsPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.COLLECTIONS_LIST));
			case GARDEN -> this.gardenPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.GARDEN_OVERVIEW));
			case MINING -> this.miningPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.MINING_OVERVIEW));
			case FORAGING -> this.foragingPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.FORAGING_OVERVIEW));
			case CRIMSON -> this.crimsonPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.CRIMSON_OVERVIEW));
			case RIFT -> this.riftPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.RIFT_OVERVIEW));
			case BESTIARY -> this.bestiaryPage.mouseScrolled(mouseX, mouseY, scrollY);
			case EVENTS -> this.eventsPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.EVENTS_BINGO));
			case INVENTORIES -> {
				if (this.inventoryLayout.mouseScrolled(mouseX, mouseY, scrollY)) {
					yield true;
				}
				yield this.inventoryPage.mouseScrolled(mouseX, mouseY, scrollY);
			}
			default -> false;
		};
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event != null) {
			boolean typingInvSearch = this.inventorySearch != null
				&& this.inventorySearch.isVisible()
				&& this.inventorySearch.isFocused();
			boolean typingPlayerSearch = this.playerSearch != null
				&& this.playerSearch.isVisible()
				&& this.playerSearch.isFocused();
			if (typingPlayerSearch && (event.key() == 257 || event.key() == 335)) {
				submitPlayerSearch();
				return true;
			}
			boolean directional = isDirectionalKey(event.key());
			if (directional && !typingInvSearch && !typingPlayerSearch) {
				MoulberryMode.keyPressed(event.key());
				if (uiInteractive() && this.tab == PvTab.DUNGEONS && this.dungeonPage.keyPressed(event.key())) {
					return true;
				}
				// Screen arrow-key focus navigation would otherwise steal these
				// into the inventory/player search boxes.
				return true;
			}
			// Always feed B/A/Enter into Konami unless typing in search.
			if (!typingInvSearch && !typingPlayerSearch && MoulberryMode.keyPressed(event.key())) {
				return true;
			}
			if (uiInteractive()
				&& this.tab == PvTab.HOME
				&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW
				&& this.homePage.slayerKeyPressed(event.key())) {
				return true;
			}
			if (uiInteractive()
				&& this.tab == PvTab.HOME
				&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW
				&& (event.key() == 256 || event.key() == 259)
				&& this.homePage.requestSbLevelBack()) {
				// ESC / Backspace collapses SB Level overlay without closing PV.
				return true;
			}
			if (uiInteractive() && this.tab == PvTab.DUNGEONS && this.dungeonPage.keyPressed(event.key())) {
				return true;
			}
			if (uiInteractive() && this.profileSelector.menuOpen() && event.key() == 256) {
				this.profileSelector.closeMenu();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event != null) {
			boolean typingSearch = (this.inventorySearch != null
				&& this.inventorySearch.isVisible()
				&& this.inventorySearch.isFocused())
				|| (this.playerSearch != null
				&& this.playerSearch.isVisible()
				&& this.playerSearch.isFocused());
			if (!typingSearch) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && MoulberryMode.charTyped(text.charAt(0))) {
					return true;
				}
			}
			if (uiInteractive() && this.tab == PvTab.DUNGEONS) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && this.dungeonPage.charTyped(text.charAt(0))) {
					return true;
				}
			}
			if (uiInteractive()
				&& this.tab == PvTab.HOME
				&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && this.homePage.slayerCharTyped(text.charAt(0))) {
					return true;
				}
			}
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	Font font() {
		return this.font;
	}

	int loadGeneration() {
		return this.loadGeneration;
	}

	int bumpLoadGeneration() {
		return ++this.loadGeneration;
	}

	boolean isLoadGeneration(int generation) {
		return generation == this.loadGeneration;
	}

	void setDataReady(boolean ready) {
		this.dataReady = ready;
	}

	ProfileScreenDataLoader dataLoader() {
		return this.dataLoader;
	}

	String profileId() {
		return this.profileId;
	}

	UUID playerUuid() {
		return this.playerUuid;
	}

	JsonObject profilesRoot() {
		return this.profilesRoot;
	}

	HomePage homePage() {
		return this.homePage;
	}

	MiscStatsPage homeMiscPage() {
		return this.homeMiscPage;
	}

	GardenPage gardenPage() {
		return this.gardenPage;
	}

	MuseumPage museumPage() {
		return this.museumPage;
	}

	EventsPage eventsPage() {
		return this.eventsPage;
	}
}
