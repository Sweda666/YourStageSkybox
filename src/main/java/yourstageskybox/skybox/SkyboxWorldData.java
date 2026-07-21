package yourstageskybox.skybox;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;

/**
 * 世界存档数据 —— 1.16.5 版本。
 * <p>
 * 在 1.16.5 中 WorldSavedData 仍可用但已标记为 deprecated。
 * 1.18+ 需要用 SavedData + DimensionDataStorage 替代。
 * </p>
 */
public class SkyboxWorldData extends WorldSavedData {

    private static final String DATA_NAME = "YourStageSkybox";

    public SkyboxWorldData() {
        super(DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        SkyboxManager.loadPlayerDataFromNBT(nbt);
    }

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        SkyboxManager.savePlayerDataToNBT(compound);
        return compound;
    }

    /**
     * 从世界存档中获取或创建 SkyboxWorldData 实例（仅服务端有效）。
     * <p>
     * 注意：1.16.5 中 WorldSavedData 的获取 API 与 1.12.2 不同。
     * 使用 ServerWorld#getDataStorage() 访问 DimensionSavedDataManager。
     * </p>
     */
    public static SkyboxWorldData get(World world) {
        if (world.isClientSide()) return null;
        if (!(world instanceof ServerWorld)) return null;

        ServerWorld serverWorld = (ServerWorld) world;
        DimensionSavedDataManager storage = serverWorld.getDataStorage();

        SkyboxWorldData data = storage.computeIfAbsent(
                () -> new SkyboxWorldData(),
                DATA_NAME
        );
        return data;
    }
}
