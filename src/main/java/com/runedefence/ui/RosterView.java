package com.runedefence.ui;

import com.runedefence.model.Content;
import com.runedefence.model.DefenderDefinition;
import com.runedefence.model.UpgradeDefinition;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/** Every defender, locked or not, with its upgrades in the tooltip. */
final class RosterView extends JComponent
{
	private static final int ROW = 46;
	private static final int ICON = 28;

	private final Content content;
	private final SpriteAtlas atlas;
	private int level = 1;

	RosterView(Content content, SpriteAtlas atlas)
	{
		this.content = content;
		this.atlas = atlas;
		setPreferredSize(new Dimension(224, ROW * content.defenders.length + 4));
		setToolTipText("");
	}

	void setLevel(int value)
	{
		level = value;
		repaint();
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		int i = (e.getY() - 2) / ROW;
		if (i < 0 || i >= content.defenders.length)
		{
			return null;
		}
		DefenderDefinition d = content.defenders[i];
		StringBuilder s = new StringBuilder("<html><b>").append(d.name).append("</b><br>").append(d.description);
		appendPath(s, "Path A", d.pathA);
		appendPath(s, "Path B", d.pathB);
		return s.append("</html>").toString();
	}

	private static void appendPath(StringBuilder s, String title, UpgradeDefinition[] path)
	{
		s.append("<br><b>").append(title).append("</b>");
		for (UpgradeDefinition u : path)
		{
			s.append("<br>").append(u.name).append(" (").append(u.cost).append("g): ").append(u.description);
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics;
		g.setColor(Theme.STONE_DARK);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setFont(Theme.small());
		for (int i = 0; i < content.defenders.length; i++)
		{
			DefenderDefinition d = content.defenders[i];
			int y = 2 + i * ROW;
			boolean locked = level < d.requiredLevel;
			Theme.slab(g, 2, y, getWidth() - 4, ROW - 2, Theme.STONE);
			Composite old = g.getComposite();
			if (locked)
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
			}
			g.drawImage(atlas.frame(d.id, 0), 6, y + 8, ICON, ICON, null);
			g.setComposite(old);
			if (locked)
			{
				g.drawImage(atlas.frame("lock", 0), 14, y + 16, null);
			}
			Theme.text(g, d.name, 38, y + 14, locked ? Theme.TEXT_DIM : Theme.ORANGE);
			Theme.text(g, d.role, 38, y + 28, Theme.TEXT);
			if (d.size > 1)
			{
				String footprint = d.size + "x" + d.size;
				Theme.text(g, footprint, getWidth() - g.getFontMetrics().stringWidth(footprint) - 8, y + 14, Theme.TEXT_DIM);
			}
			Theme.text(g, d.cost + "g", 38, y + 41, Theme.YELLOW);
			Theme.text(g, locked ? "Unlocks at level " + d.requiredLevel : "Unlocked", 76, y + 41, locked ? Theme.RED : Theme.GREEN);
		}
	}
}
