package cn.blockforge.generated.keyconflictwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * 物理按键引用：输入类型（键盘键 / 鼠标键）+ 按键码。用于拉黑表与长按记录。
 */
public final class KeyRef {
    public final InputConstants.Type type;
    public final int code;

    public KeyRef(InputConstants.Type type, int code) {
        this.type = type;
        this.code = code;
    }

    public InputConstants.Key toInputKey() {
        return type.getOrCreate(code);
    }

    public Component display() {
        return toInputKey().getDisplayName();
    }

    public String serialize() {
        return type.name() + ":" + code;
    }

    public static KeyRef deserialize(String s) {
        int idx = s.indexOf(':');
        if (idx <= 0 || idx == s.length() - 1) {
            return null;
        }
        try {
            InputConstants.Type t = InputConstants.Type.valueOf(s.substring(0, idx));
            int c = Integer.parseInt(s.substring(idx + 1));
            return new KeyRef(t, c);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeyRef other)) return false;
        return code == other.code && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, code);
    }

    @Override
    public String toString() {
        return serialize();
    }
}
