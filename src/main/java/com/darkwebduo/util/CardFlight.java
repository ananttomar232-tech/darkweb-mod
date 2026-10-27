package com.darkwebduo.util;

import net.minecraft.world.phys.Vec3;

/**
 * Shared (server + client) timeline and maths of the delivery-box opening sequence.
 * The server uses it to place trail particles, the client renderer uses it to draw the flying card, so both agree.
 */
public final class CardFlight {
    private CardFlight() {}

    public static final int T_CHARGE_END = 24;   // box shakes, energy spirals in
    public static final int T_LID_START = 24;    // lid starts to open
    public static final int T_LID_END = 42;      // lid fully open
    public static final int T_BURST = 36;        // light burst + card appears
    public static final int T_RISE_END = 66;     // card has risen to its apex
    public static final int T_DELIVER = 90;      // card reaches the player, item is handed over
    public static final int T_VISUAL_END = 118;  // beam / sparkles fade out

    public static final float LID_OPEN_DEGREES = 112.0F;

    public static final float RISE_START_Y = 0.55F;
    public static final float RISE_TOP_Y = 2.25F;

    /** Lid angle in degrees at time t (ticks, fractional) with a springy overshoot. */
    public static float lidAngle(float t) {
        if (t <= T_LID_START) return 0F;
        if (t >= T_LID_END + 10) return LID_OPEN_DEGREES;
        float u = clamp01((t - T_LID_START) / (float) (T_LID_END - T_LID_START));
        float c1 = 1.70158F;
        float c3 = c1 + 1F;
        float e = 1F + c3 * (float) Math.pow(u - 1F, 3) + c1 * (float) Math.pow(u - 1F, 2);
        float angle = LID_OPEN_DEGREES * e;
        if (t > T_LID_END) {
            // tiny settling wobble after the lid has finished
            float k = (t - T_LID_END) / 10F;
            angle = LID_OPEN_DEGREES + (float) Math.sin(k * Math.PI * 3) * 2.5F * (1F - k);
        }
        return angle;
    }

    /** True while the card is visible. */
    public static boolean cardVisible(float t) {
        return t >= T_BURST && t < T_DELIVER;
    }

    /**
     * World position of the card centre.
     *
     * @param base   centre of the box floor (x, y, z)
     * @param target world position the card flies into (the opener's chest)
     */
    public static Vec3 cardPos(float t, Vec3 base, Vec3 target) {
        if (t < T_BURST) return base.add(0, RISE_START_Y, 0);
        if (t < T_RISE_END) {
            float u = clamp01((t - T_BURST) / (float) (T_RISE_END - T_BURST));
            float e = 1F - (float) Math.pow(1F - u, 3);
            double y = RISE_START_Y + (RISE_TOP_Y - RISE_START_Y) * e;
            double wob = Math.sin(t * 0.35) * 0.05 * e;
            return base.add(wob, y, Math.cos(t * 0.35) * 0.05 * e);
        }
        float u = clamp01((t - T_RISE_END) / (float) (T_DELIVER - T_RISE_END));
        float e = u * u * (3F - 2F * u);
        Vec3 p0 = base.add(0, RISE_TOP_Y, 0);
        Vec3 p2 = target;
        Vec3 mid = p0.add(p2).scale(0.5);
        // control point: lifted and swirled sideways so the card arcs instead of flying straight
        Vec3 side = new Vec3(-(p2.z - p0.z), 0, p2.x - p0.x).normalize().scale(0.9);
        Vec3 p1 = mid.add(0, 1.1, 0).add(side);
        double a = (1 - e) * (1 - e), b = 2 * (1 - e) * e, c = e * e;
        return new Vec3(a * p0.x + b * p1.x + c * p2.x, a * p0.y + b * p1.y + c * p2.y, a * p0.z + b * p1.z + c * p2.z);
    }

    /** Uniform scale of the card (pop in, shrink into the inventory). */
    public static float cardScale(float t) {
        float in = clamp01((t - T_BURST) / 6F);
        float out = clamp01((T_DELIVER - t) / 8F);
        return (0.25F + 0.75F * easeOutBack(in)) * (0.35F + 0.65F * out);
    }

    /** Spin angle in degrees. */
    public static float cardSpin(float t) {
        float speed = t < T_RISE_END ? 14F : 36F;
        return t * speed;
    }

    /** Glow beam intensity 0..1 (rises at the burst, fades at the end). */
    public static float beamIntensity(float t) {
        if (t < T_BURST - 4) return 0F;
        float in = clamp01((t - (T_BURST - 4)) / 6F);
        float out = clamp01((T_VISUAL_END - t) / 24F);
        return in * out;
    }

    /** Charge glow 0..1 during the charge phase. */
    public static float chargeIntensity(float t) {
        if (t < 0 || t > T_BURST + 6) return 0F;
        return clamp01(t / (float) T_CHARGE_END);
    }

    public static float clamp01(float v) {
        return v < 0F ? 0F : (v > 1F ? 1F : v);
    }

    private static float easeOutBack(float u) {
        float c1 = 1.70158F;
        float c3 = c1 + 1F;
        return 1F + c3 * (float) Math.pow(u - 1F, 3) + c1 * (float) Math.pow(u - 1F, 2);
    }
}
