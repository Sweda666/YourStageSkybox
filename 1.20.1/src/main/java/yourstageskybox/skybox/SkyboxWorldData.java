package yourstageskybox.skybox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * 世界存档数据 —— 1.20.1 版本（SavedData 替代 WorldSavedData）。
 */
public class SkyboxWorldData extends SavedData {

    private static final String DATA_NAME = "YourStageSkybox";

    public SkyboxWorldData() {}

    public static SkyboxWorldData load(CompoundTag tag) {
        SkyboxWorldData data = new SkyboxWorldData();
        SkyboxManager.loadPlayerDataFromNBT(tag);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SkyboxManager.savePlayerDataToNBT(tag);
        return tag;
    }

    /**
     * 从世界存档中获取或创建 SkyboxWorldData 实例（仅服务端有效）。
     */
    public static SkyboxWorldData get(Level level) {
        if (level.isClientSide()) return null;
        if (!(level instanceof ServerLevel serverLevel)) return null;

        DimensionDataStorage storage = serverLevel.getDataStorage();
        return storage.computeIfAbsent(SkyboxWorldData::load, SkyboxWorldData::new, DATA_NAME);
    }
}
