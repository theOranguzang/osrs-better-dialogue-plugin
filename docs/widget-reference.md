# Widget Reference

This document lists the OSRS widget groups and child indices used by Better
Dialogue to detect, read, and blank dialogue text.

> ⚠️ **These indices are approximate.** They are based on known RuneLite
> patterns at the time the plugin was written. Widget child indices can shift
> after game updates. **Always verify with the in-game Widget Inspector before
> relying on them.**

---

## How to use the Widget Inspector

1. Enable the **Widget Inspector** plugin in RuneLite (it ships with RuneLite by
   default under the "RuneLite" category in the plugin list).
2. Open the Widget Inspector panel (Wrench icon → Widget Inspector).
3. Trigger a dialogue in-game (talk to any NPC).
4. In the inspector, expand the widget tree. Look for the group that becomes
   visible when the dialogue box appears.
5. Click individual child widgets to see their text content and on-screen bounds
   highlighted.

---

## NPC Dialogue — `InterfaceID.DIALOG_NPC` (group 231)

Shown when an NPC is speaking to the player.

| Child index | Contents | Plugin action |
|-------------|----------|---------------|
| 0 | Root container (visibility check) | Checked for `isHidden()` |
| 4 | NPC name text | Read → `cachedNpcName`; blanked by `reBlankWidgets()` in overlay |
| 5 | "Click here to continue" | Blanked by `reBlankWidgets()` in overlay |
| 6 | Body text | **Blanked every frame**; content → `cachedNpcBody` |

---

## Player Dialogue — `InterfaceID.DIALOG_PLAYER` (group 217)

Shown when the player's response is displayed in the chatbox.

| Child index | Contents | Plugin action |
|-------------|----------|---------------|
| 0 | Root container | Checked for `isHidden()` |
| 4 | Player name text | Read → `cachedPlayerName`; blanked by `reBlankWidgets()` in overlay |
| 5 | "Click here to continue" | Blanked by `reBlankWidgets()` in overlay |
| 6 | Body text | **Blanked every frame**; content → `cachedPlayerBody` |

---

## Option Menu — `InterfaceID.DIALOG_OPTION` (group 219)

Shown for multi-choice dialogue (1–5 options).

The plugin accesses this dialogue via **static child 1**, which is a container
widget. All text lives in the **dynamic children** of that container — NOT in
static children 2–6. This is confirmed via the Widget Inspector.

```java
Widget container    = client.getWidget(InterfaceID.DIALOG_OPTION, 1);
Widget[] dynChildren = container.getDynamicChildren();
// dynChildren[0]   = title ("Select an option")  HasListener=false, color=0x800000
// dynChildren[1..n] = clickable option rows       HasListener=true
```

| Dynamic child index | Contents | Plugin action |
|---------------------|----------|---------------|
| 0 | Title — "Select an option" | Read → `cachedOptionTitle`; ref preserved in `cachedOptionTitleWidget`; **blanked every frame** |
| 1 | Option row 1 | Read → `cachedOptionTexts[0]`; **blanked every frame** |
| 2 | Option row 2 | Read → `cachedOptionTexts[1]`; **blanked every frame** |
| … | … | … |
| n | Option row n | Read → `cachedOptionTexts[n-1]`; **blanked every frame** |

Hidden dynamic children (unused option slots) are skipped.

> ⚠️ **Do not use static child indices 2–6 for option text.** The static
> children of `DIALOG_OPTION` do not hold the option strings in the current
> game client. All option text is in dynamic children of static child 1.

---

## Sprite / Item Dialogue — `InterfaceID.DIALOG_SPRITE` (group 193)

Shown for item-description and action-result dialogues
(e.g. "You light the logs.").

| Child index | Contents | Plugin action |
|-------------|----------|---------------|
| 0 | Root container | Checked for `isHidden()` |
| 2 | Body text | **Blanked every frame**; content → `cachedSpriteBody` |
| 3 | "Click here to continue" | Blanked by `reBlankWidgets()` in overlay |

---

## Updating indices after a game update

If dialogue text stops being replaced after a game update:

1. Open the Widget Inspector while a dialogue is active.
2. Find the group ID by looking for the widget that becomes visible when the
   dialogue opens (compare the group numbers shown in the inspector).
3. Expand the group and note which child contains the body text string.
4. Update the relevant constants at the top of `DialogueWidgetManager`:

```java
// NPC dialogue (InterfaceID.DIALOG_NPC)
private static final int NPC_CHILD_NAME     = 4;  // ← update here
private static final int NPC_CHILD_TEXT     = 6;  // ← update here
private static final int NPC_CHILD_CONTINUE = 5;  // ← update here
```

5. Compile with `./gradlew compileJava` and re-test in-game.
6. **Update this file** to reflect the new indices.

---

## Other dialogue types (not yet implemented)

| Dialogue type | Likely group | Notes |
|---------------|--------------|-------|
| Level-up box | `InterfaceID.LEVEL_UP` | Congratulations text |
| Quest complete scroll | `InterfaceID.QUEST_COMPLETED` | Scroll-style layout; may require different text layout logic |
| Gem / skill guide | Various | Not standard dialogue; lower priority |

These are defined in `DialogueType` but `DialogueWidgetManager` does not yet
detect or handle them. The `LEVEL_UP` and `QUEST_COMPLETE` enum variants are
placeholders for future work.

