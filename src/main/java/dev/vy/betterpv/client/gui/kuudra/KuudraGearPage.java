package dev.vy.betterpv.client.gui.kuudra;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.CrimsonKuudraCard;
import dev.vy.betterpv.client.data.CrimsonSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.KuudraGearSnapshot;
import dev.vy.betterpv.client.data.KuudraProfileFacts;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.crimson.CrimsonUi;
import dev.vy.betterpv.client.gui.crimson.page.KuudraPage;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** A compact, scrollable overview of Kuudra gear evidence and profile statistics. */
public final class KuudraGearPage {
	private CrimsonSnapshot snapshot = CrimsonSnapshot.empty();
	private KuudraProfileFacts facts = KuudraProfileFacts.empty();
	private final List<CrimsonUi.HoverZone> hover = new ArrayList<>();
	private final Map<String, ItemStack> icons = new HashMap<>();
	private final KuudraPage overview = new KuudraPage();
	private boolean detailed;
	private int switchX, switchY;
	private int x, y, w, h, leftW, statsScroll, gearScroll, statsMax, gearMax;

	public void apply(CrimsonSnapshot snapshot, JsonObject root, String profileId, UUID player, boolean sameIdentity) {
		this.snapshot = snapshot == null ? CrimsonSnapshot.empty() : snapshot;
		this.facts = KuudraProfileFacts.fromProfiles(root, profileId, player);
		if (!sameIdentity) {
			this.statsScroll = this.gearScroll = 0;
			this.icons.clear();
			this.overview.resetScroll();
		}
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my, int screenW, int screenH) {
		this.hover.clear();
		this.switchX = x;
		this.switchY = y;
		for (int i = 0; i < 2; i++) {
			boolean selected = (i == 1) == this.detailed;
			int bx = x + i * 76;
			PvDraw.fill(g, bx, y, 72, 17, selected ? 0xFF2A3A55 : 0xFF16161E);
			g.outline(bx, y, 72, 17, selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER);
			PvDraw.text(g, font, i == 0 ? "Overview" : "Details", bx + 7, y + 4, PvDraw.COLOR_TEXT);
		}
		y += 22;
		h -= 22;
		if (!this.detailed) {
			this.overview.render(g, font, this.snapshot, x, y, w, h, mx, my, screenW, screenH);
			return;
		}
		PvDraw.textBold(g, font, "Kuudra", x + 2, y + 2, PvDraw.COLOR_GOLD);
		PvDraw.text(g, font, CrimsonUi.trim(font, "Visible gear across inventory & storage. Hover for details.", w - 4),
			x + 2, y + 16, PvDraw.COLOR_MUTED);
		this.x = x; this.y = y + 32; this.w = w; this.h = Math.max(20, h - 32);
		this.leftW = Math.min(170, Math.max(145, w / 3));
		PvDraw.innerPanel(g, x, this.y, this.leftW, this.h);
		PvDraw.innerPanel(g, x + this.leftW + 6, this.y, w - this.leftW - 6, this.h);
		if (!this.snapshot.kuudraCard().gear().loaded()) {
			PvDraw.text(g, font, "Loading Kuudra gear...", x + 8, this.y + 10, PvDraw.COLOR_MUTED);
			return;
		}
		drawStats(g, font);
		drawGear(g, font, mx, my);
	}

