package yourstageskybox.proxy;

import yourstageskybox.ModConfig;
import yourstageskybox.command.CommandYourStageSkybox;
import yourstageskybox.skybox.SkyboxManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 通用代理 —— 服务端/客户端共用逻辑。
 */
public class CommonProxy {

    public CommonProxy() {
        // 在构造时注册游戏事件监听
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void commonSetup(final FMLCommonSetupEvent event) {
    }

    public void clientSetup(final FMLClientSetupEvent event) {
    }

    /**
     * 注册命令（Brigadier 方式）。
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandYourStageSkybox.register(event.getDispatcher());
    }
}
