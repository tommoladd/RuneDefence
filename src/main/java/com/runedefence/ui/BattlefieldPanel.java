package com.runedefence.ui;

import com.runedefence.engine.DefenderInstance;
import com.runedefence.engine.EnemyInstance;
import com.runedefence.engine.FxEvent;
import com.runedefence.engine.GameEngine;
import com.runedefence.engine.Projectile;
import com.runedefence.model.DefenderDefinition;
import com.runedefence.model.MapDefinition;
import com.runedefence.model.Records;
import com.runedefence.model.WaveDefinition;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Draws the game and takes clicks on it. Gameplay stays in logical tile units;
 * pixels only exist in here, so resizing can never change how a match plays.
 */
final class BattlefieldPanel extends JComponent
{
	static final int NATIVE_TILE = 28;

	private static final double FX_LIFE = 0.6;
	private static final int FX_SLOTS = 48;
	private static final double CLICK_LIFE = 0.3;
	private static final double BANNER_LIFE = 2;
	private static final int FEET_BELOW_CENTRE = 8;
	private static final int SPRITE_FOOT_MARGIN = 3;
	private static final double RING_DIAMETER = 24;
	private static final int MONSTER_ICON = 18;

	interface Listener
	{
		/** Selection or placement mode changed. */
		void onFieldChanged();

		void onTutorialFinished();
	}

	private static final class ActiveFx
	{
		FxEvent.Kind kind;
		double x;
		double y;
		int value;
		String sprite;
		double radius;
		double age = FX_LIFE;
	}

	private static final String[] TUTORIAL = {
		"Welcome to RuneDefence!|Stop the raiders reaching the keep.|Leaks cost you health.",
		"Pick a defender from the shop,|then click open grass to place it.|Right-click to cancel.",
		"Kills and wave clears pay gold.|Spend it on defenders and upgrades.|Gold resets every match.",
		"Gaining XP in OSRS, in any skill,|raises your RuneDefence level and|unlocks new defenders. Good luck!"
	};

	private final GameEngine engine;
	private final SpriteAtlas atlas;
	private final Listener listener;
	private final ActiveFx[] fx = new ActiveFx[FX_SLOTS];

	private boolean[][] closed;
	private MapDefinition closedFor;
	private int closedLane;
	private BufferedImage terrain;
	private MapDefinition terrainMap;
	private int terrainTile;

	private MapDefinition menuMap;
	private boolean menuLocked;
	private Records.MapBest menuBest;
	private DefenderDefinition placing;
	private DefenderInstance selected;
	/** Mouse position in tile units; negative when it's off the field. */
	private double hoverX = -1;
	private double hoverY = -1;

	private double alpha;
	private double animTime;
	private boolean effects = true;
	private int tutorialStep = -1;

	private double clickAge = CLICK_LIFE;
	private double clickX;
	private double clickY;
	private boolean clickOk;

	private int bannerWave = -1;
	private double bannerAge = BANNER_LIFE;

