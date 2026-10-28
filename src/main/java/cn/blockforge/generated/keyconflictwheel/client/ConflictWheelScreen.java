package cn.blockforge.generated.keyconflictwheel.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 冲突快捷键轮盘界面。
 *
 * 普通模式（按住冲突键弹出）：松开按键的那一刻，鼠标停在哪个圈层就触发什么——
 * 内圈某扇区 = 对该功能短按一次；
 * 外圈某扇区 = 该功能的长按开关（没在长按就立刻开始按住，已经在长按就结束它），
 * 同一个键上可以同时按住多个模组的功能；
 * 中心孔 = 取消（该键上有长按在跑时 = 结束这些长按）；分界白环／圆盘外 = 取消；
 * 屏幕右下角两个小按钮 = 左"拉黑这个键"、右"进入调整位置模式"，
 * 按住鼠标右键在这两个按钮上一拖，就能把这排按钮挪到屏幕上任何位置（松手即保存）。
 *
 * 调整位置模式（点"调整位置"进入）：轮盘被临时锁定，不再响应松开按键；
 * 轮盘右下角多出两个小把手：按住"移动"把手可拖动整个轮盘，按住"缩放"把手上下拖可放大缩小
 * （滚轮在缩放把手上滚一下也能微调 5%）；
 * 按住轮盘上某个功能（扇区）左右拖动，可以改它在轮盘上的排列顺序，松手放下、确认才保存；
 * 外圈外侧每个扇区有一个 ×，点它可以取消那个功能的快捷键；
 * 鼠标停在扇区上会弹出提示框（完整功能名／分类，小扇区文字显示不全时看这里）；
 * 右下角"确认"保存位置／大小／顺序，左下角"取消"放弃改动，"居中"一键回到屏幕正中。
 * 按 ESC／右键也等于取消。
 */
public final class ConflictWheelScreen extends Screen {

    /** 区域编码：-2 圆外、-1 中心孔、0 缓冲/按钮区、1 内圈(短按)、2 外圈(长按) */
    private static final int ZONE_OUTSIDE = -2;
    private static final int ZONE_CENTER = -1;
    private static final int ZONE_BUFFER = 0;
    private static final int ZONE_SHORT = 1;
    private static final int ZONE_LONG = 2;

    private final KeyRef ref;
    private final List<KeyMapping> bindings;
    private int count;

    private int zone = ZONE_OUTSIDE;
    private int sector = -1;
    private boolean firstSample = true;
    private boolean finished;
    /** 覆盖层模式：不交给 Minecraft.screen，游戏照常运行，只在本类里自绘 */
    private boolean overlay;
    private double lastMouseX = -9999, lastMouseY = -9999;

    // ---------- 调整位置模式 ----------
    private boolean adjustMode;
    private boolean dragging;
    private boolean positionSettled;        // 本次调整已保存/已还原，退出时不用再回滚
    private double grabDX, grabDY;          // 按下点相对轮盘中心，拖动时保持这个把手位置
    private double entryOffsetX, entryOffsetY; // 进入调整模式时的位置，取消时还原
    private int hotCross = -1;              // 当前鼠标悬停在哪个 × 上
    private int hoverSector = -1;           // 调整模式下鼠标悬停在哪个扇区上（弹提示框用）
    private boolean adjustFirstSample;      // 进入调整模式后的第一帧采样不播悬停音
    private double entryScale;              // 进入调整模式时的大小（取消时还原）
    private List<String> entryOrder = List.of(); // 进入调整模式时的扇区顺序（取消时还原）

    // ---------- 轮盘右下角的两个小把手（仅调整模式） ----------
    private int hotHandle = -1;             // 悬停在哪个把手上：1=移动 2=缩放
    private boolean scaling;                // 正按住缩放把手
    private double scaleStartY, scaleStartValue;

    // ---------- 拖扇区改顺序（仅调整模式） ----------
    private int reorderFrom = -1;           // 正被"拿起来"的那个扇区（当前索引）

    // ---------- 右下角那排小按钮：右键按住可整排挪位置 ----------
    private boolean hudDragging;
    private double hudGrabDX, hudGrabDY;    // 按下点相对主按钮中心

    // 每帧按屏幕尺寸更新
    // 右下角那排小按钮，从左到右：调整位置 / 切换界面风格 / 拉黑此键
    //   bxc/byc = 最右（正常=拉黑此键，调整=确认位置）
    //   stx     = 中间（正常=切换界面风格，调整=恢复居中）
    //   axc/cyc = 最左（正常=调整位置）
    // sxc/syc = 屏幕左下角（正常=结束长按，调整=取消调整）
    // mvx/mvy、scx/scy = 调整模式下轮盘右下角的移动把手／缩放把手
    private float cx, cy, rHole, rSplitA, rSplitB, rOuter, bxc, byc, bs, sxc, syc;
    private float axc, cyc, mxc, stx, rCross, crossSize;
    private float mvx, mvy, scx, scy, hb;

    public ConflictWheelScreen(KeyRef ref, List<KeyMapping> bindings) {
        super(Component.translatable("key_conflict_wheel.wheel.title"));
        this.ref = ref;
        this.bindings = new ArrayList<>(bindings);
        this.count = this.bindings.size();
    }

    public KeyRef getRef() {
        return ref;
    }

    /** 松开物理键时是否要忽略（调整位置模式下轮盘被"临时锁定"） */
    public boolean isAdjusting() {
        return adjustMode;
    }

    // ---------- 覆盖层模式 ----------

    /**
     * 以覆盖层方式打开：仍旧按一个界面初始化自己，但<b>不</b>交给 Minecraft.screen。
     * 这样原版不会调用 KeyMapping.releaseAll()，世界与键盘照常运转，轮盘只是画在最上层。
     */
    public void openAsOverlay(Minecraft mc, int guiWidth, int guiHeight) {
        this.overlay = true;
        this.init(mc, guiWidth, guiHeight);
        this.layout();
    }

    /** 已经结算／被取消，可以关掉了 */
    public boolean isFinished() {
        return finished;
    }

    /** 外部（ESC）要求关闭：调整模式下等于取消调整，否则直接取消轮盘 */
    public void requestClose() {
        if (finished) return;
        if (adjustMode) {
            cancelAdjust();
        } else {
            closeWheel();
        }
    }

    /** 只有非覆盖层模式才需要把 this 从 Minecraft.screen 上摘下来 */
    private void closeScreenRef() {
        if (!overlay && this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        // 正用右键挪那排小按钮时界面被关掉了：把挪到的位置存下来，别半途而废
        if (hudDragging) {
            hudDragging = false;
            WheelConfig.get().saveButtons();
        }
        // 调整中途被别的原因关掉（退出世界等）：位置／大小／顺序还原，别留下半截改动
        if (adjustMode && !positionSettled) restoreEntryState();
        // 没结算就被关掉（ESC 等）：这个键还按着的话，要等它真正抬起才允许再弹轮盘
        if (!finished && !adjustMode && ConflictWheelClient.isPhysicallyDown(ref)) {
            ConflictWheelClient.INSTANCE.lockUntilRelease(ref);
        }
    }

    // ---------- 渲染 ----------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (overlay && this.minecraft != null) {
            // 覆盖层没有原版 setScreen 帮忙初始化尺寸，窗口一改大小就得自己跟上
            this.width = this.minecraft.getWindow().getGuiScaledWidth();
            this.height = this.minecraft.getWindow().getGuiScaledHeight();
        }
        layout();
        sampleMouse(mouseX, mouseY);

        if (count <= 0) return;

        Matrix4f pose = graphics.pose().last().pose();
        float twoPi = (float) (Math.PI * 2);
        float base = (float) (-Math.PI / 2);
        float step = twoPi / count;
        float gap = Math.min(0.05f, 3.0f / ((rHole + rOuter) * 0.5f));

        WheelStyle st = style();

        // 压暗背景（调整位置时压得轻一点，方便看清游戏画面里的位置）
        graphics.fill(0, 0, this.width, this.height, adjustMode ? 0x50000000 : st.screenDim);

