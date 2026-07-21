package yourstageskybox.api;

import yourstageskybox.network.NetworkHandler;
import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 公共 API —— 供其他模组或脚本调用以控制天空盒切换。
 *
 * <pre>
 * // 全局（指定世界全部玩家）
 * SkyboxAPI.setSkybox(world, "dawn_red");
 * SkyboxAPI.clearSkybox(world);
 *
 * // 指定玩家 + 维度
 * SkyboxAPI.setSkybox(world, "dawn_red", player);
 * SkyboxAPI.clearSkybox(world, player);
 *
 * // 带 alpha / duration 参数
 * SkyboxAPI.setSkybox(world, "dawn_red", 0.7f, 3000, player);
 *
 * // 查询
 * String current = SkyboxAPI.getSkybox(world);
 * String playerSkybox = SkyboxAPI.getSkybox(world, player);
 * float alpha = SkyboxAPI.getSkyboxAlpha(world, player);
 * long duration = SkyboxAPI.getSkyboxDuration(world);
 * Map&lt;UUID, String&gt; allPlayers = SkyboxAPI.getAllPlayerSkyboxes(world);
 * Set&lt;String&gt; all = SkyboxAPI.getAvailableSkyboxes();
 * SkyboxAPI.registerSkybox("dawn_red");
 * </pre>
 */
public class SkyboxAPI {

    // ==================== 设置（全局） ====================

    /**
     * 将指定世界全部玩家的天空盒切换为给定名称。
     */
    public static boolean setSkybox(World world, String name) {
        return setSkybox(world, name, null);
    }

    /**
     * 将指定世界全部玩家的天空盒切换为给定名称，带透明度参数。
     */
    public static boolean setSkybox(World world, String name, float alpha) {
        return setSkybox(world, name, alpha, -1, null);
    }

    /**
     * 将指定世界全部玩家的天空盒切换为给定名称，带透明度 + 过渡时长。
     */
    public static boolean setSkybox(World world, String name, float alpha, long durationMs) {
        return setSkybox(world, name, alpha, durationMs, null);
    }

    // ==================== 设置（指定玩家） ====================

    /**
     * 将指定世界中特定玩家的天空盒切换为给定名称。
     * 如果 player 为 null，则作用于该世界所有玩家。
     */
    public static boolean setSkybox(World world, String name, EntityPlayerMP player) {
        return setSkybox(world, name, 1.0f, -1, player);
    }

