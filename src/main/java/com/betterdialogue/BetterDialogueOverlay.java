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

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

/**
 * Paints replacement text on top of the OSRS dialogue boxes using
 * {@link Graphics2D}.
 *
 * <p>The overlay runs on {@link OverlayLayer#ABOVE_WIDGETS} so it is drawn
 * after all widget rendering, ensuring our text is always on top. It reads
 * the {@link DialogueState} snapshot set by {@link BetterDialoguePlugin} each
 * game tick and draws word-wrapped, colour-tagged replacement text using the
 * configured system font via {@link FontRenderer}.
 *
 * <p>No background fill is painted — {@code setText("")} already blanks the
 * original bitmap text, so the game's native parchment texture shows through
 * naturally underneath the replacement text.
 */
@Singleton
public class BetterDialogueOverlay extends Overlay
{
	/**
	 * Colour of the "Select an option" title in the option dialogue.
	 * Confirmed via Widget Inspector: TextColor = 0x800000 (dark red).
	 */
	private static final Color OPTION_TITLE_COLOR = new Color(0x80, 0x00, 0x00);

	/**
	 * Colour used to render the NPC/player name above the body text.
	 * Matches vanilla OSRS: Widget Inspector reports TextColor = 0x000080 (dark blue).
	 */
	private static final Color NAME_COLOR = new Color(0x00, 0x00, 0x80);

	/**
	 * Parchment background colour of the OSRS dialogue box.
	 * Used to fill the continue-widget area before painting our replacement text,
	 * overwriting any white "Please wait..." the engine rendered that frame
	 * (which cannot be prevented due to widget rendering running before overlays).
	 * Value confirmed via Widget Inspector: {@code #D6CCAF}.
	 */
	private static final Color PARCHMENT_COLOR = new Color(0xD6, 0xCC, 0xAF);

	/** Vertical padding between the widget top and the first text baseline. */
	private static final int V_PADDING = 4;

	@Inject
	private Client client;

	@Inject
	private BetterDialogueConfig config;

	@Inject
	private FontRenderer fontRenderer;

	/** Updated each game tick by the plugin. Volatile for cross-thread visibility. */
	private volatile DialogueState currentState;

