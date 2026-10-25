package cn.blockforge.generated.keyconflictwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 轮盘的运行时核心：拦截冲突按键、接管该键、把玩家的选择合成为真实按键。
 *
 * 触发时机：按住冲突键弹出轮盘，松开按键的那一刻，鼠标停在哪个圈层就触发什么——
 * 内圈某扇区＝对该功能短按一次；外圈某扇区＝长按该功能（再按一次原键、松开时停在
 * 同一外圈＝结束）；中心孔＝取消（有长按在跑时＝结束该键全部长按）；其余＝取消。
 *
 * 优先级：键盘事件由 {@link EarlyKeyboardGuard} 在原版 KeyboardHandler 之前就抢下
 * （1.21.1 的 InputEvent.Key 发得晚，原版已经先给同键上所有模组的 KeyMapping 记了按下，
 * 铁魔法这类模组会赶在我们的事件监听器之前看到这次按键），轮盘的键根本不会派发给
 * 别的模组；鼠标侧键仍用 HIGHEST 的 Pre 事件接管（Pre 在原版更新状态之前）。
 * 临时解绑同键上的其他模组绑定，别的模组既不会跟着一起触发，
 * 也不会趁机弹自己的界面把轮盘顶掉。合成触发由 {@link KeySynth} 完成。
 */
public final class ConflictWheelClient {

    public static final ConflictWheelClient INSTANCE = new ConflictWheelClient();

    /** K 键：打开总控菜单 */
    public static final KeyMapping MENU_KEY = new KeyMapping(
            "key.key_conflict_wheel.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.key_conflict_wheel");

    /** 切换轮盘界面风格（原版 GUI / 淡玻璃 HUD）。默认不绑定，需要的玩家自己去"控制"里设 */
    public static final KeyMapping STYLE_KEY = new KeyMapping(
            "key.key_conflict_wheel.toggle_style",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.key_conflict_wheel");

    /** 轮盘被 ESC／外部关掉后，等物理键真正抬起才允许再次弹轮盘 */
    private final Set<KeyRef> awaitRelease = new HashSet<>();
    /** 当前物理上"按着"的键（用来判断收尾时是否还需要等待抬起） */
    private static final Set<KeyRef> physicallyDown = new HashSet<>();

    public static boolean isPhysicallyDown(KeyRef ref) {
        return physicallyDown.contains(ref);
    }

    private static void trackPhysical(KeyRef ref, int action) {
        if (action == GLFW.GLFW_RELEASE) physicallyDown.remove(ref);
        else if (action == GLFW.GLFW_PRESS) physicallyDown.add(ref);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MENU_KEY);
        event.register(STYLE_KEY);
    }

