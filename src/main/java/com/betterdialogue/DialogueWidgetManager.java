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

	private static final int NPC_CHILD_NAME     = 4;
	private static final int NPC_CHILD_TEXT     = 6;
	private static final int NPC_CHILD_CONTINUE = 5;

	private static final int PLAYER_CHILD_NAME     = 4;
	private static final int PLAYER_CHILD_TEXT     = 6;
	private static final int PLAYER_CHILD_CONTINUE = 5;

	private static final int OPTION_CHILD_TITLE = 1;
	private static final int OPTION_CHILD_FIRST = 2;
	private static final int OPTION_COUNT       = 5;

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

	// -------------------------------------------------------------------------
	// Original-text store (for shutdown restoration)
	// -------------------------------------------------------------------------

	/** Maps each blanked widget to its original text so it can be restored on shutdown. */
	private final Map<Widget, String> savedTexts = new HashMap<>();

	// -------------------------------------------------------------------------
	// Per-type text caches
	//
	// These are populated ONLY when the engine provides non-empty widget text.
	// They are preserved on frames where the widget is already blank (because we
	// blanked it ourselves), ensuring the overlay always has something to paint.
	// -------------------------------------------------------------------------

	private List<TextSegment> cachedNpcBody    = Collections.emptyList();
	private String            cachedNpcName    = "";

	private List<TextSegment> cachedPlayerBody = Collections.emptyList();
	private String            cachedPlayerName = "";

	private List<TextSegment> cachedSpriteBody = Collections.emptyList();

	private List<String> cachedOptionTexts   = Collections.emptyList();
	private Widget[]     cachedOptionWidgets = new Widget[0];
	private String       cachedOptionTitle   = "";

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
		if (isVisible(npcRoot))
		{
			return buildNpcState();
		}

		Widget playerRoot = client.getWidget(InterfaceID.DIALOG_PLAYER, 0);
		if (isVisible(playerRoot))
		{
			return buildPlayerState();
		}

		Widget optionRoot = client.getWidget(InterfaceID.DIALOG_OPTION, 0);
		if (isVisible(optionRoot))
		{
			return buildOptionState();
		}

		Widget spriteRoot = client.getWidget(InterfaceID.DIALOG_SPRITE, 0);
		if (isVisible(spriteRoot))
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

		// ---- Blank: always, every frame ----
		blankWidget(textWidget);

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
			null
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

		blankWidget(textWidget);

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
			null
		);
	}

	private DialogueState buildOptionState()
	{
		Widget titleWidget = client.getWidget(InterfaceID.DIALOG_OPTION, OPTION_CHILD_TITLE);

		// ---- Capture + blank the title independently ----
		// The title ("Select an option") is a separate widget from the choices.
		// It needs its own capture-then-blank cycle so it never flashes in Quill font.
		if (titleWidget != null)
		{
			String titleRaw = titleWidget.getText();
			if (titleRaw != null && !titleRaw.isEmpty())
			{
				cachedOptionTitle = stripTags(titleRaw);
			}
			blankWidget(titleWidget);
		}

		// ---- Capture + blank each option child ----
		// Always collect live widget refs (needed for bounds in the overlay).
		// Only refresh the text cache when option widgets have non-empty text.
		List<String> freshTexts  = new ArrayList<>();
		List<Widget> liveWidgets = new ArrayList<>();

		for (int i = 0; i < OPTION_COUNT; i++)
		{
			Widget opt = client.getWidget(InterfaceID.DIALOG_OPTION, OPTION_CHILD_FIRST + i);
			if (opt == null || opt.isHidden())
			{
				continue;
			}

			liveWidgets.add(opt);

			String optText = opt.getText();
			if (optText != null && !optText.isEmpty())
			{
				freshTexts.add(stripTags(optText));
			}

			blankWidget(opt);
		}

		if (!freshTexts.isEmpty())
		{
			// New real text arrived — update both text and widget caches atomically
			cachedOptionTexts   = freshTexts;
			cachedOptionWidgets = liveWidgets.toArray(new Widget[0]);
		}
		else if (!liveWidgets.isEmpty())
		{
			// Widgets visible but already blank (we blanked them last frame).
			// Update widget refs only so the overlay has current bounds.
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
			titleWidget,
			null,
			null,
			cachedOptionWidgets
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

		blankWidget(textWidget);

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
			null
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
				cachedNpcBody = Collections.emptyList();
				cachedNpcName = "";
				break;
			case PLAYER_DIALOGUE:
				cachedPlayerBody = Collections.emptyList();
				cachedPlayerName = "";
				break;
			case OPTION_DIALOGUE:
				cachedOptionTexts   = Collections.emptyList();
				cachedOptionWidgets = new Widget[0];
				cachedOptionTitle   = "";
				break;
			case SPRITE_DIALOGUE:
				cachedSpriteBody = Collections.emptyList();
				break;
			default:
				break;
		}
	}

	// -------------------------------------------------------------------------
	// Widget blanking
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

		String text = raw
			.replace("<br>", "\n")
			.replace("<lt>", "<")
			.replace("<gt>", ">");

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
