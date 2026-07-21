package yourstageskybox;

import com.google.common.collect.Maps;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 独立于 Minecraft Locale 系统的翻译加载器。
 * <p>
 * 直接读取 classpath 中的 .lang 文件（UTF-8），不受 {@code @SideOnly} 限制。
 * </p>
 */
public final class YourStageSkyboxLocale {

    private static final Map<String, String> translations = Maps.newHashMap();
    private static boolean loaded;

    private YourStageSkyboxLocale() {}

    /** 在 CommonProxy.serverStarting 中调用一次即可 */
    public static void init() {
        if (loaded) return;
        loaded = true;

        // 优先当前语言，fallback 到 en_us
        String lang = getCurrentLanguage();
        load("lang/en_us.lang");
        if (!"en_us".equals(lang)) {
            load("lang/" + lang + ".lang");
        }
    }

    /** 获取翻译文本（不格式化） */
    public static String translate(String key) {
        String val = translations.get(key);
        return val != null ? val : key;
    }

    /** 获取翻译并格式化 */
    public static String format(String key, Object... args) {
        String val = translations.get(key);
        return val != null ? String.format(val, args) : key;
    }

    // ---- 内部 ----

    private static String getCurrentLanguage() {
        try {
            // 反射读取 Minecraft 语言设置（客户端才有）
            Class<?> cls = Class.forName("net.minecraft.client.Minecraft");
            Object mc = cls.getMethod("getMinecraft").invoke(null);
            Object langObj = cls.getMethod("getLanguageManager").invoke(mc);
            Object current = langObj.getClass().getMethod("getCurrentLanguage").invoke(langObj);
            return (String) current.getClass().getMethod("getLanguageCode").invoke(current);
        } catch (Exception ignored) {
            return "en_us";
        }
    }

    private static void load(String path) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                YourStageSkyboxLocale.class.getClassLoader().getResourceAsStream("assets/yourstageskybox/" + path),
                StandardCharsets.UTF_8))) {
            if (r == null) return;
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    translations.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
