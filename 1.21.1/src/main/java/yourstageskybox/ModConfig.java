package yourstageskybox;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * YourStageSkybox 配置文件（1.21.1 版本）。
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

    public final ForgeConfigSpec.BooleanValue autoRegister;

    private ModConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("YourStageSkybox Configuration").push("general");

        autoRegister = builder
                .comment(" If enabled, auto-scan resource packs for skyboxes on client startup.",
                         " If disabled, only register when /yourstageskybox set <name> is used.")
                .define("autoRegister", false);

        builder.pop();
    }

    public static boolean autoRegister() {
        return INSTANCE.autoRegister.get();
    }
}