        // 玻璃风格：用三层同心圆近似一层径向压暗，让细线在天空/雪地这类亮背景上也能看清
        if (st.veil) {
            WheelDraw.ringSector(pose, cx, cy, 0, rOuter * 0.55f, 0, twoPi, 0x22000000);
            WheelDraw.ringSector(pose, cx, cy, rOuter * 0.55f, rOuter * 0.85f, 0, twoPi, 0x14000000);
            WheelDraw.ringSector(pose, cx, cy, rOuter * 0.85f, rOuter + 2.5f, 0, twoPi, 0x0C000000);
        }

        // 底盘
        WheelDraw.ringSector(pose, cx, cy, 0, rOuter + 2.5f, 0, twoPi, st.backdrop);
        if ((st.rim >>> 24) != 0) {
            WheelDraw.ringSector(pose, cx, cy, rOuter + 1.0f, rOuter + 2.5f, 0, twoPi, st.rim);
        }

        // 扇区（内圈=短按带，外圈=长按带）
        for (int i = 0; i < count; i++) {
            float a0 = base + i * step;
            float a1 = a0 + step;
            boolean hot = isHot(i);
            boolean holding = ConflictWheelClient.isHeld(bindings.get(i));
            int innerColor = hot ? st.hotInner : (i % 2 == 0 ? st.innerA : st.innerB);
            int outerColor = holding
                    ? st.holdOuter
                    : (hot ? st.hotOuter : (i % 2 == 0 ? st.outerA : st.outerB));
            WheelDraw.ringSector(pose, cx, cy, rHole, rSplitA, a0 + gap, a1 - gap, innerColor);
            WheelDraw.ringSector(pose, cx, cy, rSplitB, rOuter, a0 + gap, a1 - gap, outerColor);

            // 原版风格：每个扇区两条边（迎光的那条提亮、背光的那条压暗）做出"凸起"的手感
            // 用中性白/黑而不是灰，换任何底色（黄铜、苔绿）都不会脏
            if (st.bevel) {
                float off = Math.min(0.010f, step * 0.10f);
                bevelEdge(pose, rHole, rSplitA, a0 + off, 0x46FFFFFF);
                bevelEdge(pose, rHole, rSplitA, a1 - off, 0x78000000);
                bevelEdge(pose, rSplitB, rOuter, a0 + off, 0x38FFFFFF);
                bevelEdge(pose, rSplitB, rOuter, a1 - off, 0x66000000);
            }

            if (holding) {
                WheelDraw.ringSector(pose, cx, cy, rOuter + 0.5f, rOuter + 3.0f, a0 + gap, a1 - gap, st.holdRim);
            }
        }

        // 中心孔
        WheelDraw.ringSector(pose, cx, cy, 0, rHole - 1.5f, 0, twoPi, st.center);

        // 内/外圈分界环：暗槽 + 一根亮线，保证"短按带 / 长按带"一眼能分开
        if ((st.groove >>> 24) != 0) {
            WheelDraw.ringSector(pose, cx, cy, rSplitA, rSplitB, 0, twoPi, st.groove);
        }
        float midSplit = (rSplitA + rSplitB) * 0.5f;
        float halfW = Math.max(0.5f, st.splitW * 0.5f);
        WheelDraw.ringSector(pose, cx, cy, midSplit - halfW, midSplit + halfW, 0, twoPi, st.split);

        // 扇区之间的暗色分隔线 + 外圈描边
        for (int i = 0; i < count; i++) {
            float a = base + i * step;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            WheelDraw.line(pose,
                    cx + cos * (rHole - 1), cy + sin * (rHole - 1),
                    cx + cos * (rOuter + 2), cy + sin * (rOuter + 2),
                    st.sepW, st.sep);
        }

        if (!adjustMode && ConflictWheelClient.holdsOn(ref) == 0 && (st.rim >>> 24) == 0) {
            WheelDraw.ringSector(pose, cx, cy, rOuter + 2, rOuter + 3.5f, 0, twoPi, 0xFF38384F);
        }

        if (adjustMode) {
            drawAdjustGuides(graphics, pose, base, step);
        }

