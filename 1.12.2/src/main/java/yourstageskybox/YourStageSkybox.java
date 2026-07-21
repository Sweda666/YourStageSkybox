package yourstageskybox;

import yourstageskybox.network.NetworkHandler;
import yourstageskybox.proxy.CommonProxy;
import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxRenderer;
import yourstageskybox.skybox.SkyboxState;
import yourstageskybox.skybox.SkyboxWorldData;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Map;
import java.util.UUID;

/**
 * YourStageSkybox —— 1.12.2 Forge 模组。
 * 提供 API 与指令，用于切换通过资源包导入的天空盒材质。
 */
@Mod(modid = YourStageSkybox.MODID, name = YourStageSkybox.NAME, version = YourStageSkybox.VERSION,
        acceptableRemoteVersions = "*")
public class YourStageSkybox {

    public static final String MODID = "yourstageskybox";
    public static final String NAME = "YourStageSkybox";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MODID)
    public static YourStageSkybox instance;

    @SidedProxy(clientSide = "yourstageskybox.proxy.ClientProxy",
                 serverSide = "yourstageskybox.proxy.CommonProxy")
    public static CommonProxy proxy;

    // ---- 生命周期 ----

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.init(event);
        NetworkHandler.init();
        YourStageSkyboxLocale.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    // ---- 玩家事件 ----

    /**
     * 玩家登录时：确保持久化数据已加载，然后同步该玩家所有维度的天空盒状态。
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) event.player;

            // 懒加载持久化数据（WorldEvent.Load 可能在 PlayerLoggedInEvent 之后才触发）
            if (SkyboxManager.getWorldData() == null) {
                SkyboxWorldData data = SkyboxWorldData.get(player.world);
                if (data != null) {
                    SkyboxManager.initWorldData(data);
                }
            }

            // 同步该玩家在每个维度的天空盒状态
            UUID playerId = player.getUniqueID();
            Map<Integer, SkyboxState> playerSkyboxes = SkyboxManager.getAllActiveSkyboxesForPlayer(playerId);
            for (Map.Entry<Integer, SkyboxState> entry : playerSkyboxes.entrySet()) {
                NetworkHandler.sendSkyboxSyncToPlayer(player, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 世界加载时：客户端设置天空盒渲染器，服务端恢复持久化数据。
     */
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote) {
            // 客户端：为 WorldProvider 注入 SkyboxRenderer
            ((WorldClient) world).provider.setSkyRenderer(new SkyboxRenderer());
        } else if (world.provider.getDimension() == 0) {
            // 服务端主世界：加载持久化的玩家天空盒数据
            SkyboxWorldData data = SkyboxWorldData.get(world);
            if (data != null) {
                SkyboxManager.initWorldData(data);
            }
        }
    }
}
