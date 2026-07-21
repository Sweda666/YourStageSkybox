package yourstageskybox;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * YourStageSkybox 配置文件（config/yourstageskybox-common.toml）。
 */
public class ModConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ModConfig INSTANCE;

    static {
        final Pair<ModConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder()
                .configure(ModConfig::new);
        SPEC = pair.getRight();
        INSTANCE = pair.getLeft();
    }

    /** 客户端启动时是否自动扫描资源包并注册天空盒（false = 仅通过指令注册） */
    public final ForgeConfigSpec.BooleanValue autoRegister;

    private ModConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("YourStageSkybox Configuration")
               .push("general");

        autoRegister = builder
                .comment(" 如果启用，客户端启动时会自动扫描所有资源包中的天空盒并注册；\n"
                       + " 如果禁用，仅在使用 /yourstageskybox set <名称> 时才注册。",
                         "If enabled, auto-scan resource packs for skyboxes on client startup.",
                         "If disabled, only register when /yourstageskybox set <name> is used.")
                .define("autoRegister", false);

        builder.pop();
    }

    /** 便捷访问 */
    public static boolean autoRegister() {
        return INSTANCE.autoRegister.get();
    }
}