	private void drawStats(GuiGraphicsExtractor g, Font font) {
		CrimsonKuudraCard card = this.snapshot.kuudraCard();
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[]{"PROFILE", ""});
		boolean currentMp = this.facts.accessories() && card.magicalPower() > 0;
		String mp = currentMp ? Integer.toString(card.magicalPower())
			: this.facts.highestMagicalPower() == null ? "Unknown" : Integer.toString(this.facts.highestMagicalPower().intValue());
		rows.add(new String[]{currentMp ? "MP (estimate)" : "MP (API peak)", mp});
		rows.add(new String[]{"Power", card.selectedPower().isBlank() ? "Unknown" : card.selectedPowerLabel()});
		rows.add(new String[]{"Profile bank", this.facts.bank() == null ? "Unknown" : FormatUtil.shortCoins(this.facts.bank())});
		rows.add(new String[]{"Combat", known(this.facts.combat(), card.combatLevel())});
		rows.add(new String[]{"Foraging", known(this.facts.foraging(), card.foragingLevel())});
		rows.add(new String[]{"Catacombs", known(this.facts.catacombs(), card.cataLevel())});
		rows.add(new String[]{"SkyBlock level", known(this.facts.skyBlock(), card.skyBlockLevel())});
		rows.add(new String[]{"KUUDRA RUNS", ""});
		rows.add(new String[]{"Total", known(this.facts.runs(), this.snapshot.kuudraTotalCompletions())});
		int tierNumber = 1;
		for (CrimsonSnapshot.KuudraTier tier : CrimsonSnapshot.KuudraTier.values()) {
			rows.add(new String[]{"T" + tierNumber++ + " " + tier.label(), known(this.facts.runs(), this.snapshot.kuudra(tier).completions())});
		}
		rows.add(new String[]{"API VISIBILITY", ""});
		KuudraGearSnapshot.Coverage coverage = card.gear().coverage();
		rows.add(new String[]{"Inventory", visible(coverage.inventory())});
		rows.add(new String[]{"Armor", visible(coverage.armor())});
		rows.add(new String[]{"Equipment", visible(coverage.equipment())});
		rows.add(new String[]{"Pets", visible(coverage.pets())});
		this.statsMax = Math.max(0, rows.size() * 18 + 12 - this.h);
		this.statsScroll = Math.min(this.statsScroll, this.statsMax);
		g.enableScissor(this.x + 1, this.y + 1, this.x + this.leftW - 1, this.y + this.h - 1);
		int rowY = this.y + 7 - this.statsScroll;
		for (String[] row : rows) {
			if (row[1].isEmpty()) {
				PvDraw.textBold(g, font, row[0], this.x + 7, rowY, PvDraw.COLOR_GOLD);
			} else {
				int valueWidth = Math.min(font.width(row[1]), this.leftW / 2);
				PvDraw.text(g, font, CrimsonUi.trim(font, row[0], this.leftW - valueWidth - 21), this.x + 7, rowY, PvDraw.COLOR_MUTED);
				PvDraw.textRight(g, font, CrimsonUi.trim(font, row[1], this.leftW / 2), this.x + this.leftW - 8, rowY, PvDraw.COLOR_TEXT);
				addHover(this.x + 2, rowY - 2, this.leftW - 5, 17,
					List.of(PvTooltip.Line.title(row[0], PvDraw.COLOR_GOLD), PvTooltip.Line.meta(row[1])));
			}
			rowY += 18;
		}
		g.disableScissor();
		scrollbar(g, this.x + this.leftW - 3, this.statsScroll, this.statsMax);
	}

	private void drawGear(GuiGraphicsExtractor g, Font font, int mx, int my) {
		int panelX = this.x + this.leftW + 6;
		int panelW = this.w - this.leftW - 6;
		List<KuudraGearSnapshot.Section> sections = this.snapshot.kuudraCard().gear().sections();
		int contentHeight = 12;
		for (var section : sections) contentHeight += 22 + section.checks().size() * 30;
		this.gearMax = Math.max(0, contentHeight - this.h);
		this.gearScroll = Math.min(this.gearScroll, this.gearMax);
		g.enableScissor(panelX + 1, this.y + 1, panelX + panelW - 1, this.y + this.h - 1);
		int rowY = this.y + 7 - this.gearScroll;
		for (var section : sections) {
			PvDraw.textBold(g, font, section.title(), panelX + 7, rowY, PvDraw.COLOR_GOLD);
			rowY += 22;
			for (var check : section.checks()) {
				if (CrimsonUi.visible(rowY, 28, this.y + 1, this.y + this.h - 1)) {
					int color = color(check.state());
					if (mx >= panelX && mx < panelX + panelW && my >= rowY && my < rowY + 28) {
						PvDraw.fill(g, panelX + 2, rowY - 2, panelW - 5, 28, 0x14FFFFFF);
					}
					PvDraw.fill(g, panelX + 5, rowY, 2, 22, color);
					if (!check.items().isEmpty()) {
						String id = check.items().getFirst().id();
						CrimsonUi.drawItemIcon(g, this.icons.computeIfAbsent(id, SkyBlockItemFactory::iconStack), panelX + 10, rowY + 2, 16);
					}
					int textX = panelX + 31;
					String status = status(check.state());
					PvDraw.text(g, font, CrimsonUi.trim(font, check.label(), panelW - 47 - font.width(status)), textX, rowY, PvDraw.COLOR_TEXT);
					PvDraw.textRight(g, font, status, panelX + panelW - 8, rowY, color);
					PvDraw.text(g, font, CrimsonUi.trim(font, check.summary(), panelW - 42), textX, rowY + 12, PvDraw.COLOR_MUTED);
					addHover(panelX + 2, rowY - 2, panelW - 5, 28, tooltip(check, font, Math.min(310, this.w - 24)));
				}
				rowY += 30;
			}
		}
		g.disableScissor();
		scrollbar(g, panelX + panelW - 3, this.gearScroll, this.gearMax);
	}

	private static List<PvTooltip.Line> tooltip(KuudraGearSnapshot.Check check, Font font, int maxWidth) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.title(check.label(), PvDraw.COLOR_GOLD));
		lines.add(PvTooltip.Line.of(status(check.state()), color(check.state())));
		for (var item : check.items()) {
			wrap(lines, item.name() + " (" + KuudraGearSnapshot.pretty(item.location()) + ")", font, maxWidth);
			wrap(lines, String.join(" / ", item.details()), font, maxWidth);
		}
		for (String note : check.notes()) wrap(lines, note, font, maxWidth);
		return lines;
	}

	private static void wrap(List<PvTooltip.Line> lines, String text, Font font, int width) {
		String line = "";
		for (String word : text.split(" ")) {
			if (!line.isBlank() && font.width(line + " " + word) > width) {
				lines.add(PvTooltip.Line.meta(line)); line = "";
			}
			line += (line.isEmpty() ? "" : " ") + word;
		}
		if (!line.isBlank()) lines.add(PvTooltip.Line.meta(line));
	}

	private void addHover(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
		int top = Math.max(y, this.y + 1), bottom = Math.min(y + h, this.y + this.h - 1);
		if (bottom > top) this.hover.add(CrimsonUi.HoverZone.of(x, top, w, bottom - top, lines));
	}

	private void scrollbar(GuiGraphicsExtractor g, int x, int scroll, int max) {
		if (max <= 0) return;
		int track = this.h - 4, thumb = Math.max(12, track * this.h / (max + this.h));
		PvDraw.fill(g, x, this.y + 2 + (track - thumb) * scroll / max, 2, thumb, PvDraw.COLOR_MUTED);
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!this.detailed) return this.overview.mouseScrolled(mx, my, amount);
		if (mx < this.x || mx >= this.x + this.w || my < this.y || my >= this.y + this.h) return false;
		if (mx < this.x + this.leftW) this.statsScroll = clamp(this.statsScroll - (int) (amount * 30), this.statsMax);
		else this.gearScroll = clamp(this.gearScroll - (int) (amount * 30), this.gearMax);
		return true;
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		if (!this.detailed) {
			this.overview.renderTooltip(g, font, mx, my, screenW, screenH);
			return;
		}
		CrimsonUi.drawHover(g, font, this.hover, mx, my, screenW, screenH);
	}

	public boolean mouseClicked(double mx, double my) {
		if (my < this.switchY || my >= this.switchY + 17) return false;
		for (int i = 0; i < 2; i++) {
			int x = this.switchX + i * 76;
			if (mx >= x && mx < x + 72) {
				this.detailed = i == 1;
				return true;
			}
		}
		return false;
	}

	private static int clamp(int value, int max) { return Math.max(0, Math.min(value, max)); }
	private static String known(boolean known, int value) { return known ? FormatUtil.commas(value) : "Unknown"; }
	private static String visible(boolean visible) { return visible ? "Visible" : "Unknown"; }
	private static String status(KuudraGearSnapshot.State state) {
		return switch (state) { case FOUND -> "Found"; case PARTIAL -> "Partial"; case NOT_SEEN -> "Not seen"; case UNKNOWN -> "Unknown"; };
	}
	private static int color(KuudraGearSnapshot.State state) {
		return switch (state) { case FOUND -> 0xFF77DD99; case PARTIAL -> 0xFFFFCC66; case NOT_SEEN -> 0xFFAAAAAA; case UNKNOWN -> 0xFF8888AA; };
	}
}
