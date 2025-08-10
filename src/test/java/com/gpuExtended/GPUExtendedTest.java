package com.gpuExtended;

import com.gpuExtended.util.Props;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.externalplugins.ExternalPluginManager;

import java.io.InputStream;
import java.util.Properties;
import java.util.jar.Manifest;

public class GPUExtendedTest
{
	public static void main(String[] args) throws Exception
	{
		Props.DEVELOPMENT = true;
		try (InputStream manifestStream = GPUExtendedTest.class.getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF")) {
			if (manifestStream != null) {
				Manifest manifest = new Manifest(manifestStream);
				String value = manifest.getMainAttributes().getValue("Built-By-Shadow");
				boolean builtByShadow = "true".equalsIgnoreCase(value);
				System.out.println("Built-By-Shadow=" + value);
				if (!builtByShadow){
					Props.set("resource-path", "src/main/resources");
				} else {
					Props.DEVELOPMENT = false;
				}
			}
		}
		ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
		useLatestPluginHub();
		ExternalPluginManager.loadBuiltin(GpuExtendedPlugin.class);
		RuneLite.main(args);
	}

	private static void useLatestPluginHub()
	{
		if (System.getProperty("runelite.pluginhub.version") == null)
		{
			try
			{
				Properties props = new Properties();
				try (InputStream in = RuneLiteProperties.class.getResourceAsStream("runelite.properties"))
				{
					props.load(in);
				}

				String version = props.getProperty("runelite.pluginhub.version");
				String[] parts = version.split("[.-]");
				if (parts.length > 3 && parts[3].equals("SNAPSHOT"))
				{
					int patch = Integer.parseInt(parts[2]) - 1;
					version = parts[0] + "." + parts[1] + "." + patch;
					System.out.println("Detected SNAPSHOT version with no manually specified plugin-hub version. " +
							"Setting runelite.pluginhub.version to {}: " + version);

					System.setProperty("runelite.pluginhub.version", version);
				}
			}
			catch (Exception ex)
			{
				System.out.println("Failed to automatically use latest plugin-hub version: " + ex);
			}
		}
	}
}