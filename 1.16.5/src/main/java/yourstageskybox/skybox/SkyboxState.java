package yourstageskybox.skybox;

/**
 * 天空盒激活状态（纯数据类，1.12.2 → 1.16.5 无需改动）。
 */
public class SkyboxState {

    public String name;
    public float alpha = 1.0f;
    public float red = 1.0f;
    public float green = 1.0f;
    public float blue = 1.0f;
    public long durationMs = 2000L;

    public SkyboxState() {}

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

    public boolean isVanilla() {
        return name == null || name.isEmpty();
    }

    private static float clamp(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    @Override
    public String toString() {
        return isVanilla()
                ? "(vanilla sky)"
                : name + " alpha=" + String.format("%.2f", alpha)
                    + " color=(" + String.format("%.2f", red) + "," + String.format("%.2f", green) + "," + String.format("%.2f", blue) + ")"
                    + " dur=" + durationMs + "ms";
    }
}
