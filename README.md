# Better Dialogue — RuneLite Plugin

Replaces the hard-to-read OSRS bitmap cursive font (Quill 8 / "Freehand") with a
clean, configurable TrueType font rendered via Java2D overlays.

## Features

- Replaces text in **NPC dialogue**, **player dialogue**, **option menus**, and **item/action sprite dialogue**
- Per-type enable/disable toggles in the config panel
- Font picker: Roboto, Inter, Open Sans, Lato, Source Sans 3, or a custom `.ttf` path
- Configurable font size (10–28 pt), body/name/continue-prompt colours
- Optional sub-pixel anti-aliasing
- Original widget text is restored on plugin shutdown — nothing is permanently altered

## How it works

The game renders dialogue through the Widget system using a fixed bitmap font.
Because RuneLite's `Widget.setText()` cannot change the font, this plugin:

1. **Detects** which dialogue widget is currently visible (checked every game tick)
2. **Blanks** the original text child widget (`widget.setText("")`)
3. **Paints** replacement text with a `Graphics2D` overlay on `OverlayLayer.ABOVE_WIDGETS`

Click-to-continue and option selection are unaffected — the click target is the
widget bounds, not its rendered text.

## Widget child indices

The indices below are approximate; **verify with the in-game Widget Inspector**
and adjust in `DialogueWidgetManager` if they differ after a game update.

| Dialogue type     | InterfaceID constant  | Name | Text | Continue |
|-------------------|-----------------------|------|------|----------|
| NPC dialogue      | `DIALOG_NPC` (231)    | 4    | 6    | 5        |
| Player dialogue   | `DIALOG_PLAYER` (217) | 4    | 6    | 5        |
| Option menu       | `DIALOG_OPTION` (219) | 1    | 2–6  | —        |
| Sprite/item       | `DIALOG_SPRITE` (193) | —    | 2    | 3        |

## Bundled fonts

Drop `.ttf` files into `src/main/resources/fonts/` matching the names expected
by `FontChoice` (e.g. `Roboto-Regular.ttf`).  Free downloads from
[Google Fonts](https://fonts.google.com).  Without them the plugin falls back
to the JVM's built-in `SansSerif` family.

## Development setup

```
./gradlew run          # launch RuneLite with the plugin loaded
./gradlew build        # compile + test
./gradlew shadowJar    # build a fat JAR
```

Requires JDK 11+.
