package yourstageskybox.skybox;

import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 世界存档数据 —— 将玩家的天空盒分配持久化到世界 NBT 中。
 * <p>
 * 解决服务端/客户端重启后玩家天空盒设置丢失的问题。
 * 数据委托给 {@link SkyboxManager} 进行实际的 NBT 序列化。
 * </p>
 */
public class SkyboxWorldData extends WorldSavedData {

    private static final String DATA_NAME = "YourStageSkybox";

    public SkyboxWorldData() {
        super(DATA_NAME);
    }

    public SkyboxWorldData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        SkyboxManager.loadPlayerDataFromNBT(nbt);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        SkyboxManager.savePlayerDataToNBT(compound);
        return compound;
    }

    /**
     * 从世界存档中获取或创建 SkyboxWorldData 实例（仅服务端有效）。
     */
    public static SkyboxWorldData get(World world) {
        if (world.isRemote) return null;
        MapStorage storage = world.getPerWorldStorage();
        SkyboxWorldData data = (SkyboxWorldData) storage.getOrLoadData(SkyboxWorldData.class, DATA_NAME);
        if (data == null) {
            data = new SkyboxWorldData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }
}
