package com.betterdialogue;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads and caches TrueType fonts, and provides word-wrap / centred-text
 * drawing utilities used by {@link BetterDialogueOverlay}.
 *
 * <h3>Font resolution order</h3>
 * <ol>
 *   <li>Custom file path (when {@link FontChoice#CUSTOM} is selected)</li>
 *   <li>Bundled TTF resource from {@code src/main/resources/fonts/}</li>
 *   <li>JVM fallback: {@link Font#SANS_SERIF}</li>
 * </ol>
 */
@Slf4j
@Singleton
public class FontRenderer
{
	@Inject
	private BetterDialogueConfig config;

	// Cached derived font — invalidated whenever config changes
	private Font cachedFont = null;
	private FontChoice cachedChoice = null;
	private int cachedSize = -1;
	private String cachedPath = null;

	// Separate cache for the option-dialogue font (smaller size)
	private Font cachedOptionFont = null;
	private int  cachedOptionSize = -1;

	// -------------------------------------------------------------------------
	// Font loading
	// -------------------------------------------------------------------------

	/** Returns the currently configured {@link Font}, creating it if necessary. */
	public Font getFont()
	{
		FontChoice choice = config.fontName();
		int size = config.fontSize();
		String customPath = config.customFontPath();

		if (cachedFont != null
			&& choice == cachedChoice
			&& size == cachedSize
			&& Objects.equals(customPath, cachedPath))
		{
			return cachedFont;
		}

		cachedChoice = choice;
		cachedSize = size;
		cachedPath = customPath;

		Font font = null;

		if (choice == FontChoice.CUSTOM && customPath != null && !customPath.isEmpty())
		{
			font = loadFontFromFile(customPath, size);
		}

		if (font == null && choice != FontChoice.CUSTOM && choice.getResourcePath() != null)
		{
			font = loadFontFromResource(choice.getResourcePath(), size);
		}

		if (font == null)
		{
			log.debug("Falling back to JVM SANS_SERIF font at size {}", size);
			font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
		}

		cachedFont = font;
		return cachedFont;
	}

	private Font loadFontFromResource(String resourcePath, int size)
	{
		try (InputStream is = FontRenderer.class.getResourceAsStream("/" + resourcePath))
		{
			if (is == null)
			{
				log.debug("Bundled font resource not found: {}", resourcePath);
				return null;
			}
			Font base = Font.createFont(Font.TRUETYPE_FONT, is);
			return base.deriveFont(Font.PLAIN, (float) size);
		}
		catch (Exception e)
		{
			log.warn("Failed to load bundled font '{}': {}", resourcePath, e.getMessage());
			return null;
		}
	}

	private Font loadFontFromFile(String path, int size)
	{
		try
		{
			Font base = Font.createFont(Font.TRUETYPE_FONT, new File(path));
			return base.deriveFont(Font.PLAIN, (float) size);
		}
		catch (Exception e)
		{
			log.warn("Failed to load custom font '{}': {}", path, e.getMessage());
			return null;
		}
	}

	// -------------------------------------------------------------------------
	// Rendering helpers
	// -------------------------------------------------------------------------

	/**
	 * Applies (or removes) anti-aliasing rendering hints based on the current
	 * config. Call this once per {@link BetterDialogueOverlay#render} frame
	 * before drawing any text.
	 */
	public void applyRenderingHints(Graphics2D g)
	{
		if (config.antiAlias())
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			g.setRenderingHint(RenderingHints.KEY_RENDERING,
				RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
				RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		}
		else
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		}
	}

	/**
	 * Returns a font at {@link BetterDialogueConfig#optionFontSize()} derived
	 * from the same TTF as {@link #getFont()}.  Option rows are only 16 px tall
	 * so this is intentionally smaller than the main dialogue font.
	 */
	public Font getOptionFont()
	{
		int size = config.optionFontSize();
		// Invalidate if the base font changed (different TTF) or size changed
		if (cachedOptionFont != null
			&& size == cachedOptionSize
			&& config.fontName() == cachedChoice
			&& Objects.equals(config.customFontPath(), cachedPath))
		{
			return cachedOptionFont;
		}
		// Derive from the base font (loads / caches the TTF if not yet done)
		cachedOptionFont = getFont().deriveFont(Font.PLAIN, (float) size);
		cachedOptionSize = size;
		return cachedOptionFont;
	}

	/**
	 * Draws a single-line string centred horizontally within {@code bounds}
	 * using an explicitly supplied {@code font} rather than the config default.
	 *
	 * @return the y coordinate of the bottom of the drawn line
	 */
	public int drawCenteredString(Graphics2D g, String text, Rectangle bounds, int y, Color color, Font font)
	{
		if (text == null || text.isEmpty())
		{
			return y;
		}
		g.setFont(font);
		g.setColor(color);
		FontMetrics fm = g.getFontMetrics(font);
		int x = bounds.x + (bounds.width - fm.stringWidth(text)) / 2;
		g.drawString(text, x, y + fm.getAscent());
		return y + fm.getHeight();
	}

	/**
	 * Draws a single-line string centred horizontally within {@code bounds},
	 * starting at the given {@code y} coordinate (top of the line, not baseline).
	 *
	 * @return the y coordinate of the bottom of the drawn line
	 */
	public int drawCenteredString(Graphics2D g, String text, Rectangle bounds, int y, Color color)
	{
		if (text == null || text.isEmpty())
		{
			return y;
		}
		Font font = getFont();
		g.setFont(font);
		g.setColor(color);
		FontMetrics fm = g.getFontMetrics(font);
		int x = bounds.x + (bounds.width - fm.stringWidth(text)) / 2;
		g.drawString(text, x, y + fm.getAscent());
		return y + fm.getHeight();
	}

	/**
	 * Word-wraps {@code segments} to fit within {@code bounds.width} and draws
	 * each line centred horizontally.
	 *
	 * @param g        graphics context
	 * @param segments styled text segments (may contain {@code '\n'} for forced breaks)
	 * @param bounds   the text area rectangle (used for width constraint and x origin)
	 * @param startY   top y coordinate of the first line
	 * @return y coordinate immediately below the last drawn line
	 */
	public int drawWrappedText(Graphics2D g,
	                           List<TextSegment> segments,
	                           Rectangle bounds,
	                           int startY)
	{
		if (segments == null || segments.isEmpty())
		{
			return startY;
		}

		Font font = getFont();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics(font);

		int maxWidth = bounds.width - 8; // 4 px horizontal padding each side
		int lineHeight = fm.getHeight();
		int y = startY;

		// 1. Tokenise all segments into word tokens (preserving newline positions)
		List<WordToken> tokens = tokenise(segments);

		// 2. Layout tokens into display lines
		List<List<WordToken>> lines = layoutLines(tokens, fm, maxWidth);

		// 3. Draw each line centred inside bounds
		for (List<WordToken> line : lines)
		{
			if (y + lineHeight > bounds.y + bounds.height)
			{
				break; // no more vertical space
			}

			int lineWidth = measureLine(line, fm);
			int x = bounds.x + (bounds.width - lineWidth) / 2;

			boolean firstToken = true;
			for (WordToken tok : line)
			{
				if (!firstToken)
				{
					// draw the space in the previous token's colour
					g.setColor(tok.color);
					g.drawString(" ", x, y + fm.getAscent());
					x += fm.stringWidth(" ");
				}
				g.setColor(tok.color);
				g.drawString(tok.word, x, y + fm.getAscent());
				x += fm.stringWidth(tok.word);
				firstToken = false;
			}
			y += lineHeight;
		}

		return y;
	}

	// -------------------------------------------------------------------------
	// Private layout helpers
	// -------------------------------------------------------------------------

	/** Converts segments into a flat list of word tokens, inserting newline sentinels. */
	private static List<WordToken> tokenise(List<TextSegment> segments)
	{
		List<WordToken> tokens = new ArrayList<>();
		for (TextSegment seg : segments)
		{
			String[] lines = seg.getText().split("\n", -1);
			for (int li = 0; li < lines.length; li++)
			{
				for (String word : lines[li].split(" ", -1))
				{
					if (!word.isEmpty())
					{
						tokens.add(new WordToken(word, seg.getColor(), false));
					}
				}
				if (li < lines.length - 1)
				{
					tokens.add(new WordToken("", seg.getColor(), true)); // forced newline
				}
			}
		}
		return tokens;
	}

	/** Groups tokens into lines that fit within {@code maxWidth}. */
	private static List<List<WordToken>> layoutLines(List<WordToken> tokens,
	                                                  FontMetrics fm,
	                                                  int maxWidth)
	{
		List<List<WordToken>> lines = new ArrayList<>();
		List<WordToken> current = new ArrayList<>();
		int currentWidth = 0;
		int spaceWidth = fm.stringWidth(" ");

		for (WordToken tok : tokens)
		{
			if (tok.newline)
			{
				lines.add(new ArrayList<>(current));
				current.clear();
				currentWidth = 0;
				continue;
			}

			int wordWidth = fm.stringWidth(tok.word);
			int widthNeeded = current.isEmpty() ? wordWidth : (spaceWidth + wordWidth);

			if (!current.isEmpty() && currentWidth + widthNeeded > maxWidth)
			{
				lines.add(new ArrayList<>(current));
				current.clear();
				currentWidth = 0;
				widthNeeded = wordWidth;
			}

			current.add(tok);
			currentWidth += widthNeeded;
		}

		if (!current.isEmpty())
		{
			lines.add(current);
		}

		return lines;
	}

	/** Computes the pixel width of a rendered line (including inter-word spaces). */
	private static int measureLine(List<WordToken> line, FontMetrics fm)
	{
		int width = 0;
		int spaceWidth = fm.stringWidth(" ");
		for (int i = 0; i < line.size(); i++)
		{
			if (i > 0)
			{
				width += spaceWidth;
			}
			width += fm.stringWidth(line.get(i).word);
		}
		return width;
	}

	// -------------------------------------------------------------------------
	// Inner types
	// -------------------------------------------------------------------------

	private static final class WordToken
	{
		final String word;
		final Color color;
		final boolean newline;

		WordToken(String word, Color color, boolean newline)
		{
			this.word = word;
			this.color = color;
			this.newline = newline;
		}
	}
}

