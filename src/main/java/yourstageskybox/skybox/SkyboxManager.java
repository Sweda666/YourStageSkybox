package yourstageskybox.skybox;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 天空盒管理器 —— 1.20.1 版本。
 */
public class SkyboxManager {

    public static final String SKYBOX_DOMAIN = "yourstageskybox";
    public static final String SKYBOX_PATH_PREFIX = "skyboxes";
    public static final String[] FACE_SUFFIXES = {
            "panorama_0", "panorama_1", "panorama_2",
            "panorama_3", "panorama_4", "panorama_5"
    };

    private static final Set<String> registeredSkyboxes = new LinkedHashSet<>();
    private static final Map<Integer, SkyboxState> activeSkyboxes = new HashMap<>();
    private static final Map<UUID, Map<Integer, SkyboxState>> playerActiveSkyboxes = new HashMap<>();
    private static SkyboxWorldData worldData;

    private static final Map<Integer, TransitionState> transitions = new HashMap<>();
    private static final Map<Integer, Long> defaultDurationPerDim = new HashMap<>();

    private static final Map<String, ResourceLocation[]> skyboxTextures = new LinkedHashMap<>();

    // ==================== 维度 ID 映射 ====================

    /** 将 Level 映射为内部使用的整数维度标识 */
    public static int getDimensionId(Level level) {
        ResourceLocation loc = level.dimension().location();
        if (loc.equals(new ResourceLocation("overworld"))) return 0;
        if (loc.equals(new ResourceLocation("the_nether"))) return -1;
        if (loc.equals(new ResourceLocation("the_end"))) return 1;
        return loc.toString().hashCode();
    }

    /**
     * 通过整数维度 ID 查找 Level（需服务器上下文）。
     */
    @Nullable
    public static Level getLevelByDimensionId(net.minecraft.server.MinecraftServer server, int dimId) {
        for (net.minecraft.server.level.ServerLevel sl : server.getAllLevels()) {
            if (getDimensionId(sl) == dimId) return sl;
        }
        return null;
    }

    // ==================== 初始化 ====================

    @OnlyIn(Dist.CLIENT)
    public static void initClient() {
        registeredSkyboxes.clear();
        skyboxTextures.clear();
        activeSkyboxes.clear();
        transitions.clear();
    }

    public static void initWorldData(SkyboxWorldData data) { worldData = data; }
    private static void markPlayerDataDirty() { if (worldData != null) worldData.setDirty(); }
    public static SkyboxWorldData getWorldData() { return worldData; }

    // ==================== 自动发现 ====================

    @OnlyIn(Dist.CLIENT)
    public static Set<String> discoverSkyboxes() {
        Set<String> found = new LinkedHashSet<>();
        String basePath = "assets/" + SKYBOX_DOMAIN + "/textures/" + SKYBOX_PATH_PREFIX + "/";

        Minecraft mc = Minecraft.getInstance();
        ResourceManager rm = mc.getResourceManager();

        // 遍历所有已加载资源包的命名空间
        for (String namespace : rm.getNamespaces()) {
            if (!SKYBOX_DOMAIN.equals(namespace)) continue;

            // 尝试列出 skyboxes 目录下的内容
            ResourceLocation testLoc = new ResourceLocation(SKYBOX_DOMAIN,
                    "textures/" + SKYBOX_PATH_PREFIX + "/.placeholder");
            try {
                // 使用 listResources 递归查找 panorama_0.png
                Map<ResourceLocation, Resource> resources = rm.listResources(
                        "textures/" + SKYBOX_PATH_PREFIX,
                        loc -> loc.getPath().endsWith("panorama_0.png")
                );
                for (ResourceLocation loc : resources.keySet()) {
                    String path = loc.getPath(); // textures/skyboxes/<name>/panorama_0.png
                    String sub = path.substring(("textures/" + SKYBOX_PATH_PREFIX + "/").length());
                    int slash = sub.indexOf('/');
                    if (slash > 0) {
                        found.add(sub.substring(0, slash));
                    }
                }
            } catch (Exception ignored) {}
        }

        for (String name : found) {
            if (!registeredSkyboxes.contains(name)) {
                registerSkybox(name);
            }
        }
        return found;
    }

    // ==================== 注册 ====================

    @OnlyIn(Dist.CLIENT)
    public static boolean registerSkybox(String name) {
        if (name == null || name.isEmpty()) return false;

        ResourceLocation[] textures = new ResourceLocation[6];
        for (int i = 0; i < 6; i++) {
            textures[i] = buildFaceLocation(name, i);
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            mc.getResourceManager().getResourceOrThrow(buildFaceLocation(name, 0));
        } catch (Exception e) {
            return false;
        }

        skyboxTextures.put(name, textures);
        registeredSkyboxes.add(name);
        return true;
    }

    public static void registerSkyboxName(String name) {
        if (name != null && !name.isEmpty()) registeredSkyboxes.add(name);
    }

