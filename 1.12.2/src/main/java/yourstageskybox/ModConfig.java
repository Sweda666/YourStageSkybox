package yourstageskybox;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

/**
 * YourStageSkybox 配置文件（config/yourstageskybox.cfg）。
 */
public class ModConfig {

    /** 客户端启动时是否自动扫描资源包并注册天空盒（false = 仅通过指令注册） */
    public static boolean autoRegister = false;

    private static Configuration config;

    public static void init(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), "yourstageskybox.cfg");
        config = new Configuration(configFile);
        sync();
    }

    public static void sync() {
        autoRegister = config.getBoolean(
                "autoRegister",
                Configuration.CATEGORY_GENERAL,
                true,
                "如果启用，客户端启动时会自动扫描所有资源包中的天空盒并注册；"
                + "如果禁用，仅在使用 /yourstageskybox set <名称> 时才注册。"
        );

        if (config.hasChanged()) {
            config.save();
        }
    }
}
