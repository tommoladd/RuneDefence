package com.runedefence.ui;

import com.runedefence.model.Content;
import com.runedefence.model.DefenderDefinition;
import com.runedefence.model.Progression;
import com.runedefence.model.UpgradeDefinition;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Every defender. An unlocked one opens a page giving every upgrade and exactly what it
 * changes. A locked one shows only its name and the level that unlocks it, and can't be opened.
 */
final class RosterView extends JComponent
{
	private static final int WIDTH = 224;
	private static final int HEADER = 48;
	private static final int ROW = 46;
	private static final int ICON = 28;
	private static final int PAGE_TOP = 26;
	private static final int PAGE_ICON = 42;
	private static final int LINE_HEIGHT = 14;
	/** Text wraps to this whatever the panel's width, so that a scrollbar can't change the page's height. */
	private static final int TEXT_WIDTH = 206;
	private static final int TEXT_X = 8;
	private static final int INDENT = 8;

	/** One line of a defender's page, already wrapped to fit. */
	private static final class Line
	{
		final String text;
		final Color colour;
		final int x;
		final int gapBefore;

		Line(String text, Color colour, int x, int gapBefore)
		{
			this.text = text;
			this.colour = colour;
			this.x = x;
			this.gapBefore = gapBefore;
		}
	}

	private final Content content;
	private final SpriteAtlas atlas;
	private final StoneButton back = new StoneButton("< Roster");
	private final List<Line> page = new ArrayList<>();
	private Progression progress = new Progression(0, 0);
	private DefenderDefinition open;
	private int hoverRow = -1;
	private int listScroll;

	RosterView(Content content, SpriteAtlas atlas)
	{
		this.content = content;
		this.atlas = atlas;
		setLayout(null);
		back.setVisible(false);
		back.setToolTipText("Back to the whole roster");
		back.addActionListener(e -> close());
		add(back);
		setToolTipText("");
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int row = rowAt(e.getY());
				if (open == null && row >= 0)
				{
					open(content.defenders[row]);
				}
			}

			@Override
			public void mouseMoved(MouseEvent e)
			{
				int row = rowAt(e.getY());
				setHoverRow(row >= 0 && !isLocked(content.defenders[row]) ? row : -1);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setHoverRow(-1);
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		resize();
	}

	void setProgress(Progression value)
	{
		progress = value;
		if (open != null && isLocked(open))
		{
			// Swapping account or changing the XP rate can lower the level while a page is open.
			close();
		}
		repaint();
	}

	void open(DefenderDefinition d)
	{
		if (isLocked(d))
		{
			return;
		}
		listScroll = getVisibleRect().y;
		open = d;
		hoverRow = -1;
		setCursor(Cursor.getDefaultCursor());
		back.setVisible(true);
		buildPage();
		resize();
		scrollTo(0);
		repaint();
	}

	void close()
	{
		open = null;
		back.setVisible(false);
		page.clear();
		resize();
		scrollTo(listScroll);
		repaint();
	}

	boolean isShowingPage()
	{
		return open != null;
	}

	/** The words on the open page. */
	String pageText()
	{
		StringBuilder s = new StringBuilder();
		for (Line line : page)
		{
			s.append(line.text).append('\n');
		}
		return s.toString();
	}

	private boolean isLocked(DefenderDefinition d)
	{
		return progress.level() < d.requiredLevel;
	}

	private int rowAt(int y)
	{
		if (open != null || y < HEADER)
		{
			return -1;
		}
		int row = (y - HEADER) / ROW;
		return row < content.defenders.length ? row : -1;
	}

