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
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.JComponent;

/** Permanent level and XP, next unlock, personal bests and recent matches. */
final class ProgressView extends JComponent
{
	private static final int BASE_HEIGHT = 340;
	private static final int MAP_LINES = 4;
	private static final int LINE_HEIGHT = 15;

	private final Content content;
	private Progression progress = new Progression(0, 0);
	private Records records = new Records();

	ProgressView(Content content)
	{
		this.content = content;
		setPreferredSize(new Dimension(224, BASE_HEIGHT));
	}

	void setProgress(Progression value)
	{
		progress = value;
		repaint();
	}

	void setRecords(Records value)
	{
		records = value;
		// Each map played adds four lines to the page, so the scroll area has to grow with it.
		setPreferredSize(new Dimension(224, BASE_HEIGHT + records.maps.size() * MAP_LINES * LINE_HEIGHT));
		revalidate();
		repaint();
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
		int w = getWidth();
		int x = 8;
		int y = 22;
		int level = progress.level();

		g.setFont(Theme.bold());
		Theme.text(g, "RuneDefence level " + level, x, y, Theme.ORANGE);
		y += 8;
		g.setColor(Theme.STONE_EDGE);
		g.fillRect(x, y, w - 16, 12);
		g.setColor(Theme.HP_RED.darker());
		g.fillRect(x + 1, y + 1, w - 18, 10);
		g.setColor(Theme.HP_GREEN.darker());
		g.fillRect(x + 1, y + 1, (int) ((w - 18) * progress.levelProgress()), 10);
		y += 28;

		g.setFont(Theme.small());
		y = row(g, "RuneDefence XP", fmt(progress.runeDefenceXp()), x, y, w);
		if (level < Progression.MAX_LEVEL)
		{
			y = row(g, "To next level", fmt(Progression.xpForLevel(level + 1) - progress.runeDefenceXp()), x, y, w);
		}
		y = row(g, "OSRS XP counted", fmt(progress.eligibleOsrsXp), x, y, w);
		y = row(g, "This session", fmt(progress.sessionOsrsXp), x, y, w);
		y += 4;

		DefenderDefinition next = nextUnlock(level);
		Theme.text(g, "Next unlock", x, y, Theme.YELLOW);
		y += 16;
		Theme.text(g, next == null ? "Whole roster unlocked!" : next.name + " at level " + next.requiredLevel, x, y, Theme.TEXT);
		y += 15;
		String perXp = progress.xpRate == 1 ? "Every OSRS XP" : "Every " + progress.xpRate + " OSRS XP";
		Theme.text(g, perXp + " in any skill", x, y, Theme.TEXT_DIM);
		y += 14;
		Theme.text(g, "earns 1 RuneDefence XP.", x, y, Theme.TEXT_DIM);
		y += 24;

		Theme.text(g, "Personal bests", x, y, Theme.YELLOW);
		y += 16;
		boolean any = false;
		for (MapDefinition m : content.maps)
		{
			Records.MapBest best = records.maps.get(m.id);
			if (best != null)
			{
				any = true;
				Theme.text(g, m.name, x, y, Theme.TEXT);
				y += 15;
				y = row(g, "Best wave", best.bestWave + "/" + m.waves.length, x, y, w);
				y = row(g, "Best score", fmt(best.bestScore), x, y, w);
				y = row(g, "Wins / matches", best.wins + " / " + best.matches, x, y, w);
			}
		}
		if (!any)
		{
			Theme.text(g, "No matches finished yet.", x, y, Theme.TEXT_DIM);
			y += 15;
		}
		y += 8;

		Theme.text(g, "Recent matches", x, y, Theme.YELLOW);
		y += 16;
		if (records.history.isEmpty())
		{
			Theme.text(g, "None yet.", x, y, Theme.TEXT_DIM);
		}
		for (Records.MatchResult r : records.history)
		{
			Color c = r.victory ? Theme.GREEN : Theme.RED;
			Theme.text(g, r.victory ? "Won" : "Lost", x, y, c);
			Theme.text(g, "wave " + r.wave, x + 34, y, Theme.TEXT);
			String score = fmt(r.score);
			Theme.text(g, score, w - x - g.getFontMetrics().stringWidth(score), y, Theme.YELLOW);
			y += 15;
		}
	}

	private DefenderDefinition nextUnlock(int level)
	{
		DefenderDefinition next = null;
		for (DefenderDefinition d : content.defenders)
		{
			if (d.requiredLevel > level && (next == null || d.requiredLevel < next.requiredLevel))
			{
				next = d;
			}
		}
		return next;
	}

	private static int row(Graphics2D g, String label, String value, int x, int y, int w)
	{
		Theme.text(g, label, x, y, Theme.TEXT);
		Theme.text(g, value, w - x - g.getFontMetrics().stringWidth(value), y, Theme.YELLOW);
		return y + 15;
	}
}
