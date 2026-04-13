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

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detects active dialogue widgets each client tick, extracts their text into a
 * {@link DialogueState} snapshot, and hides the original widget text so the
 * {@link BetterDialogueOverlay} can paint its own replacement text on top.
 *
 * <h3>Capture-then-blank contract</h3>
 * Widget text is only parsed into the per-type cache when {@code getText()}
 * returns a non-empty string (i.e. on the first frame the engine provides new
 * dialogue text).  On every subsequent frame the widget is already blank
 * (because we called {@code setText("")} the previous frame), so we leave the
 * cache untouched and the overlay continues to render the last good snapshot.
 * This prevents the "blank frame" bug where a freshly-blanked widget produces
 * an empty segment list and the overlay paints nothing.
 *
 * <h3>Cache lifetime</h3>
 * When the active dialogue type changes (or no dialogue is open), the cache for
 * the previously active type is cleared.  This prevents a stale cache from
 * one NPC flashing briefly when a new NPC's dialogue opens.
 *
 * <h3>Widget child indices</h3>
 * The indices below are approximate.  <strong>Verify with the in-game Widget
 * Inspector</strong> — they can change after game updates.
 *
 * <table>
 *   <tr><th>Type</th><th>InterfaceID</th><th>Name</th><th>Text</th><th>Continue</th></tr>
 *   <tr><td>NPC</td><td>DIALOG_NPC (231)</td><td>4</td><td>6</td><td>5</td></tr>
 *   <tr><td>Player</td><td>DIALOG_PLAYER (217)</td><td>4</td><td>6</td><td>5</td></tr>
 *   <tr><td>Options</td><td>DIALOG_OPTION (219)</td><td>1 (title)</td><td>2–6</td><td>—</td></tr>
 *   <tr><td>Sprite</td><td>DIALOG_SPRITE (193)</td><td>—</td><td>2</td><td>3</td></tr>
 * </table>
 */
@Slf4j
@Singleton
public class DialogueWidgetManager
{
	// -------------------------------------------------------------------------
	// Widget child indices (verify with Widget Inspector)
	// -------------------------------------------------------------------------

	/**
	 * Text colour used to camouflage option-menu widget text against the
	 * parchment background.  The engine key handler (1–5 shortcuts) reads
	 * widget text content, not colour, so keeping the text intact while making
	 * it invisible preserves keyboard selection.
	 * Value matches the vanilla OSRS dialogue parchment: {@code #D6CCAF}.
	 */
	static final int OPTION_CAMOUFLAGE_COLOR = 0xD6CCAF;

	private static final int NPC_CHILD_NAME     = 4;
	private static final int NPC_CHILD_TEXT     = 6;
	private static final int NPC_CHILD_CONTINUE = 5;

	private static final int PLAYER_CHILD_NAME     = 4;
	private static final int PLAYER_CHILD_TEXT     = 6;
	private static final int PLAYER_CHILD_CONTINUE = 5;

	// Option dialogue (InterfaceID.DIALOG_OPTION)
	// Container: static child 1.  Title and options are dynamic children of that container.
	//   Dynamic[0] = title "Select an option" (HasListener=false, color=0x800000)
	//   Dynamic[1..n] = clickable option rows  (HasListener=true)
	// NOTE: there are NO useful static child constants here — access is via getDynamicChildren().

	private static final int SPRITE_CHILD_TEXT     = 2;
	private static final int SPRITE_CHILD_CONTINUE = 3;

	// -------------------------------------------------------------------------
	// Tag parsing
	// -------------------------------------------------------------------------

	private static final Pattern TAG_STRIP = Pattern.compile("<[^>]*>");

	// -------------------------------------------------------------------------
	// Injected dependencies
	// -------------------------------------------------------------------------

	@Inject
	private Client client;

	@Inject
	private BetterDialogueConfig config;

	// -------------------------------------------------------------------------
	// Original-text store (for shutdown restoration)
	// -------------------------------------------------------------------------

	/** Maps each blanked widget to its original text so it can be restored on shutdown. */
	private final Map<Widget, String> savedTexts = new HashMap<>();