	BattlefieldPanel(GameEngine engine, SpriteAtlas atlas, Listener listener)
	{
		this.engine = engine;
		this.atlas = atlas;
		this.listener = listener;
		for (int i = 0; i < FX_SLOTS; i++)
		{
			fx[i] = new ActiveFx();
		}
		menuMap = engine.content().maps[0];
		MapDefinition map = displayMap();
		setPreferredSize(new Dimension(map.columns * NATIVE_TILE, map.rows * NATIVE_TILE));
		setOpaque(true);

		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				hover(e);
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				hover(e);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hoverX = -1;
				hoverY = -1;
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				press(e);
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	// ---- State --------------------------------------------------------------

	DefenderDefinition placing()
	{
		return placing;
	}

	void setPlacing(DefenderDefinition def)
	{
		placing = def;
		selected = null;
		listener.onFieldChanged();
	}

	DefenderInstance selected()
	{
		return selected;
	}

	void setSelected(DefenderInstance d)
	{
		selected = d;
		placing = null;
		listener.onFieldChanged();
	}

	void clearSelection()
	{
		if (selected != null || placing != null)
		{
			selected = null;
			placing = null;
			listener.onFieldChanged();
		}
	}

	/** What the map picker is showing, while there's no match. */
	void setMenu(MapDefinition map, boolean locked, Records.MapBest best)
	{
		menuMap = map;
		menuLocked = locked;
		menuBest = best;
	}

	void setEffects(boolean enabled)
	{
		effects = enabled;
	}

	void showTutorial()
	{
		tutorialStep = 0;
	}

	void setHover(int col, int row)
	{
		hoverX = col + 0.5;
		hoverY = row + 0.5;
	}

	/** Top left tile for a footprint centred on the mouse, nudged back onto the map at the edges. */
	private int anchor(double hover, int size, int limit)
	{
		int tile = (int) Math.floor(hover - size / 2.0 + 0.5);
		return Math.max(0, Math.min(tile, limit - size));
	}

	/** Advances visual-only timers. Effects freeze with the match when it is paused. */
	void animate(double seconds, double interpolation)
	{
		alpha = interpolation;
		FxEvent event;
		while ((event = engine.pollFx()) != null)
		{
			if (effects)
			{
				addFx(event);
			}
		}
		if (engine.state() == GameEngine.State.PAUSED)
		{
			alpha = 1;
			return;
		}
		animTime += seconds;
		clickAge += seconds;
		bannerAge += seconds;
		for (ActiveFx f : fx)
		{
			if (f.age < FX_LIFE)
			{
				f.age += seconds;
			}
		}
		if (engine.state() == GameEngine.State.RUNNING && bannerWave != engine.waveIndex())
		{
			bannerWave = engine.waveIndex();
			bannerAge = 0;
		}
	}

	private void addFx(FxEvent e)
	{
		// Takes the oldest slot, which caps the number of effects on screen.
		ActiveFx oldest = fx[0];
		for (ActiveFx f : fx)
		{
			if (f.age > oldest.age)
			{
				oldest = f;
			}
		}
		oldest.kind = e.kind;
		oldest.x = e.x;
		oldest.y = e.y;
		oldest.value = e.value;
		oldest.sprite = e.sprite;
		oldest.radius = e.radius;
		oldest.age = 0;
	}

	private MapDefinition displayMap()
	{
		return engine.map() != null ? engine.map() : menuMap;
	}

	private int tile()
	{
		MapDefinition m = displayMap();
		return Math.max(1, Math.min(getWidth() / m.columns, getHeight() / m.rows));
	}

	private int originX(int tile)
	{
		return (getWidth() - displayMap().columns * tile) / 2;
	}

	private int originY(int tile)
	{
		return (getHeight() - displayMap().rows * tile) / 2;
	}

	// ---- Input --------------------------------------------------------------

	private void hover(MouseEvent e)
	{
		int tile = tile();
		hoverX = (e.getX() - originX(tile)) / (double) tile;
		hoverY = (e.getY() - originY(tile)) / (double) tile;
	}

	private void press(MouseEvent e)
	{
		hover(e);
		if (tutorialStep >= 0)
		{
			tutorialStep++;
			if (tutorialStep >= TUTORIAL.length)
			{
				tutorialStep = -1;
				listener.onTutorialFinished();
			}
			return;
		}
		if (SwingUtilities.isRightMouseButton(e))
		{
			clearSelection();
			return;
		}
		if (engine.map() == null)
		{
			return;
		}
		if (placing != null)
		{
			MapDefinition m = engine.map();
			int col = anchor(hoverX, placing.size, m.columns);
			int row = anchor(hoverY, placing.size, m.rows);
			boolean ok = engine.place(placing.id, col, row) == GameEngine.Result.OK;
			showClick(ok, col + placing.size / 2.0, row + placing.size / 2.0);
			if (ok)
			{
				placing = null;
				listener.onFieldChanged();
			}
			return;
		}
		DefenderInstance d = engine.defenderAt((int) Math.floor(hoverX), (int) Math.floor(hoverY));
		if (d != null)
		{
			setSelected(d);
		}
		else
		{
			clearSelection();
		}
	}

	private void showClick(boolean ok, double x, double y)
	{
		clickOk = ok;
		clickAge = 0;
		clickX = x;
		clickY = y;
	}

	// ---- Painting -----------------------------------------------------------

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics;
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(Theme.STONE_EDGE);
		g.fillRect(0, 0, getWidth(), getHeight());

		int tile = tile();
		int ox = originX(tile);
		int oy = originY(tile);
		MapDefinition map = displayMap();
		if (terrain == null || terrainMap != map || terrainTile != tile)
		{
			terrain = Terrain.render(map, atlas, tile);
			terrainMap = map;
			terrainTile = tile;
		}
		g.drawImage(terrain, ox, oy, null);
		g.translate(ox, oy);
		g.setFont(Theme.small());

		if (engine.map() != null)
		{
			paintClosedRoutes(g, tile);
			paintPlacement(g, tile);
			paintSelection(g, tile);
			paintDefenders(g, tile);
			paintEnemies(g, tile);
			paintProjectiles(g, tile);
			paintFx(g, tile);
			paintClick(g, tile);
		}
		paintOverlays(g, tile, map);
		g.translate(-ox, -oy);
	}

