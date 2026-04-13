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

	/**
	 * Cached text from the "Click here to continue" / "Please wait..." widget,
	 * tags stripped.  Empty string when the dialogue type has no continue widget
	 * or the widget hasn't been seen yet.
	 */
	private final String continueText;
}
