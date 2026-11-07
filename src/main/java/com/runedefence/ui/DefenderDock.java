package com.runedefence.ui;

import com.runedefence.engine.DefenderInstance;
import com.runedefence.engine.GameEngine;
import com.runedefence.model.UpgradeDefinition;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Upgrade, retarget or sell the selected defender. */
final class DefenderDock extends JPanel
{
	private final GameEngine engine;
	private final BattlefieldPanel field;
	private final Runnable onChanged;
	private final StoneButton[] paths = {new StoneButton(""), new StoneButton("")};
	private final StoneButton target = new StoneButton("");
	private final StoneButton sell = new StoneButton("");
	private final Title title = new Title();

	DefenderDock(GameEngine engine, BattlefieldPanel field, Runnable onChanged)
	{
		super(new BorderLayout());
		this.engine = engine;
		this.field = field;
		this.onChanged = onChanged;
		setBackground(Theme.STONE_DARK);

		JPanel rows = new JPanel(new GridLayout(3, 1, 0, 1));
		rows.setOpaque(false);
		for (int i = 0; i < paths.length; i++)
		{
			int path = i;
			paths[i].addActionListener(e -> upgrade(path));
			rows.add(paths[i]);
		}
		JPanel bottom = new JPanel(new GridLayout(1, 2, 1, 0));
		bottom.setOpaque(false);
		target.addActionListener(e -> retarget());
		sell.addActionListener(e -> sellSelected());
		bottom.add(target);
		bottom.add(sell);
		rows.add(bottom);

		add(title, BorderLayout.NORTH);
		add(rows, BorderLayout.CENTER);
	}

	private void upgrade(int path)
	{
		DefenderInstance d = field.selected();
		if (d != null && engine.upgrade(d, path) == GameEngine.Result.OK)
		{
			onChanged.run();
		}
	}

	private void retarget()
	{
		DefenderInstance d = field.selected();
		if (d != null)
		{
			engine.cycleTargeting(d);
			onChanged.run();
		}
	}

	private void sellSelected()
	{
		DefenderInstance d = field.selected();
		if (d != null && engine.sell(d) == GameEngine.Result.OK)
		{
			field.clearSelection();
			onChanged.run();
		}
	}

	void refresh()
	{
		DefenderInstance d = field.selected();
		if (d == null)
		{
			return;
		}
		for (int i = 0; i < paths.length; i++)
		{
			UpgradeDefinition next = GameEngine.nextUpgrade(d, i);
			if (next == null)
			{
				paths[i].setLabel("Path complete", null);
				paths[i].setEnabled(false);
			}
			else if (GameEngine.isPathBlocked(d, i))
			{
				paths[i].setLabel("Locked by other path", "Only one path can reach its second upgrade");
				paths[i].setEnabled(false);
			}
			else
			{
				boolean afford = engine.gold() >= next.cost && engine.isMatchActive();
				paths[i].setLabel(next.name + " (" + next.cost + "g)", upgradeTip(d, next, i));
				paths[i].setTextColour(afford ? Theme.YELLOW : Theme.RED);
				paths[i].setEnabled(engine.isMatchActive());
			}
		}
		target.setLabel("Target: " + d.targeting.label(), "Which enemy in range to attack. Click to change.");
		sell.setLabel("Sell (" + GameEngine.sellValue(d) + "g)", "Refunds 70% of the gold spent on this defender");
		sell.setEnabled(engine.isMatchActive());
		title.repaint();
	}

	/** What this upgrade would do to this defender, as it stands on this board. */
	private String upgradeTip(DefenderInstance d, UpgradeDefinition next, int path)
	{
		StringBuilder tip = new StringBuilder("<html>").append(next.description);
		double speedBoost = (1 + d.auraSpeed) * engine.map().defenderSpeed;
		for (String effect : DefenderText.upgradeEffects(d.def, d.tierA, d.tierB, path, 1 + d.auraDamage, speedBoost))
		{
			tip.append("<br>").append(effect.replace(">", "&gt;"));
		}
		return tip.append("</html>").toString();
	}

	private final class Title extends JComponent
	{
		Title()
		{
			setPreferredSize(new Dimension(224, 17));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			DefenderInstance d = field.selected();
			if (d == null)
			{
				return;
			}
			Graphics2D g = (Graphics2D) graphics;
			g.setFont(Theme.small());
			Theme.text(g, d.def.name, 4, 13, Theme.ORANGE);
			setToolTipText("Damage, seconds between attacks (lower is faster) and range. Green means a nearby ally is boosting it.");
			String stats = String.format(Locale.ROOT, "%d  %s  %.1f", d.effectiveDamage(), DefenderText.seconds(engine.attackInterval(d)), d.range);
			boolean boosted = d.auraDamage > 0 || d.auraSpeed > 0;
			Theme.text(g, stats, getWidth() - g.getFontMetrics().stringWidth(stats) - 4, 13, boosted ? Theme.GREEN : Theme.TEXT);
		}
	}
}
