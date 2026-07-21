package yourstageskybox.skybox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 天空盒管理器 —— 管理已注册的天空盒纹理和每维度的激活状态。
 * <p>
 * 客户端：负责纹理加载、激活状态、过渡动画和渲染数据。<br>
 * 服务端：负责跟踪激活状态并通过网络包同步至客户端。
 * </p>
 */
public class SkyboxManager {

    public static final String SKYBOX_DOMAIN = "yourstageskybox";
    public static final String SKYBOX_PATH_PREFIX = "skyboxes";
    public static final String[] FACE_SUFFIXES = {
            "panorama_0", // 北 (-Z)
            "panorama_1", // 东 (+X)
            "panorama_2", // 南 (+Z)
            "panorama_3", // 西 (-X)
            "panorama_4", // 上 (+Y)
            "panorama_5"  // 下 (-Y)
    };

    // ---- 服务端 / 客户端共用 ----
    /** 已注册的天空盒名称集合 */
    private static final Set<String> registeredSkyboxes = new LinkedHashSet<>();
    /** 客户端：每维度的当前激活天空盒状态 */
    private static final Map<Integer, SkyboxState> activeSkyboxes = new HashMap<>();
    /** 服务端：按玩家 UUID 追踪每维度的激活天空盒状态 */
    private static final Map<UUID, Map<Integer, SkyboxState>> playerActiveSkyboxes = new HashMap<>();
    /** 持久化数据的 WorldSavedData 引用（仅服务端有效） */
    private static SkyboxWorldData worldData;

    // ---- 客户端过渡系统 ----
    /** 客户端：每维度正在进行的过渡动画 */
    private static final Map<Integer, TransitionState> transitions = new HashMap<>();
    /** 每维度的默认过渡时长（毫秒），仅服务端 / 全局用 */
    private static final Map<Integer, Long> defaultDurationPerDim = new HashMap<>();

    // 反射缓存 —— 仅解析一次
    private static Field defaultPacksField;
    private static Field packFileField;

    // ---- 仅客户端 ----
    /** 天空盒名称 → 六面纹理的 ResourceLocation */
    private static final Map<String, ResourceLocation[]> skyboxTextures = new LinkedHashMap<>();

    // ==================== 客户端初始化 ====================