	@Inject
	BetterDialogueOverlay()
	{
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.HIGH);
	}

	/** Called by the plugin once per game tick to update the displayed state. */
	public void setState(DialogueState state)
	{
		this.currentState = state;
	}

	// -------------------------------------------------------------------------
	// Overlay#render
	// -------------------------------------------------------------------------

	@Override
	public Dimension render(Graphics2D graphics)
	{
		DialogueState state = currentState;
		if (state == null)
		{
			return null;
		}

		// Belt-and-suspenders: re-blank every widget in the current state right
		// here, before we paint.  onClientTick already does this once per frame,
		// but render() fires in the same frame directly before pixels are written,
		// so any text the engine reset between the ClientTick and this render call
		// is caught here and cleared before it can appear on screen.
		reBlankWidgets(state);

		fontRenderer.applyRenderingHints(graphics);

		switch (state.getType())
		{
			case NPC_DIALOGUE:
				if (config.replaceNpc())
				{
					renderNpcDialogue(graphics, state);
				}
				break;

			case PLAYER_DIALOGUE:
				if (config.replacePlayer())
				{
					renderPlayerDialogue(graphics, state);
				}
				break;

			case OPTION_DIALOGUE:
				if (config.replaceOptions())
				{
					renderOptionDialogue(graphics, state);
				}
				break;

			case SPRITE_DIALOGUE:
				if (config.replaceSprite())
				{
					renderSpriteDialogue(graphics, state);
				}
				break;

			default:
				break;
		}

		return null; // DYNAMIC overlays return null
	}

	// -------------------------------------------------------------------------
	// Per-type renderers
	// -------------------------------------------------------------------------

	private void renderNpcDialogue(Graphics2D g, DialogueState state)
	{
		Widget textWidget = state.getTextWidget();
		if (textWidget == null || textWidget.isHidden())
		{
			return;
		}

		Rectangle bounds = textWidget.getBounds();
		if (bounds == null || bounds.width <= 0)
		{
			return;
		}

		// ---- Name ----
		Widget nameWidget = state.getNameWidget();
		if (nameWidget != null && !nameWidget.isHidden() && state.getNpcName() != null && !state.getNpcName().isEmpty())
		{
			Rectangle nameBounds = nameWidget.getBounds();
			if (nameBounds != null && nameBounds.width > 0)
			{
				fontRenderer.drawCenteredString(
					g, state.getNpcName(), nameBounds,
					centreY(g, nameBounds, fontRenderer.getFont()),
					NAME_COLOR);
			}
		}

		// ---- Body ----
		fontRenderer.drawWrappedText(g, state.getBodySegments(), bounds, bounds.y + V_PADDING);

		// ---- Continue ("Click here to continue" / "Please wait...") ----
		// Pass the live static-child widget directly — static children are always the same object.
		renderContinueText(g, state.getContinueWidget(), state.getContinueText());
	}

	private void renderPlayerDialogue(Graphics2D g, DialogueState state)
	{
		Widget textWidget = state.getTextWidget();
		if (textWidget == null || textWidget.isHidden())
		{
			return;
		}

		Rectangle bounds = textWidget.getBounds();
		if (bounds == null || bounds.width <= 0)
		{
			return;
		}

		// ---- Name ----
		Widget nameWidget = state.getNameWidget();
		if (nameWidget != null && !nameWidget.isHidden() && state.getNpcName() != null && !state.getNpcName().isEmpty())
		{
			Rectangle nameBounds = nameWidget.getBounds();
			if (nameBounds != null && nameBounds.width > 0)
			{
				fontRenderer.drawCenteredString(
					g, state.getNpcName(), nameBounds,
					centreY(g, nameBounds, fontRenderer.getFont()),
					NAME_COLOR);
			}
		}

		// ---- Body ----
		fontRenderer.drawWrappedText(g, state.getBodySegments(), bounds, bounds.y + V_PADDING);

		// ---- Continue ----
		renderContinueText(g, state.getContinueWidget(), state.getContinueText());
	}

	private void renderOptionDialogue(Graphics2D g, DialogueState state)
	{
		Widget container = state.getTextWidget();
		if (container == null || container.isHidden())
		{
			return;
		}

		Rectangle containerBounds = container.getBounds();
		if (containerBounds == null || containerBounds.width <= 0)
		{
			return;
		}

		// No fillRect — option text is camouflaged to the parchment colour so the
		// engine key handler (1–5) can still read it.  The overlay text paints on
		// top; ghost text in parchment-on-parchment is nearly invisible underneath.

		// Use the smaller option font — each row is only 16 px tall
		Font optionFont = fontRenderer.getOptionFont();

		// ---- Title ("Select an option") ----
		Widget titleWidget = state.getNameWidget();
		if (titleWidget != null && !titleWidget.isHidden())
		{
			Rectangle titleBounds = titleWidget.getBounds();
			if (titleBounds != null && titleBounds.width > 0)
			{
				fontRenderer.drawCenteredString(
					g, state.getNpcName(), titleBounds,
					centreY(g, titleBounds, optionFont),
					OPTION_TITLE_COLOR, optionFont);
			}
		}

		// ---- Option rows ----
		Widget[] optionWidgets = state.getOptionWidgets();
		List<String> options = state.getOptions();

		if (optionWidgets == null || options == null)
		{
			return;
		}

		Point mouse = client.getMouseCanvasPosition();

		for (int i = 0; i < optionWidgets.length && i < options.size(); i++)
		{
			Widget optWidget = optionWidgets[i];
			if (optWidget == null || optWidget.isHidden())
			{
				continue;
			}

			Rectangle optBounds = optWidget.getBounds();
			if (optBounds == null || optBounds.width <= 0)
			{
				continue;
			}

			boolean hovered = mouse != null
				&& optBounds.contains(mouse.getX(), mouse.getY());
			Color textColor = hovered ? Color.WHITE : Color.BLACK;

			fontRenderer.drawCenteredString(
				g, options.get(i), optBounds,
				centreY(g, optBounds, optionFont),
				textColor, optionFont);
		}
	}

	/**
	 * Computes the top-of-line y coordinate that vertically centres text
	 * (at the given font) within {@code bounds}.
	 */
	private static int centreY(Graphics2D g, Rectangle bounds, Font font)
	{
		FontMetrics fm = g.getFontMetrics(font);
		return bounds.y + (bounds.height - fm.getHeight()) / 2;
	}

	private void renderSpriteDialogue(Graphics2D g, DialogueState state)
	{
		Widget textWidget = state.getTextWidget();
		if (textWidget == null || textWidget.isHidden())
		{
			return;
		}

		Rectangle bounds = textWidget.getBounds();
		if (bounds == null || bounds.width <= 0)
		{
			return;
		}

		fontRenderer.drawWrappedText(g, state.getBodySegments(), bounds, bounds.y + V_PADDING);

		// ---- Continue ----
		// Re-fetch the live dynamic child every frame — the engine may create a new Widget
		// object when it writes "Please wait...", making the stale state reference stale.
		// Double-blank here (render-time) in addition to the tick-handler blank so the new
		// object is guaranteed empty before the engine renders it this frame.
		Widget spriteRoot = client.getWidget(net.runelite.api.widgets.InterfaceID.DIALOG_SPRITE, 0);
		Widget liveContinue = null;
		if (spriteRoot != null)
		{
			Widget[] dyn = spriteRoot.getDynamicChildren();
			if (dyn != null && dyn.length > DialogueWidgetManager.SPRITE_CONTINUE_DYN_INDEX)
			{
				liveContinue = dyn[DialogueWidgetManager.SPRITE_CONTINUE_DYN_INDEX];
			}
		}
		if (liveContinue != null)
		{
			safeBlank(liveContinue); // double-blank: closes the tick→render timing gap
		}
		renderContinueText(g, liveContinue != null ? liveContinue : state.getContinueWidget(),
			state.getContinueText());
	}

	/**
	 * Renders the continue/wait text centred over {@code widget}.
	 *
	 * <p>NPC/player continue widgets are camouflaged (colour set to parchment) by
	 * {@code reBlankWidgets()} so the engine spacebar handler can still read their text.
	 * Sprite continue widgets are blanked ({@code setText("")}); sprite dialogues do not
	 * use spacebar, so blanking is safe and produces no ghost artefacts.
	 *
	 * <p>Live widget text is preferred when available (NPC/player — text preserved by
	 * camouflage); falls back to the tick-handler cache ({@code fallbackText}) when the
	 * widget is blank (sprite) or when the cache is ahead of the live state.
	 *
	 * @param widget       live continue widget; may be {@code null}
	 * @param fallbackText cached continue text from the last tick; used when widget text is empty
	 */
	private void renderContinueText(Graphics2D g, Widget widget, String fallbackText)
	{
		if (widget == null)
		{
			return;
		}

		// Prefer fresh live text (still present for NPC/player because camouflage preserves it).
		// For sprite the text is already blank, so we fall through to the cache.
		String liveText = widget.getText();
		String text = (liveText != null && !liveText.isEmpty())
			? DialogueWidgetManager.stripTags(liveText)
			: fallbackText;

		if (text == null || text.isEmpty())
		{
			return;
		}

		Rectangle cb = widget.getBounds();
		if (cb == null || cb.width <= 0)
		{
			return;
		}

		Font font = fontRenderer.getFont();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics(font);

		int textX = cb.x + (cb.width  - fm.stringWidth(text)) / 2;
		int textY = cb.y + (cb.height - fm.getHeight())        / 2;

		g.setColor(NAME_COLOR);
		g.drawString(text, textX, textY + fm.getAscent());
	}

	// -------------------------------------------------------------------------
	// Utility
	// -------------------------------------------------------------------------

	/**
	 * Re-blanks all text widgets referenced by {@code state}.
	 *
	 * <p>The engine can reset a widget's text between the {@code onClientTick}
	 * that built the state and the {@code render()} call that paints our
	 * replacement — for example on the very first frame a dialogue opens.
	 * Calling this at the top of every render() ensures the original bitmap
	 * text is never visible, regardless of engine timing.
	 *
	 * <p>We only blank non-empty text so we don't make redundant JNI/C++ calls
	 * on every frame once the widget is already empty.
	 */
	private void reBlankWidgets(DialogueState state)
	{
		// The container widget for option dialogue has no text — safeBlank is a no-op.
		safeBlank(state.getTextWidget());

		if (state.getType() == DialogueType.OPTION_DIALOGUE)
		{
			// Option widgets are camouflaged (text preserved) so the engine
			// key handler (1–5) can still read their content.
			// Re-apply the camouflage colour each frame in case the engine resets it.
			safeCamouflage(state.getNameWidget());
			Widget[] optWidgets = state.getOptionWidgets();
			if (optWidgets != null)
			{
				for (Widget opt : optWidgets)
				{
					safeCamouflage(opt);
				}
			}
		}
		else
		{
			safeBlank(state.getNameWidget());
			// Sprite continue is blanked (setText); NPC/player continue is camouflaged so spacebar still fires.
			if (state.getType() == DialogueType.SPRITE_DIALOGUE)
			{
				safeBlank(state.getContinueWidget());
			}
			else
			{
				safeCamouflage(state.getContinueWidget());
			}
			Widget[] optWidgets = state.getOptionWidgets();
			if (optWidgets != null)
			{
				for (Widget opt : optWidgets)
				{
					safeBlank(opt);
				}
			}
		}
	}

	/** Blanks {@code widget}'s text only if it is currently non-empty. */
	private static void safeBlank(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		String t = widget.getText();
		if (t != null && !t.isEmpty())
		{
			widget.setText("");
		}
	}

	/**
	 * Re-applies the camouflage colour to an option widget every frame.
	 * Text content is intentionally preserved — only the colour is changed.
	 */
	private static void safeCamouflage(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		widget.setTextColor(DialogueWidgetManager.OPTION_CAMOUFLAGE_COLOR);
	}
}

