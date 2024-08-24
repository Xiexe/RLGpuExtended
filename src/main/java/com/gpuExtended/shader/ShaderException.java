
package com.gpuExtended.shader;

import com.gpuExtended.shader.template.Template;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class ShaderException extends Exception
{
	private static final Pattern NVIDIA_ERROR_REGEX = Pattern.compile("^(\\d+)\\((\\d+)\\) : (.*)$", Pattern.MULTILINE);

	public ShaderException(String message)
	{
		super(message);
	}

	public static ShaderException CompileError(String error, Template template, Shader.Unit ...units)
	{
		StringBuilder sb = new StringBuilder();
		Matcher m = NVIDIA_ERROR_REGEX.matcher(error);
		if (m.find())
		{
			try
			{
				sb.append(String.format("Compile error when compiling shader%s: %s\n",
						units.length == 1 ? "" : "s",
						Arrays.stream(units)
								.map(u -> u.filename)
								.collect(Collectors.joining(", "))));

				int offset = 0;
				do {
					if (m.start() > offset)
						sb.append(error, offset, m.start());
					offset = m.end();

					int index = Integer.parseInt(m.group(1));
					int lineNumber = Integer.parseInt(m.group(2));
					String include = template.includeList.get(index);

					String errorString = m.group(3);
					sb.append(String.format(
							"%s : L%d : %s",
							include, lineNumber, errorString));
				} while (m.find());
			}
			catch (Exception ex)
			{
				log.error("Error while parsing shader compilation error: " + ex);
			}

			return new ShaderException(sb.toString());
		}

		return new ShaderException("Failed to parse compiler error for shader. Error: " + error);
	}
}
