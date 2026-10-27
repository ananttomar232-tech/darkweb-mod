package com.darkwebduo.client.cutscene;

import com.darkwebduo.cutscene.CutsceneIds;

/**
 * Data-driven camera choreography. A camera key places the camera on an orbit around the focus point:
 * position = focus + (cos(angle) * radius, height, sin(angle) * radius); it always looks at focus + (0, look, 0).
 * Keys are interpolated with smooth cubic Hermite curves.
 */
public final class CutsceneScripts {
    private CutsceneScripts() {}

    public static final class Key {
        public final float t, radius, height, angle, look, fov, roll, shake;

        public Key(float t, float radius, float height, float angle, float look, float fov, float roll, float shake) {
            this.t = t; this.radius = radius; this.height = height; this.angle = angle;
            this.look = look; this.fov = fov; this.roll = roll; this.shake = shake;
        }

        float get(int i) {
            switch (i) {
                case 0: return radius;
                case 1: return height;
                case 2: return angle;
                case 3: return look;
                case 4: return fov;
                case 5: return roll;
                default: return shake;
            }
        }
    }

    public static final class Caption {
        public final int from, to;
        public final String title, sub;
        public final int rgb;
        public final boolean big;

        public Caption(int from, int to, String title, String sub, int rgb, boolean big) {
            this.from = from; this.to = to; this.title = title; this.sub = sub; this.rgb = rgb; this.big = big;
        }
    }

    public static final class Flash {
        public final int at, argb, dur;

        public Flash(int at, int argb, int dur) {
            this.at = at; this.argb = argb; this.dur = dur;
        }
    }

    public static final class Glitch {
        public final int from, to;
        public final float amount;

        public Glitch(int from, int to, float amount) {
            this.from = from; this.to = to; this.amount = amount;
        }
    }

    public static final class Script {
        public final Key[] keys;
        public final Caption[] captions;
        public final Flash[] flashes;
        public final Glitch[] glitches;
        /** true: focus = the boss entity's feet (when it exists), false: focus = packet origin */
        public final boolean focusBoss;
        /** from this tick on the focus is the packet origin again (used when the boss disappears); -1 = never */
        public final int originFrom;
        public final int duration;

        public Script(Key[] keys, Caption[] captions, Flash[] flashes, Glitch[] glitches, boolean focusBoss, int originFrom, int duration) {
            this.keys = keys; this.captions = captions; this.flashes = flashes; this.glitches = glitches;
            this.focusBoss = focusBoss; this.originFrom = originFrom; this.duration = duration;
        }
    }

    private static Key k(float t, float r, float h, float a, float look, float fov, float roll, float shake) {
        return new Key(t, r, h, a, look, fov, roll, shake);
    }

    private static final int CYAN = 0x19E6FF, MAGENTA = 0xFF2BD6, CRIMSON = 0xFF2040, GOLD = 0xFFC43A, WHITE = 0xFFFFFF;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI"};

