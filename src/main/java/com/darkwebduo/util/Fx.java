package com.darkwebduo.util;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Server-side VFX helpers shared by boxes and bosses. All particle sends use the long-distance variant
 * (visible up to {@link #RANGE} blocks and ignoring the "minimal particles" setting) so boss VFX read from across an arena.
 */
public final class Fx {
    public static final double RANGE = 120.0;

    private Fx() {}

    // ---------- colour helpers ----------
    public static Vector3f rgb(int hex) {
        return new Vector3f(((hex >> 16) & 255) / 255f, ((hex >> 8) & 255) / 255f, (hex & 255) / 255f);
    }

    public static DustParticleOptions dust(int hex, float size) {
        return new DustParticleOptions(rgb(hex), size);
    }

    public static DustColorTransitionOptions dustFade(int from, int to, float size) {
        return new DustColorTransitionOptions(rgb(from), rgb(to), size);
    }

    // ---------- raw send ----------
    /** Sends a particle packet to all players within RANGE. count==0 means (dx,dy,dz) is a velocity (non-dust particles). */
    public static void send(ServerLevel level, ParticleOptions p, double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(x, y, z) <= RANGE * RANGE) {
                level.sendParticles(sp, p, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    public static void send(ServerLevel level, ParticleOptions p, Vec3 pos, int count, double spread, double speed) {
        send(level, p, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    /** One particle with an explicit velocity. */
    public static void vel(ServerLevel level, ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
        send(level, p, x, y, z, 0, vx, vy, vz, 1.0);
    }

    // ---------- shapes ----------
    /** Horizontal ring of n particles at radius r, flat at height y; optional outward velocity. */
    public static void ring(ServerLevel level, ParticleOptions p, double cx, double y, double cz, double r, int n, double outwardSpeed) {
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2 * i) / n;
            double c = Math.cos(a), s = Math.sin(a);
            if (outwardSpeed == 0) send(level, p, cx + c * r, y, cz + s * r, 1, 0, 0, 0, 0);
            else vel(level, p, cx + c * r, y, cz + s * r, c * outwardSpeed, 0, s * outwardSpeed);
        }
    }

    /** Vertical ring (in the plane defined by the horizontal direction (hx,hz)) used for slashes / portals. */
    public static void vring(ServerLevel level, ParticleOptions p, Vec3 center, double hx, double hz, double r, int n) {
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len < 1e-6) { hx = 1; hz = 0; len = 1; }
        hx /= len; hz /= len;
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2 * i) / n;
            double u = Math.cos(a) * r, v = Math.sin(a) * r;
            send(level, p, center.x + hx * u, center.y + v, center.z + hz * u, 1, 0, 0, 0, 0);
        }
    }

    /** Spherical shell of n particles. */
    public static void sphere(ServerLevel level, ParticleOptions p, Vec3 c, double r, int n, double outwardSpeed) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - (i / (double) (n - 1)) * 2;
            double rad = Math.sqrt(Math.max(0, 1 - y * y));
            double th = golden * i;
            double x = Math.cos(th) * rad, z = Math.sin(th) * rad;
            if (outwardSpeed == 0) send(level, p, c.x + x * r, c.y + y * r, c.z + z * r, 1, 0, 0, 0, 0);
            else vel(level, p, c.x + x * r, c.y + y * r, c.z + z * r, x * outwardSpeed, y * outwardSpeed, z * outwardSpeed);
        }
    }

    /** Dense line of particles from a to b (laser / beam). step = spacing in blocks. */
    public static void line(ServerLevel level, ParticleOptions p, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-4) return;
        Vec3 dir = d.scale(1.0 / len);
        for (double t = 0; t <= len; t += step) {
            send(level, p, a.x + dir.x * t, a.y + dir.y * t, a.z + dir.z * t, 1, 0, 0, 0, 0);
        }
    }

    /** Helix around the vertical axis through (cx,cz), from y0 up to y1. phase in radians. */
    public static void helix(ServerLevel level, ParticleOptions p, double cx, double y0, double y1, double cz, double r, int points, double turns, double phase) {
        for (int i = 0; i < points; i++) {
            double t = i / (double) Math.max(1, points - 1);
            double a = phase + t * turns * Math.PI * 2;
            send(level, p, cx + Math.cos(a) * r, y0 + (y1 - y0) * t, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** Vertical column of particles. */
    public static void column(ServerLevel level, ParticleOptions p, double x, double y0, double y1, double z, double step, double spread) {
        for (double y = y0; y <= y1; y += step) {
            send(level, p, x, y, z, 1, spread, 0, spread, 0);
        }
    }

    /** Explosion-like burst: n particles flying outward in random directions. */
    public static void burst(ServerLevel level, ParticleOptions p, Vec3 c, int n, double speed, java.util.Random rnd) {
        for (int i = 0; i < n; i++) {
            double x = rnd.nextGaussian(), y = rnd.nextGaussian(), z = rnd.nextGaussian();
            double l = Math.sqrt(x * x + y * y + z * z);
            if (l < 1e-6) continue;
            vel(level, p, c.x, c.y, c.z, x / l * speed, y / l * speed, z / l * speed);
        }
    }

    // ---------- sound ----------
    public static void sound(ServerLevel level, double x, double y, double z, SoundEvent s, SoundSource src, float vol, float pitch) {
        level.playSound(null, x, y, z, s, src, vol, pitch);
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent s, SoundSource src, float vol, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, s, src, vol, pitch);
    }
}
