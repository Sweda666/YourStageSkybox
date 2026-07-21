package yourstageskybox.skybox;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 天空盒过渡状态 —— 仅客户端有效。
 * <p>
 * 记录一次切换中旧/新天空盒的名称、颜色、透明度、起止时间，
 * 供 {@link SkyboxRenderer} 每帧插值绘制实现淡入淡出。
 * </p>
 */
@SideOnly(Side.CLIENT)
public class TransitionState {

    /** 过渡前的天空盒名称（null = 原版天空） */
    public final String fromName;
    /** 过渡后的天空盒名称（null = 原版天空） */
    public final String toName;

    /** 过渡开始时间（{@link System#currentTimeMillis()}） */
    public final long startTime;
    /** 过渡总时长（毫秒） */
    public final long durationMs;

    // ---- 旧天空盒颜色参数 ----
    public final float fromAlpha;
    public final float fromR;
    public final float fromG;
    public final float fromB;

    // ---- 新天空盒颜色参数 ----
    public final float toAlpha;
    public final float toR;
    public final float toG;
    public final float toB;

    public TransitionState(String fromName, String toName, long durationMs,
                           float fromAlpha, float fromR, float fromG, float fromB,
                           float toAlpha, float toR, float toG, float toB) {
        this.fromName = (fromName != null && !fromName.isEmpty()) ? fromName : null;
        this.toName   = (toName   != null && !toName.isEmpty())   ? toName   : null;
        this.startTime = System.currentTimeMillis();
        this.durationMs = Math.max(1, durationMs);
        this.fromAlpha = fromAlpha;
        this.fromR = fromR;
        this.fromG = fromG;
        this.fromB = fromB;
        this.toAlpha = toAlpha;
        this.toR = toR;
        this.toG = toG;
        this.toB = toB;
    }

    /**
     * 计算当前过渡进度（0.0 ~ 1.0），使用 easeInOutCubic 缓动。
     */
    public float getProgress() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= durationMs) return 1.0f;

        float t = (float) elapsed / durationMs;
        // easeInOutCubic
        if (t < 0.5f) {
            return 4.0f * t * t * t;
        } else {
            float f = t - 1.0f;
            return 1.0f + 4.0f * f * f * f;
        }
    }

    public boolean isComplete() {
        return System.currentTimeMillis() - startTime >= durationMs;
    }

    @Override
    public String toString() {
        float progress = getProgress();
        return (fromName != null ? fromName : "原版")
                + " → " + (toName != null ? toName : "原版")
                + " [" + String.format("%.1f", progress * 100) + "%] "
                + String.format("%.1f", progress * durationMs / 1000f) + "/"
                + String.format("%.1f", durationMs / 1000f) + "s";
    }
}
