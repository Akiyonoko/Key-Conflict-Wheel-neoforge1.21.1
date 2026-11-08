# Key Conflict Wheel

Hold a key that several mods share — a radial wheel pops up, and you pick which action to run.
Built for modpacks where every key is already taken and you are tired of "which mod stole my V key".

## How it works

- Hold a key that more than one mod has bound.
- The wheel appears, one sector per action, labelled with the mod and the action name.
- Release the key with the cursor over a sector:
  - inner ring — one normal press of that action
  - outer ring — hold mode; the action stays pressed until you pick the same sector again
  - centre hole — cancel, and stop any holds running on that key

## Also in the box

- Priority protection — another mod's screen can't steal or dismiss the wheel
- Works with mods that run their own key handling, like Iron's Spells 'n Spellbooks or Punchy! Moves
- Combination keys (Alt+X, Ctrl+X) are left entirely to vanilla — the wheel never takes them over
- Blacklist pre-filled with W/A/S/D, Space and mouse left/right, so walking and mining never pop the wheel; one click on the wheel blacklists the key you are looking at, and the K menu can undo any of it
- Adjust Mode — drag to move the wheel, drag a handle to resize it, drag actions to reorder sectors, × clears a single keybind
- Five wheel styles — Vanilla, Glass, Brass, Terminal, Twilight
- Languages — English, 简体中文, 繁體中文, 日本語

## Install

- Minecraft 1.21.1 with NeoForge 21.1.x
- Client-side only — nothing to install on the server

## Config

Press K in game for the master menu: on/off, wheel size, style, sounds, long-press auto-repeat, reset layout, and the blocked-keys list.

Everything is stored in `config/key_conflict_wheel.json`.

## Debugging

Launch with `-Dkcw.debug=true` to log what the wheel actually synthesizes on each trigger (target action, click count, modifiers, whether it went through vanilla). Useful when an action from another mod "does nothing".

## Building

```
gradle build
```

Needs JDK 21. The jar lands in `build/libs/`.

## 中文简介

同一条快捷键被多个模组占用时，按住它会弹出圆盘：鼠标停在扇区内圈松开是短按一次，停在外圈是长按（再选同一扇区结束），停在中心是取消。
轮盘会抢在别的模组之前接管冲突键，所以不会再出现"两个功能一起触发"或者"轮盘刚弹出就被顶掉"。
已兼容铁魔法、Punchy! Moves 这类自带轮盘或自带按键处理的模组；`Alt+X` 这类组合键一律交还原版，轮盘不接管修饰键。
出厂默认拉黑 W/A/S/D、空格、鼠标左右键，走路挖方块绝不会弹轮盘；按 K 打开总菜单可以调大小、界面风格、音效、长按连发，也能管理拉黑列表。

## License

MIT.
