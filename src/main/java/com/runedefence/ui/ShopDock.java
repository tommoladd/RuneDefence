package com.runedefence.ui;

import com.runedefence.engine.GameEngine;
import com.runedefence.model.DefenderDefinition;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Pages of defenders to buy, each page a row that stays readable in a narrow sidebar. */
final class ShopDock extends JPanel
{
	static final int PER_PAGE = 5;

	private static final int ICON = 28;
	private static final int ARROW_WIDTH = 22;
	private static final int ARROW_HEIGHT = 18;

	private final GameEngine engine;
	private final SpriteAtlas atlas;
	private final BattlefieldPanel field;
	private final DefenderDefinition[] roster;
	private final ShopButton[] buttons = new ShopButton[PER_PAGE];
	private final Hint hint = new Hint();
	private int level = 1;
	private int page;

	ShopDock(GameEngine engine, SpriteAtlas atlas, BattlefieldPanel field)
	{
		super(new BorderLayout());
		this.engine = engine;
		this.atlas = atlas;
		this.field = field;
		this.roster = engine.content().defenders;
		setBackground(Theme.STONE_DARK);
		JPanel row = new JPanel(new GridLayout(1, PER_PAGE, 1, 0));
		row.setOpaque(false);
		for (int i = 0; i < PER_PAGE; i++)
		{
			buttons[i] = new ShopButton();
			row.add(buttons[i]);
		}
		add(row, BorderLayout.CENTER);
		add(hint, BorderLayout.SOUTH);
		showPage(0);
	}

	void setLevel(int value)
	{
		level = value;
	}

	int pageCount()
	{
		return (roster.length + PER_PAGE - 1) / PER_PAGE;
	}

	void showPage(int index)
	{
		page = Math.floorMod(index, pageCount());
		for (int i = 0; i < PER_PAGE; i++)
		{
			int slot = page * PER_PAGE + i;
			buttons[i].setDefender(slot < roster.length ? roster[slot] : null);
		}
		refresh();
	}

	void refresh()
	{
		for (ShopButton b : buttons)
		{
			b.update();
		}
		hint.repaint();
	}

	private final class ShopButton extends JButton
	{
		private DefenderDefinition def;

		ShopButton()
		{
			setFocusable(false);
			setBorderPainted(false);
			setContentAreaFilled(false);
			setFocusPainted(false);
			setMargin(new Insets(0, 0, 0, 0));
			addActionListener(e -> choose());
		}

		void setDefender(DefenderDefinition value)
		{
			def = value;
			setEnabled(def != null);
			getAccessibleContext().setAccessibleName(def == null ? "Empty" : def.name + ", costs " + def.cost + " gold");
		}

		private void choose()
		{
			if (def == null || level < def.requiredLevel || !engine.isMatchActive())
			{
				return;
			}
			if (field.placing() == def)
			{
				field.clearSelection();
			}
			else if (engine.gold() >= def.cost)
			{
				field.setPlacing(def);
			}
		}

		void update()
		{
			String tip = null;
			if (def != null)
			{
				String footprint = def.size > 1 ? " Takes " + def.size + "x" + def.size + " tiles." : "";
				if (level < def.requiredLevel)
				{
					tip = def.name + " is locked: needs RuneDefence level " + def.requiredLevel;
				}
				else if (engine.isMatchActive() && engine.gold() < def.cost)
				{
					tip = def.name + ": costs " + def.cost + " gold, you have " + engine.gold();
				}
				else
				{
					tip = def.name + " (" + def.role + "): " + def.description + footprint;
				}
			}
			setToolTipText(tip);
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics;
			if (def == null)
			{
				Theme.slab(g, 0, 0, getWidth(), getHeight(), Theme.STONE_DARK);
				return;
			}
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			boolean locked = level < def.requiredLevel;
			boolean afford = !engine.isMatchActive() || engine.gold() >= def.cost;
			boolean chosen = field.placing() == def;
			boolean hover = getModel().isRollover() && !locked;
			Theme.slab(g, 0, 0, getWidth(), getHeight(), chosen ? Theme.STONE_LIGHT : hover ? Theme.STONE_MID : Theme.STONE);
			Composite old = g.getComposite();
			if (locked || !afford)
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, locked ? 0.3f : 0.55f));
			}
			// Big defenders shrink to fit, so that every icon stays inside its own button.
			g.drawImage(atlas.frame(def.id, 0), (getWidth() - ICON) / 2, 2, ICON, ICON, null);
			g.setComposite(old);
			g.setFont(Theme.small());
			int y = getHeight() - 5;
			if (locked)
			{
				g.drawImage(atlas.frame("lock", 0), (getWidth() - 13) / 2, 10, null);
				Theme.centered(g, "Lv " + def.requiredLevel, getWidth() / 2, y, Theme.ORANGE);
			}
			else
			{
				Theme.centered(g, def.cost + "g", getWidth() / 2, y, afford ? Theme.YELLOW : Theme.RED);
			}
		}
	}

	/** Two lines of help on the left, with the page arrows and number on the right. */
	private final class Hint extends JComponent
	{
		private final StoneButton previous = new StoneButton("<");
		private final StoneButton next = new StoneButton(">");

		Hint()
		{
			setPreferredSize(new Dimension(224, 34));
			previous.setLabel("<", "Previous page of defenders");
			next.setLabel(">", "Next page of defenders");
			previous.addActionListener(e -> showPage(page - 1));
			next.addActionListener(e -> showPage(page + 1));
			add(previous);
			add(next);
		}

		@Override
		public void doLayout()
		{
			int x = getWidth() - ARROW_WIDTH * 2 - 2;
			previous.setBounds(x, 1, ARROW_WIDTH, ARROW_HEIGHT);
			next.setBounds(x + ARROW_WIDTH, 1, ARROW_WIDTH, ARROW_HEIGHT);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics;
			g.setFont(Theme.small());
			Theme.centered(g, (page + 1) + "/" + pageCount(), getWidth() - ARROW_WIDTH - 2, 31, Theme.TEXT_DIM);
			DefenderDefinition placing = field.placing();
			if (placing != null)
			{
				Theme.text(g, placing.name, 4, 13, Theme.ORANGE);
				String size = placing.size > 1 ? " (" + placing.size + "x" + placing.size + ")" : "";
				Theme.text(g, "Click a tile" + size + ".", 4, 28, Theme.TEXT);
			}
			else if (!engine.isMatchActive())
			{
				Theme.text(g, "Start a match to recruit.", 4, 13, Theme.TEXT_DIM);
			}
			else
			{
				Theme.text(g, "Recruit above, or click", 4, 13, Theme.TEXT);
				Theme.text(g, "a defender to upgrade.", 4, 28, Theme.TEXT);
			}
		}
	}
}