	/** Where the route shifts, the corridors not in use go dark. */
	private void paintClosedRoutes(Graphics2D g, int tile)
	{
		MapDefinition m = engine.map();
		int lane = engine.activeLane();
		if (lane < 0)
		{
			return;
		}
		if (closedFor != m || closedLane != lane)
		{
			closedFor = m;
			closedLane = lane;
			closed = new boolean[m.rows][m.columns];
			for (int row = 0; row < m.rows; row++)
			{
				for (int col = 0; col < m.columns; col++)
				{
					closed[row][col] = m.isOnPath(col, row) && !m.isOnLane(lane, col, row);
				}
			}
		}
		g.setColor(Theme.CLOSED_ROUTE);
		for (int row = 0; row < m.rows; row++)
		{
			for (int col = 0; col < m.columns; col++)
			{
				if (closed[row][col])
				{
					g.fillRect(col * tile, row * tile, tile, tile);
				}
			}
		}
	}

	private void paintPlacement(Graphics2D g, int tile)
	{
		if (placing == null || hoverX < 0 || hoverY < 0)
		{
			return;
		}
		MapDefinition m = engine.map();
		if (hoverX >= m.columns || hoverY >= m.rows)
		{
			return;
		}
		int size = placing.size;
		int col = anchor(hoverX, size, m.columns);
		int row = anchor(hoverY, size, m.rows);
		g.setColor(engine.canPlaceAt(size, col, row) ? Theme.VALID_FILL : Theme.INVALID_FILL);
		g.fillRect(col * tile, row * tile, size * tile, size * tile);
		paintRange(g, col + size / 2.0, row + size / 2.0, placing.rangeTiles, tile);
		Composite old = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
		g.drawImage(atlas.frame(placing.id, 0), col * tile, row * tile - 2, size * tile, size * tile, null);
		g.setComposite(old);
	}

	private void paintSelection(Graphics2D g, int tile)
	{
		if (selected == null)
		{
			return;
		}
		paintRange(g, selected.x, selected.y, selected.range, tile);
		g.setColor(Theme.SELECT_LINE);
		int span = selected.def.size * tile;
		g.drawRect(selected.col * tile, selected.row * tile, span - 1, span - 1);
	}

	private static void paintRange(Graphics2D g, double x, double y, double range, int tile)
	{
		int r = (int) (range * tile);
		int cx = (int) (x * tile);
		int cy = (int) (y * tile);
		g.setColor(Theme.RANGE_FILL);
		g.fillOval(cx - r, cy - r, r * 2, r * 2);
		g.setColor(Theme.RANGE_LINE);
		g.drawOval(cx - r, cy - r, r * 2, r * 2);
	}

	private void paintDefenders(Graphics2D g, int tile)
	{
		BufferedImage pad = atlas.frame("pad", 0);
		List<DefenderInstance> list = engine.defenders();
		for (int i = 0; i < list.size(); i++)
		{
			DefenderInstance d = list.get(i);
			int x = d.col * tile;
			int y = d.row * tile;
			int span = d.def.size * tile;
			g.drawImage(pad, x, y, span, span, null);
			int frame;
			if (d.stunTimer > 0)
			{
				frame = 0;
			}
			else if (d.attackAnim > 0.12)
			{
				frame = 2;
			}
			else if (d.attackAnim > 0)
			{
				frame = 3;
			}
			else
			{
				// Each defender breathes slightly out of step with its neighbours.
				frame = (int) ((animTime + (d.col + d.row) * 0.17) / 0.6) % 2;
			}
			drawFacing(g, atlas.frame(d.def.id, frame), x, y - tile / 14, span, span, d.facingLeft);
			if (d.stunTimer > 0)
			{
				// Petrified: frozen on one frame, under a skin of stone.
				g.setColor(Theme.PETRIFIED);
				g.fillOval(x + span / 8, y, span * 3 / 4, span - 2);
			}
			int tiers = d.tierA + d.tierB;
			for (int t = 0; t < tiers; t++)
			{
				g.setColor(Color.BLACK);
				g.fillRect(x + 1, y + span - 4 - t * 4, 3, 3);
				g.setColor(Theme.YELLOW);
				g.fillRect(x + 1, y + span - 4 - t * 4, 2, 2);
			}
		}
	}

