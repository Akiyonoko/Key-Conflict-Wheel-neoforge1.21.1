package cn.blockforge.generated.keyconflictwheel.client;

/**
 * 轮盘的界面风格。两套风格共用同一套几何与交互，只有配色和画法不同：
 * <ul>
 *   <li>{@link #VANILLA} 原版 MC：灰阶 + 上亮下暗的立体边，最贴近原版按钮；</li>
 *   <li>{@link #GLASS} 半透明：低透明度 + 细线 + 径向压暗，几乎不挡画面。</li>
 * </ul>
 * 半透明在调整模式下会被强制换成原版配色（见 ConflictWheelScreen#style()），
 * 否则扇区外的 × 和右下两个把手在低对比下看不清。
 */
public enum WheelStyle {
    VANILLA("vanilla",
            0x70000000,   // screenDim   全屏压暗
            false,        // veil        是否叠径向压暗
            0xEE101010,   // backdrop    底盘
            0x00000000,   // rim         外缘细环（alpha=0 则不画）
            0xEE3F3F3F, 0xEE4A4A4A,   // innerA / innerB  内圈（短按带）
            0xEE2A2A2A, 0xEE323232,   // outerA / outerB  外圈（长按带）
            0xF0818181,   // hotInner    悬停：内圈
            0xF05E5E5E,   // hotOuter    悬停：外圈
            0xFCC6A34A,   // holdOuter   正在长按：外圈整块
            0xFFF0D68C,   // holdRim     正在长按：外缘亮边
            0xD0000000,   // groove      分界环的暗槽
            0xEBC6C6C6,   // split       分界环亮线
            2.0f,         // splitW      分界环线宽
            0xFF000000,   // sep         扇区分隔线
            2.0f,         // sepW
            0xFA0C0C0C,   // center      中心孔
            0xFFFFFFFF,   // text
            0xFFFFFFFF,   // textHot
            0xFFB4B4B4,   // textDim
            true),        // bevel       画立体边

    GLASS("glass",
            0x4C000000,
            true,
            0x28000000,
            0x3CFFFFFF,
            0x0CFFFFFF, 0x08FFFFFF,
            0x07FFFFFF, 0x05FFFFFF,
            0x30FFFFFF,
            0x1CFFFFFF,
            0x88F0B24A,
            0xCCFFD682,
            0x00000000,
            0x33FFFFFF,
            1.5f,
            0x30FFFFFF,
            1.0f,
            0x50000000,
            0xE8F1F5F8,
            0xFFFFFFFF,
            0xC8D2DCE2,
            false);

    public final String id;
    public final int screenDim;
    public final boolean veil;
    public final int backdrop;
    public final int rim;
    public final int innerA;
    public final int innerB;
    public final int outerA;
    public final int outerB;
    public final int hotInner;
    public final int hotOuter;
    public final int holdOuter;
    public final int holdRim;
    public final int groove;
    public final int split;
    public final float splitW;
    public final int sep;
    public final float sepW;
    public final int center;
    public final int text;
    public final int textHot;
    public final int textDim;
    public final boolean bevel;

    WheelStyle(String id, int screenDim, boolean veil, int backdrop, int rim,
               int innerA, int innerB, int outerA, int outerB,
               int hotInner, int hotOuter, int holdOuter, int holdRim,
               int groove, int split, float splitW,
               int sep, float sepW, int center,
               int text, int textHot, int textDim, boolean bevel) {
        this.id = id;
        this.screenDim = screenDim;
        this.veil = veil;
        this.backdrop = backdrop;
        this.rim = rim;
        this.innerA = innerA;
        this.innerB = innerB;
        this.outerA = outerA;
        this.outerB = outerB;
        this.hotInner = hotInner;
        this.hotOuter = hotOuter;
        this.holdOuter = holdOuter;
        this.holdRim = holdRim;
        this.groove = groove;
        this.split = split;
        this.splitW = splitW;
        this.sep = sep;
        this.sepW = sepW;
        this.center = center;
        this.text = text;
        this.textHot = textHot;
        this.textDim = textDim;
        this.bevel = bevel;
    }

    /** 存档里的字符串 -> 枚举；认不出来的（手改坏了、删掉的风格）一律回到 VANILLA */
    public static WheelStyle byId(String id) {
        if (id != null) {
            for (WheelStyle s : values()) {
                if (s.id.equalsIgnoreCase(id)) return s;
            }
        }
        return VANILLA;
    }

    public WheelStyle next() {
        WheelStyle[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** 语言键后缀，和 lang 里的 key_conflict_wheel.style.* 对应 */
    public String langKey() {
        return "key_conflict_wheel.style." + id;
    }
}
