package com.betterdialogue;

import lombok.Data;
import net.runelite.api.widgets.Widget;

import java.util.List;

/**
 * Immutable snapshot of the currently active dialogue produced by
 * {@link DialogueWidgetManager} once per game tick.
 *
 * <p>Fields may be {@code null} when not applicable to the dialogue type
 * (e.g. {@code options} is only populated for {@link DialogueType#OPTION_DIALOGUE}).
 */
@Data
public class DialogueState
{
	/** Which kind of dialogue is currently visible. */
	private final DialogueType type;

	/**
	 * NPC name (for {@link DialogueType#NPC_DIALOGUE}) or player name
	 * (for {@link DialogueType#PLAYER_DIALOGUE}), with markup stripped.
	 * Also used as the title string for option menus.
	 */
	private final String npcName;

	/**
	 * Parsed body text as colour-annotated segments.
	 * {@code null} for option dialogue (use {@link #options} instead).
	 */
	private final List<TextSegment> bodySegments;

	/**
	 * Cleaned option strings for {@link DialogueType#OPTION_DIALOGUE}.
	 * {@code null} for all other types.
	 */
	private final List<String> options;

	// ---------- live widget references (used to get on-screen bounds) ----------

	/** The body-text widget whose text has been blanked. */
	private final Widget textWidget;

	/** Name/title widget (may be {@code null}). */
	private final Widget nameWidget;

	/** "Click here to continue" widget (may be {@code null}). */
	private final Widget continueWidget;

	/**
	 * Individual option widgets for {@link DialogueType#OPTION_DIALOGUE}.
	 * {@code null} for all other types.
	 */
	private final Widget[] optionWidgets;
}