	/**
	 * Maps each camouflaged option widget to its original text colour so it
	 * can be restored on shutdown.  Option widgets are camouflaged rather than
	 * blanked so the engine key handler (1–5) can still read their text.
	 */
	private final Map<Widget, Integer> savedColors = new HashMap<>();

	// -------------------------------------------------------------------------
	// Per-type text caches
	//
	// These are populated ONLY when the engine provides non-empty widget text.
	// They are preserved on frames where the widget is already blank (because we
	// blanked it ourselves), ensuring the overlay always has something to paint.
	// -------------------------------------------------------------------------

	private List<TextSegment> cachedNpcBody     = Collections.emptyList();
	private String            cachedNpcName     = "";
	private String            cachedNpcContinue = "";

	private List<TextSegment> cachedPlayerBody     = Collections.emptyList();
	private String            cachedPlayerName     = "";
	private String            cachedPlayerContinue = "";

	private List<TextSegment> cachedSpriteBody     = Collections.emptyList();
	private String            cachedSpriteContinue = "";

	private List<String> cachedOptionTexts   = Collections.emptyList();
	private Widget[]     cachedOptionWidgets = new Widget[0];
	private String       cachedOptionTitle   = "";
	/**
	 * Stable reference to the title dynamic child widget (dynamic index 0 of the
	 * options container).  Preserved across frames so {@link BetterDialogueOverlay}
	 * can re-camouflage it in {@code reBlankWidgets()} even on frames where the
	 * text colour has already been set.
	 */
	private Widget       cachedOptionTitleWidget = null;

	/** Which dialogue type was active on the previous call to {@link #getCurrentDialogue()}. */
	private DialogueType lastSeenType = null;


	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Returns a {@link DialogueState} for whichever dialogue is currently
	 * visible, or {@code null} if no supported dialogue is open.
	 * Call once per client tick from the plugin's event handler.
	 */
	public DialogueState getCurrentDialogue()
	{
		DialogueState state = detectAndBuild();

		DialogueType currentType = state != null ? state.getType() : null;

		// When the active type changes (or dialogue closes), clear the stale cache
		// for the type that just went away so its text can't bleed into the next dialogue.
		if (currentType != lastSeenType)
		{
			clearCacheFor(lastSeenType);
			lastSeenType = currentType;
		}

		return state;
	}

	/**
	 * Restores every widget whose text was blanked and clears all caches.
	 * Must be called from the plugin's {@code shutDown()} so nothing is left
	 * in a broken state after the plugin is disabled.
	 */
	public void restoreAll()
	{
		for (Map.Entry<Widget, String> entry : savedTexts.entrySet())
		{
			try
			{
				entry.getKey().setText(entry.getValue());
			}
			catch (Exception e)
			{
				log.warn("Failed to restore widget text", e);
			}
		}
		savedTexts.clear();

		for (Map.Entry<Widget, Integer> entry : savedColors.entrySet())
		{
			try
			{
				entry.getKey().setTextColor(entry.getValue());
			}
			catch (Exception e)
			{
				log.warn("Failed to restore widget text color", e);
			}
		}
		savedColors.clear();

		clearCacheFor(DialogueType.NPC_DIALOGUE);
		clearCacheFor(DialogueType.PLAYER_DIALOGUE);
		clearCacheFor(DialogueType.OPTION_DIALOGUE);
		clearCacheFor(DialogueType.SPRITE_DIALOGUE);
		lastSeenType = null;
	}

	// -------------------------------------------------------------------------
	// Detection + state builders
	// -------------------------------------------------------------------------

	private DialogueState detectAndBuild()
	{
		Widget npcRoot = client.getWidget(InterfaceID.DIALOG_NPC, 0);
		if (isVisible(npcRoot) && config.replaceNpc())
		{
			return buildNpcState();
		}

		Widget playerRoot = client.getWidget(InterfaceID.DIALOG_PLAYER, 0);
		if (isVisible(playerRoot) && config.replacePlayer())
		{
			return buildPlayerState();
		}

		// Options container lives at DIALOG_OPTION static child 1 (confirmed via Widget Inspector).
		// Its dynamic children hold the title [0] and clickable options [1..n].
		Widget optionContainer = client.getWidget(InterfaceID.DIALOG_OPTION, 1);
		if (isVisible(optionContainer) && config.replaceOptions())
		{
			return buildOptionState();
		}

		Widget spriteRoot = client.getWidget(InterfaceID.DIALOG_SPRITE, 0);
		if (isVisible(spriteRoot) && config.replaceSprite())
		{
			return buildSpriteState();
		}

		return null;
	}

