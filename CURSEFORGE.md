# CurseForge 上传模板 — Key Conflict Wheel（快捷键冲突轮盘）

> 这份文件是给你上传 CurseForge 时照着填/复制用的，不会被打进 jar。
> 下面第 1～4 节是"建项目时网页上要填的栏位"，第 5 节是可整段复制的项目介绍正文。

---

## 1. 创建项目（Create Project）表单

| 栏位 | 填写内容 |
|---|---|
| Project name | `Key Conflict Wheel` |
| License | MIT License |
| Primary Category | `Utility & QoL` |
| Secondary Category | `Information`（可选） |
| Upload File | 先跳过，建好项目后再传 jar |

## 2. 首次上传文件（Upload File）表单

| 栏位 | 填写内容 |
|---|---|
| File title | `1.0.0 (1.21.1)` |
| Release type | `Release` |
| External link | 不勾 |
| Game versions | Minecraft: `1.21.1`；Mod loader: `NeoForge` |
| Client / Server | 只勾 **Client**（本模组纯客户端） |
| Dependencies | 无需手动添加（NeoForge 由 loader 版本体现） |
| Changelog | 用第 4 节的模板 |

## 3. 摘要（Summary，页面短简介，≤120 字符）

```
When several mods share one key, hold it and pick from a radial wheel: inner ring = press, outer ring = hold.
```

## 4. Changelog 模板（每次传新 jar 时填）

```
## 1.0.0
First public release.

- Radial wheel: hold a key bound by several mods, release over a sector to choose it
- Inner ring = single press, outer ring = hold (press again on the same sector to stop), centre = cancel
- Priority protection: other mods' screens can no longer steal the wheel
- Early keyboard takeover: fixed clashes with wheel-style mods (e.g. Iron's Spells 'n Spellbooks)
- Adjust Mode: drag to reposition, resize wheel, reorder sectors by dragging actions, × clears one action's keybind
- Right-click drag the button row to anywhere on screen
- Default key blacklist out of the box: W/A/S/D, Space and mouse LMB/RMB stay completely vanilla — re-enable any of them in the K menu
- Press K for the config menu: master switch, wheel size, sounds, long-press auto-repeat, reset layout
- Five wheel UI styles — Vanilla, Glass, Brass, Terminal, Twilight — cycled from the K menu or from an optional hotkey (bind "Toggle Wheel UI Style" in Controls)
- Languages: English / 简体中文 / 繁體中文 / 日本語
```

### 1.1.0 更新说明模板

```
## 1.1.0
- New: two wheel UI styles (Vanilla MC / Translucent) — switch in the K menu, or bind the new "Toggle Wheel UI Style" key (unbound by default)
- New: sector labels now shrink to fit instead of being cut off with "..."
- New: 128×128 mod icon
- Translucent falls back to high contrast while Adjust Mode is open, so the small handles stay readable
```

## 5. 项目介绍正文（Description，切到 Markdown 编辑模式整段粘贴）

见本文件末尾"DESCRIPTION.md"分隔线之后的内容；那里是最终版正文。

---

## 6. 上传前检查清单

- [ ] 项目 Logo：建议至少 256×256 方图。jar 里的 `icon.png` 已经是 128×128，
      项目页图标用 `模组图标_上传用/key_conflict_wheel_icon_400.png` 那张即可。
- [ ] 至少 1 张截图：轮盘弹出时的游戏截图（内/外圈分界线要看得清）。
      最好再来一张"调整位置模式"的截图。
- [ ] 介绍正文里的 `![](...)` 图片占位符，在 CurseForge 编辑器里直接粘贴/上传图片会自动变成链接。
- [ ] 文件名建议改名后上传：`key-conflict-wheel-1.0.0-1.21.1-neoforge.jar`
- [ ] 首次提交后 CurseForge 会人工审核，通常几天内；别重复提交。

---

# DESCRIPTION.md（以下整段复制到 CurseForge 介绍编辑器）

# Key Conflict Wheel — 快捷键冲突轮盘