	private static void drawFacing(Graphics2D g, BufferedImage image, int x, int y, int w, int h, boolean left)
	{
		if (left)
		{
			g.drawImage(image, x + w, y, -w, h, null);
		}
		else
		{
			g.drawImage(image, x, y, w, h, null);
		}
	}

	private void paintEnemies(Graphics2D g, int tile)
	{
		List<EnemyInstance> list = engine.enemies();
		for (int i = 0; i < list.size(); i++)
		{
			EnemyInstance e = list.get(i);
			double x = (e.prevX + (e.x - e.prevX) * alpha + e.laneOffset) * tile;
			double y = (e.prevY + (e.y - e.prevY) * alpha + e.laneOffset) * tile;
			SpriteAtlas.Sprite sprite = atlas.get(e.def.id);
			int w = sprite.width * tile / NATIVE_TILE;
			int h = sprite.height * tile / NATIVE_TILE;
			int left = (int) x - w / 2;
			int top = (int) y + FEET_BELOW_CENTRE * tile / NATIVE_TILE - h + SPRITE_FOOT_MARGIN;
			if (e.isRooted())
			{
				// A snare around its feet, big enough to see at a glance in a crowd.
				int ringWidth = Math.max(w * 3 / 4, tile * 2 / 3);
				g.setColor(Theme.BIND_FILL);
				g.fillOval((int) x - ringWidth / 2, top + h - tile / 4, ringWidth, tile / 3);
				g.setColor(Theme.BIND_LINE);
				g.drawOval((int) x - ringWidth / 2, top + h - tile / 4, ringWidth, tile / 3);
			}
			drawFacing(g, sprite.frames[(int) (e.progress * 5) % sprite.frames.length], left, top, w, h, e.facingLeft);
			paintStatuses(g, e, (int) x, top + 1);
			if (e.hp < e.maxHp)
			{
				paintHealthBar(g, (int) x, top - 3, e.def.boss ? 30 : 18, e.hp / e.maxHp);
			}
		}
	}

	/** A row of coloured chips over the head, one for each thing currently affecting it. */
	private static void paintStatuses(Graphics2D g, EnemyInstance e, int cx, int y)
	{
		int x = cx - 8;
		x = chip(g, e.isArmourBroken(), Theme.ORANGE, x, y);
		x = chip(g, e.isBurning(), Theme.BURN_CHIP, x, y);
		x = chip(g, e.isSlowed(), Theme.SLOW_CHIP, x, y);
		x = chip(g, e.isRooted(), Theme.ROOT_CHIP, x, y);
		chip(g, e.isWeakened(), Theme.CURSE_CHIP, x, y);
	}

	private static int chip(Graphics2D g, boolean on, Color colour, int x, int y)
	{
		if (!on)
		{
			return x;
		}
		g.setColor(Color.BLACK);
		g.fillRect(x, y, 4, 4);
		g.setColor(colour);
		g.fillRect(x, y, 3, 3);
		return x + 4;
	}

	private static void paintHealthBar(Graphics2D g, int cx, int y, int w, double fraction)
	{
		int left = cx - w / 2;
		g.setColor(Theme.HP_RED);
		g.fillRect(left, y, w, 3);
		g.setColor(Theme.HP_GREEN);
		g.fillRect(left, y, (int) Math.ceil(w * Math.max(0, fraction)), 3);
	}

