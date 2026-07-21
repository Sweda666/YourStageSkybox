package yourstageskybox.network;

import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络包注册与收发管理（1.21.1 SimpleChannel 版本）。
 */
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("yourstageskybox", "main"),
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

    // ==================== 维度级别 ====================

    public static void sendSkyboxSync(Level level, SkyboxState state) {
        int dimId = SkyboxManager.getDimensionId(level);
        CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> level.dimension()),
                toPacket(dimId, state, SkyboxManager.getDefaultDuration(dimId))
        );
    }

    public static void sendSkyboxClear(Level level) {
        int dimId = SkyboxManager.getDimensionId(level);
        CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> level.dimension()),
                new PacketSyncSkybox(dimId, null)
        );
    }

    // ==================== 玩家级别 ====================

    public static void sendSkyboxSyncToPlayer(ServerPlayer player, int dimension, SkyboxState state) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                toPacket(dimension, state, SkyboxManager.getDefaultDuration(dimension))
        );
    }

    public static void sendSkyboxClearToPlayer(ServerPlayer player, int dimension) {
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
