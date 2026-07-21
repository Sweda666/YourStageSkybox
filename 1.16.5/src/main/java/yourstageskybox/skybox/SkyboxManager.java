package yourstageskybox.skybox;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.resources.IResourcePack;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 天空盒管理器 —— 1.16.5 版本。
 * <p>
 * 核心逻辑与 1.12.2 基本一致，API 路径更新为 Mojang mappings。
 * </p>
 */
public class SkyboxManager {

    public static final String SKYBOX_DOMAIN = "yourstageskybox";
    public static final String SKYBOX_PATH_PREFIX = "skyboxes";
    public static final String[] FACE_SUFFIXES = {
            "panorama_0", "panorama_1", "panorama_2",
            "panorama_3", "panorama_4", "panorama_5"
    };

    // ---- 服务端 / 客户端共用 ----
    private static final Set<String> registeredSkyboxes = new LinkedHashSet<>();
    private static final Map<Integer, SkyboxState> activeSkyboxes = new HashMap<>();
    private static final Map<UUID, Map<Integer, SkyboxState>> playerActiveSkyboxes = new HashMap<>();
    private static SkyboxWorldData worldData;

    // ---- 客户端过渡系统 ----
    private static final Map<Integer, TransitionState> transitions = new HashMap<>();
    private static final Map<Integer, Long> defaultDurationPerDim = new HashMap<>();

    // ---- 仅客户端 ----
    private static final Map<String, ResourceLocation[]> skyboxTextures = new LinkedHashMap<>();

    // ==================== 客户端初始化 ====================

    @OnlyIn(Dist.CLIENT)
    public static void initClient() {
        registeredSkyboxes.clear();
        skyboxTextures.clear();
        activeSkyboxes.clear();
        transitions.clear();
    }

    public static void initWorldData(SkyboxWorldData data) {
        worldData = data;
    }

    private static void markPlayerDataDirty() {
        if (worldData != null) worldData.setDirty();
    }

    public static SkyboxWorldData getWorldData() {
        return worldData;
    }

    // ==================== 维度 ID 映射 ====================

    /**
     * 将 World 映射为内部使用的整数维度标识。
     * 默认维度：0=主世界, -1=下界, 1=末地；自定义维度使用 registry 中的 ID。
     */
    public static int getDimensionId(World world) {
        ResourceLocation loc = world.dimension().location();
        if (loc.equals(new ResourceLocation("overworld"))) return 0;
        if (loc.equals(new ResourceLocation("the_nether"))) return -1;
        if (loc.equals(new ResourceLocation("the_end"))) return 1;
        // 自定义维度：使用 registry 分配的整数 ID
        Registry<World> dimReg = world.registryAccess().registryOrThrow(Registry.DIMENSION_REGISTRY);
        return dimReg.getId(dimReg.get(loc));
    }

    /**
     * 通过整数维度 ID 查找 World（需服务器上下文）。
     */
    @Nullable
    public static World getWorldByDimensionId(net.minecraft.server.MinecraftServer server, int dimId) {
        for (net.minecraft.world.server.ServerWorld sw : server.getAllLevels()) {
            if (getDimensionId(sw) == dimId) return sw;
        }
        return null;
    }

    // ==================== 自动发现 ====================

