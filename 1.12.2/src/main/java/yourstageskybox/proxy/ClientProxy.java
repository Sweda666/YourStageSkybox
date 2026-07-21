package yourstageskybox.proxy;

import yourstageskybox.ModConfig;
import yourstageskybox.skybox.SkyboxManager;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        SkyboxManager.initClient();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        if (ModConfig.autoRegister) {
            SkyboxManager.discoverSkyboxes();
        }
    }
}
