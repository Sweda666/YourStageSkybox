package yourstageskybox.network;

import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 网络包注册与收发管理。
 */
public class NetworkHandler {

    private static final String CHANNEL_NAME = "YSB_SKYBOX";
    private static SimpleNetworkWrapper network;
    private static int discriminator = 0;

    public static void init() {
        network = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        network.registerMessage(HandlerSyncSkybox.class, PacketSyncSkybox.class, discriminator++, Side.CLIENT);
    }

    // ==================== 发送 ====================

    public static void sendSkyboxSync(int dimension, SkyboxState state) {
        if (network == null) return;
        network.sendToDimension(toPacket(dimension, state, SkyboxManager.getDefaultDuration(dimension)), dimension);
    }

    /** 清除指定维度的天空盒（恢复原版）。 */
    public static void sendSkyboxClear(int dimension) {
        if (network == null) return;
        network.sendToDimension(new PacketSyncSkybox(dimension, null), dimension);
    }

    public static void sendSkyboxSyncToPlayer(EntityPlayerMP player, int dimension, SkyboxState state) {
        if (network == null) return;
        network.sendTo(toPacket(dimension, state, SkyboxManager.getDefaultDuration(dimension)), player);
    }

    /** 清除指定玩家某维度的天空盒（恢复原版）。 */
    public static void sendSkyboxClearToPlayer(EntityPlayerMP player, int dimension) {
        if (network == null) return;
        network.sendTo(new PacketSyncSkybox(dimension, null), player);
    }

    private static PacketSyncSkybox toPacket(int dimension, SkyboxState state, long defaultDuration) {
        if (state == null) return new PacketSyncSkybox(dimension, null);
        return new PacketSyncSkybox(
                dimension, state.name,
                state.alpha, state.red, state.green, state.blue,
                state.durationMs > 0 ? state.durationMs : defaultDuration);
    }

    // --------------------------------------------------

    public static class HandlerSyncSkybox implements IMessageHandler<PacketSyncSkybox, IMessage> {
        @Override
        public IMessage onMessage(PacketSyncSkybox message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                int dim = message.getDimension();
                String name = message.getSkyboxName();
                if (name == null || name.isEmpty()) {
                    SkyboxState current = SkyboxManager.getSkyboxState(dim);
                    if (current != null && !current.isVanilla()) {
                        // 有当前天空盒 → 创建过渡到原版
                        SkyboxState vanilla = new SkyboxState(null);
                        vanilla.durationMs = current.durationMs;
                        vanilla.alpha = current.alpha;
                        SkyboxManager.setActiveSkybox(dim, vanilla);
                    } else {
                        SkyboxManager.clearActiveSkybox(dim);
                    }
                } else {
                    SkyboxState state = new SkyboxState(
                            name,
                            message.getAlpha(),
                            message.getDurationMs());
                    state.red = message.getRed();
                    state.green = message.getGreen();
                    state.blue = message.getBlue();
                    SkyboxManager.setActiveSkybox(dim, state);
                }
            });
            return null;
        }
    }
}