	private void paintProjectiles(Graphics2D g, int tile)
	{
		List<Projectile> list = engine.projectiles();
		for (int i = 0; i < list.size(); i++)
		{
			Projectile p = list.get(i);
			int x = (int) ((p.prevX + (p.x - p.prevX) * alpha) * tile);
			int y = (int) ((p.prevY + (p.y - p.prevY) * alpha) * tile) - tile / 7;
			switch (p.kind)
			{
				case "arrow":
					paintShaft(g, x, y, p.dirX, p.dirY, 8, Theme.ARROW_SHAFT, Color.WHITE);
					break;
				case "dart":
					paintShaft(g, x, y, p.dirX, p.dirY, 5, Theme.DART_SHAFT, Theme.DART_TIP);
					break;
				default:
					BufferedImage image = atlas.frame(p.kind, (int) (animTime * 12));
					g.drawImage(image, x - image.getWidth() / 2, y - image.getHeight() / 2, null);
					break;
			}
		}
	}

	private static void paintShaft(Graphics2D g, int x, int y, double dx, double dy, int length, Color shaft, Color tip)
	{
		int tx = x - (int) (dx * length);
		int ty = y - (int) (dy * length);
		g.setColor(Color.BLACK);
		g.drawLine(x, y + 1, tx, ty + 1);
		g.setColor(shaft);
		g.drawLine(x, y, tx, ty);
		g.setColor(tip);
		g.fillRect(x, y, 2, 2);
	}

	private void paintFx(Graphics2D g, int tile)
	{
		// Puffs and blasts go underneath, so hit numbers are always readable.
		for (ActiveFx f : fx)
		{
			if (f.age >= FX_LIFE)
			{
				continue;
			}
			if (f.kind == FxEvent.Kind.BLAST)
			{
				paintBlast(g, f, tile);
			}
			else if (f.kind == FxEvent.Kind.DEATH)
			{
				paintBurst(g, "dust", f, tile, f.value);
			}
		}
		for (ActiveFx f : fx)
		{
			if (f.age >= FX_LIFE)
			{
				continue;
			}
			if (f.kind == FxEvent.Kind.HIT || f.kind == FxEvent.Kind.LEAK)
			{
				paintSplat(g, f, tile, Theme.SPLAT_RED, Theme.SPLAT_RED_DARK);
			}
			else if (f.kind == FxEvent.Kind.CRIT)
			{
				paintSplat(g, f, tile, Theme.SPLAT_GOLD, Theme.SPLAT_GOLD_DARK);
			}
			else if (f.kind == FxEvent.Kind.GAZE)
			{
				int r = tile / 2 + (int) (f.age * 20);
				g.setColor(Theme.YELLOW);
				g.drawOval((int) (f.x * tile) - r, (int) (f.y * tile) - r, r * 2, r * 2);
			}
			else if (f.kind == FxEvent.Kind.MISS)
			{
				paintSplat(g, f, tile, Theme.SPLAT_BLUE, Theme.SPLAT_BLUE_DARK);
			}
		}
	}

	/** The ring is drawn at the real blast radius, so players can see what it reached. */
	private void paintBlast(Graphics2D g, ActiveFx f, int tile)
	{
		SpriteAtlas.Sprite s = atlas.get(f.sprite);
		int frame = Math.min(s.frames.length - 1, (int) (f.age / FX_LIFE * s.frames.length));
		int size = (int) (f.radius * 2 * tile * s.width / RING_DIAMETER);
		g.drawImage(s.frames[frame], (int) (f.x * tile) - size / 2, (int) (f.y * tile) - size / 2, size, size, null);
	}

	private void paintBurst(Graphics2D g, String name, ActiveFx f, int tile, int scale)
	{
		SpriteAtlas.Sprite s = atlas.get(name);
		int frame = Math.min(s.frames.length - 1, (int) (f.age / FX_LIFE * s.frames.length));
		int w = s.width * scale * tile / NATIVE_TILE;
		int h = s.height * scale * tile / NATIVE_TILE;
		g.drawImage(s.frames[frame], (int) (f.x * tile) - w / 2, (int) (f.y * tile) - h / 2, w, h, null);
	}

