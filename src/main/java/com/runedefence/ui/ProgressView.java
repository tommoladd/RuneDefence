package com.runedefence.ui;

import com.runedefence.model.Content;
import com.runedefence.model.DefenderDefinition;
import com.runedefence.model.MapDefinition;
import com.runedefence.model.Progression;
import com.runedefence.model.Records;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.JComponent;

/** Permanent level and XP, next unlock, personal bests and recent matches, as a column of cards. */
final class ProgressView extends JComponent
{
	private static final int WIDTH = 224;
	private static final int MARGIN = 3;
	private static final int PAD = 8;
	private static final int CARD_GAP = 4;
	private static final int ROW = 15;
	private static final int ICON = 32;

	private static final int HEADING_HEIGHT = 22;
	private static final int LEVEL_CARD_HEIGHT = 114;
	private static final int UNLOCK_CARD_HEIGHT = 44;
	private static final int MAP_CARD_HEIGHT = 50;
	private static final int EMPTY_CARD_HEIGHT = 24;
	private static final int MATCH_ROW_HEIGHT = 18;
	private static final int BAR_HEIGHT = 14;
	private static final int WAVE_BAR_HEIGHT = 6;

	private final Content content;
	private final SpriteAtlas atlas;
	private Progression progress = new Progression(0, 0);
	private Records records = new Records();

	ProgressView(Content content, SpriteAtlas atlas)
	{
		this.content = content;
		this.atlas = atlas;
		resize();
	}

	void setProgress(Progression value)
	{
		progress = value;
		repaint();
	}

	void setRecords(Records value)
	{
		records = value;
		resize();
		repaint();
	}

	private int mapsPlayed()
	{
		int played = 0;
		for (MapDefinition m : content.maps)
		{
			played += records.maps.containsKey(m.id) ? 1 : 0;
		}
		return played;
	}

	/** Adds up the same pieces the painting walks down, so the scroll area always fits them. */
	int contentHeight()
	{
		int played = mapsPlayed();
		int bests = played == 0 ? EMPTY_CARD_HEIGHT : played * (MAP_CARD_HEIGHT + CARD_GAP) - CARD_GAP;
		int recent = records.history.isEmpty() ? EMPTY_CARD_HEIGHT : records.history.size() * MATCH_ROW_HEIGHT + 2;
		return MARGIN + LEVEL_CARD_HEIGHT + CARD_GAP + UNLOCK_CARD_HEIGHT + HEADING_HEIGHT + bests + HEADING_HEIGHT + recent + MARGIN * 2;
	}

	private void resize()
	{
		setPreferredSize(new Dimension(WIDTH, contentHeight()));
		revalidate();
	}

	private static String fmt(long n)
	{
		return NumberFormat.getIntegerInstance(Locale.UK).format(n);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics;
		g.setColor(Theme.STONE_DARK);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		int x = MARGIN;
		int w = getWidth() - MARGIN * 2;
		int y = MARGIN;
		y = paintLevelCard(g, x, y, w) + CARD_GAP;
		y = paintUnlockCard(g, x, y, w);
		y = paintHeading(g, "Personal bests", x, y);
		y = paintBests(g, x, y, w);
		y = paintHeading(g, "Recent matches", x, y);
		paintRecent(g, x, y, w);
	}

