# Architecture — Better Dialogue Plugin

## Motivation

Old-School RuneScape renders all dialogue text using a fixed bitmap font called
**Quill 8** (also called "Freehand 521" or colloquially the "cursive font"). It
is stylistically authentic but hard to read at small sizes, inaccessible for
users with reading difficulties, and visually jarring on high-DPI displays.

RuneLite's `Widget.setText()` API can change _what_ a widget says but it cannot
change _how_ it is drawn — the engine always uses its own internal bitmap
renderer. The only way to substitute a different font is to paint outside the
engine's rendering pipeline through RuneLite's **Overlay** system, which
provides a full `Graphics2D` context drawn after the engine finishes a frame.

---

## Core approach

```
┌──────────────────────────────────────┐
│             OSRS game engine         │
│                                      │
│  Widget system                       │
│    DialogueWidget                    │
│      ├─ [child 4] name               │  ← we blank this text
│      ├─ [child 5] continue prompt    │  ← we blank this text
│      └─ [child 6] body text          │  ← we blank this text
│                                      │
│  (engine renders — but widgets       │
│   now contain empty strings)         │
└──────────────────────────────────────┘
          ↓  frame handed to overlay system
┌──────────────────────────────────────┐
│     BetterDialogueOverlay.render()   │
│                                      │
│  1. fill background rect (covers     │
│     any residual bitmap text)        │
│  2. drawWrappedText() with           │
│     configured TrueType font         │
└──────────────────────────────────────┘
```

---

## Rendering pipeline

### Per-frame sequence

```
onClientTick  (~50 fps, every client frame)
  └─ DialogueWidgetManager.getCurrentDialogue()
      ├─ Check each dialogue root widget for visibility
      ├─ If visible:
      │   ├─ read widget.getText()
      │   ├─ if non-empty → parse colour/break tags → update text cache
      │   ├─ widget.setText("")     ← blank the widget EVERY frame
      │   └─ return DialogueState (type + cached segments + live widget refs)
      └─ overlay.setState(state)

BetterDialogueOverlay.render(Graphics2D)  (called every rendered frame)
  ├─ reBlankWidgets(state)   ← belt-and-suspenders blank before any pixels written
  ├─ fontRenderer.applyRenderingHints()
  └─ switch(state.getType())
      ├─ NPC / Player  → fillBackground() + drawWrappedText()
      ├─ Options       → per-option fillBackground() + drawCenteredString()
      │                  + hover colour detection via mouse position
      └─ Sprite        → fillBackground() + drawWrappedText()
```

### Why `ClientTick` instead of `GameTick`

`GameTick` fires every **600 ms** (one server tick). When a new dialogue opens
the engine sets widget text immediately, but `GameTick` may not fire for another
~600 ms, leaving several rendered frames where the original Quill font is
visible.

`ClientTick` fires every **client frame** (~20 ms). The window between the
engine writing text and us blanking it is at most a single frame.

---

## The capture-then-blank pattern

This is the most important design invariant in the codebase.

### The problem

```
Frame 1  onClientTick:  widget.getText() = "Hello"  → parse → blank (setText(""))
Frame 2  onClientTick:  widget.getText() = ""        → parse → empty list
Frame 2  render():      state.getBodySegments() == [] → nothing painted  ← blank screen
```

After the first blank, every subsequent `onClientTick` call sees an empty widget
and produces a `DialogueState` with no segments. The overlay has nothing to
paint.

### The fix — per-type text caches

```java
String raw = textWidget.getText();
if (raw != null && !raw.isEmpty()) {
    // Only update the cache when the engine gives us real text
    cachedNpcBody = parseSegments(raw, Color.BLACK);
}
// Always blank — even on frames where raw was already ""
blankWidget(textWidget);
// Always build the state from the cache, not the (now empty) widget
return new DialogueState(..., cachedNpcBody, ...);
```

The cache is populated **only when `getText()` returns non-empty text**. On
every subsequent frame the widget is already blank, so the cache is left
untouched and the overlay continues to render the last good snapshot.

