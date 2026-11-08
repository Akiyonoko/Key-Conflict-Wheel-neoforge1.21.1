package cn.blockforge.generated.keyconflictwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * 修饰键（Shift / Ctrl / Alt）与 NeoForge KeyModifier 之间的换算。
 *
 * 1.21.1 的 KeyMapping 自带修饰键：getKey() 只是主键，getKeyModifier() 才是
 * 「还要按住哪个修饰键」。同一个物理键上的 X 与 Alt+X 是两个不同的绑定组，
 * 混在一起算就会把本来不冲突的键误判成冲突、把 Alt+X 的那次按键直接吞掉。
 *
 * 换算优先级 ALT > CONTROL > SHIFT：NeoForge 的 KeyModifier 一个绑定只认一个
 * 修饰键，同时按两个时就得挑一个来代表这一组。
 */
public final class KeyMods {
    private KeyMods() {}

    /** 当前按着的修饰键（查硬件状态） */
    public static KeyModifier active() {
        if (Screen.hasAltDown()) return KeyModifier.ALT;
        if (Screen.hasControlDown()) return KeyModifier.CONTROL;
        if (Screen.hasShiftDown()) return KeyModifier.SHIFT;
        return KeyModifier.NONE;
    }

    /**
     * 从 GLFW 回调给的 modifiers 位换算。
     * 比查实时状态更准：按下那一刻的修饰键状态被记在事件里，松开时它不会跟着变。
     */
    public static KeyModifier fromGlfwBits(int bits) {
        if ((bits & GLFW.GLFW_MOD_ALT) != 0) return KeyModifier.ALT;
        if ((bits & GLFW.GLFW_MOD_CONTROL) != 0) return KeyModifier.CONTROL;
        if ((bits & GLFW.GLFW_MOD_SHIFT) != 0) return KeyModifier.SHIFT;
        return KeyModifier.NONE;
    }

    /**
     * 这个物理键本身是不是修饰键（Alt / Ctrl / Shift）。
     *
     * 为什么需要：按下 Alt 的那一刻，GLFW 给的 modifiers 里已经包含它自己，
     * 于是「按 Alt」会被误判成「Alt + 某键」的组合键。Effortless Building 的
     * 轮盘默认就绑在左 Alt 上，被这么一判，那个键永远进不了接管流程，
     * 我们的轮盘在 Alt 上就再也不会出现。
     */
    public static boolean isModifierKey(KeyRef ref) {
        if (ref == null) return false;
        InputConstants.Key key = ref.toInputKey();
        return KeyModifier.ALT.matches(key)
                || KeyModifier.CONTROL.matches(key)
                || KeyModifier.SHIFT.matches(key);
    }

    /**
     * 算这个键这次属于哪个绑定组。
     * 修饰键自身一律归到 NONE 组：它不可能是「某个修饰键 + 自己」的组合，
     * 否则 Alt 上绑定的功能一个都扫不出来。
     */
    public static KeyModifier groupOf(KeyRef ref, KeyModifier mods) {
        if (isModifierKey(ref)) return KeyModifier.NONE;
        return mods == null ? KeyModifier.NONE : mods;
    }

    /** 反过来：给合成事件填 modifiers 用 */
    public static int glfwBits(KeyModifier mod) {
        if (mod == null) return 0;
        return switch (mod) {
            case ALT -> GLFW.GLFW_MOD_ALT;
            case CONTROL -> GLFW.GLFW_MOD_CONTROL;
            case SHIFT -> GLFW.GLFW_MOD_SHIFT;
            default -> 0;
        };
    }

    /** 显示用前缀（组件版），NONE 时是空组件 */
    public static Component prefix(KeyModifier mod) {
        if (mod == null || mod == KeyModifier.NONE) return Component.empty();
        return Component.literal(prefixText(mod));
    }

    /** 显示用前缀（字符串版，给扇区标签拼）：「Alt+」，NONE 时是空串 */
    public static String prefixText(KeyModifier mod) {
        if (mod == null) return "";
        return switch (mod) {
            case ALT -> "Alt+";
            case CONTROL -> "Ctrl+";
            case SHIFT -> "Shift+";
            default -> "";
        };
    }
}
