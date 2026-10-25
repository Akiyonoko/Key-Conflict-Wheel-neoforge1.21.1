package cn.blockforge.generated.keyconflictwheel;

import cn.blockforge.generated.keyconflictwheel.client.ConflictWheelClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 快捷键冲突轮盘：同一个按键被多个模组绑定时，按住该键弹出圆盘；
 * 松开按键的那一刻，鼠标停在哪个圈层就触发什么——内圈=短按一次，
 * 外圈=长按保持（再按原键结束），中心/圈外=取消。
 * 轮盘右下角可一键拉黑该键，K 键打开总控菜单（总开关/音效/轮盘大小）。
 */
@Mod(KeyConflictWheelMod.MOD_ID)
public final class KeyConflictWheelMod {
    public static final String MOD_ID = "key_conflict_wheel";

    public KeyConflictWheelMod(IEventBus modBus, Dist dist) {
        if (dist == Dist.CLIENT) {
            modBus.addListener(ConflictWheelClient::onRegisterKeyMappings);
            NeoForge.EVENT_BUS.register(ConflictWheelClient.INSTANCE);
        }
    }
}