	private int paintLevelCard(Graphics2D g, int x, int y, int w)
	{
		Theme.slab(g, x, y, w, LEVEL_CARD_HEIGHT, Theme.STONE);
		int left = x + PAD;
		int right = x + w - PAD;
		int level = progress.level();
		long xp = progress.runeDefenceXp();

		g.setFont(Theme.bold());
		Theme.text(g, "Level " + level, left, y + 18, Theme.ORANGE);
		g.setFont(Theme.small());
		rightAligned(g, fmt(xp) + " XP", right, y + 18, Theme.TEXT);

		int barY = y + 24;
		int barWidth = w - PAD * 2;
		double done = progress.levelProgress();
		g.setColor(Theme.STONE_EDGE);
		g.fillRect(left, barY, barWidth, BAR_HEIGHT);
		g.setColor(Theme.STONE_DARK);
		g.fillRect(left + 1, barY + 1, barWidth - 2, BAR_HEIGHT - 2);
		g.setColor(Theme.XP_FILL);
		g.fillRect(left + 1, barY + 1, (int) ((barWidth - 2) * done), BAR_HEIGHT - 2);
		String percent = level >= Progression.MAX_LEVEL ? "Max level" : (int) (done * 100) + "%";
		Theme.centered(g, percent, left + barWidth / 2, barY + BAR_HEIGHT - 3, Theme.TEXT);

		int line = barY + BAR_HEIGHT + 15;
		if (level < Progression.MAX_LEVEL)
		{
			Theme.text(g, fmt(Progression.xpForLevel(level + 1) - xp) + " XP to level " + (level + 1), left, line, Theme.TEXT);
		}
		else
		{
			Theme.text(g, "You've reached the top level!", left, line, Theme.TEXT);
		}

		line += ROW + 3;
		row(g, "OSRS XP counted", fmt(progress.eligibleOsrsXp), left, right, line, Theme.TEXT);
		line += ROW;
		row(g, "This session", "+" + fmt(progress.sessionOsrsXp), left, right, line, progress.sessionOsrsXp > 0 ? Theme.GREEN : Theme.TEXT);
		line += ROW;
		Theme.text(g, progress.xpRate + " OSRS XP = 1 RuneDefence XP", left, line, Theme.TEXT_MUTED);
		return y + LEVEL_CARD_HEIGHT;
	}

	private int paintUnlockCard(Graphics2D g, int x, int y, int w)
	{
		Theme.slab(g, x, y, w, UNLOCK_CARD_HEIGHT, Theme.STONE);
		int level = progress.level();
		DefenderDefinition next = content.nextUnlock(level);
		g.setFont(Theme.small());
		if (next == null)
		{
			Theme.text(g, "Whole roster unlocked!", x + PAD, y + 26, Theme.GREEN);
			return y + UNLOCK_CARD_HEIGHT;
		}
		g.drawImage(atlas.frame(next.id, 0), x + 6, y + 6, ICON, ICON, null);
		int text = x + 6 + ICON + 6;
		Theme.text(g, "Next unlock", text, y + 13, Theme.TEXT_MUTED);
		Theme.text(g, fit(g, next.name, x + w - PAD - text), text, y + 27, Theme.ORANGE);
		int levels = next.requiredLevel - level;
		Theme.text(g, "Level " + next.requiredLevel + " (" + levels + (levels == 1 ? " level" : " levels") + " to go)", text, y + 40, Theme.TEXT);
		return y + UNLOCK_CARD_HEIGHT;
	}

	private static int paintHeading(Graphics2D g, String title, int x, int y)
	{
		g.setFont(Theme.bold());
		Theme.text(g, title, x + 2, y + 18, Theme.ORANGE);
		g.setFont(Theme.small());
		return y + HEADING_HEIGHT;
	}

	private int paintBests(Graphics2D g, int x, int y, int w)
	{
		if (mapsPlayed() == 0)
		{
			paintEmpty(g, "No matches finished yet.", x, y, w);
			return y + EMPTY_CARD_HEIGHT;
		}
		for (MapDefinition m : content.maps)
		{
			Records.MapBest best = records.maps.get(m.id);
			if (best != null)
			{
				paintMapCard(g, m, best, x, y, w);
				y += MAP_CARD_HEIGHT + CARD_GAP;
			}
		}
		return y - CARD_GAP;
	}

