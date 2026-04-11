package com.betterdialogue;

/**
 * Enumerates the types of dialogue boxes present in OSRS.
 */
public enum DialogueType
{
	/** An NPC speaking to the player. */
	NPC_DIALOGUE,
	/** The player speaking (response shown in chatbox). */
	PLAYER_DIALOGUE,
	/** Multi-choice option menu (1–5 options). */
	OPTION_DIALOGUE,
	/** Item / action text ("You light the logs"). */
	SPRITE_DIALOGUE,
	/** Level-up congratulations box. */
	LEVEL_UP,
	/** Quest completion scroll. */
	QUEST_COMPLETE
}