### Cache lifetime and `lastSeenType`

A stale cache from NPC A could bleed into the opening frame of NPC B's dialogue
(before B's non-empty text has been captured). `DialogueWidgetManager` tracks
the `lastSeenType` and calls `clearCacheFor(previousType)` whenever the active
dialogue type changes or no dialogue is open.

---

## Text hiding strategy

Three options were considered:

| Option | Approach | Risk |
|--------|----------|------|
| **A (implemented)** | `widget.setText("")` on text children only | Minimal — click handlers are bound to widget bounds, not text content |
| B | Paint opaque background over text, leave widget untouched | Hard to colour-match the parchment gradient exactly |
| C | `widget.setHidden(true)` | Breaks click-to-continue and option selection |

**Option A + background fill (hybrid)** is used: the widget text is blanked so
the engine cannot render it, and a filled rectangle is also painted by the
overlay to catch any residual pixels. Original text is restored via
`DialogueWidgetManager.restoreAll()` when the plugin shuts down.

---

## Widget system overview

OSRS UIs are composed of **Widgets** arranged in a tree. Each widget has a
numeric group ID and child ID. The RuneLite API exposes them via:

```java
Widget widget = client.getWidget(int groupId, int childId);
```

Dialogue boxes each have their own group (identified by `InterfaceID` constants)
and a fixed set of children for the name, body text, and continue prompt.

### Widget groups used

| Dialogue type | `InterfaceID` constant | Group ID |
|---------------|------------------------|----------|
| NPC speaking  | `DIALOG_NPC`           | 231      |
| Player speaking | `DIALOG_PLAYER`      | 217      |
| Option menu   | `DIALOG_OPTION`        | 219      |
| Sprite / item | `DIALOG_SPRITE`        | 193      |

Child indices within each group are documented in
[`widget-reference.md`](widget-reference.md) and in
`DialogueWidgetManager` source constants.

---

## Font system

`FontRenderer` resolves a `java.awt.Font` in this priority order:

1. **Custom file path** — when `FontChoice.CUSTOM` is selected and a valid
   `.ttf` path is configured
2. **Bundled TTF resource** — `src/main/resources/fonts/<name>-Regular.ttf`
   (must be supplied manually from Google Fonts; see README)
3. **JVM fallback** — `Font.SANS_SERIF` at the configured size

The resolved font is cached; it is only recreated when the `FontChoice`, font
size, or custom path config values change.

`FontRenderer.drawWrappedText()` implements word-wrapping using
`FontMetrics.stringWidth()` and centres each line horizontally within the
widget bounds.

---

## Inline markup tags

OSRS dialogue text uses a small set of HTML-like tags. `DialogueWidgetManager`
handles:

| Tag | Meaning | Handling |
|-----|---------|----------|
| `<col=RRGGBB>` | Set text colour | Parsed; drives `TextSegment.color` |
| `</col>` | Reset to default colour | Resets `currentColor` |
| `<br>` | Line break | Replaced with `\n` before splitting |
| `<lt>` / `<gt>` | Literal `<` / `>` | Replaced before tag scanning |
| `<shad>`, `<str>`, `<u>`, etc. | Shadow, strikethrough, underline | Silently ignored |

---

## Edge cases

| # | Scenario | Current behaviour |
|---|----------|-------------------|
| 1 | **Click-to-continue** — continue prompt is a separate child widget; we blank its text | Click handler is widget-bounds-based; continues to work correctly |
| 2 | **Option hover highlights** — game normally changes text colour on hover | Overlay detects mouse position vs option bounds and renders white text on hover |
| 3 | **Scrolling quest dialogue** — widget text updates each scroll | Re-captured each `onClientTick` via the non-empty check |
| 4 | **Widget child index drift** — game updates can change child indices | All indices are constants in `DialogueWidgetManager`; verify with Widget Inspector after updates |
| 5 | **Alternative interface styles** | Background fill colour (`#C9B89A`) may not match; exposed as a future config option |
| 6 | **GPU plugin** | Overlay paints in screen-space; should be unaffected. Verify layering in production. |