    public static Script get(int id, int arg) {
        int dur = CutsceneIds.duration(id);
        switch (id) {
            case CutsceneIds.CIPHER_INTRO:
                return new Script(new Key[]{
                        k(0, 26, 14, -30, 2.5F, 70, 0, 0F),
                        k(30, 20, 8, 10, 3.5F, 66, 0, 0.1F),
                        k(58, 16, 5, 35, 4F, 62, 0, 0.6F),
                        k(62, 15, 4.5F, 40, 4.2F, 60, 0, 2.5F),
                        k(90, 9, 2F, 60, 4.8F, 52, 2, 0.5F),
                        k(108, 7.5F, 1.5F, 75, 5F, 48, -2, 0.8F),
                        k(112, 7, 1.5F, 76, 5.2F, 45, -3, 3F),
                        k(135, 8, 3F, 95, 5F, 60, 0, 1F),
                        k(160, 22, 9F, 130, 3F, 80, 0, 0F)},
                        new Caption[]{
                                new Caption(4, 44, "> ACCESS ATTEMPT DETECTED", "tracing intruder...", CYAN, false),
                                new Caption(98, 154, "THE CIPHER WARDEN", "Gatekeeper of the Dark Web", CYAN, true)},
                        new Flash[]{new Flash(60, 0xAA19E6FF, 14), new Flash(110, 0x66FF1030, 10)},
                        new Glitch[]{new Glitch(58, 68, 0.5F), new Glitch(108, 130, 0.8F)},
                        true, -1, dur);
            case CutsceneIds.CIPHER_PHASE: {
                int rgb = arg >= 3 ? CRIMSON : MAGENTA;
                String sub = arg >= 3 ? "ROOT OVERRIDE" : "FIREWALL BREACHED";
                return new Script(new Key[]{
                        k(0, 14, 4, 0, 4F, 70, 0, 0.3F),
                        k(20, 9, 3, 25, 4.5F, 55, 0, 3F),
                        k(55, 12, 5, 60, 4F, 70, 0, 0.2F)},
                        new Caption[]{new Caption(8, 50, "PHASE " + ROMAN[Math.min(6, Math.max(1, arg))], sub, rgb, true)},
                        new Flash[]{new Flash(20, 0x99000000 | rgb, 12)},
                        new Glitch[]{new Glitch(16, 30, 0.7F)},
                        true, -1, dur);
            }
            case CutsceneIds.CIPHER_DEATH:
                return new Script(new Key[]{
                        k(0, 14, 5, 20, 3F, 65, 0, 0.5F),
                        k(60, 17, 6, 100, 3.5F, 60, 3, 1.2F),
                        k(105, 13, 4, 170, 4F, 55, -3, 2.5F),
                        k(110, 13, 4, 172, 4F, 50, 0, 5F),
                        k(125, 18, 7, 200, 3F, 70, 0, 1.5F),
                        k(150, 22, 9, 240, 3F, 78, 0, 0F)},
                        new Caption[]{new Caption(36, 144, "ACCESS REVOKED", "The Cipher Warden has been deleted", CYAN, true)},
                        new Flash[]{new Flash(110, 0xFFFFFFFF, 20), new Flash(125, 0x6619E6FF, 20)},
                        new Glitch[]{new Glitch(0, 110, 0.35F), new Glitch(98, 112, 0.95F)},
                        true, -1, dur);
            case CutsceneIds.OMEGA_SUMMON:
                return new Script(new Key[]{
                        k(0, 4.5F, 1.8F, 200, 1.0F, 70, 0, 0.2F),
                        k(40, 5F, 2.2F, 230, 1.4F, 62, 0, 0.8F),
                        k(60, 9, 3F, 260, 3F, 64, 0, 1.5F),
                        k(100, 20, 14F, 300, 18F, 72, 0, 1.2F),
                        k(120, 26, 16F, 320, 26F, 78, 0, 3F),
                        k(160, 30, 12F, 340, 24F, 80, 0, 2F),
                        k(190, 24, 8F, 355, 14F, 70, 0, 2F),
                        k(208, 22, 3F, 10, 8F, 58, 0, 1F),
                        k(212, 22, 3F, 12, 8F, 55, 0, 6F),
                        k(235, 14, 2.5F, 40, 10F, 44, -3, 2F),
                        k(250, 16, 4F, 70, 9F, 60, 0, 1F),
                        k(280, 30, 10F, 110, 6F, 80, 0, 0F)},
                        new Caption[]{
                                new Caption(2, 40, "> INSERTING OMEGA KEYCARD...", "authenticating", GOLD, false),
                                new Caption(44, 96, "> ROOT PRIVILEGES REQUESTED", "no valid administrator found", GOLD, false),
                                new Caption(238, 276, "OMEGA ROOT", "The Final Administrator", GOLD, true)},
                        new Flash[]{new Flash(120, 0xCCFFFFFF, 12), new Flash(212, 0xAAFFC43A, 16), new Flash(235, 0x66FFFFFF, 10)},
                        new Glitch[]{new Glitch(120, 135, 0.6F), new Glitch(205, 215, 0.9F)},
                        false, -1, dur);
            case CutsceneIds.OMEGA_PHASE: {
                String[] subs = {"", "", "ADMINISTRATOR PRIVILEGES", "KERNEL PANIC", "RESERVED MEMORY", "OMEGA PROTOCOL"};
                int a = Math.min(5, Math.max(2, arg));
                return new Script(new Key[]{
                        k(0, 16, 6, 0, 6F, 70, 0, 0.4F),
                        k(25, 10, 5, 30, 7F, 52, 0, 4F),
                        k(70, 18, 8, 80, 6F, 72, 0, 0.3F)},
                        new Caption[]{new Caption(10, 64, "PHASE " + ROMAN[a], subs[a], a >= 4 ? CRIMSON : GOLD, true)},
                        new Flash[]{new Flash(25, 0xAAFFFFFF, 14)},
                        new Glitch[]{new Glitch(20, 36, 0.8F)},
                        true, -1, dur);
            }
            case CutsceneIds.OMEGA_DEATH:
                return new Script(new Key[]{
                        k(0, 20, 10, 0, 6F, 66, 0, 0.8F),
                        k(80, 24, 12, 90, 7F, 62, 2, 1.8F),
                        k(120, 18, 8, 150, 8F, 56, -3, 3F),
                        k(160, 22, 6, 200, 8F, 52, 3, 2.5F),
                        k(198, 26, 8, 230, 8F, 50, 0, 4F),
                        k(200, 26, 8, 232, 8F, 48, 0, 9F),
                        k(215, 8, 2F, 250, 1.4F, 56, 0, 2F),
                        k(250, 6, 1.6F, 300, 1.8F, 48, 0, 0.2F),
                        k(280, 8, 2F, 340, 1.5F, 55, 0, 0F)},
                        new Caption[]{
                                new Caption(20, 112, "SYSTEM FAILURE", "omega root is collapsing", GOLD, false),
                                new Caption(222, 276, "ROOT ACCESS GRANTED", "iPhone Duo unlocked", CYAN, true)},
                        new Flash[]{new Flash(200, 0xFFFFFFFF, 30)},
                        new Glitch[]{new Glitch(0, 200, 0.4F), new Glitch(190, 202, 1F)},
                        true, 205, dur);
            default:
                return null;
        }
    }

