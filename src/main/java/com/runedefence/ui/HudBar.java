package com.runedefence.ui;

import com.runedefence.engine.GameEngine;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** The gold, health, wave and level strip above the battlefield. */
final class HudBar extends JComponent
{
	private final GameEngine engine;
	private final SpriteAtlas atlas;
	private int level = 1;

	HudBar(GameEngine engine, SpriteAtlas atlas)
	{
		this.engine = engine;
		this.atlas = atlas;
		setPreferredSize(new Dimension(224, 22));
		setToolTipText("Match gold, keep health, wave and your RuneDefence level");
	}

	void setLevel(int value)
	{
		level = value;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics;
		Theme.slab(g, 0, 0, getWidth(), getHeight(), Theme.STONE);
		g.setFont(Theme.small());
		boolean match = engine.map() != null;
		int y = 15;
		int inset = (getWidth() - 224) / 2;
		g.drawImage(atlas.frame("coin", 0), inset + 3, 4, null);
		Theme.text(g, match ? Integer.toString(engine.gold()) : "-", inset + 18, y, Theme.YELLOW);
		g.drawImage(atlas.frame("heart", 0), inset + 58, 4, null);
		Theme.text(g, match ? Integer.toString(engine.baseHealth()) : "-", inset + 73, y, Theme.TEXT);
		String wave = match ? "Wave " + Math.min(engine.waveIndex() + 1, engine.waveCount()) + "/" + engine.waveCount() : "Wave -";
		Theme.text(g, wave, inset + 97, y, Theme.TEXT);
		String lv = "Lv " + level;
		Theme.text(g, lv, inset + 221 - g.getFontMetrics().stringWidth(lv), y, Theme.ORANGE);
	}
}