    @OnlyIn(Dist.CLIENT)
    public static Set<String> discoverSkyboxes() {
        Set<String> found = new LinkedHashSet<>();
        String searchPath = "textures/" + SKYBOX_PATH_PREFIX;

        Minecraft mc = Minecraft.getInstance();
        IResourceManager rm = mc.getResourceManager();

        // 方案 1：通过 ResourceManager 发现（适用于所有资源包类型）
        for (String namespace : rm.getNamespaces()) {
            if (!SKYBOX_DOMAIN.equals(namespace)) continue;
            try {
                // 遍历已注册的天空盒名称，验证纹理是否存在
                for (String name : new LinkedHashSet<>(registeredSkyboxes)) {
                    ResourceLocation testLoc = new ResourceLocation(SKYBOX_DOMAIN,
                            searchPath + "/" + name + "/" + FACE_SUFFIXES[0] + ".png");
                    if (rm.hasResource(testLoc)) found.add(name);
                }
            } catch (Exception ignored) {}
        }

        // 方案 2：文件系统扫描（发现新天空盒，适用于文件夹/zip 资源包）
        for (IResourcePack pack : collectResourcePacks()) {
            try {
                if (!pack.getNamespaces(net.minecraft.resources.ResourcePackType.CLIENT_RESOURCES).contains(SKYBOX_DOMAIN)) continue;
                File packFile = getPackFile(pack);
                if (packFile == null) continue;
                if (packFile.isDirectory()) {
                    scanDirectory(packFile, "assets/" + SKYBOX_DOMAIN + "/" + searchPath + "/", found);
                } else if (packFile.isFile()) {
                    scanZipFile(packFile, "assets/" + SKYBOX_DOMAIN + "/" + searchPath + "/", found);
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

    /** 从 IResourcePack 提取底层 File（FolderPack / FilePack 均继承 ResourcePack.file） */
    @OnlyIn(Dist.CLIENT)
    private static File getPackFile(IResourcePack pack) {
        if (pack instanceof net.minecraft.resources.ResourcePack) {
            return ((net.minecraft.resources.ResourcePack) pack).file;
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    private static List<IResourcePack> collectResourcePacks() {
        List<IResourcePack> packs = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        // 用户选中及默认资源包
        if (mc.getResourcePackRepository() != null) {
            packs.addAll(mc.getResourcePackRepository().openAllSelected());
        }
        return packs;
    }

    @OnlyIn(Dist.CLIENT)
    private static void scanDirectory(File packRoot, String basePath, Set<String> output) {
        File skyboxesDir = new File(packRoot, basePath);
        File[] subdirs = skyboxesDir.listFiles(File::isDirectory);
        if (subdirs == null) return;
        for (File subdir : subdirs) {
            if (new File(subdir, "panorama_0.png").exists()) {
                output.add(subdir.getName());
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void scanZipFile(File zipFile, String basePath, Set<String> output) {
        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(basePath) && name.endsWith("/panorama_0.png")) {
                    String sub = name.substring(basePath.length());
                    int slash = sub.indexOf('/');
                    if (slash > 0) {
                        output.add(sub.substring(0, slash));
                    }
                }
            }
        } catch (Exception ignored) {}
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
            mc.getResourceManager().getResource(buildFaceLocation(name, 0));
        } catch (Exception e) {
            return false;
        }

        skyboxTextures.put(name, textures);
        registeredSkyboxes.add(name);
        return true;
    }

    public static void registerSkyboxName(String name) {
        if (name != null && !name.isEmpty()) {
            registeredSkyboxes.add(name);
        }
    }

    // ==================== 激活 / 清除 ====================

    @OnlyIn(Dist.CLIENT)
    public static void setActiveSkybox(int dimension, SkyboxState state) {
        if (state == null) {
            activeSkyboxes.remove(dimension);
            return;
        }
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
            state.alpha = oldState.alpha;
            state.red = oldState.red;
            state.green = oldState.green;
            state.blue = oldState.blue;
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
        if (state == null || state.isVanilla()) {
            activeSkyboxes.remove(dimension);
        } else {
            activeSkyboxes.put(dimension, state);
        }
    }

    public static void setActiveSkyboxServer(int dimension, String name) {
        if (name == null || name.isEmpty()) {
            activeSkyboxes.remove(dimension);
        } else {
            SkyboxState state = new SkyboxState(name);
            SkyboxState oldState = activeSkyboxes.get(dimension);
            if (oldState != null) {
                state.alpha = oldState.alpha;
                state.red = oldState.red;
                state.green = oldState.green;
                state.blue = oldState.blue;
                state.durationMs = oldState.durationMs;
            }
            activeSkyboxes.put(dimension, state);
        }
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

    public static void setSkyboxColor(int dimension, float r, float g, float b) {
        SkyboxState state = activeSkyboxes.get(dimension);
        if (state != null) {
            state.red = clamp(r); state.green = clamp(g); state.blue = clamp(b);
        }
    }

    public static void setSkyboxAlphaForPlayer(UUID playerId, int dimension, float alpha) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        if (playerMap != null) {
            SkyboxState state = playerMap.get(dimension);
            if (state != null) {
                state.alpha = clamp(alpha);
                markPlayerDataDirty();
            }
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

    public static SkyboxState getSkyboxState(int dimension) {
        return activeSkyboxes.get(dimension);
    }

    @OnlyIn(Dist.CLIENT)
    public static TransitionState getTransition(int dimension) {
        return transitions.get(dimension);
    }

    @OnlyIn(Dist.CLIENT)
    public static TransitionState updateTransition(int dimension) {
        TransitionState t = transitions.get(dimension);
        if (t == null) return null;
        if (t.isComplete()) {
            transitions.remove(dimension);
            return null;
        }
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

    public static void loadPlayerDataFromNBT(CompoundNBT nbt) {
        playerActiveSkyboxes.clear();
        if (!nbt.contains("Players")) return;

        ListNBT playerList = nbt.getList("Players", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundNBT playerTag = playerList.getCompound(i);
            UUID uuid = UUID.fromString(playerTag.getString("UUID"));
            Map<Integer, SkyboxState> dimMap = new HashMap<>();

            ListNBT dimList = playerTag.getList("Dimensions", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < dimList.size(); j++) {
                CompoundNBT dimTag = dimList.getCompound(j);
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

    public static void savePlayerDataToNBT(CompoundNBT compound) {
        ListNBT playerList = new ListNBT();
        for (Map.Entry<UUID, Map<Integer, SkyboxState>> entry : playerActiveSkyboxes.entrySet()) {
            CompoundNBT playerTag = new CompoundNBT();
            playerTag.putString("UUID", entry.getKey().toString());

            ListNBT dimList = new ListNBT();
            for (Map.Entry<Integer, SkyboxState> dimEntry : entry.getValue().entrySet()) {
                CompoundNBT dimTag = new CompoundNBT();
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
        for (String name : names) {
            registerSkybox(name);
        }
        for (Map.Entry<Integer, SkyboxState> entry : new HashMap<>(activeSkyboxes).entrySet()) {
            SkyboxState state = entry.getValue();
            if (state != null && state.name != null && !skyboxTextures.containsKey(state.name)) {
                activeSkyboxes.remove(entry.getKey());
            }
        }
        for (Map.Entry<Integer, TransitionState> entry : new HashMap<>(transitions).entrySet()) {
            TransitionState t = entry.getValue();
            boolean fromDead = t.fromName != null && !skyboxTextures.containsKey(t.fromName);
            boolean toDead = t.toName != null && !skyboxTextures.containsKey(t.toName);
            if (fromDead || toDead) transitions.remove(entry.getKey());
        }
    }

    // ==================== 内部工具 ====================

    @OnlyIn(Dist.CLIENT)
    private static ResourceLocation buildFaceLocation(String name, int index) {
        return new ResourceLocation(SKYBOX_DOMAIN,
                "textures/" + SKYBOX_PATH_PREFIX + "/" + name + "/" + FACE_SUFFIXES[index] + ".png");
    }

    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
}