	private void paintSplat(Graphics2D g, ActiveFx f, int tile, Color face, Color edge)
	{
		// Only shown for the first half of its life, like a hit marker, and rises slightly.
		if (f.age > FX_LIFE * 0.7)
		{
			return;
		}
		int cx = (int) (f.x * tile);
		int cy = (int) (f.y * tile) - tile / 3 - (int) (f.age * 8);
		String value = Integer.toString(f.kind == FxEvent.Kind.MISS ? 0 : f.value);
		// Wide enough for the big numbers late-game hits reach.
		int half = Math.max(7, g.getFontMetrics().stringWidth(value) / 2 + 3);
		g.setColor(edge);
		g.fillRect(cx - half, cy - 4, half * 2, 9);
		g.fillRect(cx - half + 2, cy - 6, half * 2 - 4, 13);
		g.setColor(face);
		g.fillRect(cx - half + 1, cy - 3, half * 2 - 2, 7);
		g.fillRect(cx - half + 3, cy - 5, half * 2 - 6, 11);
		Theme.centered(g, value, cx, cy + 4, Color.WHITE);
	}

	private void paintClick(Graphics2D g, int tile)
	{
		if (clickAge >= CLICK_LIFE)
		{
			return;
		}
		int size = 7 - (int) (clickAge / CLICK_LIFE * 5);
		int cx = (int) (clickX * tile);
		int cy = (int) (clickY * tile);
		g.setColor(clickOk ? Theme.YELLOW : Theme.RED);
		g.drawLine(cx - size, cy - size, cx + size, cy + size);
		g.drawLine(cx - size, cy + size, cx + size, cy - size);
		g.drawLine(cx - size + 1, cy - size, cx + size + 1, cy + size);
		g.drawLine(cx - size + 1, cy + size, cx + size + 1, cy - size);
	}

	private void paintOverlays(Graphics2D g, int tile, MapDefinition map)
	{
		int w = map.columns * tile;
		int h = map.rows * tile;
		GameEngine.State state = engine.state();
		if (tutorialStep >= 0)
		{
			paintTutorial(g, w, h);
			return;
		}
		switch (state)
		{
			case MENU:
				paintMapPicker(g, w, h, map);
				break;
			case PAUSED:
				paintBox(g, w, h / 2 - 22, 38, Theme.DIM_OVERLAY);
				g.setFont(Theme.bold());
				Theme.centered(g, "Paused", w / 2, h / 2 + 2, Theme.YELLOW);
				break;
			case VICTORY:
			case DEFEAT:
				paintEnd(g, w, h, state == GameEngine.State.VICTORY);
				break;
			case PREPARING:
			case WAVE_COMPLETE:
				paintNextWave(g, w);
				break;
			default:
				break;
		}
		if (state == GameEngine.State.RUNNING && bannerAge < BANNER_LIFE)
		{
			paintBanner(g, w, h);
		}
	}

	private void paintMapPicker(Graphics2D g, int w, int h, MapDefinition map)
	{
		paintBox(g, w, h / 2 - 76, 150, Theme.PANEL_OVERLAY);
		g.setFont(Theme.bold());
		Theme.centered(g, map.name, w / 2, h / 2 - 54, Theme.ORANGE);
		g.setFont(Theme.small());
		int y = h / 2 - 34;
		for (String line : map.description)
		{
			Theme.centered(g, line, w / 2, y, Theme.TEXT);
			y += 15;
		}
		y = h / 2 + 22;
		Theme.centered(g, map.waves.length + " waves, " + map.startingGold + " starting gold", w / 2, y, Theme.YELLOW);
		y += 16;
		if (menuLocked)
		{
			Theme.centered(g, "Unlocks at RuneDefence level " + map.requiredLevel, w / 2, y, Theme.RED);
		}
		else if (menuBest != null)
		{
			Theme.centered(g, "Best wave " + menuBest.bestWave + ", best score " + menuBest.bestScore, w / 2, y, Theme.GREEN);
		}
		else
		{
			Theme.centered(g, "Not played yet", w / 2, y, Theme.TEXT_DIM);
		}
		paintMonsterIcons(g, map, w, y + 6);
	}

	/** A row of the monsters this map can send, drawn small enough for the longest list to fit. */
	private void paintMonsterIcons(Graphics2D g, MapDefinition map, int w, int y)
	{
		int count = map.monsters.length;
		int step = Math.min(MONSTER_ICON + 2, (w - 8) / Math.max(1, count));
		int x = (w - step * count) / 2;
		for (String id : map.monsters)
		{
			g.drawImage(atlas.get(id).frames[0], x, y, Math.min(step, MONSTER_ICON), Math.min(step, MONSTER_ICON), null);
			x += step;
		}
	}

