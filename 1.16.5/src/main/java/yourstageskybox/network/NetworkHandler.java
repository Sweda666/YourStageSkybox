package yourstageskybox.network;

import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/**
 * 网络包注册与收发管理（1.16.5 SimpleChannel 版本）。
 */
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("yourstageskybox", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void init() {
        CHANNEL.registerMessage(
                packetId++,
                PacketSyncSkybox.class,
                PacketSyncSkybox::encode,
                PacketSyncSkybox::decode,
                PacketSyncSkybox::handle
        );
    }

    // ==================== 发送（维度级别） ====================

    /** 向指定维度的所有玩家同步天空盒状态。 */
    public static void sendSkyboxSync(World world, SkyboxState state) {
        int dimId = SkyboxManager.getDimensionId(world);
        CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> world.dimension()),
                toPacket(dimId, state, SkyboxManager.getDefaultDuration(dimId))
        );
    }

    /** 清除指定维度的天空盒（恢复原版）。 */
    public static void sendSkyboxClear(World world) {
        int dimId = SkyboxManager.getDimensionId(world);
        CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> world.dimension()),
                new PacketSyncSkybox(dimId, null)
        );
    }

    // ==================== 发送（玩家级别） ====================

    public static void sendSkyboxSyncToPlayer(ServerPlayerEntity player, int dimension, SkyboxState state) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                toPacket(dimension, state, SkyboxManager.getDefaultDuration(dimension))
        );
    }

    /** 清除指定玩家某维度的天空盒（恢复原版）。 */
    public static void sendSkyboxClearToPlayer(ServerPlayerEntity player, int dimension) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncSkybox(dimension, null)
        );
    }

    private static PacketSyncSkybox toPacket(int dimension, SkyboxState state, long defaultDuration) {
        if (state == null) return new PacketSyncSkybox(dimension, null);
        return new PacketSyncSkybox(
                dimension, state.name,
                state.alpha, state.red, state.green, state.blue,
                state.durationMs > 0 ? state.durationMs : defaultDuration);
    }
}
