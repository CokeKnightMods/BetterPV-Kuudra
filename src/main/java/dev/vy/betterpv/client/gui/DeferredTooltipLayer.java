package dev.vy.betterpv.client.gui;

import dev.vy.betterpv.client.gui.auctions.AuctionPage;
import dev.vy.betterpv.client.gui.bestiary.BestiaryPage;
import dev.vy.betterpv.client.gui.collections.CollectionsPage;
import dev.vy.betterpv.client.gui.crimson.CrimsonPage;
import dev.vy.betterpv.client.gui.kuudra.KuudraGearPage;
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
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.gui.nav.PvTab;
import dev.vy.betterpv.client.gui.pets.PetsPage;
import dev.vy.betterpv.client.gui.rift.RiftPage;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Collects and draws deferred page / selector tooltips above search chrome. */
final class DeferredTooltipLayer {
	private static final int PAD = 8;

	private DeferredTooltipLayer() {
	}

	static void render(
		GuiGraphicsExtractor g,
		Font font,
		int width,
		int height,
		int panelX,
		int panelY,
		int panelW,
		int panelH,
		int mouseX,
		int mouseY,
		PvTab tab,
		PvSubTab homeSub,
		ProfileSelectorController selector,
		InventorySplitLayout inventoryLayout,
		BestiarySplitLayout bestiaryLayout,
		IconButtonBar inventoryBar,
		HomePage homePage,
		MiscStatsPage homeMiscPage,
		DungeonPage dungeonPage,
		PetsPage petsPage,
		AuctionPage auctionPage,
		CollectionsPage collectionsPage,
		GardenPage gardenPage,
		MiningPage miningPage,
		ForagingPage foragingPage,
		FishingPage fishingPage,
		CrimsonPage crimsonPage,
		KuudraGearPage kuudraGearPage,
		RiftPage riftPage,
		MuseumPage museumPage,
		EventsPage eventsPage,
		InventoryPage inventoryPage,
		BestiaryPage bestiaryPage
	) {
		boolean footerHover = selector.footerHover(mouseX, mouseY);
		List<PvTooltip.Line> profileTip = selector.tooltip(mouseX, mouseY, footerHover);
		if (profileTip != null && !selector.menuOpen()) {
			PvTooltip.drawStyled(g, font, profileTip, mouseX, mouseY, width, height);
		}
		if (selector.menuOpen() && selector.combinedHover(mouseX, mouseY)) {
			return;
		}

		int bodyX = panelX + PAD;
		int bodyY = panelY + PAD;
		int bodyW = panelW - PAD * 2;
		int bodyH = panelH - PAD * 2;

		switch (tab) {
			case HOME -> {
				if (homeSub == PvSubTab.HOME_MISC) {
					homeMiscPage.renderTooltip(g, font, mouseX, mouseY, width, height);
				} else {
					homePage.renderTooltip(g, font, mouseX, mouseY, width, height);
				}
			}
			case DUNGEONS -> dungeonPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case PETS -> petsPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case AUCTIONS -> auctionPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case COLLECTIONS -> collectionsPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case GARDEN -> gardenPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case MINING -> miningPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case FORAGING -> foragingPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case FISHING -> fishingPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case CRIMSON -> crimsonPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case KUUDRA -> kuudraGearPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case RIFT -> riftPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case MUSEUM -> museumPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			case EVENTS -> eventsPage.renderTooltip(
				g, font, mouseX, mouseY, width, height, bodyX, bodyY, bodyW, bodyH
			);
			default -> { }
		}

		if (tab.isInventorySplit()) {
			inventoryPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			if (inventoryLayout.paneTip() != null) {
				inventoryBar.maybeTooltip(
					g,
					font,
					inventoryLayout.paneTip(),
					true,
					inventoryLayout.paneTipX(),
					inventoryLayout.paneTipY()
				);
			}
		}
		if (tab.isBestiarySplit()) {
			bestiaryPage.renderTooltip(g, font, mouseX, mouseY, width, height);
			if (bestiaryLayout.categoryTip() != null) {
				inventoryBar.maybeTooltip(
					g,
					font,
					bestiaryLayout.categoryTip(),
					true,
					bestiaryLayout.categoryTipX(),
					bestiaryLayout.categoryTipY()
				);
			}
		}
	}
}
