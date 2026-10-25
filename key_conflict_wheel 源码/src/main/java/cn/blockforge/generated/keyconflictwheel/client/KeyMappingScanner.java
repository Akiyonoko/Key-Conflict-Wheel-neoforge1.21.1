package cn.blockforge.generated.keyconflictwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 全量按键绑定扫描 + 合成触发工具。
 * 通过类型（而非名字）反射定位 KeyMapping 内部的注册表与 clickCount，
 * 因此不受混淆映射改名影响。失败时模组安全降级（不弹轮盘）。
 */
public final class KeyMappingScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("KeyConflictWheel");

    private static Map<?, KeyMapping> registry;
    private static Field clickCountField;
    private static boolean warned;

    private KeyMappingScanner() {}

    @SuppressWarnings("unchecked")
    private static synchronized void init() {
        if (registry != null || warned) return;
        try {
            for (Field f : KeyMapping.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object value = f.get(null);
                    if (value instanceof Map<?, ?> map && mapValuesAreKeyMapping(map)) {
                        registry = (Map<?, KeyMapping>) map;
                    }
                }
            }
            for (Field f : KeyMapping.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
                if (f.isSynthetic()) continue;
                f.setAccessible(true);
                clickCountField = f;
            }
            if (registry == null) {
                warned = true;
                LOGGER.error("[KeyConflictWheel] 无法定位 KeyMapping 注册表，轮盘功能不可用");
            }
        } catch (Throwable t) {
            warned = true;
            LOGGER.error("[KeyConflictWheel] 按键扫描初始化失败，轮盘功能不可用", t);
        }
    }

    private static boolean mapValuesAreKeyMapping(Map<?, ?> map) {
        for (Object v : map.values()) {
            return v instanceof KeyMapping;
        }
        // 空表无法判断，检查泛型签名
        return false;
    }

    public static Collection<KeyMapping> all() {
        init();
        if (registry == null) return List.of();
        return new ArrayList<>(registry.values());
    }

    /**
     * 找出绑定了该物理按键的全部 KeyMapping（键盘键；同码的鼠标/扫描键不混入）。
     */
    public static List<KeyMapping> mappingsFor(KeyRef ref) {
        List<KeyMapping> out = new ArrayList<>();
        for (KeyMapping m : all()) {
            InputConstants.Key k = m.getKey();
            if (k.getType() != ref.type || k.getValue() != ref.code) continue;
            out.add(m);
        }
        return out;
    }

    /**
     * 归零一批绑定：清掉 clickCount 与按下状态，让依赖轮询的模组“看不见”这次按键。
     */
    public static void zero(List<KeyMapping> mappings) {
        for (KeyMapping m : mappings) {
            m.consumeClick();
            m.setDown(false);
        }
    }

    /** 读取内部 clickCount（原版只在真实按键时 +1，我们自己补） */
    public static int clicks(KeyMapping m) {
        init();
        if (clickCountField == null) return 0;
        try {
            return clickCountField.getInt(m);
        } catch (Throwable e) {
            return 0;
        }
    }

    /** 增加 clickCount：让靠 consumeClick() 判断"按了一下"的模组也能收到 */
    public static void addClick(KeyMapping m, int n) {
        init();
        if (clickCountField == null) return;
        try {
            clickCountField.setInt(m, Math.min(3, clickCountField.getInt(m) + n));
        } catch (Throwable ignored) {
        }
    }
}
