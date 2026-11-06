package com.runedefence.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JButton;

/**
 * A chiselled stone button. It never takes keyboard focus, so clicking it leaves
 * the game's own key bindings working.
 */
class StoneButton extends JButton
{
	private Color textColour = Theme.TEXT;
	private boolean active;

	StoneButton(String text)
	{
		super(text);
		setFocusable(false);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setFocusPainted(false);
		setRolloverEnabled(true);
		setFont(Theme.small());
		setPreferredSize(new Dimension(60, 22));
	}

	void setLabel(String text, String tooltip)
	{
		if (!text.equals(getText()))
		{
			setText(text);
			getAccessibleContext().setAccessibleName(text);
		}
		setToolTipText(tooltip);
	}

	void setTextColour(Color c)
	{
		textColour = c;
		repaint();
	}

	void setActive(boolean value)
	{
		if (active != value)
		{
			active = value;
			repaint();
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics;
		boolean down = getModel().isArmed() && getModel().isPressed();
		boolean hover = getModel().isRollover() && isEnabled();
		Color face = !isEnabled() ? Theme.STONE_DARK : active || down ? Theme.STONE_LIGHT : hover ? Theme.STONE_MID : Theme.STONE;
		Theme.slab(g, 0, 0, getWidth(), getHeight(), face);
		g.setFont(getFont());
		int y = (getHeight() + g.getFontMetrics().getAscent()) / 2 - 2 + (down ? 1 : 0);
		Theme.centered(g, getText(), getWidth() / 2, y, isEnabled() ? textColour : Theme.TEXT_DIM);
	}
}
