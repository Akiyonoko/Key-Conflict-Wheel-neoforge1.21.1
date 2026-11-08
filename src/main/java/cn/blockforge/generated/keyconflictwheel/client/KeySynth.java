package cn.blockforge.generated.keyconflictwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按键接管与合成触发（轮盘的"手"）。
 *
 * 接管：把冲突键上所有模组绑定临时解绑（setKey(UNKNOWN)），于是别的模组在监听器里的
 * 键码判定统统失败＝这次按键对它们不存在；同时每个 tick 把状态清零，连"轮询 isDown()"的
 * 模组也看不见这次按键。轮盘收起、长按结束时原样还回去。
 *
 * 合成：要触发哪个功能，就只把它一个绑回那个键，然后<b>交给原版按键处理器</b>
 * （{@link net.minecraft.client.KeyboardHandler#keyPress}）走一遍，最后恢复"全部解绑"。
 * 为什么必须走原版：
 * - 有的模组把逻辑直接 mixin 在 KeyboardHandler#keyPress 上（Punchy! Moves 就是这样，
 *   而且它处理完还会 cancel 掉原版后续处理）。我们只改 KeyMapping 字段、手发
 *   InputEvent.Key，是绕开了那道门，它们的 mixin 永远不跑，表现就是
 *   "轮盘提示已短按，目标模组毫无反应"。
 * - 走原版还顺带把 scancode 和修饰键（Alt/Ctrl/Shift）带对，组合键绑定才认得出来。
 *
 * 兜底：原版调用失败（或当前有界面挡着）时退回手发 InputEvent.Key；
 * 原版处理被别的模组 mixin 掐掉时（KeyMapping.click 没跑），补一次 clickCount，
 * 保证靠 consumeClick() 判断按下的模组仍然收得到。
 */
public final class KeySynth {

    private static final Logger LOGGER = LoggerFactory.getLogger("KeyConflictWheel");
    /** 打 -Dkcw.debug=true 时打印每次合成的前后状态，排查"点了没反应"用 */
    private static final boolean DEBUG = Boolean.getBoolean("kcw.debug");

    /** 短按后多少 tick 自动松手 */
    private static final int SHORT_PRESS_TICKS = 2;
    /** 长按期间每隔多少 tick 补一次"重复按下"（模拟真实按住时的键盘连发） */
    private static final int REPEAT_INTERVAL_TICKS = 8;

    /** 被接管的物理键 -> 接管时看到的绑定清单 */
    private static final Map<KeyRef, List<KeyMapping>> takenOver = new LinkedHashMap<>();
    /** 被临时解绑的绑定 -> 它本来的按键（用于恢复） */
    private static final Map<KeyMapping, InputConstants.Key> hiddenKey = new IdentityHashMap<>();
    /** 正在虚拟按住的绑定 -> 它的物理键 */
    private static final Map<KeyMapping, KeyRef> holds = new LinkedHashMap<>();
    /** 短按进行中：绑定 -> 剩余 tick */
    private static final Map<KeyMapping, PendingShort> pendingShorts = new IdentityHashMap<>();
    /** 每个长按已持续的 tick（控制连发节奏） */
    private static final Map<KeyMapping, Integer> holdTicks = new IdentityHashMap<>();

    private static boolean synthesizing;

    private KeySynth() {}

    private static final class PendingShort {
        final KeyRef ref;
        int ticks;
        PendingShort(KeyRef ref, int ticks) { this.ref = ref; this.ticks = ticks; }
    }

    /** 自己发出的合成事件期间，本模组的按键监听器要放行，别把自己弹回轮盘 */
    public static boolean isSynthesizing() {
        return synthesizing;
    }

    // ---------- 接管 ----------

    /** 接管这个物理键：记下绑定清单并全部临时解绑。可重复调用（幂等）。 */
    public static void takeOver(KeyRef ref, List<KeyMapping> bindings) {
        List<KeyMapping> list = takenOver.computeIfAbsent(ref, k -> new ArrayList<>());
        for (KeyMapping m : bindings) {
            if (!list.contains(m)) list.add(m);
            hide(m);
        }
    }

    public static boolean isTakenOver(KeyRef ref) {
        return takenOver.containsKey(ref);
    }

    /** 该物理键当前的绑定清单（解绑后按键码已经扫不到了，所以优先用接管时的记录） */
    public static List<KeyMapping> bindingsOf(KeyRef ref) {
        List<KeyMapping> list = takenOver.get(ref);
        if (list == null) return KeyMappingScanner.mappingsFor(ref);
        List<KeyMapping> out = new ArrayList<>(list);
        for (KeyMapping m : KeyMappingScanner.mappingsFor(ref)) {
            if (!out.contains(m)) out.add(m);
        }
        return out;
    }

    /**
     * 只取「按住这一组修饰键时才成立」的绑定：X 与 Alt+X 是两个独立的组。
     * 轮盘弹不弹、扇区里列什么，都按这个组来算。
     */
    public static List<KeyMapping> bindingsOf(KeyRef ref, KeyModifier mods) {
        KeyModifier want = mods == null ? KeyModifier.NONE : mods;
        List<KeyMapping> out = new ArrayList<>();
        for (KeyMapping m : bindingsOf(ref)) {
            if (m.getKeyModifier() == want) out.add(m);
        }
        return out;
    }

    /** 维持接管效果：非长按的绑定清空状态，长按的保持"按着" */
    public static void enforce() {
        if (takenOver.isEmpty()) return;
        for (List<KeyMapping> list : takenOver.values()) {
            for (KeyMapping m : list) {
                if (holds.containsKey(m)) {
                    if (!m.isDown()) m.setDown(true);
                } else if (!pendingShorts.containsKey(m)) {
                    m.consumeClick();
                    m.setDown(false);
                }
            }
        }
    }

    // ---------- 触发 ----------

    /** 短按一次：立刻"按下"，SHORT_PRESS_TICKS 后自动"松手" */
    public static void shortPress(KeyRef ref, KeyMapping mapping) {
        takeOver(ref, List.of(mapping));
        pendingShorts.remove(mapping);
        pressState(mapping);
        fireEvent(mapping, ref, GLFW.GLFW_PRESS);
        // 这个功能如果同时被长按着，短按只算多按一下，不能把长按松开
        if (!holds.containsKey(mapping)) {
            pendingShorts.put(mapping, new PendingShort(ref, SHORT_PRESS_TICKS));
        }
    }

    /** 开始长按：立刻"按下"，之后一直按着，直到 releaseHold */
    public static void startHold(KeyRef ref, KeyMapping mapping) {
        if (holds.containsKey(mapping)) return;
        takeOver(ref, List.of(mapping));
        PendingShort ps = pendingShorts.remove(mapping);
        if (ps != null) releaseState(mapping, ps.ref);
        pressState(mapping);
        fireEvent(mapping, ref, GLFW.GLFW_PRESS);
        holds.put(mapping, ref);
        holdTicks.put(mapping, 0);
    }

    /** 结束一次长按：发"松开"事件并清状态 */
    public static void releaseHold(KeyMapping mapping) {
        KeyRef ref = holds.remove(mapping);
        holdTicks.remove(mapping);
        if (ref == null) return;
        releaseState(mapping, ref);
    }

    public static void releaseAllOn(KeyRef ref) {
        for (KeyMapping m : new ArrayList<>(holds.keySet())) {
            if (ref.equals(holds.get(m))) releaseHold(m);
        }
    }

    /** 结束全部长按（玩家从总菜单或异常收尾用） */
    public static void releaseEverything() {
        for (Map.Entry<KeyMapping, KeyRef> e : new LinkedHashMap<>(holds).entrySet()) {
            releaseState(e.getKey(), e.getValue());
        }
        holds.clear();
        holdTicks.clear();
        for (Map.Entry<KeyMapping, PendingShort> e : new IdentityHashMap<>(pendingShorts).entrySet()) {
            releaseState(e.getKey(), e.getValue().ref);
        }
        pendingShorts.clear();
        giveBackAll();
    }

    /** 放弃所有接管：不补发事件，用于断线/退出这类必须立刻恢复原样的场合 */
    public static void giveBackAll() {
        holds.clear();
        holdTicks.clear();
        pendingShorts.clear();
        takenOver.clear();
        List<KeyMapping> all = new ArrayList<>(hiddenKey.keySet());
        for (KeyMapping m : all) restore(m);
        rebuildLookups();
    }

    // ---------- 永久解绑（轮盘"调整位置"模式里的 ×） ----------

    /**
     * 永久取消某个功能的快捷键（轮盘调整模式里点扇区外的 ×）。
     * 先结束它的长按/短按（该发的"松开"事件要发出去），再把它从接管记录里摘掉——
     * 这样收尾时不会把键再绑回去，也就是"真的解绑"，不是临时藏起来。
     * 修饰键要一起清掉：只清主键的话，这个绑定还挂着 Alt，等于没解干净。
     */
    public static void unbindForever(KeyMapping mapping) {
        boolean released = false;
        if (holds.containsKey(mapping)) {
            releaseHold(mapping);
            released = true;
        }
        PendingShort ps = pendingShorts.remove(mapping);
        if (ps != null && !released) releaseState(mapping, ps.ref);
        holdTicks.remove(mapping);

        for (List<KeyMapping> list : takenOver.values()) list.remove(mapping);
        hiddenKey.remove(mapping);

        mapping.setDown(false);
        mapping.consumeClick();
        mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
        rebuildLookups();
    }

    /**
     * 在"所有被临时解绑的绑定都还原成真实按键"的状态下执行一段代码（用来写 options.txt）。
     * 必须在还原状态下保存，否则会把我们只是临时藏起来的键以 unknown 写进玩家的按键设置里。
     */
    public static void withRealKeys(Runnable action) {
        List<KeyMapping> hidden = new ArrayList<>(hiddenKey.keySet());
        for (KeyMapping m : hidden) restore(m);
        rebuildLookups();
        try {
            action.run();
        } finally {
            for (KeyMapping m : hidden) hide(m);
            // hide() 会把状态清零，长按中的那几个立刻补回"按着"
            for (KeyMapping m : holds.keySet()) m.setDown(true);
            rebuildLookups();
        }
    }

    // ---------- 查询（给界面用） ----------

    public static boolean isHeld(KeyMapping mapping) {
        return holds.containsKey(mapping);
    }

    public static int holdCountOn(KeyRef ref) {
        int n = 0;
        for (KeyMapping m : holds.keySet()) if (ref.equals(holds.get(m))) n++;
        return n;
    }

    public static int holdCount() {
        return holds.size();
    }

    public static List<KeyMapping> heldBindings() {
        return new ArrayList<>(holds.keySet());
    }

    public static KeyRef refOf(KeyMapping mapping) {
        return holds.get(mapping);
    }

    /** 这个绑定是不是被我们藏起来了（诊断用） */
    public static boolean isHidden(KeyMapping mapping) {
        return hiddenKey.containsKey(mapping);
    }

    // ---------- 每 tick 推进 ----------

    /**
     * @param wheelRef 轮盘当前开在哪个物理键上（没开传 null）
     */
    public static void tick(KeyRef wheelRef) {
        if (!pendingShorts.isEmpty()) {
            List<KeyMapping> due = null;
            for (Map.Entry<KeyMapping, PendingShort> e : pendingShorts.entrySet()) {
                if (--e.getValue().ticks <= 0) {
                    if (due == null) due = new ArrayList<>();
                    due.add(e.getKey());
                }
            }
            if (due != null) {
                for (KeyMapping m : due) {
                    if (DEBUG) LOGGER.info("[KCW] 松手前 clickCount={} 目标={}（0＝对方已经吃掉这次点击，1＝没人读）", KeyMappingScanner.clicks(m), m.getName());
                    PendingShort ps = pendingShorts.remove(m);
                    if (ps != null) releaseState(m, ps.ref);
                }
            }
        }

        if (!holds.isEmpty()) {
            if (WheelConfig.get().longPressRepeat) {
                for (Map.Entry<KeyMapping, KeyRef> e : new ArrayList<>(holds.entrySet())) {
                    int n = holdTicks.merge(e.getKey(), 1, Integer::sum);
                    if (n >= REPEAT_INTERVAL_TICKS) {
                        holdTicks.put(e.getKey(), 0);
                        e.getKey().setDown(true);
                        // 真实按住的连发也不会加 clickCount，这里保持一致，交给原版处理
                        fireEvent(e.getKey(), e.getValue(), GLFW.GLFW_REPEAT);
                    }
                }
            } else {
                holdTicks.clear();
            }
        }

        enforce();
        sweepIdle(wheelRef);
    }

    /** 这个键上既没开轮盘、也没在长按/短按：把绑定原样还回去 */
    private static void sweepIdle(KeyRef wheelRef) {
        for (KeyRef ref : new ArrayList<>(takenOver.keySet())) {
            if (ref.equals(wheelRef)) continue;
            List<KeyMapping> list = takenOver.get(ref);
            if (list == null) continue;
            boolean busy = false;
            for (KeyMapping m : list) {
                if (holds.containsKey(m) || pendingShorts.containsKey(m)) { busy = true; break; }
            }
            if (busy) continue;

            List<KeyMapping> give = new ArrayList<>(list);
            for (Map.Entry<KeyMapping, InputConstants.Key> e : hiddenKey.entrySet()) {
                InputConstants.Key real = e.getValue();
                if (real.getType() == ref.type && real.getValue() == ref.code && !give.contains(e.getKey())) {
                    give.add(e.getKey());
                }
            }
            takenOver.remove(ref);
            for (KeyMapping m : give) restore(m);
            rebuildLookups();
        }
    }

    // ---------- 小工具 ----------

    /**
     * 按下状态：只置 isDown。
     * clickCount 交给 fireEvent 统一处理——先看原版有没有点上一次，没点上才补，
     * 免得原版点一次、我们再点一次，变成按两下。
     */
    private static void pressState(KeyMapping mapping) {
        mapping.setDown(true);
    }

    private static void releaseState(KeyMapping mapping, KeyRef ref) {
        mapping.setDown(false);
        fireEvent(mapping, ref, GLFW.GLFW_RELEASE);
        mapping.consumeClick();
    }

    private static void hide(KeyMapping mapping) {
        if (hiddenKey.containsKey(mapping)) return;
        InputConstants.Key current = mapping.getKey();
        if (isUnknown(current)) return;
        hiddenKey.put(mapping, current);
        // 只改主键，不动 keyModifier：还原时才不会把 Alt/Ctrl 丢掉
        mapping.setKey(InputConstants.UNKNOWN);
        mapping.consumeClick();
        mapping.setDown(false);
    }

    private static void restore(KeyMapping mapping) {
        InputConstants.Key real = hiddenKey.remove(mapping);
        if (real != null && isUnknown(mapping.getKey())) {
            // 仍然是我们改的样子才还原（玩家中途自己在键位界面改过就不动它）
            mapping.setKey(real);
        }
    }

    private static void rebuildLookups() {
        try {
            KeyMapping.resetMapping();
        } catch (Throwable ignored) {
        }
    }

    private static boolean isUnknown(InputConstants.Key key) {
        // 1.21.1 里"未绑定"就是 KEYSYM:-1
        return key == null || key.getValue() == InputConstants.UNKNOWN.getValue();
    }

    /**
     * 只把目标一个绑回原键、交给原版处理一遍、然后恢复原状——别的模组只会看到这个功能。
     *
     * modifiers 取的是「目标绑定自己声明的修饰键」，不是「玩家此刻按着什么」：
     * 轮盘结算时玩家可能已经把 Alt 松开了，但对这个功能来说它仍然是 Alt+X。
     */
    private static void fireEvent(KeyMapping mapping, KeyRef ref, int action) {
        InputConstants.Key saved = hiddenKey.remove(mapping);
        boolean wasHidden = saved != null;
        if (wasHidden) {
            mapping.setKey(saved);
        } else {
            saved = mapping.getKey();
        }
        // 键刚换回来，KeyMapping 内部的 MAP 还是旧的（这个绑定还挂在 UNKNOWN 下）。
        // 不重建的话，靠 KeyMapping.click(key) 走查找表的模组根本找不到它。
        rebuildLookups();

        KeyModifier modifier = mapping.getKeyModifier();
        int mods = KeyMods.glfwBits(modifier);
        int scancode = 0;
        if (ref.type == InputConstants.Type.KEYSYM) {
            try {
                scancode = GLFW.glfwGetKeyScancode(ref.code);
            } catch (Throwable ignored) {
            }
        }

        Minecraft mc = Minecraft.getInstance();
        synthesizing = true;
        try {
            boolean viaVanilla = false;
            if (ref.type == InputConstants.Type.KEYSYM
                    && mc != null && mc.keyboardHandler != null && mc.screen == null) {
                long window = mc.getWindow() == null ? 0L : mc.getWindow().getWindow();
                if (window != 0L) {
                    try {
                        // 关键一步：走原版入口。把逻辑挂在 KeyboardHandler#keyPress 上的模组
                        // （Punchy! Moves 就是，而且它会 cancel 掉原版后续处理）只有经过这里才响应。
                        mc.keyboardHandler.keyPress(window, ref.code, scancode, action, mods);
                        viaVanilla = true;
                    } catch (Throwable t) {
                        LOGGER.error("[KeyConflictWheel] 合成按键走原版处理失败，退回手动发事件", t);
                    }
                }
            }
            if (!viaVanilla) {
                if (ref.type == InputConstants.Type.MOUSE) {
                    InputEvent.MouseButton.Pre pre = new InputEvent.MouseButton.Pre(ref.code, action, mods);
                    NeoForge.EVENT_BUS.post(pre);
                    if (!((ICancellableEvent) pre).isCanceled()) {
                        NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Post(ref.code, action, mods));
                    }
                } else {
                    NeoForge.EVENT_BUS.post(new InputEvent.Key(ref.code, scancode, action, mods));
                }
            }
            // 原版那一步可能被别的模组的 mixin 掐掉（Punchy! Moves 就 cancel 了），
            // 那样 KeyMapping.click() 不会跑，靠 consumeClick() 的模组就收不到，这里补一次。
            if (action == GLFW.GLFW_PRESS && KeyMappingScanner.clicks(mapping) == 0) {
                KeyMappingScanner.addClick(mapping, 1);
            }
            if (DEBUG) {
                LOGGER.info("[KCW] 合成 action={} key={} scancode={} mods={} 目标={} clickCount={} 字段={} 走原版={}",
                        action, ref.code, scancode, mods, mapping.getName(),
                        KeyMappingScanner.clicks(mapping), KeyMappingScanner.clickCountFieldName(), viaVanilla);
            }
        } finally {
            synthesizing = false;
            if (wasHidden && saved != null && !isUnknown(saved)) {
                // 恢复"接管中＝全部解绑"这个不变量
                mapping.setKey(InputConstants.UNKNOWN);
                hiddenKey.put(mapping, saved);
                rebuildLookups();
            }
        }
    }
}
