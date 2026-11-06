package com.runedefence.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import net.runelite.client.ui.FontManager;

/** The stone-and-parchment look, using RuneLite's bundled pixel fonts. */
final class Theme
{
	static final Color STONE_DARK = new Color(0x25221B);
	static final Color STONE = new Color(0x3B3629);
	static final Color STONE_MID = new Color(0x4C4636);
	static final Color STONE_LIGHT = new Color(0x6B6450);
	static final Color STONE_EDGE = new Color(0x14120D);

	static final Color TEXT = new Color(0xE8DFC0);
	static final Color TEXT_DIM = new Color(0x9A927A);
	static final Color YELLOW = new Color(0xFFE040);
	static final Color ORANGE = new Color(0xFF981F);
	static final Color GREEN = new Color(0x40D040);
	static final Color RED = new Color(0xF04030);

	static final Color HP_GREEN = new Color(0x18C818);
	static final Color HP_RED = new Color(0xC81818);
	static final Color SPLAT_RED = new Color(0xB81818);
	static final Color SPLAT_RED_DARK = new Color(0x741010);
	static final Color SPLAT_GOLD = new Color(0xC88810);
	static final Color SPLAT_GOLD_DARK = new Color(0x7C5408);
	static final Color SPLAT_BLUE = new Color(0x2858B8);
	static final Color SPLAT_BLUE_DARK = new Color(0x183878);

	static final Color DIM_OVERLAY = new Color(0, 0, 0, 150);
	static final Color PANEL_OVERLAY = new Color(0, 0, 0, 205);
	static final Color ARROW_SHAFT = new Color(0xD8C8A0);
	static final Color DART_SHAFT = new Color(0x8A6A3A);
	static final Color DART_TIP = new Color(0xD0D4D8);
	static final Color VALID_FILL = new Color(60, 220, 60, 70);
	static final Color INVALID_FILL = new Color(230, 50, 40, 90);
	static final Color CLOSED_ROUTE = new Color(0, 0, 0, 150);
	static final Color PETRIFIED = new Color(120, 120, 115, 170);
	static final Color BURN_CHIP = new Color(0xFF7020);
	static final Color SLOW_CHIP = new Color(0x70C8F0);
	static final Color ROOT_CHIP = new Color(0x58B030);
	static final Color CURSE_CHIP = new Color(0xB060E0);
	static final Color RANGE_FILL = new Color(255, 255, 255, 28);
	static final Color RANGE_LINE = new Color(255, 255, 255, 130);
	static final Color SELECT_LINE = new Color(0xFFE040);

	private Theme()
	{
	}

	static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	static Font bold()
	{
		return FontManager.getRunescapeBoldFont();
	}

	/** Text with the game's hard one-pixel drop shadow. */
	static void text(Graphics2D g, String s, int x, int y, Color c)
	{
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	static void centered(Graphics2D g, String s, int cx, int y, Color c)
	{
		text(g, s, cx - g.getFontMetrics().stringWidth(s) / 2, y, c);
	}

	/** A raised stone slab with a highlight on the top left and shade on the bottom right. */
	static void slab(Graphics2D g, int x, int y, int w, int h, Color face)
	{
		g.setColor(STONE_EDGE);
		g.drawRect(x, y, w - 1, h - 1);
		g.setColor(face);
		g.fillRect(x + 1, y + 1, w - 2, h - 2);
		g.setColor(face.brighter());
		g.drawLine(x + 1, y + 1, x + w - 2, y + 1);
		g.drawLine(x + 1, y + 1, x + 1, y + h - 2);
		g.setColor(face.darker());
		g.drawLine(x + 1, y + h - 2, x + w - 2, y + h - 2);
		g.drawLine(x + w - 2, y + 2, x + w - 2, y + h - 2);
	}
}