    // ==================== 激活 / 清除 ====================

    @OnlyIn(Dist.CLIENT)
    public static void setActiveSkybox(int dimension, SkyboxState state) {
        if (state == null) { activeSkyboxes.remove(dimension); return; }
        String newName = state.name;
        if (newName != null && !newName.isEmpty() && !skyboxTextures.containsKey(newName)) {
            if (!registerSkybox(newName)) return;
        }
        SkyboxState oldState = activeSkyboxes.get(dimension);
        String oldName = (oldState != null) ? oldState.name : null;

        if (Objects.equals(oldName, newName)) {
            activeSkyboxes.put(dimension, state);
            transitions.remove(dimension);
            return;
        }

        long duration = (state.durationMs > 0) ? state.durationMs : getDefaultDuration(dimension);
        float oldA = (oldState != null) ? oldState.alpha : 1.0f;
        float oldR = (oldState != null && !oldState.isVanilla()) ? oldState.red : 1.0f;
        float oldG = (oldState != null && !oldState.isVanilla()) ? oldState.green : 1.0f;
        float oldB = (oldState != null && !oldState.isVanilla()) ? oldState.blue : 1.0f;
        float newA = state.alpha;
        float newR = (!state.isVanilla()) ? state.red : 1.0f;
        float newG = (!state.isVanilla()) ? state.green : 1.0f;
        float newB = (!state.isVanilla()) ? state.blue : 1.0f;

        transitions.put(dimension, new TransitionState(oldName, newName, duration,
                oldA, oldR, oldG, oldB, newA, newR, newG, newB));
        activeSkyboxes.put(dimension, state);
    }

    @OnlyIn(Dist.CLIENT)
    public static void setActiveSkybox(int dimension, String name) {
        SkyboxState state = new SkyboxState(name);
        SkyboxState oldState = activeSkyboxes.get(dimension);
        if (oldState != null && !oldState.isVanilla()) {
            state.alpha = oldState.alpha; state.red = oldState.red;
            state.green = oldState.green; state.blue = oldState.blue;
            state.durationMs = oldState.durationMs;
        }
        setActiveSkybox(dimension, state);
    }

    public static void clearActiveSkybox(int dimension) {
        activeSkyboxes.remove(dimension);
        transitions.remove(dimension);
    }

    // ---- 服务端 ----
    public static void setActiveSkyboxServer(int dimension, SkyboxState state) {
        if (state == null || state.isVanilla()) activeSkyboxes.remove(dimension);
        else activeSkyboxes.put(dimension, state);
    }

