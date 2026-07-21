package yourstageskybox.api;

import yourstageskybox.network.NetworkHandler;
import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 公共 API —— 1.20.1 版本。
 */
public class SkyboxAPI {

    public static boolean setSkybox(Level level, String name) { return setSkybox(level, name, null); }
    public static boolean setSkybox(Level level, String name, float alpha) { return setSkybox(level, name, alpha, -1, null); }
    public static boolean setSkybox(Level level, String name, float alpha, long durationMs) { return setSkybox(level, name, alpha, durationMs, null); }
    public static boolean setSkybox(Level level, String name, ServerPlayer player) { return setSkybox(level, name, 1.0f, -1, player); }

    public static boolean setSkybox(Level level, String name, float alpha, long durationMs, ServerPlayer player) {
        if (level == null || name == null || name.isEmpty()) return false;
        int dim = SkyboxManager.getDimensionId(level);
        SkyboxState state = new SkyboxState(name, alpha);
        if (durationMs > 0) state.durationMs = durationMs;

        if (!level.isClientSide()) {
            if (player != null) {
                SkyboxManager.setActiveSkyboxForPlayer(player.getUUID(), dim, state);
                NetworkHandler.sendSkyboxSyncToPlayer(player, dim, state);
            } else {
                SkyboxManager.setActiveSkyboxServer(dim, state);
                NetworkHandler.sendSkyboxSync(level, state);
            }
            return true;
        } else {
            SkyboxManager.setActiveSkybox(dim, state);
            return SkyboxManager.getActiveSkybox(dim) != null;
        }
    }

    public static void clearSkybox(Level level) { clearSkybox(level, null); }

    public static void clearSkybox(Level level, ServerPlayer player) {
        if (level == null) return;
        int dim = SkyboxManager.getDimensionId(level);
        if (!level.isClientSide()) {
            if (player != null) {
                SkyboxManager.clearActiveSkyboxForPlayer(player.getUUID(), dim);
                NetworkHandler.sendSkyboxClearToPlayer(player, dim);
            } else {
                SkyboxManager.clearActiveSkybox(dim);
                NetworkHandler.sendSkyboxClear(level);
            }
        } else {
            SkyboxManager.clearActiveSkybox(dim);
        }
    }

    public static boolean setSkyboxAlpha(Level level, float alpha, ServerPlayer player) {
        if (level == null || player == null) return false;
        int dim = SkyboxManager.getDimensionId(level);
        SkyboxManager.setSkyboxAlphaForPlayer(player.getUUID(), dim, alpha);
        if (!level.isClientSide()) {
            SkyboxState state = SkyboxManager.getSkyboxStateForPlayer(player.getUUID(), dim);
            if (state != null) NetworkHandler.sendSkyboxSyncToPlayer(player, dim, state);
        }
        return true;
    }

    public static boolean setSkyboxColor(Level level, float r, float g, float b, ServerPlayer player) {
        if (level == null || player == null) return false;
        int dim = SkyboxManager.getDimensionId(level);
        SkyboxState state = SkyboxManager.getSkyboxStateForPlayer(player.getUUID(), dim);
        if (state != null) {
            state.red = clamp(r); state.green = clamp(g); state.blue = clamp(b);
            if (!level.isClientSide()) NetworkHandler.sendSkyboxSyncToPlayer(player, dim, state);
        }
        return true;
    }

    public static void setSkyboxDuration(Level level, long durationMs) {
        if (level == null) return;
        SkyboxManager.setDefaultDuration(SkyboxManager.getDimensionId(level), durationMs);
    }

    public static String getSkybox(Level level) {
        return level != null ? SkyboxManager.getActiveSkybox(SkyboxManager.getDimensionId(level)) : null;
    }

    public static String getSkybox(Level level, ServerPlayer player) {
        if (level == null || player == null) return null;
        return SkyboxManager.getActiveSkyboxForPlayer(player.getUUID(), SkyboxManager.getDimensionId(level));
    }

    public static float getSkyboxAlpha(Level level, ServerPlayer player) {
        if (level == null || player == null) return 1.0f;
        SkyboxState state = SkyboxManager.getSkyboxStateForPlayer(player.getUUID(), SkyboxManager.getDimensionId(level));
        return (state != null) ? state.alpha : 1.0f;
    }

    public static long getSkyboxDuration(Level level) {
        return level != null ? SkyboxManager.getDefaultDuration(SkyboxManager.getDimensionId(level)) : 2000L;
    }

    public static SkyboxState getSkyboxState(Level level, ServerPlayer player) {
        if (level == null || player == null) return null;
        return SkyboxManager.getSkyboxStateForPlayer(player.getUUID(), SkyboxManager.getDimensionId(level));
    }

    public static Map<UUID, String> getAllPlayerSkyboxes(Level level) {
        if (level == null) return Collections.emptyMap();
        int dim = SkyboxManager.getDimensionId(level);
        Map<UUID, String> result = new HashMap<>();
        for (Map.Entry<UUID, Map<Integer, SkyboxState>> entry : SkyboxManager.getAllPlayerSkyboxesEntries()) {
            SkyboxState state = entry.getValue().get(dim);
            if (state != null) result.put(entry.getKey(), state.name);
        }
        return Collections.unmodifiableMap(result);
    }

    public static void registerSkybox(String name) { SkyboxManager.registerSkyboxName(name); }
    public static Set<String> getAvailableSkyboxes() { return SkyboxManager.getRegisteredSkyboxes(); }

    private static float clamp(float v) { return v < 0 ? 0 : Math.min(v, 1); }
}