	private void setHoverRow(int row)
	{
		if (row != hoverRow)
		{
			hoverRow = row;
			setCursor(row >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			repaint();
		}
	}

	private void resize()
	{
		int height = open == null ? HEADER + ROW * content.defenders.length + 4 : pageHeight();
		setPreferredSize(new Dimension(WIDTH, height));
		revalidate();
	}

	private void scrollTo(int y)
	{
		// After the revalidate above has taken effect, or the old height would limit the scroll.
		SwingUtilities.invokeLater(() -> scrollRectToVisible(new Rectangle(0, y, 1, getVisibleRect().height)));
	}

	@Override
	public void doLayout()
	{
		back.setBounds(2, 2, 66, 20);
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		int row = rowAt(e.getY());
		if (row < 0)
		{
			return null;
		}
		DefenderDefinition d = content.defenders[row];
		return isLocked(d) ? "Reach level " + d.requiredLevel + " to unlock." : "Click for stats and upgrades.";
	}

	// ---- A defender's page --------------------------------------------------

	private void buildPage()
	{
		page.clear();
		DefenderDefinition d = open;
		// The icon and name are painted separately, beside this text.
		wrap(d.description, Theme.TEXT, 0, 0);
		heading("Base stats");
		wrap("Costs " + d.cost + "g, takes " + footprint(d) + ".", Theme.YELLOW, 0, 0);
		for (String stat : DefenderText.baseStats(d))
		{
			wrap(stat, Theme.TEXT, 0, 0);
		}
		buildPath(d, 0, "Path A");
		buildPath(d, 1, "Path B");
		heading("How upgrades work");
		wrap("You can buy the first upgrade on both paths, but only one path can go on to its second.", Theme.TEXT, 0, 0);
		if (DefenderText.canBind(d))
		{
			heading("How binding works");
			wrap("A bound enemy stops walking. It can't be bound again until it has walked for as long as it was held, and bosses can't be bound at all.", Theme.TEXT, 0, 0);
		}
	}

	private void buildPath(DefenderDefinition d, int path, String title)
	{
		UpgradeDefinition[] upgrades = d.path(path);
		int total = 0;
		for (UpgradeDefinition u : upgrades)
		{
			total += u.cost;
		}
		heading(title + " (" + total + "g in all)");
		for (int tier = 0; tier < upgrades.length; tier++)
		{
			UpgradeDefinition u = upgrades[tier];
			wrap((tier + 1) + ". " + u.name + " (" + u.cost + "g)", Theme.YELLOW, 0, tier == 0 ? 0 : 3);
			wrap(u.description, Theme.TEXT_MUTED, INDENT, 0);
			for (String effect : DefenderText.upgradeEffects(d, path == 0 ? tier : 0, path == 1 ? tier : 0, path))
			{
				wrap(effect, Theme.GREEN, INDENT, 0);
			}
		}
	}

	private static String footprint(DefenderDefinition d)
	{
		return d.size == 1 ? "1 tile" : d.size + "x" + d.size + " tiles";
	}

	private void heading(String text)
	{
		page.add(new Line(text, Theme.ORANGE, TEXT_X, 8));
	}

	private void wrap(String text, Color colour, int indent, int gapBefore)
	{
		FontMetrics metrics = getFontMetrics(Theme.small());
		int width = TEXT_WIDTH - indent;
		StringBuilder line = new StringBuilder();
		int gap = gapBefore;
		for (String word : text.split(" "))
		{
			if (line.length() > 0 && metrics.stringWidth(line + " " + word) > width)
			{
				page.add(new Line(line.toString(), colour, TEXT_X + indent, gap));
				line.setLength(0);
				gap = 0;
			}
			if (line.length() > 0)
			{
				line.append(' ');
			}
			line.append(word);
		}
		page.add(new Line(line.toString(), colour, TEXT_X + indent, gap));
	}

	private int pageHeight()
	{
		int y = pageTextTop();
		for (Line line : page)
		{
			y += line.gapBefore + LINE_HEIGHT;
		}
		return y + 8;
	}

	private static int pageTextTop()
	{
		return PAGE_TOP + PAGE_ICON + 4;
	}

	// ---- Painting -----------------------------------------------------------

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics;
		g.setColor(Theme.STONE_DARK);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setFont(Theme.small());
		if (open == null)
		{
			paintHeader(g);
			paintRows(g);
		}
		else
		{
			paintPage(g);
		}
	}

