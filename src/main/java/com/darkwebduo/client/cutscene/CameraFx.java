package com.darkwebduo.client.cutscene;

/** Client-side screen shake and colour flash state (driven by ShakePacket / FlashPacket and by cutscene scripts). */
public final class CameraFx {
    private static float shakeIntensity;
    private static int shakeTicks;
    private static int shakeTotal = 1;

    private static int flashArgb;
    private static int flashTicks;
    private static int flashTotal = 1;

    private CameraFx() {}

    public static void shake(float intensity, int ticks) {
        if (ticks <= 0 || intensity <= 0F) return;
        float current = shakeAmount(0F);
        if (intensity >= current) {
            shakeIntensity = intensity;
            shakeTicks = ticks;
            shakeTotal = ticks;
        } else {
            // a weaker shake while a strong one is running just extends it slightly
            shakeTicks = Math.max(shakeTicks, Math.min(ticks, shakeTotal));
        }
    }

    public static float shakeAmount(float partial) {
        if (shakeTicks <= 0) return 0F;
        float k = Math.max(0F, (shakeTicks - partial) / (float) shakeTotal);
        return shakeIntensity * k * k;
    }

    public static void flash(int argb, int ticks) {
        if (ticks <= 0) return;
        flashArgb = argb;
        flashTicks = ticks;
        flashTotal = ticks;
    }

    /** Current flash opacity 0..1 (already multiplied by the colour's own alpha). */
    public static float flashAlpha(float partial) {
        if (flashTicks <= 0) return 0F;
        float k = Math.max(0F, (flashTicks - partial) / (float) flashTotal);
        float peak = ((flashArgb >>> 24) & 255) / 255F;
        return peak * k * k;
    }

    public static int flashRgb() {
        return flashArgb & 0x00FFFFFF;
    }

    public static void tick() {
        if (shakeTicks > 0) shakeTicks--;
        if (flashTicks > 0) flashTicks--;
    }

    public static void clear() {
        shakeTicks = 0;
        flashTicks = 0;
    }

    /** Smooth pseudo-noise in -1..1 (sum of sines), good enough for camera shake. */
    public static float noise(float t, float seed) {
        return (float) (Math.sin(t * 13.1 + seed) * 0.55 + Math.sin(t * 27.7 + seed * 1.7) * 0.3 + Math.sin(t * 51.3 + seed * 2.3) * 0.15);
    }
}
