package cn.blockforge.generated.keyconflictwheel.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置：总开关、拉黑按键表、轮盘缩放、轮盘位置偏移、右下角小按钮位置、
 * 每个键上的扇区顺序、音效开关、长按连发。
 * 存到 config/key_conflict_wheel.json。
 *
 * 默认拉黑 W/A/S/D/空格/鼠标左右键/ESC，保证正常跑跳、挖方块不会被轮盘打断；
 * ESC 是系统键，被轮盘接管后会挡住开菜单，所以也默认拉黑；
 * 玩家随时可以在总菜单（K）里逐个解除或一键清空，"恢复默认拉黑"能原样装回来。
 */
public final class WheelConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("KeyConflictWheel");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 默认拉黑的键：移动、跳跃、破坏/放置方块的鼠标键，以及 ESC。与 KeyRef.serialize() 同格式。 */
    public static final List<String> DEFAULT_BLACKLIST = List.of(
            serializeRef(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_W),
            serializeRef(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_A),
            serializeRef(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_S),
            serializeRef(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_D),
            serializeRef(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE),
            serializeRef(InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT),
            serializeRef(InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT),
            serializeRef(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ESCAPE));

    public boolean masterEnabled = true;
    public boolean soundsEnabled = true;
    /** 长按期间自动补"重复按下"（模拟真实按住时的键盘连发），个别模组嫌太频繁可以关掉 */
    public boolean longPressRepeat = true;
    public Set<String> blacklisted = new LinkedHashSet<>();
    /** 默认黑名单是否已经铺过一次（老存档升级时自动补默认键，铺过之后玩家删掉就不会再塞回来） */
    public boolean defaultsApplied = false;
    /** ESC 是否已经补进默认拉黑（后加的一条规则，单独记一个标记，老配置也能补一次） */
    public boolean escDefaultApplied = false;
    public double wheelScale = 1.0;
    /** 轮盘中心相对屏幕中心的偏移（GUI 缩放像素），在轮盘上的"调整位置"模式里拖动得到 */
    public double offsetX = 0.0;
    public double offsetY = 0.0;
    /** 右下角那排小按钮相对默认位置（贴屏幕右下角）的偏移，按住右键拖动得到 */
    public double hudDX = 0.0;
    public double hudDY = 0.0;
    /** 每个物理键上的扇区顺序：KeyRef 序列化 -> 功能名列表。在轮盘上拖扇区重排得到 */
    public Map<String, List<String>> sectorOrder = new LinkedHashMap<>();
    /** 轮盘界面风格：vanilla（原版 GUI）／glass（淡玻璃 HUD）。缺省玻璃，看不清时玩家可自己切回原版 */
    public String uiStyle = WheelStyle.GLASS.id;

    private static WheelConfig instance = load();

    public static WheelConfig get() {
        return instance;
    }

    public static void reload() {
        instance = load();
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("key_conflict_wheel.json");
    }

    private static WheelConfig load() {
        try {
            Path path = file();
            if (Files.exists(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                WheelConfig cfg = GSON.fromJson(json, WheelConfig.class);
                if (cfg != null) {
                    if (cfg.blacklisted == null) cfg.blacklisted = new LinkedHashSet<>();
                    if (cfg.sectorOrder == null) cfg.sectorOrder = new LinkedHashMap<>();
                    cfg.wheelScale = clampScale(cfg.wheelScale);
                    cfg.offsetX = clampOffset(cfg.offsetX);
                    cfg.offsetY = clampOffset(cfg.offsetY);
                    cfg.hudDX = clampOffset(cfg.hudDX);
                    cfg.hudDY = clampOffset(cfg.hudDY);
                    cfg.uiStyle = WheelStyle.byId(cfg.uiStyle).id;
                    // 老配置文件升级：铺一次默认拉黑的移动键，之后玩家删掉就不再塞回来
                    if (!cfg.defaultsApplied) {
                        mergeDefaults(cfg);
                        cfg.defaultsApplied = true;
                        cfg.save();
                    }
                    // ESC 默认拉黑：单独一条，老配置也补一次（ESC 被轮盘接管会挡住开菜单）
                    if (!cfg.escDefaultApplied) {
                        cfg.blacklisted.add(serializeRef(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ESCAPE));
                        cfg.escDefaultApplied = true;
                        cfg.save();
                    }
                    return cfg;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[KeyConflictWheel] 配置文件读取失败，使用默认值", e);
        }
        WheelConfig fresh = new WheelConfig();
        mergeDefaults(fresh);
        fresh.defaultsApplied = true;
        fresh.escDefaultApplied = true;
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("[KeyConflictWheel] 配置文件写入失败", e);
        }
    }

    public boolean isBlacklisted(KeyRef ref) {
        return blacklisted.contains(ref.serialize());
    }

    /** 当前界面风格（存档里认不出来的值自动回落到原版） */
    public WheelStyle style() {
        return WheelStyle.byId(uiStyle);
    }

    public void setStyle(WheelStyle style) {
        this.uiStyle = style.id;
        save();
    }

    /** 切到下一套风格（K 菜单按钮和快捷键共用） */
    public WheelStyle cycleStyle() {
        WheelStyle next = style().next();
        setStyle(next);
        return next;
    }

    public void blacklist(KeyRef ref) {
        blacklisted.add(ref.serialize());
        save();
    }

    public void unblacklist(KeyRef ref) {
        blacklisted.remove(ref.serialize());
        save();
    }

    /** 把默认拉黑的移动键补进表里（已存在的不重复、不移位） */
    public static void mergeDefaults(WheelConfig cfg) {
        cfg.blacklisted.addAll(DEFAULT_BLACKLIST);
    }

    /** 有没有玩家解除过的默认键（有才显示"恢复默认拉黑"按钮） */
    public boolean hasMissingDefaults() {
        return !blacklisted.containsAll(DEFAULT_BLACKLIST);
    }

    /** 把默认拉黑的键原样装回来（玩家自己拉黑的其它键不受影响） */
    public void restoreDefaults() {
        mergeDefaults(this);
        save();
    }

    private static String serializeRef(InputConstants.Type type, int code) {
        return type.name() + ":" + code;
    }

    /** 拖动轮盘时实时更新（不落盘，等玩家点"确认"才保存） */
    public void moveWheelTo(double x, double y) {
        this.offsetX = clampOffset(x);
        this.offsetY = clampOffset(y);
    }

    /** 拖缩放把手时实时更新（不落盘，等点"确认"才保存） */
    public void scaleWheelTo(double v) {
        this.wheelScale = clampScale(v);
    }

    /** 把当前位置 + 大小落盘 */
    public void savePosition() {
        this.offsetX = clampOffset(this.offsetX);
        this.offsetY = clampOffset(this.offsetY);
        this.wheelScale = clampScale(this.wheelScale);
        save();
    }

    /** 恢复默认（屏幕正中、标准大小） */
    public void resetPosition() {
        this.offsetX = 0.0;
        this.offsetY = 0.0;
        this.wheelScale = 1.0;
        save();
    }

    /** 右下角那排小按钮：拖动时实时更新（不落盘） */
    public void moveButtonsTo(double dx, double dy) {
        this.hudDX = clampOffset(dx);
        this.hudDY = clampOffset(dy);
    }

    /** 小按钮位置落盘 */
    public void saveButtons() {
        this.hudDX = clampOffset(this.hudDX);
        this.hudDY = clampOffset(this.hudDY);
        save();
    }

    public void resetButtons() {
        this.hudDX = 0.0;
        this.hudDY = 0.0;
        save();
    }

    /** 布局是否已自定义（位置／大小／小按钮位置任一被挪过） */
    public boolean layoutCustomized() {
        return Math.abs(offsetX) > 1.0 || Math.abs(offsetY) > 1.0
                || Math.abs(wheelScale - 1.0) > 0.01
                || Math.abs(hudDX) > 1.0 || Math.abs(hudDY) > 1.0;
    }

    /**
     * 按玩家排好的顺序给这个键上的功能排序：
     * 排在名单里的按名单顺序，没排到的（新装的模组等）按名字排在后面。
     */
    public void applyOrder(KeyRef ref, List<KeyMapping> bindings) {
        List<String> order = sectorOrder.get(ref.serialize());
        if (bindings.size() < 2) return;
        Map<String, Integer> rank = new HashMap<>();
        if (order != null) {
            for (int i = 0; i < order.size(); i++) rank.putIfAbsent(order.get(i), i);
        }
        bindings.sort(Comparator
                .comparingInt((KeyMapping m) -> rank.getOrDefault(m.getName(), Integer.MAX_VALUE))
                .thenComparing(KeyMapping::getName));
    }

    /** 记下玩家排好的扇区顺序（轮盘点"确认"时调用） */
    public void rememberOrder(KeyRef ref, List<KeyMapping> bindings) {
        List<String> names = new ArrayList<>(bindings.size());
        for (KeyMapping m : bindings) names.add(m.getName());
        sectorOrder.put(ref.serialize(), names);
    }

    public List<String> orderOf(KeyRef ref) {
        List<String> order = sectorOrder.get(ref.serialize());
        return order == null ? List.of() : new ArrayList<>(order);
    }

    private static double clampScale(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 1.0;
        return Math.max(0.6, Math.min(1.6, v));
    }

    private static double clampOffset(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.max(-4000.0, Math.min(4000.0, v));
    }
}
