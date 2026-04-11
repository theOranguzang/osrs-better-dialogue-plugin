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
