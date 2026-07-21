package yourstageskybox.skybox;

/**
 * 天空盒激活状态 —— 存储每维度/每玩家的天空盒名称、透明度、色相、过渡时长。
 * <p>
 * {@code name} 为 null 或空表示恢复原版天空（此时 alpha/color 无意义）。
 * </p>
 */
public class SkyboxState {

    /** 天空盒名称（null/空 = 原版天空） */
    public String name;

    /** 透明度，范围 0.0 ~ 1.0，默认 1.0 */
    public float alpha = 1.0f;

    /** RGB 色相，默认白色 (1,1,1) */
    public float red = 1.0f;
    public float green = 1.0f;
    public float blue = 1.0f;

    /** 切换到该天空盒时的过渡时长（毫秒），默认 2000ms */
    public long durationMs = 2000L;

    // ---- 构造 ----

    public SkyboxState() {
    }

    public SkyboxState(String name) {
        this.name = (name != null && !name.isEmpty()) ? name : null;
    }

    public SkyboxState(String name, float alpha) {
        this.name = (name != null && !name.isEmpty()) ? name : null;
        this.alpha = clamp(alpha);
    }

    public SkyboxState(String name, float alpha, long durationMs) {
        this.name = (name != null && !name.isEmpty()) ? name : null;
        this.alpha = clamp(alpha);
        this.durationMs = Math.max(0, durationMs);
    }

    // ---- 查询 ----

    public boolean isVanilla() {
        return name == null || name.isEmpty();
    }

    // ---- 工具 ----

    private static float clamp(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    @Override
    public String toString() {
        return isVanilla()
                ? "(原版天空)"
                : name + " alpha=" + String.format("%.2f", alpha)
                    + " color=(" + String.format("%.2f", red) + "," + String.format("%.2f", green) + "," + String.format("%.2f", blue) + ")"
                    + " dur=" + durationMs + "ms";
    }
}