	private DialogueState buildNpcState()
	{
		Widget nameWidget     = client.getWidget(InterfaceID.DIALOG_NPC, NPC_CHILD_NAME);
		Widget textWidget     = client.getWidget(InterfaceID.DIALOG_NPC, NPC_CHILD_TEXT);
		Widget continueWidget = client.getWidget(InterfaceID.DIALOG_NPC, NPC_CHILD_CONTINUE);

		if (textWidget == null)
		{
			return null;
		}

		// ---- Capture: only update cache when the engine has real text ----
		String raw = textWidget.getText();
		if (raw != null && !raw.isEmpty())
		{
			cachedNpcBody = parseSegments(raw, Color.BLACK);
			cachedNpcName = nameWidget != null ? stripTags(nameWidget.getText()) : "";
		}

		// Capture continue text (e.g. "Click here to continue" / "Please wait...")
		if (continueWidget != null)
		{
			String rawContinue = continueWidget.getText();
			if (rawContinue != null && !rawContinue.isEmpty())
			{
				cachedNpcContinue = stripTags(rawContinue);
			}
		}

		// ---- Blank name + body; camouflage continue so spacebar still works ----
		blankWidget(textWidget);
		blankWidget(nameWidget);
		camouflageWidget(continueWidget); // setTextColor, not setText("") — engine needs text for spacebar

		// If we've never seen any text yet, nothing to render
		if (cachedNpcBody.isEmpty())
		{
			return null;
		}

		// ---- Build state from cache + live widget refs ----
		return new DialogueState(
			DialogueType.NPC_DIALOGUE,
			cachedNpcName,
			cachedNpcBody,
			null,
			textWidget,
			nameWidget,
			continueWidget,
			null,
			cachedNpcContinue
		);
	}

	private DialogueState buildPlayerState()
	{
		Widget nameWidget     = client.getWidget(InterfaceID.DIALOG_PLAYER, PLAYER_CHILD_NAME);
		Widget textWidget     = client.getWidget(InterfaceID.DIALOG_PLAYER, PLAYER_CHILD_TEXT);
		Widget continueWidget = client.getWidget(InterfaceID.DIALOG_PLAYER, PLAYER_CHILD_CONTINUE);

		if (textWidget == null)
		{
			return null;
		}

		String raw = textWidget.getText();
		if (raw != null && !raw.isEmpty())
		{
			cachedPlayerBody = parseSegments(raw, Color.BLACK);
			cachedPlayerName = nameWidget != null ? stripTags(nameWidget.getText()) : "";
		}

		// Capture continue text
		if (continueWidget != null)
		{
			String rawContinue = continueWidget.getText();
			if (rawContinue != null && !rawContinue.isEmpty())
			{
				cachedPlayerContinue = stripTags(rawContinue);
			}
		}

		blankWidget(textWidget);
		blankWidget(nameWidget);
		camouflageWidget(continueWidget); // setTextColor, not setText("") — engine needs text for spacebar

		if (cachedPlayerBody.isEmpty())
		{
			return null;
		}

		return new DialogueState(
			DialogueType.PLAYER_DIALOGUE,
			cachedPlayerName,
			cachedPlayerBody,
			null,
			textWidget,
			nameWidget,
			continueWidget,
			null,
			cachedPlayerContinue
		);
	}

