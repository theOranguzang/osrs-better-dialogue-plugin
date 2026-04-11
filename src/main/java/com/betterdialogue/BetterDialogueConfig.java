package com.betterdialogue;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

import java.awt.Color;

@ConfigGroup("betterdialogue")
public interface BetterDialogueConfig extends Config
{
	// -------------------------------------------------------------------------
	// Font
	// -------------------------------------------------------------------------

	@ConfigItem(
		keyName = "fontName",
		name = "Font",
		description = "Font used for all replaced dialogue text",
		position = 0
	)
	default FontChoice fontName()
	{
		return FontChoice.ROBOTO;
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font Size",
		description = "Point size of the replacement font (10–28)",
		position = 1
	)
	@Range(min = 10, max = 28)
	default int fontSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "customFontPath",
		name = "Custom Font Path",
		description = "Absolute path to a .ttf file. Only used when Font is set to 'Custom'.",
		position = 2
	)
	default String customFontPath()
	{
		return "";
	}

	@ConfigItem(
		keyName = "antiAlias",
		name = "Anti-aliasing",
		description = "Enable smooth sub-pixel font rendering",
		position = 3
	)
	default boolean antiAlias()
	{
		return true;
	}

	// -------------------------------------------------------------------------
	// Colours
	// -------------------------------------------------------------------------

	@ConfigItem(
		keyName = "fontColor",
		name = "Body Text Color",
		description = "Default colour for dialogue body text (overridden by inline <col> tags)",
		position = 10
	)
	default Color fontColor()
	{
		return Color.BLACK;
	}

	@ConfigItem(
		keyName = "npcNameColor",
		name = "NPC / Player Name Color",
		description = "Colour used to render the speaker's name above the dialogue",
		position = 11
	)
	default Color npcNameColor()
	{
		return new Color(0x00008B); // dark blue
	}

	@ConfigItem(
		keyName = "continueColor",
		name = "Continue Prompt Color",
		description = "Colour for 'Click here to continue'",
		position = 12
	)
	default Color continueColor()
	{
		return new Color(0x00008B); // dark blue
	}

	@ConfigItem(
		keyName = "optionHoverColor",
		name = "Option Hover Color",
		description = "Text colour when the mouse hovers over a dialogue option",
		position = 13
	)
	default Color optionHoverColor()
	{
		return Color.WHITE; // matches vanilla OSRS highlight behaviour
	}

	// -------------------------------------------------------------------------
	// Per-type toggles
	// -------------------------------------------------------------------------

	@ConfigItem(
		keyName = "replaceNpcDialogue",
		name = "Replace NPC Dialogue",
		description = "Apply custom font to NPC speech boxes",
		position = 20
	)
	default boolean replaceNpcDialogue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "replacePlayerDialogue",
		name = "Replace Player Dialogue",
		description = "Apply custom font to player response boxes",
		position = 21
	)
	default boolean replacePlayerDialogue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "replaceOptions",
		name = "Replace Option Menus",
		description = "Apply custom font to multi-choice option menus",
		position = 22
	)
	default boolean replaceOptions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "replaceSpriteDialogue",
		name = "Replace Item/Action Dialogue",
		description = "Apply custom font to sprite / item dialogue boxes",
		position = 23
	)
	default boolean replaceSpriteDialogue()
	{
		return true;
	}
}

