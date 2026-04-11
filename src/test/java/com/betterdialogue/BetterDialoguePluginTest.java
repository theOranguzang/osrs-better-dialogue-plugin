package com.betterdialogue;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Test harness that launches RuneLite with Better Dialogue loaded as an
 * external plugin.  Run via Gradle: {@code ./gradlew run}
 */
public class BetterDialoguePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BetterDialoguePlugin.class);
		RuneLite.main(args);
	}
}