    @SideOnly(Side.CLIENT)
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
        if (worldData != null) worldData.markDirty();
    }

    public static SkyboxWorldData getWorldData() {
        return worldData;
    }

    /**
     * 自动扫描所有已加载的资源包，发现并注册天空盒（仅客户端）。
     * <p>
     * 扫描每个资源包的 assets/yourstageskybox/textures/skyboxes/ 目录，
     * 找到包含 panorama_0.png 的子目录即视为有效天空盒。
     * </p>
     */
    @SideOnly(Side.CLIENT)
    public static Set<String> discoverSkyboxes() {
        Set<String> found = new LinkedHashSet<>();
        String basePath = "assets/" + SKYBOX_DOMAIN + "/textures/" + SKYBOX_PATH_PREFIX + "/";

        for (IResourcePack pack : collectResourcePacks()) {
            try {
                if (!pack.getResourceDomains().contains(SKYBOX_DOMAIN)) continue;

                File packFile = getPackFile(pack);
                if (packFile == null) continue;

                if (packFile.isDirectory()) {
                    scanDirectory(packFile, basePath, found);
                } else if (packFile.isFile()) {
                    scanZipFile(packFile, basePath, found);
                }
            } catch (Exception ignored) {
            }
        }

        for (String name : found) {
            if (!registeredSkyboxes.contains(name)) {
                registerSkybox(name);
            }
        }
        return found;
    }

    /** 收集所有已加载的 IResourcePack（默认 + 用户选择） */
    @SideOnly(Side.CLIENT)
    private static List<IResourcePack> collectResourcePacks() {
        List<IResourcePack> packs = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();

        if (defaultPacksField == null) {
            try {
                defaultPacksField = Minecraft.class.getDeclaredField("defaultResourcePacks");
                defaultPacksField.setAccessible(true);
            } catch (Exception ignored) {
            }
        }
        if (defaultPacksField != null) {
            try {
                List<IResourcePack> defaultPacks = (List<IResourcePack>) defaultPacksField.get(mc);
                if (defaultPacks != null) packs.addAll(defaultPacks);
            } catch (Exception ignored) {
            }
        }

        if (mc.getResourcePackRepository() != null) {
            for (net.minecraft.client.resources.ResourcePackRepository.Entry entry
                    : mc.getResourcePackRepository().getRepositoryEntries()) {
                IResourcePack pack = entry.getResourcePack();
                if (pack != null) packs.add(pack);
            }
        }
        return packs;
    }

    /** 通过反射获取 AbstractResourcePack 的底层文件（Field 缓存） */
    @SideOnly(Side.CLIENT)
    private static File getPackFile(IResourcePack pack) {
        if (!(pack instanceof AbstractResourcePack)) return null;
        if (packFileField == null) {
            try {
                packFileField = AbstractResourcePack.class.getDeclaredField("resourcePackFile");
                packFileField.setAccessible(true);
            } catch (Exception ignored) {
            }
        }
        if (packFileField == null) return null;
        try {
            return (File) packFileField.get(pack);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 扫描目录型资源包 */
    @SideOnly(Side.CLIENT)
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

    /** 扫描 ZIP 型资源包 */
    @SideOnly(Side.CLIENT)
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
        } catch (Exception ignored) {
        }
    }

    // ==================== 注册 ====================

    /**
     * 在客户端尝试注册一个天空盒。
     * 会根据命名约定从资源包中加载六面纹理。
     *
     * @param name 天空盒名称
     * @return 注册成功返回 true，纹理缺失返回 false
     */
    @SideOnly(Side.CLIENT)
    public static boolean registerSkybox(String name) {
        if (name == null || name.isEmpty()) return false;

        ResourceLocation[] textures = new ResourceLocation[6];
        for (int i = 0; i < 6; i++) {
            textures[i] = buildFaceLocation(name, i);
        }

        try {
            Minecraft mc = Minecraft.getMinecraft();
            mc.getResourceManager().getResource(buildFaceLocation(name, 0));
        } catch (Exception e) {
            return false;
        }

        skyboxTextures.put(name, textures);
        registeredSkyboxes.add(name);
        return true;
    }

    /**
     * 仅注册天空盒名称（服务端侧注册，不加载纹理）。
     */
    public static void registerSkyboxName(String name) {
        if (name != null && !name.isEmpty()) {
            registeredSkyboxes.add(name);
        }
    }

    // ==================== 激活 / 清除（客户端） ====================

    /**
     * 客户端：设置指定维度的天空盒状态，并启动过渡动画。
     * <p>
     * 如果该维度已有激活的天空盒，新旧之间会通过 {@link TransitionState} 进行淡入淡出。
     * </p>
     */
    @SideOnly(Side.CLIENT)
    public static void setActiveSkybox(int dimension, SkyboxState state) {
        if (state == null) {
            activeSkyboxes.remove(dimension);
            return;
        }

        String newName = state.name;
        // 按需自动注册
        if (newName != null && !newName.isEmpty() && !skyboxTextures.containsKey(newName)) {
            if (!registerSkybox(newName)) return;
        }

        SkyboxState oldState = activeSkyboxes.get(dimension);
        String oldName = (oldState != null) ? oldState.name : null;

        // 如果新旧相同且无过渡需求，直接更新状态
        if (Objects.equals(oldName, newName)) {
            // 同名但参数变了（alpha/color），直接更新无需过渡
            activeSkyboxes.put(dimension, state);
            transitions.remove(dimension);
            return;
        }

        // 需要过渡：提取新旧颜色参数
        long duration = (state.durationMs > 0) ? state.durationMs : getDefaultDuration(dimension);

        float oldA = (oldState != null) ? oldState.alpha : 1.0f;
        float oldR = (oldState != null && !oldState.isVanilla()) ? oldState.red   : 1.0f;
        float oldG = (oldState != null && !oldState.isVanilla()) ? oldState.green : 1.0f;
        float oldB = (oldState != null && !oldState.isVanilla()) ? oldState.blue  : 1.0f;

        float newA = state.alpha;
        float newR = (!state.isVanilla()) ? state.red   : 1.0f;
        float newG = (!state.isVanilla()) ? state.green : 1.0f;
        float newB = (!state.isVanilla()) ? state.blue  : 1.0f;

        transitions.put(dimension, new TransitionState(oldName, newName, duration,
                oldA, oldR, oldG, oldB,
                newA, newR, newG, newB));

        // 新状态立即写入（过渡期间 Renderer 读 from/to textures 不依赖这）
        activeSkyboxes.put(dimension, state);
    }

    /**
     * 客户端：设置指定维度的天空盒（兼容旧 API —— 无额外参数）。
     * 等价于 {@code setActiveSkybox(dimension, new SkyboxState(name))}。
     */
    @SideOnly(Side.CLIENT)
    public static void setActiveSkybox(int dimension, String name) {
        SkyboxState state = new SkyboxState(name);
        // 继承当前状态的 alpha/color/duration
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

    /**
     * 清除指定维度的激活天空盒，恢复原版天空（客户端）。
     */
    public static void clearActiveSkybox(int dimension) {
        activeSkyboxes.remove(dimension);
        transitions.remove(dimension);
    }

    // ==================== 激活 / 清除（服务端） ====================

    /**
     * 服务端：记录激活天空盒（全局，同步到指定维度所有玩家）。
     */
    public static void setActiveSkyboxServer(int dimension, SkyboxState state) {
        if (state == null || state.isVanilla()) {
            activeSkyboxes.remove(dimension);
        } else {
            activeSkyboxes.put(dimension, state);
        }
    }

    /**
     * 服务端：记录激活天空盒（兼容旧 API）。
     */
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

    /**
     * 服务端：为指定玩家设定某维度的天空盒。
     */
    public static void setActiveSkyboxForPlayer(UUID playerId, int dimension, SkyboxState state) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.computeIfAbsent(playerId, k -> new HashMap<>());
        if (state == null || state.isVanilla()) {
            playerMap.remove(dimension);
            if (playerMap.isEmpty()) {
                playerActiveSkyboxes.remove(playerId);
            }
        } else {
            playerMap.put(dimension, state);
        }
        markPlayerDataDirty();
    }

    /**
     * 服务端：为指定玩家设定某维度的天空盒（兼容旧 API）。
     */
    public static void setActiveSkyboxForPlayer(UUID playerId, int dimension, String name) {
        if (name == null || name.isEmpty()) {
            clearActiveSkyboxForPlayer(playerId, dimension);
        } else {
            SkyboxState state = new SkyboxState(name);
            Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.computeIfAbsent(playerId, k -> new HashMap<>());
            SkyboxState oldState = playerMap.get(dimension);
            if (oldState != null) {
                state.alpha = oldState.alpha;
                state.red = oldState.red;
                state.green = oldState.green;
                state.blue = oldState.blue;
                state.durationMs = oldState.durationMs;
            }
            playerMap.put(dimension, state);
            markPlayerDataDirty();
        }
    }

    /**
     * 服务端：清除指定玩家某维度的天空盒。
     */
    public static void clearActiveSkyboxForPlayer(UUID playerId, int dimension) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        if (playerMap != null) {
            playerMap.remove(dimension);
            if (playerMap.isEmpty()) {
                playerActiveSkyboxes.remove(playerId);
            }
        }
        markPlayerDataDirty();
    }

    // ==================== Alpha / Color / Duration 设置 ====================

    /**
     * 设置指定维度的当前天空盒 alpha（仅修改状态，不触发过渡）。
     */
    public static void setSkyboxAlpha(int dimension, float alpha) {
        SkyboxState state = activeSkyboxes.get(dimension);
        if (state != null) {
            state.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        }
    }

    /**
     * 设置指定维度的天空盒 RGB 色相。
     */
    public static void setSkyboxColor(int dimension, float r, float g, float b) {
        SkyboxState state = activeSkyboxes.get(dimension);
        if (state != null) {
            state.red = Math.max(0.0f, Math.min(1.0f, r));
            state.green = Math.max(0.0f, Math.min(1.0f, g));
            state.blue = Math.max(0.0f, Math.min(1.0f, b));
        }
    }

    /**
     * 为指定玩家设置某维度的天空盒 alpha。
     */
    public static void setSkyboxAlphaForPlayer(UUID playerId, int dimension, float alpha) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        if (playerMap != null) {
            SkyboxState state = playerMap.get(dimension);
            if (state != null) {
                state.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
                markPlayerDataDirty();
            }
        }
    }

    /**
     * 设置指定维度的默认过渡时长（下次 set 生效）。
     */
    public static void setDefaultDuration(int dimension, long durationMs) {
        if (durationMs > 0) {
            defaultDurationPerDim.put(dimension, durationMs);
        }
    }

    /**
     * 获取维度的默认过渡时长，未设置则返回 2000ms。
     */
    public static long getDefaultDuration(int dimension) {
        Long val = defaultDurationPerDim.get(dimension);
        return (val != null && val > 0) ? val : 2000L;
    }

    // ==================== 查询 ====================

    /**
     * 获取指定维度当前激活的天空盒名称。
     *
     * @return 天空盒名称，若为原版天空则返回 null
     */
    public static String getActiveSkybox(int dimension) {
        SkyboxState state = activeSkyboxes.get(dimension);
        return (state != null) ? state.name : null;
    }

    /**
     * 获取指定维度当前的完整天空盒状态。
     *
     * @return SkyboxState，若无激活状态则返回 null
     */
    public static SkyboxState getSkyboxState(int dimension) {
        return activeSkyboxes.get(dimension);
    }

    /**
     * 获取指定维度的过渡状态（仅客户端）。
     *
     * @return TransitionState，若无过渡则返回 null
     */
    @SideOnly(Side.CLIENT)
    public static TransitionState getTransition(int dimension) {
        return transitions.get(dimension);
    }

    /**
     * 每帧更新过渡状态——检查是否完成，完成则自动清除。
     * 由 SkyboxRenderer 调用。
     */
    @SideOnly(Side.CLIENT)
    public static TransitionState updateTransition(int dimension) {
        TransitionState t = transitions.get(dimension);
        if (t == null) return null;
        if (t.isComplete()) {
            transitions.remove(dimension);
            return null;
        }
        return t;
    }

    /**
     * 服务端：获取指定玩家某维度的天空盒名称。
     */
    public static String getActiveSkyboxForPlayer(UUID playerId, int dimension) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        if (playerMap != null) {
            SkyboxState state = playerMap.get(dimension);
            return (state != null) ? state.name : null;
        }
        return null;
    }

    /**
     * 服务端：获取指定玩家某维度的完整天空盒状态。
     */
    public static SkyboxState getSkyboxStateForPlayer(UUID playerId, int dimension) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        return (playerMap != null) ? playerMap.get(dimension) : null;
    }

    /**
     * 服务端：获取某玩家的全部维度天空盒状态（登录同步用）。
     */
    public static Map<Integer, SkyboxState> getAllActiveSkyboxesForPlayer(UUID playerId) {
        Map<Integer, SkyboxState> playerMap = playerActiveSkyboxes.get(playerId);
        return playerMap != null ? new HashMap<>(playerMap) : Collections.emptyMap();
    }

    /**
     * 服务端：获取所有有个人天空盒设置的玩家条目（用于 API 查询）。
     */
    public static Set<Map.Entry<UUID, Map<Integer, SkyboxState>>> getAllPlayerSkyboxesEntries() {
        return Collections.unmodifiableSet(new HashSet<>(playerActiveSkyboxes.entrySet()));
    }

    /**
     * 获取天空盒的六面纹理 ResourceLocation。
     *
     * @return ResourceLocation[6]，若未注册则返回 null
     */
    @SideOnly(Side.CLIENT)
    public static ResourceLocation[] getSkyboxTextures(String name) {
        if (name == null || name.isEmpty()) return null;
        return skyboxTextures.get(name);
    }

    /**
     * 获取所有已注册的天空盒名称。
     */
    public static Set<String> getRegisteredSkyboxes() {
        return Collections.unmodifiableSet(registeredSkyboxes);
    }

    // ==================== 持久化 ====================

    /**
     * 从世界 NBT 中加载玩家天空盒分配数据。
     * 由 {@link SkyboxWorldData#readFromNBT(NBTTagCompound)} 调用。
     */
    public static void loadPlayerDataFromNBT(NBTTagCompound nbt) {
        playerActiveSkyboxes.clear();
        if (!nbt.hasKey("Players")) return;

        NBTTagList playerList = nbt.getTagList("Players", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < playerList.tagCount(); i++) {
            NBTTagCompound playerTag = playerList.getCompoundTagAt(i);
            UUID uuid = UUID.fromString(playerTag.getString("UUID"));
            Map<Integer, SkyboxState> dimMap = new HashMap<>();

            NBTTagList dimList = playerTag.getTagList("Dimensions", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < dimList.tagCount(); j++) {
                NBTTagCompound dimTag = dimList.getCompoundTagAt(j);
                int dim = dimTag.getInteger("Dim");
                SkyboxState state = new SkyboxState(dimTag.getString("Skybox"));
                // 可选字段（向后兼容：旧数据无这些字段则用默认值）
                if (dimTag.hasKey("Alpha")) state.alpha = dimTag.getFloat("Alpha");
                if (dimTag.hasKey("Red")) state.red = dimTag.getFloat("Red");
                if (dimTag.hasKey("Green")) state.green = dimTag.getFloat("Green");
                if (dimTag.hasKey("Blue")) state.blue = dimTag.getFloat("Blue");
                if (dimTag.hasKey("Duration")) state.durationMs = dimTag.getLong("Duration");
                dimMap.put(dim, state);
            }
            playerActiveSkyboxes.put(uuid, dimMap);
        }
    }

    /**
     * 将玩家天空盒分配数据写入世界 NBT。
     * 由 {@link SkyboxWorldData#writeToNBT(NBTTagCompound)} 调用。
     */
    public static void savePlayerDataToNBT(NBTTagCompound compound) {
        NBTTagList playerList = new NBTTagList();
        for (Map.Entry<UUID, Map<Integer, SkyboxState>> entry : playerActiveSkyboxes.entrySet()) {
            NBTTagCompound playerTag = new NBTTagCompound();
            playerTag.setString("UUID", entry.getKey().toString());

            NBTTagList dimList = new NBTTagList();
            for (Map.Entry<Integer, SkyboxState> dimEntry : entry.getValue().entrySet()) {
                NBTTagCompound dimTag = new NBTTagCompound();
                dimTag.setInteger("Dim", dimEntry.getKey());
                SkyboxState state = dimEntry.getValue();
                dimTag.setString("Skybox", state.name != null ? state.name : "");
                dimTag.setFloat("Alpha", state.alpha);
                dimTag.setFloat("Red", state.red);
                dimTag.setFloat("Green", state.green);
                dimTag.setFloat("Blue", state.blue);
                dimTag.setLong("Duration", state.durationMs);
                dimList.appendTag(dimTag);
            }
            playerTag.setTag("Dimensions", dimList);
            playerList.appendTag(playerTag);
        }
        compound.setTag("Players", playerList);
    }

    // ==================== 重载 ====================

    /**
     * 重新扫描资源包并注册天空盒（仅客户端有效）。
     * <p>
     * 在专用服务端上调用无效果。
     * </p>
     */
    public static void reloadSkyboxes() {
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getEffectiveSide() != Side.CLIENT) {
            return;
        }
        Set<String> names = new LinkedHashSet<>(registeredSkyboxes);
        skyboxTextures.clear();
        registeredSkyboxes.clear();

        for (String name : names) {
            registerSkybox(name);
        }

        // 清理已失效的激活绑定
        for (Map.Entry<Integer, SkyboxState> entry : new HashMap<>(activeSkyboxes).entrySet()) {
            SkyboxState state = entry.getValue();
            if (state != null && state.name != null && !skyboxTextures.containsKey(state.name)) {
                activeSkyboxes.remove(entry.getKey());
            }
        }

        // 清理过渡中已失效的天空盒
        for (Map.Entry<Integer, TransitionState> entry : new HashMap<>(transitions).entrySet()) {
            TransitionState t = entry.getValue();
            boolean fromDead = t.fromName != null && !skyboxTextures.containsKey(t.fromName);
            boolean toDead = t.toName != null && !skyboxTextures.containsKey(t.toName);
            if (fromDead || toDead) {
                transitions.remove(entry.getKey());
            }
        }
    }

    // ==================== 内部工具 ====================

    @SideOnly(Side.CLIENT)
    private static ResourceLocation buildFaceLocation(String name, int index) {
        return new ResourceLocation(SKYBOX_DOMAIN,
                "textures/" + SKYBOX_PATH_PREFIX + "/" + name + "/" + FACE_SUFFIXES[index] + ".png");
    }
}
