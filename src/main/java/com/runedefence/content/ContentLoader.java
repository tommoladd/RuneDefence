package com.runedefence.content;

import com.google.gson.Gson;
import com.runedefence.model.Content;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class ContentLoader
{
	private static final String RESOURCE = "/com/runedefence/content.json";

	private ContentLoader()
	{
	}

	/** Loads and validates the game data. Throws a descriptive exception if it is unusable. */
	public static Content load(Gson gson) throws IOException
	{
		try (InputStream in = ContentLoader.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				throw new IOException("Missing " + RESOURCE);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Content content = gson.fromJson(reader, Content.class);
				ContentValidator.validate(content);
				return content;
			}
		}
	}
}
