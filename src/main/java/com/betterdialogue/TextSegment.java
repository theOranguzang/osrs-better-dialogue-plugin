package com.betterdialogue;

import lombok.Data;

import java.awt.Color;

/**
 * A contiguous run of text that shares a single {@link Color}.
 * The text may contain newline characters representing {@code <br>} tags.
 */
@Data
public class TextSegment
{
	private final String text;
	private final Color color;
}