    /** Samples all 7 channels of a script at time t (ticks, fractional): radius, height, angle, look, fov, roll, shake. */
    public static float[] sample(Key[] keys, float t) {
        float[] out = new float[7];
        int n = keys.length;
        if (t <= keys[0].t) {
            for (int c = 0; c < 7; c++) out[c] = keys[0].get(c);
            return out;
        }
        if (t >= keys[n - 1].t) {
            for (int c = 0; c < 7; c++) out[c] = keys[n - 1].get(c);
            return out;
        }
        int i = 0;
        while (i < n - 2 && t > keys[i + 1].t) i++;
        Key a = keys[i], b = keys[i + 1];
        Key p = i > 0 ? keys[i - 1] : null;
        Key q = i + 2 < n ? keys[i + 2] : null;
        float h = b.t - a.t;
        float u = (t - a.t) / h;
        float u2 = u * u, u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        for (int c = 0; c < 7; c++) {
            float v0 = a.get(c), v1 = b.get(c);
            float m0 = p == null ? (v1 - v0) : (v1 - p.get(c)) / (b.t - p.t) * h;
            float m1 = q == null ? (v1 - v0) : (q.get(c) - v0) / (q.t - a.t) * h;
            // shake is a pure "level" channel: avoid overshoot below zero
            float val = h00 * v0 + h10 * m0 + h01 * v1 + h11 * m1;
            out[c] = c == 6 ? Math.max(0F, val) : val;
        }
        return out;
    }
}