        drawLabels(graphics, base, step);
        drawCenterHud(graphics);
        if (adjustMode) {
            drawConfirmButton(graphics);
            drawCancelButton(graphics);
            drawCenterButton(graphics);
            drawCrosses(graphics, base, step);
            drawWheelHandles(graphics);
        } else {
            drawAdjustButton(graphics);
            drawStyleButton(graphics);
            drawBlacklistButton(graphics);
            drawStopButton(graphics);
        }
        drawHints(graphics);
        // 提示框：调整模式里给扇区／×／把手用；正常模式里只给这排小按钮用
        if (adjustMode) {
            drawAdjustTooltips(graphics);
            drawReorderGhost(graphics);
        } else {
            drawNormalTooltips(graphics);
        }
        if (hudDragging) {
            drawButtonDragTip(graphics);
        }
    }

    private void layout() {
        int min = Math.min(this.width, this.height);
        float scale = (float) WheelConfig.get().wheelScale;
        // 圆盘不要大于屏幕的 42%，否则"调整位置"根本没法移动
        this.rOuter = Math.min(Math.max(60.0f, min * 0.34f * scale), min * 0.42f);
        // 内圈（短按带）约占半径 2/3，外圈（长按带）约占 1/3，中间是醒目的白色分界环
        this.rHole = rOuter * 0.30f;
        this.rSplitA = rOuter * 0.62f;
        this.rSplitB = rOuter * 0.70f;
        this.bs = Math.max(9.0f, rOuter * 0.15f);
        this.crossSize = Math.max(5.0f, bs * 0.5f);
        this.rCross = rOuter + bs * 0.6f;

        // 中心：屏幕正中略偏上 + 配置里的偏移；留出 × 号与提示文字的空间后夹到屏幕内
        float baseCx = this.width / 2.0f;
        float baseCy = this.height / 2.0f - 4.0f;
        WheelConfig cfg = WheelConfig.get();
        float margin = rOuter + bs * 1.15f;
        this.cx = clampAxis(baseCx + (float) cfg.offsetX, margin, this.width - margin, baseCx);
        this.cy = clampAxis(baseCy + (float) cfg.offsetY, margin + 10.0f, this.height - margin - 22.0f, baseCy);

        // 右下角那排小按钮：默认贴屏幕右下角，玩家可以用右键把它们拖到别处
        //   从左到右：调整位置 / 切换界面风格 / 拉黑此键
        //   调整模式下：中间=恢复居中，最右=确认
        float defX = hudDefaultX(), defY = hudDefaultY();
        this.bxc = clampAxis(defX + (float) cfg.hudDX, bs + 2.0f, this.width - bs - 2.0f, defX);
        this.byc = clampAxis(defY + (float) cfg.hudDY, bs + 2.0f, this.height - bs - 2.0f, defY);
        this.stx = Math.max(this.bxc - bs * 2.5f, bs + 2.0f);
        this.axc = Math.max(this.bxc - bs * 5.0f, bs + 2.0f);
        this.cyc = this.byc;
        this.mxc = this.stx;
        // 屏幕左下角：正常模式=结束该键全部长按（仅在确有长按时出现）；调整模式=取消调整
        this.sxc = bs + 2.0f;
        this.syc = defY;

        // 调整模式：贴在轮盘右下角外侧的两个小把手（跟着轮盘一起动、一起缩放）
        this.hb = Math.max(7.0f, bs * 0.66f);
        float ring = rOuter + bs * 1.5f;
        this.mvx = cx + (float) Math.cos(Math.toRadians(26)) * ring;
        this.mvy = cy + (float) Math.sin(Math.toRadians(26)) * ring;
        this.scx = cx + (float) Math.cos(Math.toRadians(56)) * ring;
        this.scy = cy + (float) Math.sin(Math.toRadians(56)) * ring;
        float pad = hb + 2.0f;
        this.mvx = clampAxis(this.mvx, pad, this.width - pad, this.mvx);
        this.mvy = clampAxis(this.mvy, pad, this.height - pad, this.mvy);
        this.scx = clampAxis(this.scx, pad, this.width - pad, this.scx);
        this.scy = clampAxis(this.scy, pad, this.height - pad, this.scy);
    }

    /** 那排小按钮的默认位置（贴屏幕右下角） */
    private float hudDefaultX() {
        return this.width - bs - 2.0f;
    }

    private float hudDefaultY() {
        return this.height - bs - 15.0f;
    }

    /** 把中心夹在 [lo,hi] 内；屏幕太小时退回默认位置（避免 lo>hi） */
    private static float clampAxis(float v, float lo, float hi, float fallback) {
        if (lo > hi) return fallback;
        return Math.max(lo, Math.min(hi, v));
    }

    private float baseCx() {
        return this.width / 2.0f;
    }

    private float baseCy() {
        return this.height / 2.0f - 4.0f;
    }

    private void sampleMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
        if (adjustMode) {
            // 拖动中（挪轮盘／缩放／拿扇区／挪按钮）不点亮 ×／把手、也不弹扇区提示，免得挡视线
            boolean busy = dragging || scaling || reorderFrom >= 0 || hudDragging;
            int c = busy ? -1 : crossAt(mx, my);
            int h = busy ? -1 : handleAt(mx, my);
            boolean crossChanged = c != hotCross;
            boolean handleChanged = h != hotHandle;
            hotCross = c;
            hotHandle = h;
            int hs = (busy || c >= 0 || h >= 0) ? -1 : sectorUnderMouse(mx, my);
            boolean sectorChanged = hs != hoverSector;
            hoverSector = hs;
            if (adjustFirstSample) {
                adjustFirstSample = false;
            } else if ((crossChanged && c >= 0) || (handleChanged && h >= 0) || (sectorChanged && hs >= 0)) {
                WheelSounds.hover();
            }
            zone = ZONE_BUFFER;
            sector = -1;
            return;
        }
        hotCross = -1;
        hotHandle = -1;
        hoverSector = -1;
        updateZone(mx, my);
    }

    /** 调整模式下：鼠标压在哪个扇区上（内圈／分界环／外圈都算；中心孔、圆外、按钮、把手上不算，返回 -1） */
    private int sectorUnderMouse(double mx, double my) {
        if (count <= 0) return -1;
        if (inConfirmButton(mx, my) || inCenterButton(mx, my) || inCancelButton(mx, my)) return -1;
        if (inMoveHandle(mx, my) || inScaleHandle(mx, my) || inHudGroup(mx, my)) return -1;
        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < rHole || dist > rOuter) return -1;
        return sectorIndexByAngle(dx, dy);
    }

    /** 相对轮盘中心的方向落在第几个扇区（-1 = 算不出） */
    private int sectorIndexByAngle(double dx, double dy) {
        if (count <= 0) return -1;
        double ang = Math.atan2(dy, dx) + Math.PI / 2.0;
        double rel = ((ang % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
        int s = (int) (rel / (Math.PI * 2) * count);
        return Math.max(0, Math.min(count - 1, s));
    }

    /** 拖扇区改顺序时认目标扇区：只要不在中心孔里，圆外也按方向认，拖着更跟手 */
    private int sectorByAngle(double mx, double my) {
        double dx = mx - cx;
        double dy = my - cy;
        if (dx * dx + dy * dy < (rHole * 0.6) * (rHole * 0.6)) return -1;
        return sectorIndexByAngle(dx, dy);
    }

    /** 这个扇区此刻是否"发亮"：正常模式＝鼠标选中的，调整模式＝鼠标悬停的或被拿起来的 */
    private boolean isHot(int i) {
        if (adjustMode) return i == hoverSector || i == reorderFrom;
        return i == sector && (zone == ZONE_SHORT || zone == ZONE_LONG);
    }

    /** 依据鼠标坐标更新当前所在圈层/扇区（渲染和松开结算都调用这里） */
    private void updateZone(double mx, double my) {
        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        int newZone;
        int newSector = -1;
        if (inBlacklistButton(mx, my) || inStopButton(mx, my) || inAdjustButton(mx, my)
                || inStyleButton(mx, my)) {
            newZone = ZONE_BUFFER;
        } else if (dist <= rHole) {
            newZone = ZONE_CENTER;
        } else if (dist < rSplitA) {
            newZone = ZONE_SHORT;
        } else if (dist <= rSplitB) {
            newZone = ZONE_BUFFER;
        } else if (dist <= rOuter) {
            newZone = ZONE_LONG;
        } else {
            newZone = ZONE_OUTSIDE;
        }
        if (newZone == ZONE_SHORT || newZone == ZONE_LONG) {
            double ang = Math.atan2(dy, dx) + Math.PI / 2.0;
            double rel = ((ang % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
            newSector = (int) (rel / (Math.PI * 2) * count);
            if (newSector >= count) newSector = count - 1;
            if (newSector < 0) newSector = 0;
        }

        boolean changed = newZone != zone || newSector != sector;
        if (changed) {
            zone = newZone;
            sector = newSector;
            if (!firstSample && (zone == ZONE_SHORT || zone == ZONE_LONG)) {
                WheelSounds.hover();
            }
        }
        firstSample = false;
    }

    /**
     * 玩家松开物理键时由 ConflictWheelClient 调用：
     * 以此刻鼠标所在圈层结算——内圈短按、外圈长按开关、中心孔取消或收摊。
     * 调整位置模式下轮盘被临时锁定，不做任何结算。
     */
    public void fireOnRelease() {
        if (finished || adjustMode) return;
        layout();
        updateZone(lastMouseX, lastMouseY);
        if (sector >= 0 && (zone == ZONE_SHORT || zone == ZONE_LONG)) {
            fire(zone, sector);
        } else {
            finished = true;
            // 中心孔：该键上有长按在跑就把它们结束掉；其余地方松开＝纯取消
            if (zone == ZONE_CENTER) ConflictWheelClient.INSTANCE.stopHoldsOn(ref);
            closeScreenRef();
        }
    }

    private void fire(int zone, int sector) {
        if (finished) return;
        finished = true;
        KeyMapping target = bindings.get(sector);
        ConflictWheelClient client = ConflictWheelClient.INSTANCE;
        closeScreenRef();
        if (zone == ZONE_SHORT) {
            client.triggerShortPress(ref, target);
        } else {
            client.toggleLongHold(ref, target);
        }
        WheelSounds.confirm();
    }

    // ---------- 鼠标 ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (finished) return true;
        layout();

        // 右键按在右下角那排小按钮上＝把它们拖走（不算"取消／关闭轮盘"）
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && inHudGroup(mx, my)) {
            hudDragging = true;
            hudGrabDX = mx - bxc;
            hudGrabDY = my - byc;
            WheelSounds.hover();
            return true;
        }

        if (adjustMode) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                cancelAdjust();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (inConfirmButton(mx, my)) {
                    confirmAdjust();
                    return true;
                }
                if (inCancelButton(mx, my)) {
                    cancelAdjust();
                    return true;
                }
                if (inCenterButton(mx, my)) {
                    snapToCenter();
                    return true;
                }
                int c = crossAt(mx, my);
                if (c >= 0) {
                    unbindSector(c);
                    return true;
                }
                if (inMoveHandle(mx, my)) {
                    // 移动把手：整块轮盘跟着手走
                    dragging = true;
                    grabDX = mx - cx;
                    grabDY = my - cy;
                    return true;
                }
                if (inScaleHandle(mx, my)) {
                    // 缩放把手：按住上下拖，向上＝放大
                    scaling = true;
                    scaleStartY = my;
                    scaleStartValue = WheelConfig.get().wheelScale;
                    return true;
                }
                int s = sectorUnderMouse(mx, my);
                if (s >= 0) {
                    // 按住某个功能＝把它"拿起来"，拖到别的扇区就换顺序
                    reorderFrom = s;
                    return true;
                }
                // 其余地方（中心孔、圆外）：抓住轮盘开始拖动
                dragging = true;
                grabDX = mx - cx;
                grabDY = my - cy;
                return true;
            }
            return true;
        }

        if (button == 0 && inBlacklistButton(mx, my)) {
            finished = true;
            WheelConfig.get().blacklist(ref);
            ConflictWheelClient.INSTANCE.clearHold(ref);
            WheelSounds.blacklist();
            tell("key_conflict_wheel.msg.blacklisted", ref.display());
            closeScreenRef();
            return true;
        }
        if (button == 0 && inStyleButton(mx, my)) {
            WheelStyle next = WheelConfig.get().cycleStyle();
            WheelSounds.openMenu();
            tell("key_conflict_wheel.style.switched", Component.translatable(next.langKey()));
            return true;
        }
        if (button == 0 && inAdjustButton(mx, my)) {
            enterAdjust();
            return true;
        }
        if (button == 0 && inStopButton(mx, my)) {
            finished = true;
            ConflictWheelClient.INSTANCE.stopHoldsOn(ref);
            closeScreenRef();
            return true;
        }
        if (button == 1) {
            finished = true;
            closeScreenRef();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        // 挪那排小按钮
        if (hudDragging && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            WheelConfig cfg = WheelConfig.get();
            layout();
            cfg.moveButtonsTo(mx - hudGrabDX - hudDefaultX(), my - hudGrabDY - hudDefaultY());
            layout();
            return true;
        }

        if (!adjustMode || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(mx, my, button, dragX, dragY);
        }

        if (scaling) {
            double dy = scaleStartY - my;                       // 往上拖＝变大
            WheelConfig.get().scaleWheelTo(scaleStartValue + dy * 0.0042);
            layout();
            return true;
        }

        if (reorderFrom >= 0) {
            int to = sectorByAngle(mx, my);
            if (to >= 0 && to < count && to != reorderFrom) {
                KeyMapping moved = bindings.remove(reorderFrom);
                bindings.add(to, moved);
                reorderFrom = to;
                hoverSector = -1;
                hotCross = -1;
                WheelSounds.hover();
            }
            return true;
        }

        if (dragging) {
            WheelConfig cfg = WheelConfig.get();
            double nx = mx - grabDX - baseCx();
            double ny = my - grabDY - baseCy();
            // 靠近正中时吸附，方便对齐
            if (Math.abs(nx) < 4.0 && Math.abs(ny) < 4.0) {
                nx = 0.0;
                ny = 0.0;
            }
            cfg.moveWheelTo(nx, ny);
            layout();
            // 再把"贴到屏幕边缘后实际显示的位置"回填，并更新抓取点：
            // 这样拖出边界再拖回来不会突然跳一下，保存的也始终是可见位置
            cfg.moveWheelTo(cx - baseCx(), cy - baseCy());
            grabDX = mx - cx;
            grabDY = my - cy;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hudDragging) {
            hudDragging = false;
            WheelConfig.get().saveButtons();
            WheelSounds.confirm();
            tell("key_conflict_wheel.msg.btn_pos_saved");
            return true;
        }
        if (adjustMode && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            if (scaling) {
                scaling = false;
                WheelSounds.confirm();
            }
            if (reorderFrom >= 0) {
                reorderFrom = -1;
                WheelSounds.endHold();
                layout();
            }
        }
        return super.mouseReleased(mx, my, button);
    }

    /** 滚轮滚在缩放把手上：一格 5%，微调更省事 */
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (adjustMode && !hudDragging && inScaleHandle(mx, my)) {
            WheelConfig cfg = WheelConfig.get();
            cfg.scaleWheelTo(cfg.wheelScale + (scrollY > 0 ? 0.05 : -0.05));
            layout();
            WheelSounds.hover();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    // ---------- 调整位置模式 ----------

    private void enterAdjust() {
        adjustMode = true;
        positionSettled = false;
        dragging = false;
        scaling = false;
        reorderFrom = -1;
        hotHandle = -1;
        WheelConfig cfg = WheelConfig.get();
        entryOffsetX = cfg.offsetX;
        entryOffsetY = cfg.offsetY;
        entryScale = cfg.wheelScale;
        entryOrder = new ArrayList<>(bindings.size());
        for (KeyMapping mapping : bindings) entryOrder.add(mapping.getName());
        zone = ZONE_BUFFER;
        sector = -1;
        hoverSector = -1;
        adjustFirstSample = true;
        WheelSounds.open();
    }

    private void confirmAdjust() {
        WheelConfig cfg = WheelConfig.get();
        // 落盘的是"当前真正显示出来的位置"，与屏幕夹取结果一致
        cfg.moveWheelTo(cx - baseCx(), cy - baseCy());
        cfg.rememberOrder(ref, bindings);
        cfg.savePosition();
        positionSettled = true;
        adjustMode = false;
        dragging = false;
        scaling = false;
        reorderFrom = -1;
        WheelSounds.confirm();
        tell("key_conflict_wheel.msg.pos_saved");
        closeWheel();
    }

    private void cancelAdjust() {
        restoreEntryState();
        positionSettled = true;
        adjustMode = false;
        dragging = false;
        scaling = false;
        reorderFrom = -1;
        WheelSounds.blacklist();
        tell("key_conflict_wheel.msg.pos_reverted");
        closeWheel();
    }

    private void snapToCenter() {
        WheelConfig.get().moveWheelTo(0.0, 0.0);
        layout();
        WheelSounds.hover();
    }

    /** 取消／中途被关闭：位置、大小、扇区顺序全部回到进调整模式之前 */
    private void restoreEntryState() {
        WheelConfig cfg = WheelConfig.get();
        cfg.moveWheelTo(entryOffsetX, entryOffsetY);
        cfg.scaleWheelTo(entryScale);
        if (!entryOrder.isEmpty()) {
            Map<String, Integer> rank = new HashMap<>();
            for (int i = 0; i < entryOrder.size(); i++) rank.putIfAbsent(entryOrder.get(i), i);
            bindings.sort(Comparator
                    .comparingInt((KeyMapping m) -> rank.getOrDefault(m.getName(), Integer.MAX_VALUE))
                    .thenComparing(KeyMapping::getName));
            count = bindings.size();
        }
        reorderFrom = -1;
        hoverSector = -1;
        hotCross = -1;
    }

    private void closeWheel() {
        finished = true;
        // 键还按着才需要等它抬起（否则会白白吞掉玩家下一次按键）
        if (ConflictWheelClient.isPhysicallyDown(ref)) {
            ConflictWheelClient.INSTANCE.lockUntilRelease(ref);
        }
        closeScreenRef();
    }

    /** 点扇区外的 ×：取消这个功能的快捷键（真的解绑，会在原版按键设置里显示"未绑定"） */
    private void unbindSector(int index) {
        if (index < 0 || index >= count) return;
        KeyMapping target = bindings.get(index);
        Component name = target.getDisplayName().copy();
        ConflictWheelClient.INSTANCE.unbindBinding(ref, target);
        bindings.remove(index);
        count = bindings.size();
        hotCross = -1;
        sector = -1;
        hoverSector = -1;
        reorderFrom = -1;
        firstSample = true;
        WheelSounds.blacklist();
        if (count == 0) {
            // 这个键上的快捷键全被取消了：轮盘没内容可显示，保存当前位置收摊
            WheelConfig cfg = WheelConfig.get();
            cfg.moveWheelTo(cx - baseCx(), cy - baseCy());
            cfg.rememberOrder(ref, bindings);
            cfg.savePosition();
            adjustMode = false;
            tell("key_conflict_wheel.msg.wheel_empty", ref.display());
            closeWheel();
            return;
        }
        tell("key_conflict_wheel.msg.unbound", name, ref.display());
        if (count == 1) {
            tell("key_conflict_wheel.msg.no_conflict_anymore", ref.display());
        }
    }

    // ---------- 命中判定 ----------

    private boolean inBlacklistButton(double mx, double my) {
        return !adjustMode && Math.abs(mx - bxc) <= bs && Math.abs(my - byc) <= bs;
    }

    private boolean inAdjustButton(double mx, double my) {
        return !adjustMode && Math.abs(mx - axc) <= bs && Math.abs(my - cyc) <= bs;
    }

    private boolean inStyleButton(double mx, double my) {
        return !adjustMode && Math.abs(mx - stx) <= bs && Math.abs(my - byc) <= bs;
    }

    private boolean inConfirmButton(double mx, double my) {
        return adjustMode && Math.abs(mx - bxc) <= bs && Math.abs(my - byc) <= bs;
    }

    private boolean inCancelButton(double mx, double my) {
        return adjustMode && Math.abs(mx - sxc) <= bs && Math.abs(my - syc) <= bs;
    }

    private boolean inCenterButton(double mx, double my) {
        return adjustMode && Math.abs(mx - mxc) <= bs && Math.abs(my - cyc) <= bs;
    }

    private boolean inStopButton(double mx, double my) {
        return !adjustMode && ConflictWheelClient.holdsOn(ref) > 0
                && Math.abs(mx - sxc) <= bs && Math.abs(my - syc) <= bs;
    }

    // ---------- 轮盘右下角的两个小把手（仅调整模式） ----------

    /** 移动把手：按住拖动整个轮盘 */
    private boolean inMoveHandle(double mx, double my) {
        return adjustMode && Math.abs(mx - mvx) <= hb * 1.15f && Math.abs(my - mvy) <= hb * 1.15f;
    }

    /** 缩放把手：按住上下拖放大缩小（滚轮也认） */
    private boolean inScaleHandle(double mx, double my) {
        return adjustMode && Math.abs(mx - scx) <= hb * 1.15f && Math.abs(my - scy) <= hb * 1.15f;
    }

    /** 悬停命中了哪个把手：1=移动 2=缩放，都没命中 -1（× 优先于把手，免得误触） */
    private int handleAt(double mx, double my) {
        if (!adjustMode || crossAt(mx, my) >= 0) return -1;
        if (inScaleHandle(mx, my)) return 2;
        if (inMoveHandle(mx, my)) return 1;
        return -1;
    }

    /** 右下角那排小按钮（含下面那行小字）的整体范围：右键按住它就能挪位置 */
    private boolean inHudGroup(double mx, double my) {
        double x0 = Math.min(axc, bxc) - bs - 3.0;
        double x1 = Math.max(axc, bxc) + bs + 3.0;
        double y0 = cyc - bs - 3.0;
        double y1 = cyc + bs + this.font.lineHeight + 8.0;
        return mx >= x0 && mx <= x1 && my >= y0 && my <= y1;
    }

    /** 鼠标落在第几个 × 上（没有返回 -1） */
    private int crossAt(double mx, double my) {
        if (!adjustMode || count <= 0) return -1;
        float base = (float) (-Math.PI / 2);
        float step = (float) (Math.PI * 2) / count;
        for (int i = 0; i < count; i++) {
            float mid = base + (i + 0.5f) * step;
            float x = cx + (float) Math.cos(mid) * rCross;
            float y = cy + (float) Math.sin(mid) * rCross;
            if (Math.abs(mx - x) <= crossSize * 1.2f && Math.abs(my - y) <= crossSize * 1.2f) return i;
        }
        return -1;
    }

    private float crossX(int i) {
        float mid = (float) (-Math.PI / 2) + (i + 0.5f) * ((float) (Math.PI * 2) / count);
        return cx + (float) Math.cos(mid) * rCross;
    }

    private float crossY(int i) {
        float mid = (float) (-Math.PI / 2) + (i + 0.5f) * ((float) (Math.PI * 2) / count);
        return cy + (float) Math.sin(mid) * rCross;
    }

    // ---------- 绘制小件 ----------

    // ---------- 轮盘右下角的两个小把手 ----------

    /**
     * 调整模式专用：贴在轮盘右下角外侧的两个小框。
     * 左边那个按住拖动＝整块轮盘跟着手走；右边那个按住上下拖＝放大缩小（滚轮也能微调）。
     */
    private void drawWheelHandles(GuiGraphics graphics) {
        Matrix4f pose = graphics.pose().last().pose();
        // 与轮盘连两根短线，表明这俩是"挂在轮盘上"的
        WheelDraw.line(pose, cx + (mvx - cx) * 0.55f, cy + (mvy - cy) * 0.55f, mvx, mvy, 1.2f, 0xFF4A4A5C);
        WheelDraw.line(pose, cx + (scx - cx) * 0.55f, cy + (scy - cy) * 0.55f, scx, scy, 1.2f, 0xFF4A4A5C);

        boolean mvHot = hotHandle == 1 || dragging;
        fillHandle(graphics, mvx, mvy, mvHot ? 0xFF2E7FA8 : 0xFF3A3A4A, mvHot ? 0xFF1E5C7D : 0xFF23232E);
        drawMoveIcon(graphics, mvx, mvy, mvHot ? 0xFFFFFFFF : 0xFFD6F0FF, hb * 0.6f);
        handleLabel(graphics, "key_conflict_wheel.wheel.handle_move", mvHot ? 0xFFFFE08A : 0xFFB0B0C0, mvx, mvy);

        boolean scHot = hotHandle == 2 || scaling;
        fillHandle(graphics, scx, scy, scHot ? 0xFF9A6E2E : 0xFF3A3A4A, scHot ? 0xFF7D5A1E : 0xFF23232E);
        drawZoomIcon(graphics, scx, scy, scHot ? 0xFFFFFFFF : 0xFFFFE6BF, hb * 0.62f);
        handleLabel(graphics, "key_conflict_wheel.wheel.handle_scale", scHot ? 0xFFFFE08A : 0xFFB0B0C0, scx, scy);
        // 当前大小百分比：缩放到哪一档一眼看得见
        String pct = (int) Math.round(WheelConfig.get().wheelScale * 100) + "%";
        graphics.drawCenteredString(this.font, Component.literal(pct)
                        .withStyle(scaling ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                (int) scx, (int) (scy + hb + 10), 0xFFFFFFFF);
    }

    private void fillHandle(GuiGraphics graphics, float x, float y, int border, int inner) {
        int x0 = (int) (x - hb), y0 = (int) (y - hb), x1 = (int) (x + hb), y1 = (int) (y + hb);
        graphics.fill(x0, y0, x1 + 1, y1 + 1, border);
        graphics.fill(x0 + 1, y0 + 1, x1, y1, inner);
    }

    private void handleLabel(GuiGraphics graphics, String key, int color, float x, float y) {
        Component label = Component.translatable(key).withStyle(ChatFormatting.GRAY);
        int half = this.font.width(label) / 2;
        int tx = (int) Math.max(half + 2.0f, Math.min(x, this.width - half - 2.0f));
        graphics.drawCenteredString(this.font, label, tx, (int) (y + hb + 1), color);
    }

    /** 放大镜图标：圆环 + 手柄 + 中间的加号 */
    private void drawZoomIcon(GuiGraphics graphics, float x, float y, int color, float k) {
        Matrix4f pose = graphics.pose().last().pose();
        float r = k * 0.62f;
        int steps = 12;
        float px = x - k * 0.16f, py = y - k * 0.16f;
        for (int i = 0; i < steps; i++) {
            float a0 = (float) (Math.PI * 2 * i / steps), a1 = (float) (Math.PI * 2 * (i + 1) / steps);
            WheelDraw.line(pose, px + (float) Math.cos(a0) * r, py + (float) Math.sin(a0) * r,
                    px + (float) Math.cos(a1) * r, py + (float) Math.sin(a1) * r, 1.6f, color);
        }
        WheelDraw.line(pose, px + r * 0.72f, py + r * 0.72f, x + k * 0.9f, y + k * 0.9f, 2.4f, color);
        WheelDraw.line(pose, px - r * 0.55f, py, px + r * 0.55f, py, 1.5f, color);
        WheelDraw.line(pose, px, py - r * 0.55f, px, py + r * 0.55f, 1.5f, color);
    }

    /** 按住扇区拖动时，光标旁边跟着一个小框：告诉你手上拿的是哪个功能 */
    private void drawReorderGhost(GuiGraphics graphics) {
        if (reorderFrom < 0 || reorderFrom >= count) return;
        KeyMapping m = bindings.get(reorderFrom);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable("key_conflict_wheel.wheel.tip_reorder_now")
                .withStyle(ChatFormatting.AQUA).getVisualOrderText());
        lines.addAll(this.font.split(m.getDisplayName().copy().withStyle(ChatFormatting.WHITE), 150));
        lines.add(Component.translatable("key_conflict_wheel.wheel.tip_reorder_pos",
                reorderFrom + 1, count).withStyle(ChatFormatting.GRAY).getVisualOrderText());
        boxTooltip(graphics, lines, (int) lastMouseX, (int) lastMouseY);
        // 从拿起的扇区拉一根线到手，视觉上连着
        Matrix4f pose = graphics.pose().last().pose();
        float step = (float) (Math.PI * 2) / count;
        float mid = (float) (-Math.PI / 2) + (reorderFrom + 0.5f) * step;
        WheelDraw.line(pose, cx + (float) Math.cos(mid) * rOuter, cy + (float) Math.sin(mid) * rOuter,
                (float) lastMouseX, (float) lastMouseY, 1.4f, 0xAA8FE8FF);
    }

    /** 调整模式下的辅助线：屏幕正中画个十字标记，轮盘外圈描个亮边 */
    private void drawAdjustGuides(GuiGraphics graphics, Matrix4f pose, float base, float step) {
        float bx = baseCx(), by = baseCy();
        boolean centered = Math.abs(cx - bx) < 2.0f && Math.abs(cy - by) < 2.0f;
        int c = centered ? 0x66FFFFFF : 0xAA66E0FF;
        WheelDraw.line(pose, bx - 7, by, bx + 7, by, 1.6f, c);
        WheelDraw.line(pose, bx, by - 7, bx, by + 7, 1.6f, c);
        // × 号所在的引导环
        WheelDraw.ringSector(pose, cx, cy, rOuter + 2.0f, rOuter + 3.0f, 0, (float) (Math.PI * 2), 0xFF2B2B3D);
    }

    private void drawLabels(GuiGraphics graphics, float base, float step) {
        WheelStyle st = style();
        float labelR = (rHole + rSplitA) * 0.5f;
        float maxW = Math.max(26.0f, step * labelR * 0.94f - 4.0f);
        // 统一用原字号：宁可截断成 "…" ，也不要把字缩小——
        // 缩过的小字在游戏里根本看不清，还不如少显示几个字。
        for (int i = 0; i < count; i++) {
            float mid = base + (i + 0.5f) * step;
            int lx = (int) (cx + Math.cos(mid) * labelR);
            int ly = (int) (cy + Math.sin(mid) * labelR);
            String prefix = ConflictWheelClient.isHeld(bindings.get(i)) ? "◆" : "";
            String text = elide(prefix + bindings.get(i).getDisplayName().getString(), maxW);
            graphics.drawCenteredString(this.font, text, lx, ly - this.font.lineHeight / 2,
                    isHot(i) ? st.textHot : st.text);
        }
    }

    // ---------- 提示框（仅调整位置模式） ----------

    /** 鼠标悬停在扇区或外圈 × 上时，跟着光标显示整块名字（小扇区里字被截断也能看全） */
    private void drawAdjustTooltips(GuiGraphics graphics) {
        // 正在拖动（挪轮盘／缩放／拿扇区／挪按钮）时不弹这些提示；拿扇区的跟随框另有专门的
        if (dragging || scaling || reorderFrom >= 0 || hudDragging) return;
        // 右下角那排小按钮：先给按钮本体的说明
        String buttonTip = null;
        ChatFormatting buttonColor = ChatFormatting.GRAY;
        if (inConfirmButton(lastMouseX, lastMouseY)) {
            buttonTip = "key_conflict_wheel.wheel.tip_confirm";
            buttonColor = ChatFormatting.GREEN;
        } else if (inCancelButton(lastMouseX, lastMouseY)) {
            buttonTip = "key_conflict_wheel.wheel.tip_cancel";
            buttonColor = ChatFormatting.GRAY;
        } else if (inCenterButton(lastMouseX, lastMouseY)) {
            buttonTip = "key_conflict_wheel.wheel.tip_center";
            buttonColor = ChatFormatting.AQUA;
        }
        if (buttonTip != null) {
            boxTooltip(graphics, List.of(Component.translatable(buttonTip)
                    .withStyle(buttonColor).getVisualOrderText()), (int) lastMouseX, (int) lastMouseY);
            return;
        }
        if (hotHandle == 1) {
            boxTooltip(graphics, List.of(
                    Component.translatable("key_conflict_wheel.wheel.handle_move")
                            .withStyle(ChatFormatting.AQUA).getVisualOrderText(),
                    Component.translatable("key_conflict_wheel.wheel.tip_handle_move")
                            .withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                    (int) lastMouseX, (int) lastMouseY);
            return;
        }
        if (hotHandle == 2) {
            boxTooltip(graphics, List.of(
                    Component.translatable("key_conflict_wheel.wheel.handle_scale")
                            .withStyle(ChatFormatting.GOLD).getVisualOrderText(),
                    Component.translatable("key_conflict_wheel.wheel.tip_handle_scale",
                            (int) Math.round(WheelConfig.get().wheelScale * 100))
                            .withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                    (int) lastMouseX, (int) lastMouseY);
            return;
        }
        int idx = hotCross >= 0 ? hotCross : hoverSector;
        if (idx < 0 || idx >= count) return;
        KeyMapping m = bindings.get(idx);
        int maxW = Math.min(170, this.width - 50);
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (hotCross >= 0) {
            lines.add(Component.translatable("key_conflict_wheel.wheel.tip_unbind")
                    .withStyle(ChatFormatting.RED).getVisualOrderText());
        } else {
            lines.add(Component.translatable("key_conflict_wheel.wheel.tip_reorder",
                            idx + 1, count)
                    .withStyle(ChatFormatting.LIGHT_PURPLE).getVisualOrderText());
        }
        lines.addAll(this.font.split(m.getDisplayName().copy().withStyle(ChatFormatting.WHITE), maxW));
        lines.addAll(this.font.split(Component.translatable("key_conflict_wheel.wheel.tip_category",
                Component.translatable(m.getCategory()).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY), maxW));
        if (ConflictWheelClient.isHeld(m)) {
            lines.add(Component.translatable("key_conflict_wheel.wheel.tip_holding")
                    .withStyle(ChatFormatting.GOLD).getVisualOrderText());
        }
        boxTooltip(graphics, lines, (int) lastMouseX, (int) lastMouseY);
    }

    // ---------- 提示框（正常模式：右下角那排小按钮） ----------

    /** 鼠标停在这排小按钮上：说明它是干什么的，并告诉玩家能按住右键把它拖走 */
    private void drawNormalTooltips(GuiGraphics graphics) {
        if (hudDragging) return;   // 拖动时另有跟手的提示框
        String head = null, body = null;
        if (inBlacklistButton(lastMouseX, lastMouseY)) {
            head = "key_conflict_wheel.wheel.blacklist_btn";
            body = "key_conflict_wheel.wheel.tip_blacklist";
        } else if (inAdjustButton(lastMouseX, lastMouseY)) {
            head = "key_conflict_wheel.wheel.adjust_btn";
            body = "key_conflict_wheel.wheel.tip_adjust";
        } else if (inStyleButton(lastMouseX, lastMouseY)) {
            head = "key_conflict_wheel.wheel.style_btn";
            body = "key_conflict_wheel.wheel.tip_style";
        } else if (inStopButton(lastMouseX, lastMouseY)) {
            head = "key_conflict_wheel.wheel.stop_btn";
            body = "key_conflict_wheel.wheel.tip_stop";
        }
        if (head == null) return;
        int maxW = Math.min(160, this.width - 50);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(head).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        lines.addAll(this.font.split(Component.translatable(body).withStyle(ChatFormatting.GRAY), maxW));
        if (!inStopButton(lastMouseX, lastMouseY)) {
            lines.add(Component.translatable("key_conflict_wheel.wheel.tip_drag_buttons")
                    .withStyle(ChatFormatting.AQUA).getVisualOrderText());
        }
        boxTooltip(graphics, lines, (int) lastMouseX, (int) lastMouseY);
    }

    /** 右键按住挪那排小按钮时，跟手显示挪到哪儿了（松手即保存） */
    private void drawButtonDragTip(GuiGraphics graphics) {
        WheelConfig cfg = WheelConfig.get();
        boxTooltip(graphics, List.of(
                Component.translatable("key_conflict_wheel.wheel.tip_dragging")
                        .withStyle(ChatFormatting.AQUA).getVisualOrderText(),
                Component.translatable("key_conflict_wheel.wheel.tip_drag_offset",
                                (int) cfg.hudDX, (int) cfg.hudDY)
                        .withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                (int) lastMouseX, (int) lastMouseY);
    }

    /** 手画的原版风提示框：深色底＋亮边框，贴屏幕边自动翻方向 */
    private void boxTooltip(GuiGraphics graphics, List<FormattedCharSequence> lines, int mx, int my) {
        int w = 0;
        for (FormattedCharSequence s : lines) w = Math.max(w, this.font.width(s));
        int lh = this.font.lineHeight + 2;
        int h = lines.size() * lh + 1;
        int x = mx + 14;
        int y = my - h / 2;
        if (x + w + 8 > this.width - 2) x = mx - w - 16;
        if (x < 3) x = 3;
        if (y + h + 5 > this.height - 2) y = this.height - h - 8;
        if (y < 3) y = 3;
        graphics.fill(x, y, x + w + 7, y + h + 5, 0xF20E0E16);
        graphics.fill(x, y, x + w + 7, y + 1, 0xFF8A8AB4);
        graphics.fill(x, y + h + 4, x + w + 7, y + h + 5, 0xFF3C3C4A);
        graphics.fill(x, y, x + 1, y + h + 5, 0xFF62627E);
        graphics.fill(x + w + 6, y, x + w + 7, y + h + 5, 0xFF62627E);
        int yy = y + 3;
        for (FormattedCharSequence s : lines) {
            graphics.drawString(this.font, s, x + 4, yy, 0xFFFFFF, true);
            yy += lh;
        }
    }

    private void drawCenterHud(GuiGraphics graphics) {
        int half = this.font.lineHeight / 2;
        graphics.drawCenteredString(this.font,
                ref.display().copy().withStyle(adjustMode ? ChatFormatting.AQUA : ChatFormatting.YELLOW),
                (int) cx, (int) cy - half - 5, 0xFFFFFF);
        int held = ConflictWheelClient.holdsOn(ref);
        Component sub;
        if (adjustMode) {
            sub = Component.translatable("key_conflict_wheel.wheel.adjusting", count)
                    .withStyle(ChatFormatting.AQUA);
        } else {
            sub = held > 0
                    ? Component.translatable("key_conflict_wheel.wheel.holding_n", held)
                            .withStyle(ChatFormatting.GOLD)
                    : Component.translatable("key_conflict_wheel.wheel.count", count).withStyle(ChatFormatting.GRAY);
        }
        graphics.drawCenteredString(this.font, sub, (int) cx, (int) cy - half + 6, 0xFFFFFF);
    }

    /** 右下角"靠左那个"按钮的标签：太宽时整体往左挪，避免压到右边按钮的标签 */
    private void labelUnderLeft(GuiGraphics graphics, String key, ChatFormatting color,
                                float x, float y, String rightKey, float rightX) {
        Component label = Component.translatable(key).withStyle(color);
        int half = this.font.width(label) / 2;
        int rightHalf = this.font.width(Component.translatable(rightKey)) / 2;
        float tx = Math.min(x, rightX - rightHalf - half - 2.0f);
        tx = Math.max(half + 2.0f, tx);
        int y1 = (int) (y + bs);
        graphics.drawCenteredString(this.font, label, (int) tx, y1 + 3, 0xFFFFFF);
    }

    private void drawBlacklistButton(GuiGraphics graphics) {
        boolean hover = inBlacklistButton(lastMouseX, lastMouseY);
        fillButton(graphics, bxc, byc, hover ? 0xFFB33A3A : 0xFF3A3A4A, hover ? 0xFF8C2525 : 0xFF23232E);
        // "斜线禁止"图标：两条对角线
        Matrix4f pose = graphics.pose().last().pose();
        float inset = bs * 0.45f;
        WheelDraw.line(pose, bxc - inset, byc - inset, bxc + inset, byc + inset, 2.4f, 0xFFFFF0F0);
        WheelDraw.line(pose, bxc - inset, byc + inset, bxc + inset, byc - inset, 2.4f, 0xFFFFF0F0);
        labelUnder(graphics, "key_conflict_wheel.wheel.blacklist_btn", ChatFormatting.RED, bxc, byc);
    }

    private void drawStyleButton(GuiGraphics graphics) {
        boolean hover = inStyleButton(lastMouseX, lastMouseY);
        fillButton(graphics, stx, byc, hover ? 0xFF5C4A8A : 0xFF3A3A4A, hover ? 0xFF42346B : 0xFF23232E);
        // "半边圆"图标：主题／配色的通用符号
        Matrix4f pose = graphics.pose().last().pose();
        float rr = bs * 0.46f;
        float twoPi = (float) (Math.PI * 2);
        WheelDraw.ringSector(pose, stx, byc, rr - 1.5f, rr, 0, twoPi, 0xFFE8E0FF);
        WheelDraw.ringSector(pose, stx, byc, 0, rr - 1.5f,
                -(float) Math.PI / 2, (float) Math.PI / 2, 0xFFE8E0FF);
        labelUnderLeft(graphics, "key_conflict_wheel.wheel.style_btn", ChatFormatting.LIGHT_PURPLE,
                stx, byc, "key_conflict_wheel.wheel.blacklist_btn", bxc);
    }

    private void drawAdjustButton(GuiGraphics graphics) {
        boolean hover = inAdjustButton(lastMouseX, lastMouseY);
        fillButton(graphics, axc, cyc, hover ? 0xFF2E7FA8 : 0xFF3A3A4A, hover ? 0xFF1E5C7D : 0xFF23232E);
        // "四向移动"图标
        drawMoveIcon(graphics, axc, cyc, hover ? 0xFFFFFF : 0xFFD6F0FF, bs * 0.62f);
        labelUnderLeft(graphics, "key_conflict_wheel.wheel.adjust_btn", ChatFormatting.AQUA,
                axc, cyc, "key_conflict_wheel.wheel.style_btn", stx);
    }

    private void drawConfirmButton(GuiGraphics graphics) {
        boolean hover = inConfirmButton(lastMouseX, lastMouseY);
        fillButton(graphics, bxc, byc, hover ? 0xFF3FA85A : 0xFF3A4A3E, hover ? 0xFF2C7D3F : 0xFF232E26);
        // "对勾"图标
        Matrix4f pose = graphics.pose().last().pose();
        float k = bs * 0.5f;
        WheelDraw.line(pose, bxc - k, byc + k * 0.15f, bxc - k * 0.25f, byc + k * 0.85f, 2.6f, 0xFFEFFFEF);
        WheelDraw.line(pose, bxc - k * 0.25f, byc + k * 0.85f, bxc + k, byc - k * 0.75f, 2.6f, 0xFFEFFFEF);
        labelUnder(graphics, "key_conflict_wheel.wheel.confirm_btn", ChatFormatting.GREEN, bxc, byc);
    }

    private void drawCancelButton(GuiGraphics graphics) {
        boolean hover = inCancelButton(lastMouseX, lastMouseY);
        fillButton(graphics, sxc, syc, hover ? 0xFF8A8A9A : 0xFF3A3A4A, hover ? 0xFF5A5A68 : 0xFF23232E);
        Matrix4f pose = graphics.pose().last().pose();
        float inset = bs * 0.42f;
        WheelDraw.line(pose, sxc - inset, syc - inset, sxc + inset, syc + inset, 2.4f, 0xFFFFE8E8);
        WheelDraw.line(pose, sxc - inset, syc + inset, sxc + inset, syc - inset, 2.4f, 0xFFFFE8E8);
        labelUnder(graphics, "key_conflict_wheel.wheel.cancel_btn", ChatFormatting.GRAY, sxc, syc);
    }

    private void drawCenterButton(GuiGraphics graphics) {
        boolean hover = inCenterButton(lastMouseX, lastMouseY);
        fillButton(graphics, mxc, cyc, hover ? 0xFF2E7FA8 : 0xFF3A3A4A, hover ? 0xFF1E5C7D : 0xFF23232E);
        // "回到中心"图标：外框 + 中心点
        Matrix4f pose = graphics.pose().last().pose();
        float k = bs * 0.5f;
        WheelDraw.line(pose, mxc - k, cyc - k, mxc + k, cyc - k, 1.8f, 0xFFD6F0FF);
        WheelDraw.line(pose, mxc - k, cyc + k, mxc + k, cyc + k, 1.8f, 0xFFD6F0FF);
        WheelDraw.line(pose, mxc - k, cyc - k, mxc - k, cyc + k, 1.8f, 0xFFD6F0FF);
        WheelDraw.line(pose, mxc + k, cyc - k, mxc + k, cyc + k, 1.8f, 0xFFD6F0FF);
        graphics.fill((int) (mxc - 1.5f), (int) (cyc - 1.5f), (int) (mxc + 2.0f), (int) (cyc + 2.0f), 0xFFFFFFFF);
        labelUnderLeft(graphics, "key_conflict_wheel.wheel.center_btn", ChatFormatting.AQUA,
                mxc, cyc, "key_conflict_wheel.wheel.confirm_btn", bxc);
    }

    /** 外圈外侧的 × 号：点一下取消该扇区功能的快捷键 */
    private void drawCrosses(GuiGraphics graphics, float base, float step) {
        Matrix4f pose = graphics.pose().last().pose();
        for (int i = 0; i < count; i++) {
            float x = crossX(i), y = crossY(i);
            boolean hover = (i == hotCross);
            // 与扇区连一根短线，看得清楚是哪个
            float mid = base + (i + 0.5f) * step;
            WheelDraw.line(pose,
                    cx + (float) Math.cos(mid) * (rOuter + 1.0f),
                    cy + (float) Math.sin(mid) * (rOuter + 1.0f),
                    x, y, 1.4f, hover ? 0xFFFFB0B0 : 0xFF4A4A5C);
            int half = (int) crossSize;
            graphics.fill((int) (x - half), (int) (y - half), (int) (x + half) + 1, (int) (y + half) + 1,
                    hover ? 0xFFD04A4A : 0xFF2A2A36);
            graphics.fill((int) (x - half) + 1, (int) (y - half) + 1, (int) (x + half), (int) (y + half),
                    hover ? 0xFFA82E2E : 0xFF1D1D25);
            float inset = crossSize * 0.55f;
            WheelDraw.line(pose, x - inset, y - inset, x + inset, y + inset, 2.0f, 0xFFFFF0F0);
            WheelDraw.line(pose, x - inset, y + inset, x + inset, y - inset, 2.0f, 0xFFFFF0F0);
        }
    }

    private void drawStopButton(GuiGraphics graphics) {
        if (ConflictWheelClient.holdsOn(ref) == 0) return;
        boolean hover = inStopButton(lastMouseX, lastMouseY);
        fillButton(graphics, sxc, syc, hover ? 0xFFE0A03A : 0xFF3A3A4A, hover ? 0xFFB4761C : 0xFF23232E);
        // "停止"图标：实心方块
        float inset = bs * 0.42f;
        graphics.fill((int) (sxc - inset), (int) (syc - inset), (int) (sxc + inset) + 1, (int) (syc + inset) + 1,
                0xFFFFF4D6);
        labelUnder(graphics, "key_conflict_wheel.wheel.stop_btn", ChatFormatting.GOLD, sxc, syc);
    }

    private void fillButton(GuiGraphics graphics, float x, float y, int border, int inner) {
        int x0 = (int) (x - bs), y0 = (int) (y - bs), x1 = (int) (x + bs), y1 = (int) (y + bs);
        graphics.fill(x0, y0, x1 + 1, y1 + 1, border);
        graphics.fill(x0 + 1, y0 + 1, x1, y1, inner);
    }

    private void drawMoveIcon(GuiGraphics graphics, float x, float y, int color, float k) {
        Matrix4f pose = graphics.pose().last().pose();
        float a = k * 0.38f;
        // 十字
        WheelDraw.line(pose, x - k, y, x + k, y, 1.8f, color);
        WheelDraw.line(pose, x, y - k, x, y + k, 1.8f, color);
        // 四个箭头（右、左、下、上）
        WheelDraw.line(pose, x + k, y, x + k - a, y - a, 1.8f, color);
        WheelDraw.line(pose, x + k, y, x + k - a, y + a, 1.8f, color);
        WheelDraw.line(pose, x - k, y, x - k + a, y - a, 1.8f, color);
        WheelDraw.line(pose, x - k, y, x - k + a, y + a, 1.8f, color);
        WheelDraw.line(pose, x, y + k, x - a, y + k - a, 1.8f, color);
        WheelDraw.line(pose, x, y + k, x + a, y + k - a, 1.8f, color);
        WheelDraw.line(pose, x, y - k, x - a, y - k + a, 1.8f, color);
        WheelDraw.line(pose, x, y - k, x + a, y - k + a, 1.8f, color);
    }

    private void labelUnder(GuiGraphics graphics, String key, ChatFormatting color, float x, float y) {
        Component label = Component.translatable(key).withStyle(color);
        int half = this.font.width(label) / 2;
        float tx = Math.max(half + 2.0f, Math.min(x, this.width - half - 2.0f));
        int y1 = (int) (y + bs);
        graphics.drawCenteredString(this.font, label, (int) tx, y1 + 3, 0xFFFFFF);
    }

    private void drawHints(GuiGraphics graphics) {
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFE08A);

        if (adjustMode) {
            // 调整模式：下方那一排被三个按钮占了，提示优先放在轮盘上方
            int y = (int) (cy - rOuter - bs * 1.15f - 24.0f);
            if (y < 24) y = (int) (cy + rOuter + bs * 1.9f);
            if (y + 24 > this.height) y = Math.max(24, (int) (cy - rOuter - bs * 1.15f - 24.0f));
            graphics.drawCenteredString(this.font,
                    Component.translatable("key_conflict_wheel.hint.adjust",
                            (int) (cx - baseCx()), (int) (cy - baseCy()),
                            (int) Math.round(WheelConfig.get().wheelScale * 100)),
                    this.width / 2, y, 0xFF9FE8FF);
            Component extra = hotCross >= 0
                    ? Component.translatable("key_conflict_wheel.wheel.unbind_hint",
                            bindings.get(hotCross).getDisplayName().copy().withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.RED)
                    : Component.translatable("key_conflict_wheel.wheel.adjust_tip").withStyle(ChatFormatting.GRAY);
            graphics.drawCenteredString(this.font, extra, this.width / 2, y + 11, 0xFFFFFFFF);
            return;
        }

        int y = (int) (cy + rOuter + 10);
        if (y + 24 > this.height) y = (int) (cy - rOuter - 26);
        graphics.drawCenteredString(this.font,
                Component.translatable("key_conflict_wheel.hint.zones"),
                this.width / 2, y, 0xFFFFFFFF);

        // 实时提示"现在松开会怎样"，让"松开触发"的逻辑一目了然
        Intent intent = intent();
        graphics.drawCenteredString(this.font, intent.text, this.width / 2, y + 12, intent.color);
    }

    /** 现在松开会发生什么 */
    private static final class Intent {
        final Component text;
        final int color;
        Intent(Component text, int color) { this.text = text; this.color = color; }
    }

    private Intent intent() {
        if (sector >= 0 && (zone == ZONE_SHORT || zone == ZONE_LONG)) {
            KeyMapping m = bindings.get(sector);
            Component name = m.getDisplayName().copy().withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(m.getCategory()).withStyle(ChatFormatting.AQUA));
            if (zone == ZONE_SHORT) {
                return new Intent(Component.translatable("key_conflict_wheel.wheel.release.short", name), 0xFFA8FFA8);
            }
            if (ConflictWheelClient.isHeld(m)) {
                return new Intent(Component.translatable("key_conflict_wheel.wheel.release.long_stop", name), 0xFFFF8C66);
            }
            return new Intent(Component.translatable("key_conflict_wheel.wheel.release.long", name), 0xFFFFC866);
        }
        if (zone == ZONE_CENTER && ConflictWheelClient.holdsOn(ref) > 0) {
            return new Intent(Component.translatable("key_conflict_wheel.wheel.release.center_stop",
                    ConflictWheelClient.holdsOn(ref)), 0xFFFF9E9E);
        }
        return new Intent(Component.translatable("key_conflict_wheel.wheel.release.cancel"), 0xFFB0B0C0);
    }

    // ---------- 杂项 ----------

    private void tell(String key, Object... args) {
        ConflictWheelClient.INSTANCE.tell(key, args);
    }

    private String elide(String s, float maxW) {
        if (this.font.width(s) <= maxW) return s;
        while (!s.isEmpty() && this.font.width(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "…" : s + "…";
    }

    /**
     * 当前该用哪套风格。调整模式也保持玩家选的那套：
     * 右下两个把手、扇区外的 ×、确认/取消按钮用的都是独立的硬编码高对比色，
     * 不靠配色的深浅来保证可读性，所以没必要在调整时偷偷换皮。
     */
    private WheelStyle style() {
        return WheelConfig.get().style();
    }

    /** 立体边：沿某条径向边、在某段半径上画一根细线 */
    private void bevelEdge(Matrix4f pose, float r0, float r1, float angle, int argb) {
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        WheelDraw.line(pose, cx + cos * r0, cy + sin * r0, cx + cos * r1, cy + sin * r1, 2.0f, argb);
    }
}
