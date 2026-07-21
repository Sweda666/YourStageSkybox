package yourstageskybox.skybox;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 天空盒过渡状态 —— 仅客户端有效（1.20.1 版本）。
 */
@OnlyIn(Dist.CLIENT)
public class TransitionState {

    public final String fromName;
    public final String toName;
    public final long startTime;
    public final long durationMs;

    public final float fromAlpha;
    public final float fromR;
    public final float fromG;
    public final float fromB;

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

    public float getProgress() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= durationMs) return 1.0f;
        float t = (float) elapsed / durationMs;
        if (t < 0.5f) return 4.0f * t * t * t;
        float f = t - 1.0f;
        return 1.0f + 4.0f * f * f * f;
    }

    public boolean isComplete() {
        return System.currentTimeMillis() - startTime >= durationMs;
    }

    @Override
    public String toString() {
        float progress = getProgress();
        return (fromName != null ? fromName : "vanilla")
                + " -> " + (toName != null ? toName : "vanilla")
                + " [" + String.format("%.1f", progress * 100) + "%] "
                + String.format("%.1f", progress * durationMs / 1000f) + "/"
                + String.format("%.1f", durationMs / 1000f) + "s";
    }
}
