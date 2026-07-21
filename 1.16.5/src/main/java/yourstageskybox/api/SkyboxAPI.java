package yourstageskybox.api;

import yourstageskybox.network.NetworkHandler;
import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.*;

/**
 * 公共 API —— 1.16.5 版本。
 * API 签名与 1.12.2 基本兼容，EntityPlayerMP → ServerPlayerEntity。
 */
public class SkyboxAPI {

    // ==================== 设置 ====================

    public static boolean setSkybox(World world, String name) {
        return setSkybox(world, name, null);
    }

    public static boolean setSkybox(World world, String name, float alpha) {
        return setSkybox(world, name, alpha, -1, null);
    }

    public static boolean setSkybox(World world, String name, float alpha, long durationMs) {
        return setSkybox(world, name, alpha, durationMs, null);
    }

    public static boolean setSkybox(World world, String name, ServerPlayerEntity player) {
        return setSkybox(world, name, 1.0f, -1, player);
    }

    public static boolean setSkybox(World world, String name, float alpha, long durationMs,
                                     ServerPlayerEntity player) {
        if (world == null || name == null || name.isEmpty()) return false;

        int dim = SkyboxManager.getDimensionId(world);
        SkyboxState state = new SkyboxState(name, alpha);
        if (durationMs > 0) state.durationMs = durationMs;

        if (!world.isClientSide()) {
            if (player != null) {
                SkyboxManager.setActiveSkyboxForPlayer(player.getUUID(), dim, state);
                NetworkHandler.sendSkyboxSyncToPlayer(player, dim, state);
            } else {
                SkyboxManager.setActiveSkyboxServer(dim, state);
                NetworkHandler.sendSkyboxSync(world, state);
            }
            return true;
        } else {
            SkyboxManager.setActiveSkybox(dim, state);
            return SkyboxManager.getActiveSkybox(dim) != null;
        }
    }

    // ==================== 清除 ====================

    public static void clearSkybox(World world) {
        clearSkybox(world, null);
    }

    public static void clearSkybox(World world, ServerPlayerEntity player) {
        if (world == null) return;
        int dim = SkyboxManager.getDimensionId(world);

        if (!world.isClientSide()) {
            if (player != null) {
                SkyboxManager.clearActiveSkyboxForPlayer(player.getUUID(), dim);
                NetworkHandler.sendSkyboxClearToPlayer(player, dim);
            } else {
                SkyboxManager.clearActiveSkybox(dim);
                NetworkHandler.sendSkyboxClear(world);
            }
        } else {
            SkyboxManager.clearActiveSkybox(dim);
        }
    }

    // ==================== Alpha / Color / Duration ====================

    public static boolean setSkyboxAlpha(World world, float alpha, ServerPlayerEntity player) {
        if (world == null || player == null) return false;
        int dim = SkyboxManager.getDimensionId(world);
        SkyboxManager.setSkyboxAlphaForPlayer(player.getUUID(), dim, alpha);
        if (!world.isClientSide()) {
            SkyboxState state = SkyboxManager.getSkyboxStateForPlayer(player.getUUID(), dim);
            if (state != null) NetworkHandler.sendSkyboxSyncToPlayer(player, dim, state);
        }
        return true;
    }

    public static boolean setSkyboxColor(World world, float r, float g, float b, ServerPlayerEntity player) {
        if (world == null || player == null) return false;
        int dim = SkyboxManager.getDimensionId(world);
        SkyboxState state = SkyboxManager.getSkyboxStateForPlayer(player.getUUID(), dim);
        if (state != null) {
            state.red = clamp(r); state.green = clamp(g); state.blue = clamp(b);
            SkyboxManager.setActiveSkyboxForPlayer(player.getUUID(), dim, state);
            if (!world.isClientSide()) NetworkHandler.sendSkyboxSyncToPlayer(player, dim, state);
        }
        return true;
    }

    public static void setSkyboxDuration(World world, long durationMs) {
        if (world == null) return;
        SkyboxManager.setDefaultDuration(SkyboxManager.getDimensionId(world), durationMs);
    }

    // ==================== 查询 ====================

    public static String getSkybox(World world) {
        if (world == null) return null;
        return SkyboxManager.getActiveSkybox(SkyboxManager.getDimensionId(world));
    }

    public static String getSkybox(World world, ServerPlayerEntity player) {
        if (world == null || player == null) return null;
        return SkyboxManager.getActiveSkyboxForPlayer(player.getUUID(), SkyboxManager.getDimensionId(world));
    }

    public static float getSkyboxAlpha(World world, ServerPlayerEntity player) {
        if (world == null || player == null) return 1.0f;
        SkyboxState state = SkyboxManager.getSkyboxStateForPlayer(player.getUUID(), SkyboxManager.getDimensionId(world));
        return (state != null) ? state.alpha : 1.0f;
    }

    public static long getSkyboxDuration(World world) {
        if (world == null) return 2000L;
        return SkyboxManager.getDefaultDuration(SkyboxManager.getDimensionId(world));
    }

    public static SkyboxState getSkyboxState(World world, ServerPlayerEntity player) {
        if (world == null || player == null) return null;
        return SkyboxManager.getSkyboxStateForPlayer(player.getUUID(), SkyboxManager.getDimensionId(world));
    }

    public static Map<UUID, String> getAllPlayerSkyboxes(World world) {
        if (world == null) return Collections.emptyMap();
        int dim = SkyboxManager.getDimensionId(world);
        Map<UUID, String> result = new HashMap<>();
        for (Map.Entry<UUID, Map<Integer, SkyboxState>> entry : SkyboxManager.getAllPlayerSkyboxesEntries()) {
            SkyboxState state = entry.getValue().get(dim);
            if (state != null) result.put(entry.getKey(), state.name);
        }
        return Collections.unmodifiableMap(result);
    }

    public static void registerSkybox(String name) {
        SkyboxManager.registerSkyboxName(name);
    }

    public static Set<String> getAvailableSkyboxes() {
        return SkyboxManager.getRegisteredSkyboxes();
    }

    private static float clamp(float v) { return v < 0 ? 0 : Math.min(v, 1); }
}