	private void paintMapCard(Graphics2D g, MapDefinition m, Records.MapBest best, int x, int y, int w)
	{
		Theme.slab(g, x, y, w, MAP_CARD_HEIGHT, Theme.STONE);
		int left = x + PAD;
		int right = x + w - PAD;
		boolean cleared = best.bestWave >= m.waves.length;

		Theme.text(g, m.name, left, y + 14, Theme.TEXT);
		rightAligned(g, "Wave " + best.bestWave + "/" + m.waves.length, right, y + 14, cleared ? Theme.GREEN : Theme.YELLOW);

		int barY = y + 19;
		int barWidth = right - left;
		g.setColor(Theme.STONE_EDGE);
		g.fillRect(left, barY, barWidth, WAVE_BAR_HEIGHT);
		g.setColor(cleared ? Theme.GREEN.darker() : Theme.WAVE_FILL);
		g.fillRect(left + 1, barY + 1, (barWidth - 2) * Math.min(best.bestWave, m.waves.length) / m.waves.length, WAVE_BAR_HEIGHT - 2);

		Theme.text(g, "Score " + fmt(best.bestScore), left, y + 41, Theme.TEXT_MUTED);
		rightAligned(g, best.wins + " of " + best.matches + " won", right, y + 41, best.wins > 0 ? Theme.GREEN : Theme.TEXT_MUTED);
	}

	private void paintRecent(Graphics2D g, int x, int y, int w)
	{
		if (records.history.isEmpty())
		{
			paintEmpty(g, "None yet.", x, y, w);
			return;
		}
		Theme.slab(g, x, y, w, records.history.size() * MATCH_ROW_HEIGHT + 2, Theme.STONE);
		int rowY = y + 1;
		boolean shaded = false;
		for (Records.MatchResult r : records.history)
		{
			if (shaded)
			{
				g.setColor(Theme.STONE_MID);
				g.fillRect(x + 2, rowY, w - 4, MATCH_ROW_HEIGHT);
			}
			shaded = !shaded;
			paintMatch(g, r, x, rowY, w);
			rowY += MATCH_ROW_HEIGHT;
		}
	}

	/** Result, then map, then wave reached, then score, in columns that line up down the card. */
	private void paintMatch(Graphics2D g, Records.MatchResult r, int x, int y, int w)
	{
		g.setColor(r.victory ? Theme.GREEN : Theme.RED);
		g.fillRect(x + 3, y + 3, 3, MATCH_ROW_HEIGHT - 6);
		int base = y + 13;
		Theme.text(g, r.victory ? "Won" : "Lost", x + 9, base, r.victory ? Theme.GREEN : Theme.RED);
		String score = fmt(r.score);
		String wave = "W" + r.wave;
		int right = x + w - 5;
		int scoreLeft = right - g.getFontMetrics().stringWidth(score);
		int waveRight = right - 42;
		rightAligned(g, wave, waveRight, base, Theme.TEXT);
		Theme.text(g, score, scoreLeft, base, Theme.YELLOW);
		MapDefinition m = content.map(r.mapId);
		if (m != null)
		{
			int mapLeft = x + 34;
			int room = waveRight - g.getFontMetrics().stringWidth(wave) - 4 - mapLeft;
			Theme.text(g, fit(g, m.name, room), mapLeft, base, Theme.TEXT_MUTED);
		}
	}

	private static void paintEmpty(Graphics2D g, String message, int x, int y, int w)
	{
		Theme.slab(g, x, y, w, EMPTY_CARD_HEIGHT, Theme.STONE);
		Theme.text(g, message, x + PAD, y + 16, Theme.TEXT_MUTED);
	}

	private static void row(Graphics2D g, String label, String value, int left, int right, int y, Color valueColour)
	{
		Theme.text(g, label, left, y, Theme.TEXT_MUTED);
		rightAligned(g, value, right, y, valueColour);
	}

	private static void rightAligned(Graphics2D g, String s, int right, int y, Color c)
	{
		Theme.text(g, s, right - g.getFontMetrics().stringWidth(s), y, c);
	}

	/** Shortens text that wouldn't fit, rather than letting it run into the next column. */
	private static String fit(Graphics2D g, String s, int width)
	{
		if (g.getFontMetrics().stringWidth(s) <= width)
		{
			return s;
		}
		String cut = s;
		while (!cut.isEmpty() && g.getFontMetrics().stringWidth(cut + "..") > width)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.trim() + "..";
	}
}