	private DialogueState buildOptionState()
	{
		// The options container is DIALOG_OPTION static child 1.
		// All text lives in its dynamic children:
		//   [0]     = title "Select an option"  (HasListener=false, color=0x800000)
		//   [1..n]  = clickable options          (HasListener=true)
		// Unused option slots are hidden or have empty text — always skip them.
		Widget container = client.getWidget(InterfaceID.DIALOG_OPTION, 1);
		if (container == null)
		{
			return null;
		}

		Widget[] dynChildren = container.getDynamicChildren();
		if (dynChildren == null || dynChildren.length == 0)
		{
			return null;
		}

		// ---- Capture + camouflage the title (dynamic child 0) ----
		Widget titleWidget = dynChildren[0];
		cachedOptionTitleWidget = titleWidget; // preserve ref for reBlankWidgets()
		String titleRaw = titleWidget.getText();
		if (titleRaw != null && !titleRaw.isEmpty())
		{
			cachedOptionTitle = stripTags(titleRaw);
		}
		camouflageWidget(titleWidget);

		// ---- Capture + camouflage each option (dynamic children 1..n) ----
		List<String> freshTexts  = new ArrayList<>();
		List<Widget> liveWidgets = new ArrayList<>();

		for (int i = 1; i < dynChildren.length; i++)
		{
			Widget opt = dynChildren[i];
			if (opt == null || opt.isHidden())
			{
				continue;
			}

			// Always collect as a live widget so the overlay has current bounds
			liveWidgets.add(opt);

			String optText = opt.getText();
			if (optText != null && !optText.isEmpty())
			{
				freshTexts.add(stripTags(optText));
			}

			camouflageWidget(opt);
		}

		if (!freshTexts.isEmpty())
		{
			// New real text arrived — update text and widget caches atomically
			cachedOptionTexts   = freshTexts;
			cachedOptionWidgets = liveWidgets.toArray(new Widget[0]);
		}
		else if (!liveWidgets.isEmpty())
		{
			// Options already blank (we blanked them last frame) — update bounds refs only
			cachedOptionWidgets = liveWidgets.toArray(new Widget[0]);
		}

		if (cachedOptionTexts.isEmpty())
		{
			return null;
		}

		return new DialogueState(
			DialogueType.OPTION_DIALOGUE,
			cachedOptionTitle,
			null,
			cachedOptionTexts,
			container,               // textWidget — used for bounds & visibility checks
			cachedOptionTitleWidget, // nameWidget — re-blanked by reBlankWidgets()
			null,
			cachedOptionWidgets,     // option dynamic children
			""                       // no continue widget for option dialogue
		);
	}

	private DialogueState buildSpriteState()
	{
		Widget textWidget     = client.getWidget(InterfaceID.DIALOG_SPRITE, SPRITE_CHILD_TEXT);
		Widget continueWidget = client.getWidget(InterfaceID.DIALOG_SPRITE, SPRITE_CHILD_CONTINUE);

		if (textWidget == null)
		{
			return null;
		}

		String raw = textWidget.getText();
		if (raw != null && !raw.isEmpty())
		{
			cachedSpriteBody = parseSegments(raw, Color.BLACK);
		}

		// Capture continue text
		if (continueWidget != null)
		{
			String rawContinue = continueWidget.getText();
			if (rawContinue != null && !rawContinue.isEmpty())
			{
				cachedSpriteContinue = stripTags(rawContinue);
			}
		}

		blankWidget(textWidget);
		camouflageWidget(continueWidget); // setTextColor, not setText("") — engine needs text for spacebar

		if (cachedSpriteBody.isEmpty())
		{
			return null;
		}

		return new DialogueState(
			DialogueType.SPRITE_DIALOGUE,
			null,
			cachedSpriteBody,
			null,
			textWidget,
			null,
			continueWidget,
			null,
			cachedSpriteContinue
		);
	}

	// -------------------------------------------------------------------------
	// Cache management
	// -------------------------------------------------------------------------

	private void clearCacheFor(DialogueType type)
	{
		if (type == null)
		{
			return;
		}
		switch (type)
		{
			case NPC_DIALOGUE:
				cachedNpcBody     = Collections.emptyList();
				cachedNpcName     = "";
				cachedNpcContinue = "";
				break;
			case PLAYER_DIALOGUE:
				cachedPlayerBody     = Collections.emptyList();
				cachedPlayerName     = "";
				cachedPlayerContinue = "";
				break;
			case OPTION_DIALOGUE:
				cachedOptionTexts       = Collections.emptyList();
				cachedOptionWidgets     = new Widget[0];
				cachedOptionTitle       = "";
				cachedOptionTitleWidget = null;
				break;
			case SPRITE_DIALOGUE:
				cachedSpriteBody     = Collections.emptyList();
				cachedSpriteContinue = "";
				break;
			default:
				break;
		}
	}

