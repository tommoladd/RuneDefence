package com.runedefence.ui;

import com.runedefence.model.MapDefinition;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Paints a map once into a single image, so that the battlefield only ever blits it. */
final class Terrain
{
	private Terrain()
	{
	}

	private static Color colour(String hex)
	{
		return new Color(Integer.parseInt(hex, 16));
	}

	private static Color[] colours(String[] hex)
	{
		Color[] out = new Color[hex.length];
		for (int i = 0; i < hex.length; i++)
		{
			out[i] = colour(hex[i]);
		}
		return out;
	}

	static BufferedImage render(MapDefinition map, SpriteAtlas atlas, int tile)
	{
		int w = map.columns * tile;
		int h = map.rows * tile;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		// Fixed seed: a given map always looks the same.
		Random rng = new Random(map.id.hashCode());

		MapDefinition.Palette p = map.palette;
		Color groundA = colour(p.groundA);
		Color groundB = colour(p.groundB);
		Color[] groundFlecks = colours(p.groundFlecks);
		Color dirt = colour(p.path);
		Color dirtEdge = colour(p.pathEdge);
		Color[] dirtFlecks = colours(p.pathFlecks);

		for (int row = 0; row < map.rows; row++)
		{
			for (int col = 0; col < map.columns; col++)
			{
				int x = col * tile;
				int y = row * tile;
				boolean path = map.isOnPath(col, row);
				// A faint checker lets players read the grid without drawn lines.
				g.setColor(path ? dirt : (col + row) % 2 == 0 ? groundA : groundB);
				g.fillRect(x, y, tile, tile);
				flecks(g, rng, x, y, tile, path ? dirtFlecks : groundFlecks, path ? 14 : 10);
				if (path)
				{
					pathEdges(g, map, col, row, x, y, tile, dirtEdge);
				}
				else if (map.flowers && map.tiles[row].charAt(col) == '.' && rng.nextInt(9) == 0)
				{
					g.drawImage(atlas.frame("flower", 0), x, y, tile, tile, null);
				}
			}
		}

		// Scenery runs top to bottom so that nearer objects overlap further ones.
		int keepCol = -1;
		int keepRow = -1;
		for (int row = 0; row < map.rows; row++)
		{
			for (int col = 0; col < map.columns; col++)
			{
				char letter = map.tiles[row].charAt(col);
				if (letter == 'K')
				{
					if (keepCol < 0)
					{
						keepCol = col;
						keepRow = row;
					}
				}
				else if (letter != '.')
				{
					drawScenery(g, atlas, map, letter, col * tile, row * tile, tile);
				}
			}
		}
		if (keepCol >= 0)
		{
			SpriteAtlas.Sprite keep = atlas.get(map.sceneryFor('K', "keep"));
			int kw = keep.width * tile / BattlefieldPanel.NATIVE_TILE;
			int kh = keep.height * tile / BattlefieldPanel.NATIVE_TILE;
			g.drawImage(keep.frames[0], keepCol * tile, (keepRow + 1) * tile - kh, kw, kh, null);
		}
		g.dispose();
		return image;
	}

	private static String defaultScenery(char letter)
	{
		switch (letter)
		{
			case 'T':
				return "tree";
			case 'W':
				return "water";
			case 'C':
				return "camp";
			default:
				return "rock";
		}
	}

	/** Tall scenery stands on the bottom of its tile, and may overlap the tile above. */
	private static void drawScenery(Graphics2D g, SpriteAtlas atlas, MapDefinition map, char letter, int x, int y, int tile)
	{
		SpriteAtlas.Sprite s = atlas.get(map.sceneryFor(letter, defaultScenery(letter)));
		int height = s.height * tile / BattlefieldPanel.NATIVE_TILE;
		g.drawImage(s.frames[0], x, y + tile - height, tile, height, null);
	}

	private static void flecks(Graphics2D g, Random rng, int x, int y, int tile, Color[] colours, int count)
	{
		for (int i = 0; i < count; i++)
		{
			g.setColor(colours[rng.nextInt(colours.length)]);
			g.fillRect(x + rng.nextInt(tile), y + rng.nextInt(tile), 1 + rng.nextInt(2), 1);
		}
	}

	private static void pathEdges(Graphics2D g, MapDefinition map, int col, int row, int x, int y, int tile, Color edge)
	{
		g.setColor(edge);
		if (!map.isOnPath(col, row - 1))
		{
			g.fillRect(x, y, tile, 2);
		}
		if (!map.isOnPath(col, row + 1))
		{
			g.fillRect(x, y + tile - 2, tile, 2);
		}
		if (!map.isOnPath(col - 1, row))
		{
			g.fillRect(x, y, 2, tile);
		}
		if (!map.isOnPath(col + 1, row))
		{
			g.fillRect(x + tile - 2, y, 2, tile);
		}
	}
}