**Stop fighting over keybinds in your modpack.**
When several mods share the same key, just **hold it** — a radial wheel pops up and lets you pick which action to use. No more digging through Controls settings, no more "which mod stole my V key".

![](paste a screenshot of the wheel here)

## How it works

1. Hold a key that more than one mod has bound.
2. The wheel appears, one sector per action, labelled with mod name and action name.
3. Release the key while the cursor is on a sector:

| Cursor position | Result |
|---|---|
| **Inner ring** | one normal press of that action |
| **Outer ring** | hold mode — the action stays pressed until you press the key again on the same sector |
| **Centre hole** | cancel, nothing is triggered |

The boundary between inner and outer ring is drawn clearly, so you never have to guess.

## Built for big modpacks

- **Priority protection** — other mods' GUIs can't steal or dismiss the wheel.
- **Wheel-mod friendly** — early keyboard handling was rewritten so mods with their own wheels or quick-cast keys (e.g. *Iron's Spells 'n Spellbooks*) no longer double-trigger or flash-quit the wheel.
- **Blacklist, pre-filled** — `W A S D`, `Space` and mouse left/right are blocked on first launch, so running and mining never pop the wheel. Unblock a single key any time in the K menu (or clear the whole list); one click restores the defaults. On the wheel itself, one click blacklists the key you are looking at.
- **Sound feedback** — subtle hover / confirm / blacklist sounds, can be turned off.

## Make it yours (Adjust Mode)

Press the **Adjust** button on the wheel to lock it and open editing:

- Two small handles at the bottom-right: **move** the wheel, **scale** the wheel.
- Drag any action to **reorder the sectors**.
- The **×** outside a sector clears that single action's keybind.
- Hover a sector for a tooltip with the full name.
- Confirm to save. The whole button row can also be **right-click dragged** anywhere on screen.

## UI styles

Two looks, switchable in the K menu (or bind **Toggle Wheel UI Style** in Controls — unbound by default):

| Style | Look |
|---|---|
| **Vanilla MC** | grey plates with light/dark bevels, closest to the vanilla button look |
| **Translucent** | thin lines, low opacity, soft darkening behind — barely covers the world |

Translucent switches itself to the high-contrast palette while Adjust Mode is open, so the small move/scale handles and the × stay visible.

## Config menu

Press **K** in game for the master menu:

- Master on/off switch
- Wheel size slider
- UI style switcher
- Sounds on/off
- Long-press auto-repeat
- Reset layout to default
- Blocked-keys list (WASD & co. are in it by default) — unblock one by one, clear all, or restore the defaults

## Compatibility

- Minecraft **1.21.1** with **NeoForge** (21.1.x)
- **Client-side only** — not required on servers
- Tested against wheel/ability mods such as Iron's Spells 'n Spellbooks

## Languages

English / 简体中文 / 繁體中文 / 日本語 — the UI follows your game language automatically.

## 中文简介

同一条快捷键被多个模组占用时，**按住它**会弹出圆盘：鼠标停在扇区**内圈松开＝短按一次**，停在**外圈松开＝长按**（再按一次同一扇区结束长按），停在**中心＝取消**。
支持一键拉黑常用键，且出厂就把 W/A/S/D、空格、鼠标左右键默认拉黑——走路挖方块绝不会弹轮盘；想恢复哪个键，在 K 总菜单的拉黑列表里逐个解除或一键清空，也可以一键恢复默认。调整位置模式（拖动/缩放轮盘、拖拽重排扇区、× 取消单个快捷键）、按 K 打开总菜单（总开关／大小／界面风格／音效／长按连发／复位）。
**两套界面风格**：原版 MC、半透明，K 菜单里一键切换，也可以在「控制」里给"切换轮盘界面风格"绑一个键。
纯客户端模组，已针对铁魔法等自带轮盘的模组做底层键盘兼容修复。

## License

MIT — free to use in personal modpacks.

---

*Bugs and feature requests: please leave a comment on this page or attach your `logs/latest.log`.*
