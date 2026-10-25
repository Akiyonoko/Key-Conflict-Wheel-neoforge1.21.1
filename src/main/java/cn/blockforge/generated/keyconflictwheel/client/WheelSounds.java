package cn.blockforge.generated.keyconflictwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * 三档提示音：悬停轻响、确认叮声、拉黑低沉音（都用原版音效变调，无需资源）。
 */
public final class WheelSounds {
    private WheelSounds() {}

    private static void play(net.minecraft.sounds.SoundEvent event, float pitch, float volume) {
        if (!WheelConfig.get().soundsEnabled) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    public static void hover() {
        play(SoundEvents.NOTE_BLOCK_HARP.value(), 1.7f, 0.22f);
    }

    public static void confirm() {
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 0.5f);
    }

    public static void blacklist() {
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
    }

    public static void open() {
        play(SoundEvents.NOTE_BLOCK_HARP.value(), 1.2f, 0.3f);
    }

    public static void endHold() {
        play(SoundEvents.NOTE_BLOCK_HARP.value(), 0.6f, 0.35f);
    }

    public static void openMenu() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f, 0.4f);
    }
}
