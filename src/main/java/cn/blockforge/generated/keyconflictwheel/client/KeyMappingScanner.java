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
 *
 * 注册表（KeyMapping.ALL）按类型反射定位：它是唯一一个「值是 KeyMapping 的静态 Map」。
 * clickCount 优先按名字取 —— NeoForge 用官方映射，生产环境里字段名就是 clickCount；
 * 取不到才退回类型扫描，并且只接受非 final 的 int 字段。
 *
 * 为什么要改：原来的写法是「遍历所有非静态 int 字段、取最后一个」，依赖 JVM 返回的
 * 声明顺序。一旦 KeyMapping 里多了第二个 int 字段（排序值、缓存键码之类），点击就会
 * 记到别的字段上，clickCount 永远是 0 —— 表现是「轮盘能弹出、能选中、提示也弹了，
 * 但只要是靠 consumeClick() 判断按下的模组，一个都触发不了」。
 *
 * 注册表在客户端刚起来时可能还是空的，所以拿不到不做永久降级，隔 tick 再试，
 * 超过上限才认输并打 error。彻底失败时 mod 安全降级（不弹轮盘），不假装能用。
 */
public final class KeyMappingScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("KeyConflictWheel");

    /** 注册表最多重试多少次（一次 tick 一次，400 次约 20 秒） */
    private static final int MAX_REGISTRY_ATTEMPTS = 400;

    private static Map<?, KeyMapping> registry;
    private static Field clickCountField;
    private static String clickCountFieldName;
    private static boolean fieldScanned;
    private static int registryAttempts;
    /** 彻底放弃标记：注册表始终为空，或反射直接炸了 */
    private static boolean warned;

    private KeyMappingScanner() {}

    private static synchronized void init() {
        if (warned) return;
        if (registry != null && clickCountField != null) return;
        try {
            if (!fieldScanned) {
                fieldScanned = true;
                clickCountField = findClickCountField();
            }
            if (registry == null) {
                registry = findRegistry();
            }
            if (registry == null) {
                if (++registryAttempts >= MAX_REGISTRY_ATTEMPTS) {
                    warned = true;
                    LOGGER.error("[KeyConflictWheel] 无法定位 KeyMapping 注册表，轮盘功能不可用");
                }
                return;
            }
            if (clickCountField == null) {
                warned = true;
                LOGGER.error("[KeyConflictWheel] 无法定位 KeyMapping.clickCount，轮盘只能弹出、不能触发别的模组");
                return;
            }
            LOGGER.info("[KeyConflictWheel] 按键扫描就绪：{} 项绑定，clickCount 字段={}",
                    registry.size(), clickCountFieldName);
        } catch (Throwable t) {
            warned = true;
            LOGGER.error("[KeyConflictWheel] 按键扫描初始化失败，轮盘功能不可用", t);
        }
    }

    /** clickCount 的定位：先按名字，再按「非 final 的 int」兜底 */
    private static Field findClickCountField() {
        try {
            Field f = KeyMapping.class.getDeclaredField("clickCount");
            if (f.getType() == int.class && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                clickCountFieldName = "clickCount";
                return f;
            }
        } catch (NoSuchFieldException ignored) {
            // 落到下面的类型扫描
        }
        for (Field f : KeyMapping.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() != int.class) continue;
            // 按键码缓存、排序值这类都是 final，点击计数才是可变的
            if (Modifier.isFinal(f.getModifiers())) continue;
            if (f.isSynthetic()) continue;
            f.setAccessible(true);
            clickCountFieldName = f.getName() + "（类型扫描兜底）";
            LOGGER.warn("[KeyConflictWheel] 没找到 clickCount，改用字段 {} 记录点击", f.getName());
            return f;
        }
        return null;
    }

    /** 注册表：静态、类型是 Map、值是 KeyMapping */
    @SuppressWarnings("unchecked")
    private static Map<?, KeyMapping> findRegistry() {
        for (Field f : KeyMapping.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (!Map.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                Object value = f.get(null);
                if (value instanceof Map<?, ?> map && mapValuesAreKeyMapping(map)) {
                    return (Map<?, KeyMapping>) map;
                }
            } catch (Throwable ignored) {
                // 这个字段读不出来就换下一个
            }
        }
        return null;
    }

    private static boolean mapValuesAreKeyMapping(Map<?, ?> map) {
        for (Object v : map.values()) {
            return v instanceof KeyMapping;
        }
        // 空表判断不了，当作还没填满，下次再试
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
     * 用 while 而不是单次 consumeClick()，因为 clickCount 可能被连发累积到 2、3。
     */
    public static void zero(List<KeyMapping> mappings) {
        for (KeyMapping m : mappings) {
            while (m.consumeClick()) {
                // 一直清到 0
            }
            m.setDown(false);
        }
    }

    /** 读 clickCount：原版只在真实按键时 +1，我们合成时自己补 */
    public static int clicks(KeyMapping m) {
        init();
        if (clickCountField == null) return 0;
        try {
            return clickCountField.getInt(m);
        } catch (Throwable e) {
            return 0;
        }
    }

    /** 增加 clickCount：让靠 consumeClick() 判断“按了一下”的模组也能收到 */
    public static void addClick(KeyMapping m, int n) {
        init();
        if (clickCountField == null) return;
        try {
            clickCountField.setInt(m, Math.min(3, clickCountField.getInt(m) + n));
        } catch (Throwable ignored) {
        }
    }

    /** 诊断用：定位到的 clickCount 字段名（定位不到时给明确提示） */
    public static String clickCountFieldName() {
        init();
        return clickCountFieldName == null ? "<未找到>" : clickCountFieldName;
    }

    /** 绑定注册表是否已经能读到（读不到时轮盘不该假装能触发别的模组） */
    public static boolean ready() {
        init();
        return registry != null;
    }
}
