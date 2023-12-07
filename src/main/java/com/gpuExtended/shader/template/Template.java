
package com.gpuExtended.shader.template;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Template
{
	private final List<Function<String, String>> resourceLoaders = new ArrayList<>();

	public String process(String str)
	{
		StringBuilder sb = new StringBuilder();
		for (String line : str.split("\r?\n"))
		{
			if (line.startsWith("#include "))
			{
				String resource = line.substring(9);
				if (resource.startsWith("\"") && resource.endsWith("\""))
				{
					resource = resource.substring(1, resource.length() - 1);
				}

				String resourceStr = load(resource);
				sb.append(resourceStr);
			}
			else
			{
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}

	public String load(String filename)
	{
		for (Function<String, String> loader : resourceLoaders)
		{
			String value = loader.apply(filename);
			if (value != null)
			{
				return process(value);
			}
		}

		return "";
	}

	public Template add(Function<String, String> fn)
	{
		resourceLoaders.add(fn);
		return this;
	}

	public Template addInclude(Class<?> clazz)
	{
		return add(f ->
		{
			try (InputStream is = clazz.getResourceAsStream(f))
			{
				if (is != null)
				{
					return inputStreamToString(is);
				}
			}
			catch (IOException ex)
			{
				log.warn(null, ex);
			}
			return null;
		});
	}

	private static String inputStreamToString(InputStream in)
	{
		try
		{
			return CharStreams.toString(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