	// -------------------------------------------------------------------------
	// Widget blanking / camouflage
	// -------------------------------------------------------------------------


	/**
	 * Saves a widget's original text (for shutdown restoration) then blanks it.
	 * The original is saved only on the first call per widget so we never
	 * overwrite a real value with our own empty string.
	 */
	private void blankWidget(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		String current = widget.getText();
		if (current == null)
		{
			return;
		}
		if (!current.isEmpty() && !savedTexts.containsKey(widget))
		{
			savedTexts.put(widget, current);
		}
		widget.setText("");
	}

	/**
	 * Camouflages an option widget by setting its text colour to the parchment
	 * background colour ({@link #OPTION_CAMOUFLAGE_COLOR}) rather than blanking
	 * its text.  This keeps the text content intact so the engine's 1–5 key
	 * handler can still read it, while making it visually invisible.
	 * The original colour is saved on the first call so it can be restored on
	 * shutdown.
	 */
	private void camouflageWidget(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		if (!savedColors.containsKey(widget))
		{
			savedColors.put(widget, widget.getTextColor());
		}
		widget.setTextColor(OPTION_CAMOUFLAGE_COLOR);
	}

	// -------------------------------------------------------------------------
	// Shared helpers
	// -------------------------------------------------------------------------

	private static boolean isVisible(Widget w)
	{
		return w != null && !w.isHidden();
	}

	private static String stripTags(String text)
	{
		if (text == null)
		{
			return "";
		}
		return TAG_STRIP.matcher(text)
			.replaceAll("")
			.replace("<br>", " ")
			.trim();
	}

	/**
	 * Parses a raw widget text string (which may contain {@code <col=RRGGBB>}
	 * and {@code <br>} tags) into a list of colour-annotated {@link TextSegment}s.
	 *
	 * @param raw          raw text from {@link Widget#getText()}
	 * @param defaultColor colour to use when no {@code <col>} tag is active
	 * @return ordered list of styled text segments (never {@code null})
	 */
	public List<TextSegment> parseSegments(String raw, Color defaultColor)
	{
		List<TextSegment> segments = new ArrayList<>();
		if (raw == null || raw.isEmpty())
		{
			return segments;
		}

		// Replace <br> with a SPACE, not \n.
		// The game bakes <br> tags into widget text at Quill 8 character-width boundaries.
		// Those break-points are meaningless for any other font.  Collapsing them to spaces
		// lets FontRenderer.drawWrappedText() re-wrap the text from scratch using the
		// configured TrueType font's own FontMetrics — producing correct line breaks.
		String text = raw
			.replace("<br>", " ")
			.replace("<lt>", "<")
			.replace("<gt>", ">")
			.replaceAll("\\s{2,}", " ") // collapse double-spaces from adjacent <br>s
			.trim();

		Color currentColor = defaultColor;
		int pos = 0;
		int len = text.length();

		while (pos < len)
		{
			int tagStart = text.indexOf('<', pos);
			if (tagStart == -1)
			{
				appendSegment(segments, text.substring(pos), currentColor);
				break;
			}

			if (tagStart > pos)
			{
				appendSegment(segments, text.substring(pos, tagStart), currentColor);
			}

			int tagEnd = text.indexOf('>', tagStart);
			if (tagEnd == -1)
			{
				appendSegment(segments, text.substring(tagStart), currentColor);
				break;
			}

			String tagContent = text.substring(tagStart + 1, tagEnd);
			if (tagContent.startsWith("col="))
			{
				try
				{
					currentColor = new Color(Integer.parseInt(tagContent.substring(4), 16));
				}
				catch (NumberFormatException ignored)
				{
				}
			}
			else if (tagContent.equals("/col"))
			{
				currentColor = defaultColor;
			}
			// All other tags (<shad>, <str>, <u>, etc.) are silently ignored

			pos = tagEnd + 1;
		}

		return segments;
	}

	private static void appendSegment(List<TextSegment> list, String text, Color color)
	{
		if (!text.isEmpty())
		{
			list.add(new TextSegment(text, color));
		}
	}
}
