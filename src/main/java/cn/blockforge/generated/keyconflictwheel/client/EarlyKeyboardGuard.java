package cn.blockforge.generated.keyconflictwheel.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在原版 KeyboardHandler 之前接管冲突键。
 *
 * 1.21.1 里 NeoForge 的 InputEvent.Key 发得太晚：原版已经先给同一个物理键上
 * 所有 KeyMapping 记了"按下＋点了一次"，铁魔法这类模组能在我们收到事件之前就
 * 看到这次按键——于是我们的轮盘刚弹出，它那边的轮盘／施法也同时被触发。
 *
 * 解决办法：把 GLFW 的键盘回调换到最前面，自己先判断这个键是不是轮盘的键——
 * 是就直接处理（按下弹轮盘、松开结算），不再交给原版，别的模组根本看不到这次
 * 按键；不是就原样传给保存下来的原回调（vanilla 的 KeyboardHandler.keyPress），
 * 普通按键、聊天输入、ESC、F3 一切照常。合成触发时（KeySynth 自己发事件）
 * 不经过这里，也不会递归。
 *
 * 若回调安装失败（理论上来不会），自动退回原来的 HIGHEST 事件接管，功能不变，
 * 只是对抗"事件顺序型"模组的效果弱一点。
 */
public final class EarlyKeyboardGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger("KeyConflictWheel");

    /** 被我们替换下来的原版回调（vanilla 的 KeyboardHandler.keyPress 包装） */
    private static GLFWKeyCallback previous;
    private static boolean installed;
    private static long installedWindow;

    private EarlyKeyboardGuard() {}

    /** 每个客户端 tick 调用一次；正常情况下只在第一 tick 装好，之后直接返回 */
    public static void install() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        long window = mc.getWindow().getWindow();
        if (window == 0L) return;
        if (installed && installedWindow == window) return;
        try {
            GLFWKeyCallback prev = GLFW.glfwSetKeyCallback(window, (handle, key, scancode, action, modifiers) -> {
                boolean swallowed = false;
                try {
                    swallowed = ConflictWheelClient.INSTANCE.handleRawKeyboard(key, scancode, action, modifiers);
                } catch (Throwable t) {
                    // 输入回调里绝不能抛异常，否则整局游戏可能失去键盘输入
                    LOGGER.error("[KeyConflictWheel] 原始键盘拦截出错，已放行本次按键", t);
                }
                if (!swallowed) {
                    if (previous != null) {
                        previous.invoke(handle, key, scancode, action, modifiers);
                    }
                }
            });
            if (prev == null) {
                // 没有前置回调可链（正常不会发生）：照常装上，但记一笔日志
                LOGGER.warn("[KeyConflictWheel] 未取到原版键盘回调，冲突键之外的按键可能无响应，请反馈");
            }
            previous = prev;
            installedWindow = window;
            installed = true;
        } catch (Throwable t) {
            LOGGER.error("[KeyConflictWheel] 原始键盘拦截安装失败，退回事件接管模式", t);
        }
    }
}
