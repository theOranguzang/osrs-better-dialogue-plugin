/*
 * Copyright (c) 2026, theOranguzang
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.betterdialogue;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("betterdialogue")
public interface BetterDialogueConfig extends Config
{
	// -------------------------------------------------------------------------
	// Font
	// -------------------------------------------------------------------------

	@ConfigItem(
		keyName = "fontFamily",
		name = "Font",
		description = "Font used for dialogue text",
		position = 1
	)
	default FontChoice fontFamily()
	{
		return FontChoice.SANS_SERIF;
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font Size",
		description = "Size of dialogue text in pixels",
		position = 2
	)
	@Range(min = 10, max = 24)
	default int fontSize()
	{
		return 14;
	}

	@ConfigItem(
		keyName = "boldText",
		name = "Bold",
		description = "Use bold weight for dialogue text",
		position = 3
	)
	default boolean boldText()
	{
		return false;
	}

	@ConfigItem(
		keyName = "antiAlias",
		name = "Anti-aliasing",
		description = "Smooth font edges (disable for a crisper pixel look)",
		position = 4
	)
	default boolean antiAlias()
	{
		return true;
	}

	// -------------------------------------------------------------------------
	// Dialogue type toggles
	// -------------------------------------------------------------------------

	@ConfigSection(
		name = "Dialogue Types",
		description = "Toggle which dialogues get replaced",
		position = 5
	)
	String dialogueTypes = "dialogueTypes";

	@ConfigItem(
		keyName = "replaceNpc",
		name = "NPC Dialogue",
		description = "",
		section = "dialogueTypes",
		position = 6
	)
	default boolean replaceNpc()
	{
		return true;
	}

	@ConfigItem(
		keyName = "replacePlayer",
		name = "Player Dialogue",
		description = "",
		section = "dialogueTypes",
		position = 7
	)
	default boolean replacePlayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "replaceOptions",
		name = "Option Menus",
		description = "",
		section = "dialogueTypes",
		position = 8
	)
	default boolean replaceOptions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "replaceSprite",
		name = "Item/Action Dialogue",
		description = "",
		section = "dialogueTypes",
		position = 9
	)
	default boolean replaceSprite()
	{
		return true;
	}
}
