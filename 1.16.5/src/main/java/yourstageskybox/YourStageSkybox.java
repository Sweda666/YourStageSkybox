package yourstageskybox;

import yourstageskybox.network.NetworkHandler;
import yourstageskybox.proxy.CommonProxy;
import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxRenderer;
import yourstageskybox.skybox.SkyboxState;
import yourstageskybox.skybox.SkyboxWorldData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;

import java.util.Map;
import java.util.UUID;

/**
 * YourStageSkybox — 1.16.5 Forge 模组。
 */
@Mod(YourStageSkybox.MODID)
public class YourStageSkybox {

    public static final String MODID = "yourstageskybox";
    public static final String NAME = "YourStageSkybox";
    public static final String VERSION = "1.0.0";

    /** 客户端/服务端代理（通过 DistExecutor 安全创建） */
    public static final CommonProxy PROXY = DistExecutor.safeRunForDist(
            () -> yourstageskybox.proxy.ClientProxy::new,
            () -> yourstageskybox.proxy.CommonProxy::new
    );

    public YourStageSkybox() {
        // 配置注册
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);

        // 网络初始化
        NetworkHandler.init();

        // 多语言初始化
        YourStageSkyboxLocale.init();

        // Mod 总线事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        // 游戏事件
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PROXY.commonSetup(event);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        PROXY.clientSetup(event);
    }

    // ---- 玩家事件 ----

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();

        // 懒加载持久化数据
        if (SkyboxManager.getWorldData() == null) {
            SkyboxWorldData data = SkyboxWorldData.get(player.getCommandSenderWorld());
            if (data != null) {
                SkyboxManager.initWorldData(data);
            }
        }

        // 同步该玩家在每个维度的天空盒状态
        UUID playerId = player.getUUID();
        Map<Integer, SkyboxState> playerSkyboxes = SkyboxManager.getAllActiveSkyboxesForPlayer(playerId);
        for (Map.Entry<Integer, SkyboxState> entry : playerSkyboxes.entrySet()) {
            NetworkHandler.sendSkyboxSyncToPlayer(player, entry.getKey(), entry.getValue());
        }
    }

    // ---- 世界事件 ----

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = (World) event.getWorld();
        if (world.isClientSide()) {
            // 客户端：为 DimensionRenderInfo 注入 SkyboxRenderer
            ClientWorld cw = (ClientWorld) world;
            cw.effects().setSkyRenderHandler(new SkyboxRenderer());
        } else if (world.dimension() == World.OVERWORLD) {
            // 服务端主世界：加载持久化数据
            SkyboxWorldData data = SkyboxWorldData.get(world);
            if (data != null) {
                SkyboxManager.initWorldData(data);
            }
        }
    }
}
