package yourstageskybox.proxy;

import yourstageskybox.ModConfig;
import yourstageskybox.skybox.SkyboxManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 客户端代理 —— 仅客户端加载的初始化逻辑（1.21.1 版本）。
 */
@OnlyIn(Dist.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void commonSetup(FMLCommonSetupEvent event) {
        super.commonSetup(event);
        SkyboxManager.initClient();
    }

    @Override
    public void clientSetup(FMLClientSetupEvent event) {
        super.clientSetup(event);
        if (ModConfig.autoRegister()) {
            SkyboxManager.discoverSkyboxes();
        }
    }
}
