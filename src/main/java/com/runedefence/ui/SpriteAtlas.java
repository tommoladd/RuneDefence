package com.runedefence.ui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.util.ImageUtil;

/** Every sprite lives on one of a few small sheets, decoded once at startup and shared. */
public final class SpriteAtlas
{
	public static final class Sprite
	{
		public final BufferedImage[] frames;
		public final int width;
		public final int height;

		Sprite(BufferedImage[] frames)
		{
			this.frames = frames;
			this.width = frames[0].getWidth();
			this.height = frames[0].getHeight();
		}
	}

	private final Map<String, Sprite> sprites = new HashMap<>();

	public SpriteAtlas(Gson gson) throws IOException
	{
		try (InputStream in = SpriteAtlas.class.getResourceAsStream("/com/runedefence/sprites.json"))
		{
			if (in == null)
			{
				throw new IOException("Missing sprite index");
			}
			JsonObject index = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			for (Map.Entry<String, JsonElement> sheet : index.entrySet())
			{
				loadSheet(sheet.getKey(), sheet.getValue().getAsJsonObject());
			}
		}
	}

	private void loadSheet(String name, JsonObject sheetIndex)
	{
		BufferedImage source = ImageUtil.loadImageResource(SpriteAtlas.class, "/com/runedefence/sprites/" + name + ".png");
		// The file is indexed to keep it small, but ARGB is what draws quickly.
		BufferedImage sheet = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sheet.createGraphics();
		g.drawImage(source, 0, 0, null);
		g.dispose();

		for (Map.Entry<String, JsonElement> entry : sheetIndex.entrySet())
		{
			JsonArray a = entry.getValue().getAsJsonArray();
			int x = a.get(0).getAsInt();
			int y = a.get(1).getAsInt();
			int w = a.get(2).getAsInt();
			int h = a.get(3).getAsInt();
			BufferedImage[] frames = new BufferedImage[a.get(4).getAsInt()];
			for (int f = 0; f < frames.length; f++)
			{
				frames[f] = sheet.getSubimage(x + f * w, y, w, h);
			}
			sprites.put(entry.getKey(), new Sprite(frames));
		}
	}

	public Sprite get(String name)
	{
		return sprites.get(name);
	}

	public BufferedImage frame(String name, int index)
	{
		Sprite s = sprites.get(name);
		return s.frames[Math.floorMod(index, s.frames.length)];
	}
}
