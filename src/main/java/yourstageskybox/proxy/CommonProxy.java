package yourstageskybox.proxy;

import yourstageskybox.command.CommandYourStageSkybox;
import yourstageskybox.skybox.SkyboxRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 通用代理 —— 服务端/客户端共用逻辑（1.20.1 版本）。
 */
public class CommonProxy {

    public CommonProxy() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void commonSetup(final FMLCommonSetupEvent event) {}

    public void clientSetup(final FMLClientSetupEvent event) {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandYourStageSkybox.register(event.getDispatcher());
    }
}