	private static void paintBox(Graphics2D g, int w, int y, int height, Color c)
	{
		g.setColor(c);
		g.fillRect(0, y, w, height);
		g.setColor(Theme.STONE_LIGHT);
		g.drawLine(0, y, w, y);
		g.drawLine(0, y + height - 1, w, y + height - 1);
	}

	private void paintEnd(Graphics2D g, int w, int h, boolean victory)
	{
		paintBox(g, w, h / 2 - 66, 112, Theme.PANEL_OVERLAY);
		g.setFont(Theme.bold());
		Theme.centered(g, victory ? "Victory!" : "The keep has fallen", w / 2, h / 2 - 40, victory ? Theme.GREEN : Theme.RED);
		g.setFont(Theme.small());
		Theme.centered(g, "Waves cleared: " + engine.wavesCleared() + "/" + engine.waveCount(), w / 2, h / 2 - 20, Theme.TEXT);
		Theme.centered(g, "Enemies defeated: " + engine.enemiesDefeated(), w / 2, h / 2 - 4, Theme.TEXT);
		Theme.centered(g, "Gold spent: " + engine.goldSpent(), w / 2, h / 2 + 12, Theme.TEXT);
		Theme.centered(g, "Score: " + engine.score(), w / 2, h / 2 + 32, Theme.YELLOW);
	}

	private void paintNextWave(Graphics2D g, int w)
	{
		WaveDefinition wave = engine.currentWave();
		g.setColor(Theme.DIM_OVERLAY);
		g.fillRect(0, 0, w, 20);
		g.setFont(Theme.small());
		if (engine.state() == GameEngine.State.WAVE_COMPLETE && engine.map().isMilestone(engine.wavesCleared()))
		{
			g.setFont(Theme.bold());
			Theme.centered(g, "Wave " + engine.wavesCleared() + " defeated!", w / 2, 44, Theme.GREEN);
			g.setFont(Theme.small());
			Theme.centered(g, "They're only getting stronger...", w / 2, 60, Theme.ORANGE);
		}
		else if (engine.map().rotateLanesEvery > 0 && engine.waveIndex() > 0 && engine.waveIndex() % engine.map().rotateLanesEvery == 0)
		{
			g.setFont(Theme.small());
			Theme.centered(g, "The route has shifted!", w / 2, 36, Theme.ORANGE);
		}
		Theme.text(g, "Next:", 3, 14, Theme.YELLOW);
		int x = 34;
		for (WaveDefinition.Group group : wave.groups)
		{
			String count = group.count + "x";
			Theme.text(g, count, x, 14, Theme.TEXT);
			x += g.getFontMetrics().stringWidth(count) + 1;
			SpriteAtlas.Sprite s = atlas.get(group.enemy);
			g.drawImage(s.frames[0], x, 1, 18, 18, null);
			x += 21;
		}
	}

	private void paintBanner(Graphics2D g, int w, int h)
	{
		Composite old = g.getComposite();
		float fade = (float) Math.min(1, (BANNER_LIFE - bannerAge) * 2);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, fade)));
		g.setFont(Theme.bold());
		String text = "Wave " + (engine.waveIndex() + 1);
		boolean boss = false;
		for (WaveDefinition.Group group : engine.currentWave().groups)
		{
			if (engine.content().enemy(group.enemy).boss)
			{
				boss = true;
			}
		}
		Theme.centered(g, text, w / 2, 44, boss ? Theme.RED : Theme.YELLOW);
		if (boss)
		{
			g.setFont(Theme.small());
			Theme.centered(g, "A champion approaches!", w / 2, 60, Theme.ORANGE);
		}
		g.setComposite(old);
	}

	private void paintTutorial(Graphics2D g, int w, int h)
	{
		paintBox(g, w, h / 2 - 60, 110, Theme.PANEL_OVERLAY);
		g.setFont(Theme.small());
		String[] lines = TUTORIAL[tutorialStep].split("\\|");
		int y = h / 2 - 34;
		for (int i = 0; i < lines.length; i++)
		{
			Theme.centered(g, lines[i], w / 2, y, i == 0 && tutorialStep == 0 ? Theme.ORANGE : Theme.TEXT);
			y += 18;
		}
		Theme.centered(g, "Click to continue (" + (tutorialStep + 1) + "/" + TUTORIAL.length + ")", w / 2, y + 12, Theme.YELLOW);
	}
}
