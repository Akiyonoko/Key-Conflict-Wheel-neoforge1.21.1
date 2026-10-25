package cn.blockforge.generated.keyconflictwheel.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 总控菜单（默认按 K 打开）：
 * 顶部依次是功能总开关、轮盘大小（5 档）、轮盘音效、长按连发、结束全部长按、轮盘位置复位；
 * 下面是已拉黑按键列表（WASD、空格、鼠标左右键出厂就拉黑，防止走路挖方块弹轮盘），
 * 条目多时可用鼠标滚轮滚动，可逐个解除、一键清空，或"恢复默认拉黑"装回来。
 */
public final class MasterMenuScreen extends Screen {

    private static final double[] SCALES = {0.7, 0.85, 1.0, 1.2, 1.45};
    private static final String[] SCALE_KEYS = {
            "key_conflict_wheel.menu.scale.xsmall",
            "key_conflict_wheel.menu.scale.small",
            "key_conflict_wheel.menu.scale.mid",
            "key_conflict_wheel.menu.scale.large",
            "key_conflict_wheel.menu.scale.xlarge"};

    /** 一行拉黑条目的高度（像素） */
    private static final int ROW_H = 22;

    private final List<KeyRef> rows = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();
    /** 列表可视区（滚动发生在这里），以及区底"清空／恢复"那一行的位置 */
    private int areaTop;
    private int areaBottom;
    private int actionsY;
    /** 当前滚动量（像素），0＝最顶 */
    private int scroll;
    /** 最大可滚动量，0＝条目少，一屏装得下 */
    private int maxScroll;

    public MasterMenuScreen() {
        super(Component.translatable("key_conflict_wheel.menu.title"));
    }

