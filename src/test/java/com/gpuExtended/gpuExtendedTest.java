package com.gpuExtended;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GPUExtendedTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GpuExtendedPlugin.class);
		RuneLite.main(args);
	}
}