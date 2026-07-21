package yourstageskybox;

import yourstageskybox.network.NetworkHandler;
import yourstageskybox.proxy.CommonProxy;
import yourstageskybox.skybox.SkyboxManager;
import yourstageskybox.skybox.SkyboxRenderer;
import yourstageskybox.skybox.SkyboxState;
import yourstageskybox.skybox.SkyboxWorldData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
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
 * YourStageSkybox — 1.21.1 Forge 模组。
 */
@Mod(YourStageSkybox.MODID)
public class YourStageSkybox {

    public static final String MODID = "yourstageskybox";
    public static final String NAME = "YourStageSkybox";
    public static final String VERSION = "1.0.0";

    public static final CommonProxy PROXY = DistExecutor.safeRunForDist(
            () -> yourstageskybox.proxy.ClientProxy::new,
            () -> yourstageskybox.proxy.CommonProxy::new
    );

    public YourStageSkybox() {
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
        NetworkHandler.init();
        YourStageSkyboxLocale.init();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        // 在 MOD 总线注册 DimensionSpecialEffects 钩子（仅客户端）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> FMLJavaModLoadingContext.get().getModEventBus()
                        .register(SkyboxRenderer.class));

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PROXY.commonSetup(event);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        PROXY.clientSetup(event);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (SkyboxManager.getWorldData() == null) {
            SkyboxWorldData data = SkyboxWorldData.get(player.serverLevel());
            if (data != null) SkyboxManager.initWorldData(data);
        }

        UUID playerId = player.getUUID();
        Map<Integer, SkyboxState> playerSkyboxes = SkyboxManager.getAllActiveSkyboxesForPlayer(playerId);
        for (Map.Entry<Integer, SkyboxState> entry : playerSkyboxes.entrySet()) {
            NetworkHandler.sendSkyboxSyncToPlayer(player, entry.getKey(), entry.getValue());
        }
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        Level level = (Level) event.getLevel();
        if (level.isClientSide()) {
            // SkyboxRenderer 通过 RegisterDimensionSpecialEffectsEvent 注入，不需这里手动设置
        } else if (level.dimension() == Level.OVERWORLD) {
            SkyboxWorldData data = SkyboxWorldData.get(level);
            if (data != null) SkyboxManager.initWorldData(data);
        }
    }
}