    /**
     * 将指定世界中特定玩家的天空盒切换为给定名称，带 alpha/duration。
     *
     * @param world      目标世界
     * @param name       天空盒名称
     * @param alpha      透明度 0.0~1.0
     * @param durationMs 过渡时长毫秒（-1 = 使用默认值）
     * @param player     目标玩家（null = 全部玩家）
     */
    public static boolean setSkybox(World world, String name, float alpha, long durationMs, EntityPlayerMP player) {
        if (world == null || name == null || name.isEmpty()) return false;

        int dim = world.provider.getDimension();
        SkyboxState state = new SkyboxState(name, alpha);
        if (durationMs > 0) state.durationMs = durationMs;

        if (!world.isRemote) {
            if (player != null) {
                SkyboxManager.setActiveSkyboxForPlayer(player.getUniqueID(), dim, state);
                NetworkHandler.sendSkyboxSyncToPlayer(player, dim, state);
            } else {
                SkyboxManager.setActiveSkyboxServer(dim, state);
                NetworkHandler.sendSkyboxSync(dim, state);
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

    /**
     * 清除指定世界（特定玩家）的天空盒。
     * 如果 player 为 null，则清除该世界全部玩家的天空盒。
     */
    public static void clearSkybox(World world, EntityPlayerMP player) {
        if (world == null) return;

        int dim = world.provider.getDimension();

        if (!world.isRemote) {
            if (player != null) {
                SkyboxManager.clearActiveSkyboxForPlayer(player.getUniqueID(), dim);
                NetworkHandler.sendSkyboxClearToPlayer(player, dim);
            } else {
                SkyboxManager.clearActiveSkybox(dim);
                NetworkHandler.sendSkyboxClear(dim);
            }
        } else {
            SkyboxManager.clearActiveSkybox(dim);
        }
    }

    // ==================== Alpha / Color / Duration ====================

    /**
     * 设置指定玩家在某维度的天空盒透明度。
     */
    public static boolean setSkyboxAlpha(World world, float alpha, EntityPlayerMP player) {
        if (world == null || player == null) return false;
        int dim = world.provider.getDimension();
        SkyboxManager.setSkyboxAlphaForPlayer(player.getUniqueID(), dim, alpha);
        if (!world.isRemote) {
            SkyboxState state = SkyboxManager.getSkyboxStateForPlayer(player.getUniqueID(), dim);
            if (state != null) {
                NetworkHandler.sendSkyboxSyncToPlayer(player, dim, state);
            }
        }
        return true;
    }

    /**
     * 设置指定玩家在某维度的天空盒色相。
     */
    public static boolean setSkyboxColor(World world, float r, float g, float b, EntityPlayerMP player) {
        if (world == null || player == null) return false;
        int dim = world.provider.getDimension();
        SkyboxState state = SkyboxManager.getSkyboxStateForPlayer(player.getUniqueID(), dim);
        if (state != null) {
            state.red   = Math.max(0f, Math.min(1f, r));
            state.green = Math.max(0f, Math.min(1f, g));
            state.blue  = Math.max(0f, Math.min(1f, b));
            SkyboxManager.setActiveSkyboxForPlayer(player.getUniqueID(), dim, state);
            if (!world.isRemote) {
                NetworkHandler.sendSkyboxSyncToPlayer(player, dim, state);
            }
        }
        return true;
    }

    /**
     * 设置指定维度的默认过渡时长（全局，非玩家特定）。
     */
    public static void setSkyboxDuration(World world, long durationMs) {
        if (world == null) return;
        SkyboxManager.setDefaultDuration(world.provider.getDimension(), durationMs);
    }

    // ==================== 查询 ====================

    public static String getSkybox(World world) {
        if (world == null) return null;
        return SkyboxManager.getActiveSkybox(world.provider.getDimension());
    }

    public static String getSkybox(World world, EntityPlayerMP player) {
        if (world == null || player == null) return null;
        return SkyboxManager.getActiveSkyboxForPlayer(player.getUniqueID(), world.provider.getDimension());
    }

    /**
     * 获取指定玩家在某维度的天空盒透明度。
     */
    public static float getSkyboxAlpha(World world, EntityPlayerMP player) {
        if (world == null || player == null) return 1.0f;
        SkyboxState state = SkyboxManager.getSkyboxStateForPlayer(player.getUniqueID(), world.provider.getDimension());
        return (state != null) ? state.alpha : 1.0f;
    }

    /**
     * 获取指定维度的默认过渡时长（毫秒）。
     */
    public static long getSkyboxDuration(World world) {
        if (world == null) return 2000L;
        return SkyboxManager.getDefaultDuration(world.provider.getDimension());
    }

    /**
     * 获取指定玩家的完整天空盒状态。
     */
    public static SkyboxState getSkyboxState(World world, EntityPlayerMP player) {
        if (world == null || player == null) return null;
        return SkyboxManager.getSkyboxStateForPlayer(player.getUniqueID(), world.provider.getDimension());
    }

    public static Map<UUID, String> getAllPlayerSkyboxes(World world) {
        if (world == null) return Collections.emptyMap();
        int dim = world.provider.getDimension();
        Map<UUID, String> result = new HashMap<>();
        for (Map.Entry<UUID, Map<Integer, SkyboxState>> entry : SkyboxManager.getAllPlayerSkyboxesEntries()) {
            SkyboxState state = entry.getValue().get(dim);
            if (state != null) {
                result.put(entry.getKey(), state.name);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    // ==================== 注册 ====================

    public static void registerSkybox(String name) {
        SkyboxManager.registerSkyboxName(name);
    }

    public static Set<String> getAvailableSkyboxes() {
        return SkyboxManager.getRegisteredSkyboxes();
    }
}
