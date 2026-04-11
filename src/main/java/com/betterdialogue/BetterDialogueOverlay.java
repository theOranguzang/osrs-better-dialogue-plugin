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
 * game tick and then:
 * <ol>
 *   <li>Fills the text widget's bounds with the dialogue background colour to
 *       cover any residual engine-rendered text.</li>
 *   <li>Draws word-wrapped, colour-tagged replacement text using the
 *       configured TrueType font via {@link FontRenderer}.</li>
 * </ol>
 *
 * <h3>Background colour</h3>
 * The parchment / tan colour ({@code #C9B89A}) is a close match for the default
 * OSRS dialogue box background. Players using an alternative interface style
 * may need to adjust this; a future config option could expose it.
 */
@Singleton
public class BetterDialogueOverlay extends Overlay
{
	/**
	 * Approximate OSRS dialogue-box background colour.
	 * Used to paint over the original engine-rendered text before we draw our own.
	 */
	private static final Color DIALOGUE_BG = new Color(0xC9, 0xB8, 0x9A, 255);

	/**
	 * Colour of the "Select an option" title in the option dialogue.
	 * Confirmed via Widget Inspector: TextColor = 0x800000 (dark red).
	 */
	private static final Color OPTION_TITLE_COLOR = new Color(0x80, 0x00, 0x00);

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

		fillBackground(g, bounds);
		fontRenderer.drawWrappedText(g, state.getBodySegments(), bounds, bounds.y + V_PADDING);
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

		fillBackground(g, bounds);
		fontRenderer.drawWrappedText(g, state.getBodySegments(), bounds, bounds.y + V_PADDING);
	}

	private void renderOptionDialogue(Graphics2D g, DialogueState state)
	{
		Widget container = state.getTextWidget();
		if (container == null || container.isHidden())
		{
			return;
		}

		// Fill the entire options container in one pass so there are no gaps
		// between rows where the original 16 px bitmap text can peek through.
		Rectangle containerBounds = container.getBounds();
		if (containerBounds == null || containerBounds.width <= 0)
		{
			return;
		}
		fillBackground(g, containerBounds);

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

		fillBackground(g, bounds);
		fontRenderer.drawWrappedText(g, state.getBodySegments(), bounds, bounds.y + V_PADDING);
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
		safeBlank(state.getTextWidget());
		safeBlank(state.getNameWidget());

		Widget[] optWidgets = state.getOptionWidgets();
		if (optWidgets != null)
		{
			for (Widget opt : optWidgets)
			{
				safeBlank(opt);
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

	/** Fills the text area with the dialogue background colour to cover engine text. */
	private void fillBackground(Graphics2D g, Rectangle bounds)
	{
		g.setColor(DIALOGUE_BG);
		g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
	}
}