    public static void setActiveSkyboxForPlayer(UUID playerId, int dimension, SkyboxState state) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.computeIfAbsent(playerId, k -> new HashMap<>());
        if (state == null || state.isVanilla()) {
            playerMap.remove(dimension);
            if (playerMap.isEmpty()) playerActiveSkyboxes.remove(playerId);
        } else {
            playerMap.put(dimension, state);
        }
        markPlayerDataDirty();
    }

    public static void clearActiveSkyboxForPlayer(UUID playerId, int dimension) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        if (playerMap != null) {
            playerMap.remove(dimension);
            if (playerMap.isEmpty()) playerActiveSkyboxes.remove(playerId);
        }
        markPlayerDataDirty();
    }

    // ==================== Alpha / Color / Duration ====================
    public static void setSkyboxAlpha(int dimension, float alpha) {
        SkyboxState state = activeSkyboxes.get(dimension);
        if (state != null) state.alpha = clamp(alpha);
    }

    public static void setSkyboxAlphaForPlayer(UUID playerId, int dimension, float alpha) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        if (playerMap != null) {
            SkyboxState state = playerMap.get(dimension);
            if (state != null) { state.alpha = clamp(alpha); markPlayerDataDirty(); }
        }
    }

    public static void setDefaultDuration(int dimension, long durationMs) {
        if (durationMs > 0) defaultDurationPerDim.put(dimension, durationMs);
    }

    public static long getDefaultDuration(int dimension) {
        Long val = defaultDurationPerDim.get(dimension);
        return (val != null && val > 0) ? val : 2000L;
    }

    // ==================== 查询 ====================
    public static String getActiveSkybox(int dimension) {
        SkyboxState state = activeSkyboxes.get(dimension);
        return (state != null) ? state.name : null;
    }

    public static SkyboxState getSkyboxState(int dimension) { return activeSkyboxes.get(dimension); }

    @OnlyIn(Dist.CLIENT)
    public static TransitionState getTransition(int dimension) { return transitions.get(dimension); }

    @OnlyIn(Dist.CLIENT)
    public static TransitionState updateTransition(int dimension) {
        TransitionState t = transitions.get(dimension);
        if (t == null) return null;
        if (t.isComplete()) { transitions.remove(dimension); return null; }
        return t;
    }

    public static String getActiveSkyboxForPlayer(UUID playerId, int dimension) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        if (playerMap != null) {
            SkyboxState state = playerMap.get(dimension);
            return (state != null) ? state.name : null;
        }
        return null;
    }

    public static SkyboxState getSkyboxStateForPlayer(UUID playerId, int dimension) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        return (playerMap != null) ? playerMap.get(dimension) : null;
    }

    public static Map<Integer, SkyboxState> getAllActiveSkyboxesForPlayer(UUID playerId) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        return playerMap != null ? new HashMap<>(playerMap) : Collections.emptyMap();
    }

    public static Set<Map.Entry<UUID, Map<Integer, SkyboxState>>> getAllPlayerSkyboxesEntries() {
        return Collections.unmodifiableSet(new HashSet<>(playerActiveSkyboxes.entrySet()));
    }

    @OnlyIn(Dist.CLIENT)
    public static ResourceLocation[] getSkyboxTextures(String name) {
        if (name == null || name.isEmpty()) return null;
        return skyboxTextures.get(name);
    }

    public static Set<String> getRegisteredSkyboxes() {
        return Collections.unmodifiableSet(registeredSkyboxes);
    }

    // ==================== 持久化 ====================
    public static void loadPlayerDataFromNBT(CompoundTag nbt) {
        playerActiveSkyboxes.clear();
        if (!nbt.contains("Players")) return;
        ListTag playerList = nbt.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag playerTag = playerList.getCompound(i);
            UUID uuid = UUID.fromString(playerTag.getString("UUID"));
            Map<Integer, SkyboxState> dimMap = new HashMap<>();
            ListTag dimList = playerTag.getList("Dimensions", Tag.TAG_COMPOUND);
            for (int j = 0; j < dimList.size(); j++) {
                CompoundTag dimTag = dimList.getCompound(j);
                int dim = dimTag.getInt("Dim");
                SkyboxState state = new SkyboxState(dimTag.getString("Skybox"));
                if (dimTag.contains("Alpha")) state.alpha = dimTag.getFloat("Alpha");
                if (dimTag.contains("Red")) state.red = dimTag.getFloat("Red");
                if (dimTag.contains("Green")) state.green = dimTag.getFloat("Green");
                if (dimTag.contains("Blue")) state.blue = dimTag.getFloat("Blue");
                if (dimTag.contains("Duration")) state.durationMs = dimTag.getLong("Duration");
                dimMap.put(dim, state);
            }
            playerActiveSkyboxes.put(uuid, dimMap);
        }
    }

    public static void savePlayerDataToNBT(CompoundTag compound) {
        ListTag playerList = new ListTag();
        for (Map.Entry<UUID, Map<Integer, SkyboxState>> entry : playerActiveSkyboxes.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString("UUID", entry.getKey().toString());
            ListTag dimList = new ListTag();
            for (Map.Entry<Integer, SkyboxState> dimEntry : entry.getValue().entrySet()) {
                CompoundTag dimTag = new CompoundTag();
                dimTag.putInt("Dim", dimEntry.getKey());
                SkyboxState state = dimEntry.getValue();
                dimTag.putString("Skybox", state.name != null ? state.name : "");
                dimTag.putFloat("Alpha", state.alpha);
                dimTag.putFloat("Red", state.red);
                dimTag.putFloat("Green", state.green);
                dimTag.putFloat("Blue", state.blue);
                dimTag.putLong("Duration", state.durationMs);
                dimList.add(dimTag);
            }
            playerTag.put("Dimensions", dimList);
            playerList.add(playerTag);
        }
        compound.put("Players", playerList);
    }

    // ==================== 重载 ====================
    @OnlyIn(Dist.CLIENT)
    public static void reloadSkyboxes() {
        Set<String> names = new LinkedHashSet<>(registeredSkyboxes);
        skyboxTextures.clear();
        registeredSkyboxes.clear();
        for (String name : names) { registerSkybox(name); }
        for (var entry : new HashMap<>(activeSkyboxes).entrySet()) {
            SkyboxState state = entry.getValue();
            if (state != null && state.name != null && !skyboxTextures.containsKey(state.name))
                activeSkyboxes.remove(entry.getKey());
        }
        for (var entry : new HashMap<>(transitions).entrySet()) {
            TransitionState t = entry.getValue();
            if ((t.fromName != null && !skyboxTextures.containsKey(t.fromName)) ||
                (t.toName != null && !skyboxTextures.containsKey(t.toName)))
                transitions.remove(entry.getKey());
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static ResourceLocation buildFaceLocation(String name, int index) {
        return new ResourceLocation(SKYBOX_DOMAIN,
                "textures/" + SKYBOX_PATH_PREFIX + "/" + name + "/" + FACE_SUFFIXES[index] + ".png");
    }

    private static float clamp(float v) { return v < 0 ? 0 : Math.min(v, 1); }
}
