package com.betterdialogue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Bundled font choices available in the config panel.
 * The {@code resourcePath} points to a TTF file under {@code src/main/resources/}.
 * If no file is present at that path the plugin falls back to the JVM's
 * built-in {@link java.awt.Font#SANS_SERIF} family.
 */
@Getter
@RequiredArgsConstructor
public enum FontChoice
{
	ROBOTO("Roboto", "fonts/Roboto-Regular.ttf"),
	INTER("Inter", "fonts/Inter-Regular.ttf"),
	OPEN_SANS("Open Sans", "fonts/OpenSans-Regular.ttf"),
	LATO("Lato", "fonts/Lato-Regular.ttf"),
	SOURCE_SANS("Source Sans 3", "fonts/SourceSans3-Regular.ttf"),
	CUSTOM("Custom (set path below)", null);

	private final String displayName;
	/** Classpath-relative path to the bundled TTF, or {@code null} for custom. */
	private final String resourcePath;

	@Override
	public String toString()
	{
		return displayName;
	}
}