    // ---------- 输入事件（HIGHEST：先于其他模组处理按键） ----------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyInput(InputEvent.Key event) {
        if (KeySynth.isSynthesizing()) return;
        KeyRef ref = new KeyRef(InputConstants.Type.KEYSYM, event.getKey());
        trackPhysical(ref, event.getAction());
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            onPhysicalRelease(ref);
        } else {
            onPhysicalPress(ref, event.getAction());
        }
    }

    /**
     * 鼠标侧键之类也能被模组当快捷键用。在 Pre（原版更新按键状态之前）就完成接管，
     * 同一次事件里 NORMAL 优先级的其他模组 matches() 就会失败＝这次点击对它们不存在。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (KeySynth.isSynthesizing()) return;
        KeyRef ref = new KeyRef(InputConstants.Type.MOUSE, event.getButton());
        trackPhysical(ref, event.getAction());
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            onPhysicalRelease(ref);
        } else {
            onPhysicalPress(ref, event.getAction());
        }
        KeySynth.enforce();
    }

    /**
     * 原版处理鼠标键的顺序是"Pre 事件 → 更新 KeyMapping 状态 → Post 事件"，
     * 与键盘相反。所以这里在 Post 之前再兜一次底：把没被选中的绑定重新清干净。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseButtonPost(InputEvent.MouseButton.Post event) {
        if (KeySynth.isSynthesizing()) return;
        KeySynth.enforce();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ConflictWheelScreen
                && event.getNewScreen() != null
                && !(event.getNewScreen() instanceof ConflictWheelScreen)) {
            // 轮盘显示期间，别的模组想弹界面一律拦下
            event.setCanceled(true);
            return;
        }
        if (event.getNewScreen() != null
                && !(event.getNewScreen() instanceof ConflictWheelScreen)
                && KeySynth.holdCount() > 0) {
            // 有模组要开界面：先安全收尾，避免长按状态卡在半空
            KeySynth.releaseEverything();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        EarlyKeyboardGuard.install();
        KeySynth.tick(wheelRef());
        if (mc.level == null && KeySynth.holdCount() > 0) {
            KeySynth.giveBackAll();
        }
        if (MENU_KEY.consumeClick() && mc.screen == null && mc.level != null && mc.player != null) {
            mc.setScreen(new MasterMenuScreen());
            WheelSounds.openMenu();
        }
        // 风格切换：不弹窗，右下角 actionbar 提示一下当前是哪套
        if (STYLE_KEY.consumeClick() && mc.screen == null && mc.level != null && mc.player != null) {
            WheelStyle style = WheelConfig.get().cycleStyle();
            WheelSounds.openMenu();
            tell("key_conflict_wheel.style.switched", Component.translatable(style.langKey()));
        }
    }

    /** 长按中的功能会显示在画面顶部，提醒玩家别忘了它还按着 */
    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        int n = KeySynth.holdCount();
        if (n == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui) return;

        Font font = mc.font;
        MutableComponent names = null;
        MutableComponent keys = null;
        for (KeyMapping m : KeySynth.heldBindings()) {
            names = names == null
                    ? Component.empty().append(m.getDisplayName())
                    : names.append(Component.literal("、").append(m.getDisplayName()));
            KeyRef ref = KeySynth.refOf(m);
            if (ref != null) {
                keys = keys == null
                        ? Component.empty().append(ref.display())
                        : keys.append(Component.literal(" / ").append(ref.display()));
            }
        }
        int w = mc.getWindow().getGuiScaledWidth();
        event.getGuiGraphics().drawCenteredString(font,
                Component.translatable("key_conflict_wheel.hud.holding",
                        names == null ? Component.empty() : names.withStyle(ChatFormatting.WHITE)),
                w / 2, 6, 0xFFFFD060);
        event.getGuiGraphics().drawCenteredString(font,
                Component.translatable("key_conflict_wheel.hud.help",
                        keys == null ? Component.empty() : keys.withStyle(ChatFormatting.YELLOW)),
                w / 2, 6 + font.lineHeight + 2, 0xFFB8B8C8);
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        awaitRelease.clear();
        physicallyDown.clear();
        KeySynth.giveBackAll();
    }

    // ---------- 按键分发 ----------

    /**
     * 由 EarlyKeyboardGuard 在原版 KeyboardHandler 之前调用。
     * 返回 true 表示这次键盘事件已经由轮盘接管，不能再交给原版和其他模组。
     */
    public boolean handleRawKeyboard(int key, int scancode, int action, int modifiers) {
        if (KeySynth.isSynthesizing() || key < 0) return false;

        KeyRef ref = new KeyRef(InputConstants.Type.KEYSYM, key);
        ConflictWheelScreen wheel = wheelOn(ref);
        boolean taken = KeySynth.isTakenOver(ref) || awaitRelease.contains(ref);

        // 已经在轮盘流程中的键，按下、连发、松开都必须在这里吃掉。
        if (wheel != null || taken) {
            trackPhysical(ref, action);
            if (action == GLFW.GLFW_RELEASE) {
                onPhysicalRelease(ref);
            } else {
                onPhysicalPress(ref, action);
            }
            return true;
        }

        // 只有真正的首次按下才会打开轮盘；普通按键继续走原版回调。
        if (action != GLFW.GLFW_PRESS) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null || mc.player == null) return false;
        WheelConfig config = WheelConfig.get();
        if (!config.masterEnabled || config.isBlacklisted(ref)) return false;
        if (KeySynth.bindingsOf(ref).size() < 2) return false;

        trackPhysical(ref, action);
        onPhysicalPress(ref, action);
        return true;
    }

    private void onPhysicalPress(KeyRef ref, int action) {
        Minecraft mc = Minecraft.getInstance();

        // 轮盘已开：重复按键与键盘连发一律吞掉，只在松开时结算
        if (wheelOn(ref) != null) {
            KeyMappingScanner.zero(KeySynth.bindingsOf(ref));
            KeySynth.enforce();
            return;
        }
        // 被 ESC 等方式关掉轮盘后，这个键还没真正抬起：不重弹
        if (awaitRelease.contains(ref)) {
            KeyMappingScanner.zero(KeySynth.bindingsOf(ref));
            KeySynth.enforce();
            return;
        }
        if (mc.screen != null || mc.level == null || mc.player == null) {
            KeySynth.enforce();
            return;
        }

        WheelConfig config = WheelConfig.get();
        if (!config.masterEnabled || config.isBlacklisted(ref)) return;

        List<KeyMapping> bindings = KeySynth.bindingsOf(ref);
        if (bindings.size() < 2) return;   // 只有一个功能用这个键：原样放行
        WheelConfig.get().applyOrder(ref, bindings);

        // 先接管（临时解绑），这样本次事件继续往下派发时，别的模组 matches() 都会失败
        KeySynth.takeOver(ref, bindings);
        KeyMappingScanner.zero(bindings);
        KeySynth.enforce();

        if (action == GLFW.GLFW_PRESS) {
            mc.setScreen(new ConflictWheelScreen(ref, bindings));
            KeySynth.enforce();  // 开界面时原版会 releaseAll，这里把还在长按的补回去
            WheelSounds.open();
        }
    }

    private void onPhysicalRelease(KeyRef ref) {
        awaitRelease.remove(ref);
        Minecraft mc = Minecraft.getInstance();

        ConflictWheelScreen wheel = wheelOn(ref);
        if (wheel != null) {
            // 松开这一刻结算：鼠标停在哪个圈层就触发什么
            wheel.fireOnRelease();
            KeySynth.enforce();
            return;
        }
        if (mc.screen != null || KeySynth.isTakenOver(ref)) {
            // 轮盘已不在（被取消／被外部关掉）：维持状态，接管会在下一个 tick 自动收摊
            KeySynth.enforce();
        }
    }

    // ---------- 供轮盘界面调用 ----------

    /** 短按一次（内圈）：由 KeySynth 补发按下与松开事件 */
    public void triggerShortPress(KeyRef ref, KeyMapping mapping) {
        KeySynth.shortPress(ref, mapping);
        tell("key_conflict_wheel.msg.short_fired", mapping.getDisplayName());
    }

    /**
     * 外圈：长按开关。已经在长按就结束它，否则开始长按。
     * 同一个键上可以同时按住多个模组的功能。
     */
    public boolean toggleLongHold(KeyRef ref, KeyMapping mapping) {
        if (KeySynth.isHeld(mapping)) {
            KeySynth.releaseHold(mapping);
            WheelSounds.endHold();
            tell("key_conflict_wheel.msg.hold_stopped", mapping.getDisplayName());
            return false;
        }
        KeySynth.startHold(ref, mapping);
        tell("key_conflict_wheel.msg.hold_started", mapping.getDisplayName(), ref.display());
        return true;
    }

    /** 中心孔松开：有长按在跑就结束该键全部长按，否则等于取消 */
    public int stopHoldsOn(KeyRef ref) {
        int n = KeySynth.holdCountOn(ref);
        if (n > 0) {
            KeySynth.releaseAllOn(ref);
            WheelSounds.endHold();
            tell("key_conflict_wheel.msg.holds_stopped_all", n);
        }
        return n;
    }

    public void stopAllHolds() {
        int n = KeySynth.holdCount();
        KeySynth.releaseEverything();
        if (n > 0) {
            WheelSounds.endHold();
            tell("key_conflict_wheel.msg.holds_stopped_all", n);
        }
    }

    /** 轮盘被 ESC／外部关闭且没结算过：这个键要真正抬起后才允许再弹轮盘 */
    public void lockUntilRelease(KeyRef ref) {
        awaitRelease.add(ref);
    }

    /** 拉黑该键前先清掉它上面的长按与接管 */
    public void clearHold(KeyRef ref) {
        KeySynth.releaseAllOn(ref);
    }

    /**
     * 轮盘"调整位置"模式里点 × ：永久取消这个功能的快捷键。
     * 解绑后立刻写进原版按键设置（options.txt），重启也不会恢复。
     */
    public void unbindBinding(KeyRef ref, KeyMapping mapping) {
        KeySynth.unbindForever(mapping);
        // 必须在"被临时解绑的绑定都还原成真实按键"的状态下写盘，
        // 否则会把我们只是藏起来的键以"未绑定"写进玩家的 options.txt。
        Minecraft mc = Minecraft.getInstance();
        KeySynth.withRealKeys(mc.options::save);
    }

    public void tell(String key, Object... args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            out[i] = args[i] instanceof Component c ? c : Component.literal(String.valueOf(args[i]));
        }
        mc.player.displayClientMessage(Component.translatable(key, out), true);
    }

    // ---------- 小工具 ----------

    private static ConflictWheelScreen wheelOn(KeyRef ref) {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof ConflictWheelScreen w && w.getRef().equals(ref) ? w : null;
    }

    public static KeyRef wheelRef() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof ConflictWheelScreen w ? w.getRef() : null;
    }

    public static boolean isHeld(KeyMapping mapping) {
        return KeySynth.isHeld(mapping);
    }

    public static int holdsOn(KeyRef ref) {
        return KeySynth.holdCountOn(ref);
    }
}
