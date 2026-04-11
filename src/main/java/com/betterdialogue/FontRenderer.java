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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides font creation, caching, rendering-hint application, and word-wrap /
 * centred-text drawing utilities used by {@link BetterDialogueOverlay}.
 *
 * <p>Fonts are created from Java logical font names (SansSerif, Serif, etc.)
 * which are always available on every OS with no bundled TTF files required.
 * The derived {@link Font} object is cached and only rebuilt when the config
 * changes (font family, size, or bold flag).
 *
 * <h3>Statelessness contract</h3>
 * The only mutable state is the font cache.  All layout information (bounds,
 * segments) is passed in per call and is never stored on the instance.
 */
@Slf4j
@Singleton
public class FontRenderer
{
	@Inject
	private BetterDialogueConfig config;

	// -------------------------------------------------------------------------
	// Font cache — invalidated when any config key that affects the font changes
	// -------------------------------------------------------------------------

	private Font   cachedFont    = null;
	private String lastFontKey   = null;

	// -------------------------------------------------------------------------
	// Font access
	// -------------------------------------------------------------------------

	/**
	 * Returns the currently configured {@link Font}, rebuilding it only when
	 * the font family, size, or bold flag has changed since the last call.
	 */
	public Font getFont()
	{
		String key = config.fontFamily().getJavaName()
			+ "_" + config.fontSize()
			+ "_" + config.boldText();

		if (!key.equals(lastFontKey))
		{
			int style = config.boldText() ? Font.BOLD : Font.PLAIN;
			cachedFont  = new Font(config.fontFamily().getJavaName(), style, config.fontSize());
			lastFontKey = key;
			log.debug("Font rebuilt: {}", key);
		}
		return cachedFont;
	}

	/**
	 * Returns the font used for option-dialogue rows.
	 * Option rows share the same configured font as body text — "one font for
	 * everything" keeps the config panel simple.
	 */
	public Font getOptionFont()
	{
		return getFont();
	}

	// -------------------------------------------------------------------------
	// Rendering helpers
	// -------------------------------------------------------------------------

	/**
	 * Applies (or removes) text anti-aliasing rendering hints based on the
	 * current config.  Call once per {@link BetterDialogueOverlay#render} frame
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
	 * Draws a single-line string centred horizontally within {@code bounds}
	 * using an explicitly supplied {@code font}.
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
	 * Draws a single-line string centred horizontally within {@code bounds}
	 * using the currently configured font.
	 *
	 * @return the y coordinate of the bottom of the drawn line
	 */
	public int drawCenteredString(Graphics2D g, String text, Rectangle bounds, int y, Color color)
	{
		return drawCenteredString(g, text, bounds, y, color, getFont());
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

		int maxWidth  = bounds.width - 8; // 4 px horizontal padding each side
		int lineHeight = fm.getHeight();
		int y = startY;

		List<WordToken> tokens = tokenise(segments);
		List<List<WordToken>> lines = layoutLines(tokens, fm, maxWidth);

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
					tokens.add(new WordToken("", seg.getColor(), true));
				}
			}
		}
		return tokens;
	}

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

			int wordWidth   = fm.stringWidth(tok.word);
			int widthNeeded = current.isEmpty() ? wordWidth : (spaceWidth + wordWidth);

			if (!current.isEmpty() && currentWidth + widthNeeded > maxWidth)
			{
				lines.add(new ArrayList<>(current));
				current.clear();
				currentWidth = 0;
				widthNeeded  = wordWidth;
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
		final String  word;
		final Color   color;
		final boolean newline;

		WordToken(String word, Color color, boolean newline)
		{
			this.word    = word;
			this.color   = color;
			this.newline = newline;
		}
	}
}