    @Override
    protected void init() {
        WheelConfig cfg = WheelConfig.get();
        int cx = this.width / 2;

        rows.clear();
        for (String s : cfg.blacklisted) {
            KeyRef r = KeyRef.deserialize(s);
            if (r != null) rows.add(r);
        }
        rows.sort(Comparator.comparing(r -> r.display().getString()));
        rowButtons.clear();

        int hintY = this.height - 62;
        actionsY = hintY - 26;
        areaTop = 166;
        areaBottom = Math.max(actionsY - 8, areaTop + ROW_H);
        maxScroll = Math.max(0, rows.size() * ROW_H - (areaBottom - areaTop));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        addRenderableWidget(Button.builder(masterLabel(cfg), b -> {
            cfg.masterEnabled = !cfg.masterEnabled;
            cfg.save();
            if (!cfg.masterEnabled) ConflictWheelClient.INSTANCE.stopAllHolds();
            b.setMessage(masterLabel(cfg));
        }).bounds(cx - 110, 24, 220, 20).build());

        addRenderableWidget(Button.builder(scaleLabel(cfg), b -> {
            cfg.wheelScale = SCALES[(scaleIndex(cfg) + 1) % SCALES.length];
            cfg.save();
            b.setMessage(scaleLabel(cfg));
        }).bounds(cx - 110, 46, 220, 20).build());

        addRenderableWidget(Button.builder(soundLabel(cfg), b -> {
            cfg.soundsEnabled = !cfg.soundsEnabled;
            cfg.save();
            b.setMessage(soundLabel(cfg));
            if (cfg.soundsEnabled) WheelSounds.openMenu();
        }).bounds(cx - 110, 68, 220, 20).build());

        addRenderableWidget(Button.builder(repeatLabel(cfg), b -> {
            cfg.longPressRepeat = !cfg.longPressRepeat;
            cfg.save();
            b.setMessage(repeatLabel(cfg));
            WheelSounds.openMenu();
        }).bounds(cx - 110, 90, 220, 20).build());

        addRenderableWidget(Button.builder(holdLabel(), b -> {
            ConflictWheelClient.INSTANCE.stopAllHolds();
            rebuildWidgets();
        }).bounds(cx - 110, 112, 220, 20).build());

        addRenderableWidget(Button.builder(layoutLabel(cfg), b -> {
            WheelConfig c = WheelConfig.get();
            c.resetPosition();
            c.resetButtons();
            b.setMessage(layoutLabel(c));
            WheelSounds.openMenu();
        }).bounds(cx - 110, 134, 220, 20).build());

        for (KeyRef ref : rows) {
            rowButtons.add(addRenderableWidget(Button.builder(
                    Component.translatable("key_conflict_wheel.menu.unblacklist"), b -> {
                cfg.unblacklist(ref);
                rebuildWidgets();
            }).bounds(cx + 78, 0, 44, 20).build()));
        }
        applyScroll();

        boolean showClear = !rows.isEmpty();
        boolean showRestore = cfg.hasMissingDefaults();
        boolean both = showClear && showRestore;
        if (showClear) {
            addRenderableWidget(Button.builder(Component.translatable("key_conflict_wheel.menu.clear_all"), b -> {
                cfg.blacklisted.clear();
                cfg.save();
                scroll = 0;
                rebuildWidgets();
            }).bounds(cx - 110, actionsY, both ? 106 : 220, 20).build());
        }
        if (showRestore) {
            addRenderableWidget(Button.builder(Component.translatable("key_conflict_wheel.menu.restore_defaults"), b -> {
                cfg.restoreDefaults();
                WheelSounds.openMenu();
                rebuildWidgets();
            }).bounds(both ? cx + 4 : cx - 110, actionsY, both ? 106 : 220, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("key_conflict_wheel.menu.close"), b -> onClose())
                .bounds(cx - 110, this.height - 38, 220, 20).build());
    }

    /** 按当前滚动量摆放每一行的"解除"按钮，并只显示可视区内的行 */
    private void applyScroll() {
        for (int i = 0; i < rowButtons.size(); i++) {
            Button b = rowButtons.get(i);
            int y = areaTop + i * ROW_H - scroll;
            b.setY(y);
            b.visible = y + 20 >= areaTop && y <= areaBottom;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            int cx = this.width / 2;
            boolean overList = mouseX >= cx - 118 && mouseX <= cx + 132
                    && mouseY >= areaTop - 6 && mouseY <= areaBottom + 6;
            if (overList) {
                int next = (int) Math.max(0, Math.min(maxScroll, scroll - scrollY * (ROW_H + 2)));
                if (next != scroll) {
                    scroll = next;
                    applyScroll();
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, 10, 0xFFFFE08A);

        // 标题行：已拉黑（N 个），装不下时追加灰色的"（滚轮滚动）"
        Component header = Component.translatable("key_conflict_wheel.menu.blacklist_header", rows.size());
        int headerY = areaTop - 14;
        if (maxScroll > 0) {
            Component hint = Component.translatable("key_conflict_wheel.menu.scroll_hint");
            int hw = this.font.width(header);
            int gw = this.font.width(hint);
            int startX = cx - (hw + 6 + gw) / 2;
            graphics.drawString(this.font, header, startX, headerY, 0xFFFFFFFF);
            graphics.drawString(this.font, hint, startX + hw + 6, headerY, 0xFF8A8A9A);
        } else {
            graphics.drawCenteredString(this.font, header, cx, headerY,
                    rows.isEmpty() ? 0xFF9E9EAE : 0xFFFFFFFF);
        }

        int y = areaTop - scroll + 6;
        for (KeyRef ref : rows) {
            if (y >= areaTop - 4 && y <= areaBottom - 12) {
                graphics.drawString(this.font, ref.display().copy().append(Component.literal("  (" + ref.type.name() + ")")),
                        cx - 110, y, 0xE8E8F0);
            }
            y += ROW_H;
        }

        // 细滚动条：贴在"解除"按钮右边
        if (maxScroll > 0) {
            int trackH = areaBottom - areaTop;
            int thumbH = Math.max(14, trackH * trackH / (rows.size() * ROW_H));
            int thumbY = areaTop + (trackH - thumbH) * scroll / maxScroll;
            graphics.fill(cx + 126, areaTop, cx + 128, areaBottom, 0x40FFFFFF);
            graphics.fill(cx + 126, thumbY, cx + 128, thumbY + thumbH, 0xA0FFFFFF);
        }

        int hintY = this.height - 62;
        graphics.drawCenteredString(this.font,
                Component.translatable("key_conflict_wheel.menu.hint1",
                        ConflictWheelClient.MENU_KEY.getTranslatedKeyMessage()),
                cx, hintY, 0xFFB0B0C0);
        graphics.drawCenteredString(this.font,
                Component.translatable("key_conflict_wheel.menu.hint2"),
                cx, hintY + 12, 0xFFB0B0C0);
    }

    // ---------- 标签文案 ----------

    private static Component masterLabel(WheelConfig cfg) {
        return Component.translatable("key_conflict_wheel.menu.master",
                Component.translatable(cfg.masterEnabled ? "key_conflict_wheel.menu.on" : "key_conflict_wheel.menu.off"));
    }

    private static Component soundLabel(WheelConfig cfg) {
        return Component.translatable("key_conflict_wheel.menu.sound",
                Component.translatable(cfg.soundsEnabled ? "key_conflict_wheel.menu.on" : "key_conflict_wheel.menu.off"));
    }

    private static Component repeatLabel(WheelConfig cfg) {
        return Component.translatable("key_conflict_wheel.menu.repeat",
                Component.translatable(cfg.longPressRepeat ? "key_conflict_wheel.menu.on" : "key_conflict_wheel.menu.off"));
    }

    private static Component holdLabel() {
        int n = KeySynth.holdCount();
        return n > 0
                ? Component.translatable("key_conflict_wheel.menu.stop_holds", n)
                : Component.translatable("key_conflict_wheel.menu.no_holds");
    }

    private static Component layoutLabel(WheelConfig cfg) {
        return Component.translatable(cfg.layoutCustomized()
                ? "key_conflict_wheel.menu.layout_custom"
                : "key_conflict_wheel.menu.layout_default");
    }

    private static int scaleIndex(WheelConfig cfg) {
        int best = 2;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < SCALES.length; i++) {
            double d = Math.abs(cfg.wheelScale - SCALES[i]);
            if (d < bestDiff) {
                bestDiff = d;
                best = i;
            }
        }
        return best;
    }

    private static Component scaleLabel(WheelConfig cfg) {
        return Component.translatable("key_conflict_wheel.menu.scale",
                Component.translatable(SCALE_KEYS[scaleIndex(cfg)]));
    }
}