	private void paintHeader(Graphics2D g)
	{
		int level = progress.level();
		int unlocked = 0;
		for (DefenderDefinition d : content.defenders)
		{
			unlocked += level >= d.requiredLevel ? 1 : 0;
		}
		Theme.text(g, "Unlocked " + unlocked + " of " + content.defenders.length, TEXT_X, 13, Theme.ORANGE);
		DefenderDefinition next = content.nextUnlock(level);
		Theme.text(g, next == null ? "Whole roster unlocked!" : "Next: " + next.name + " at level " + next.requiredLevel, TEXT_X, 28, Theme.TEXT);
		Theme.text(g, "Click an unlocked one for its upgrades.", TEXT_X, 43, Theme.TEXT_MUTED);
	}

	private void paintRows(Graphics2D g)
	{
		for (int i = 0; i < content.defenders.length; i++)
		{
			DefenderDefinition d = content.defenders[i];
			int y = HEADER + i * ROW;
			boolean locked = isLocked(d);
			if (locked)
			{
				paintLockedRow(g, d, y);
				continue;
			}
			Theme.slab(g, 2, y, getWidth() - 4, ROW - 2, i == hoverRow ? Theme.STONE_MID : Theme.STONE);
			paintIcon(g, d, 6, y + 8, ICON, false);
			Theme.text(g, d.name, 38, y + 14, Theme.ORANGE);
			Theme.text(g, d.role, 38, y + 28, Theme.TEXT);
			String chevron = ">";
			Theme.text(g, chevron, getWidth() - g.getFontMetrics().stringWidth(chevron) - 8, y + 28, Theme.TEXT_DIM);
			if (d.size > 1)
			{
				String footprint = d.size + "x" + d.size;
				Theme.text(g, footprint, getWidth() - g.getFontMetrics().stringWidth(footprint) - 8, y + 14, Theme.TEXT_DIM);
			}
			Theme.text(g, d.cost + "g", 38, y + 41, Theme.YELLOW);
			Theme.text(g, "Unlocked", 76, y + 41, Theme.GREEN);
		}
	}

	/** Darker than an unlocked row, with only what it takes to plan for it. */
	private void paintLockedRow(Graphics2D g, DefenderDefinition d, int y)
	{
		Theme.slab(g, 2, y, getWidth() - 4, ROW - 2, Theme.STONE_DARK);
		paintIcon(g, d, 6, y + 8, ICON, true);
		Theme.text(g, d.name, 38, y + 19, Theme.TEXT_MUTED);
		Theme.text(g, "Unlocks at level " + d.requiredLevel, 38, y + 34, Theme.RED);
	}

	private void paintIcon(Graphics2D g, DefenderDefinition d, int x, int y, int size, boolean locked)
	{
		Composite old = g.getComposite();
		if (locked)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
		}
		g.drawImage(atlas.frame(d.id, 0), x, y, size, size, null);
		g.setComposite(old);
		if (locked)
		{
			g.drawImage(atlas.frame("lock", 0), x + (size - 13) / 2, y + (size - 13) / 2, null);
		}
	}

	private void paintPage(Graphics2D g)
	{
		paintIcon(g, open, TEXT_X, PAGE_TOP, PAGE_ICON, false);
		Font small = g.getFont();
		g.setFont(Theme.bold());
		int nameX = TEXT_X + PAGE_ICON + 6;
		Theme.text(g, open.name, nameX, PAGE_TOP + 14, Theme.ORANGE);
		g.setFont(small);
		Theme.text(g, open.role, nameX, PAGE_TOP + 28, Theme.TEXT);
		Theme.text(g, "Unlocked at level " + open.requiredLevel, nameX, PAGE_TOP + 41, Theme.GREEN);
		int y = pageTextTop();
		for (Line line : page)
		{
			y += line.gapBefore + LINE_HEIGHT;
			Theme.text(g, line.text, line.x, y - 3, line.colour);
		}
	}
}
