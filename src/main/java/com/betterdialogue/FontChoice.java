package com.betterdialogue;

/**
 * Font choices available in the config panel.
 * All values are Java logical font names that are guaranteed to resolve on
 * every OS (Windows, macOS, Linux) without any bundled TTF files.
 */
public enum FontChoice
{
	SANS_SERIF("SansSerif", "Sans Serif"),        // Arial on Windows, Helvetica on Mac, DejaVu on Linux
	SERIF("Serif", "Serif"),                       // Times New Roman / similar
	MONOSPACED("Monospaced", "Monospaced"),        // Courier New / similar
	DIALOG("Dialog", "Dialog"),                    // Java default UI font
	DIALOG_INPUT("DialogInput", "Dialog Input");   // Java default monospaced UI font

	private final String javaName;
	private final String displayName;

	FontChoice(String javaName, String displayName)
	{
		this.javaName = javaName;
		this.displayName = displayName;
	}

	/** The name to pass to {@code new Font(name, style, size)}. */
	public String getJavaName()
	{
		return javaName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
