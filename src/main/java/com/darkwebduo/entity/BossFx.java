package com.darkwebduo.entity;

import com.darkwebduo.net.ModNetwork;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side simulation of everything a boss "throws": homing projectiles, expanding shockwave rings, telegraphed blast zones,
 * advancing fire walls and gravity vortices. No entities are spawned; everything is particles + damage tests.
 * The owning boss spawns objects here and calls {@link #tick} once per server tick.
 */
public final class BossFx {
    private final BossBase owner;
    private final Random rnd = new Random();

    public final List<Proj> projectiles = new ArrayList<>();
    public final List<Ring> rings = new ArrayList<>();
    public final List<Zone> zones = new ArrayList<>();
    public final List<Wall> walls = new ArrayList<>();
    public final List<Vortex> vortices = new ArrayList<>();

    public BossFx(BossBase owner) {
        this.owner = owner;
    }

    public void clear() {
        projectiles.clear();
        rings.clear();
        zones.clear();
        walls.clear();
        vortices.clear();
    }

    // ================================================================== types

    /** Homing / straight bolt. */
    public static final class Proj {
        public Vec3 pos;
        public Vec3 vel;
        public int life;
        public float dmg;
        public double radius = 0.9;
        public double blastRadius = 3.0;
        public int color = 0x19E6FF;
        public int color2 = 0xFF2BD6;
        public LivingEntity target;
        public double turn = 0.0; // 0 = straight, ~0.12 = strong homing
        public SoundEvent hitSound;
        public boolean magic = true;
    }

    /** Expanding horizontal shockwave. Damages entities whose distance from the centre lies inside the ring band. */
    public static final class Ring {
        public Vec3 c;
        public double r;
        public double speed;
        public double maxR;
        public double thickness = 1.6;
        public double height = 1.0; // entities must be within this height above the ring plane (a jump clears it)
        public float dmg;
        public double kb = 1.2;
        public double up = 0.5;
        public int color = 0x19E6FF;
        public int color2 = 0xFF2BD6;
        public Set<UUID> hit = new HashSet<>();
        public int delay = 0;
        public boolean visual = false; // true: particles only, no damage
    }

    /** Telegraphed circular blast: ring grows while charging, then detonates. style: 0 blast, 1 light pillar, 2 lightning, 3 annihilation column. */
    public static final class Zone {
        public Vec3 c;
        public double radius;
        public int delay;
        public int age = 0;
        public float dmg;
        public int color = 0x19E6FF;
        public int color2 = 0xFF2BD6;
        public int style = 0;
        public double kb = 0.9;
        public double up = 0.6;
        public double height = 40.0;
        public SoundEvent sound;
        public boolean safeZone = false; // true: damages everything OUTSIDE the radius (server-crash style safe tile)
        public boolean lite = false;     // fewer telegraph particles (used when dozens of zones are active)
        public boolean visual = false;   // true: no damage, only effects
        public LivingEntity track;       // if set, the zone follows this entity until 12 ticks before detonation
    }

    /** Advancing line of fire with a safe gap. */
    public static final class Wall {
        public Vec3 pos;      // current centre of the wall line (on the floor)
        public Vec3 dir;      // movement direction (unit, horizontal)
        public Vec3 along;    // direction the wall extends (unit, horizontal, perpendicular to dir)
        public double length;
        public double gapCenter; // offset along 'along' from centre where the gap is
        public double gapWidth;
        public double speed;
        public int life;
        public float dmg;
        public int color = 0x19E6FF;
        public int color2 = 0x1060FF;
    }

    /** Pulls entities in, then detonates. */
    public static final class Vortex {
        public Vec3 c;
        public double radius;
        public int life;
        public int maxLife;
        public double pull;
        public float dmg;
        public double blastRadius;
        public int color = 0xB020FF;
        public int color2 = 0x000000;
    }

    // ================================================================== tick

    public void tick(ServerLevel sl) {
        tickProjectiles(sl);
        tickRings(sl);
        tickZones(sl);
        tickWalls(sl);
        tickVortices(sl);
    }

    private void tickProjectiles(ServerLevel sl) {
        Iterator<Proj> it = projectiles.iterator();
        while (it.hasNext()) {
            Proj p = it.next();
            p.life--;
            if (p.target != null && p.target.isAlive() && p.turn > 0) {
                Vec3 want = p.target.position().add(0, p.target.getBbHeight() * 0.6, 0).subtract(p.pos).normalize().scale(p.vel.length());
                p.vel = p.vel.lerp(want, Math.min(1.0, p.turn));
            }
            Vec3 next = p.pos.add(p.vel);
            boolean blocked = false;
            BlockPos bp = BlockPos.containing(next);
            BlockState st = sl.getBlockState(bp);
            if (!st.isAir() && !st.getCollisionShape(sl, bp).isEmpty()) blocked = true;

            // trail
            Fx.send(sl, Fx.dustFade(p.color, p.color2, 1.3F), p.pos.x, p.pos.y, p.pos.z, 2, 0.08, 0.08, 0.08, 0);
            Fx.send(sl, ParticleTypes.END_ROD, p.pos.x, p.pos.y, p.pos.z, 1, 0.02, 0.02, 0.02, 0.01);
            if ((p.life & 1) == 0) Fx.send(sl, ParticleTypes.ELECTRIC_SPARK, p.pos.x, p.pos.y, p.pos.z, 1, 0.15, 0.15, 0.15, 0.1);

            boolean hitEntity = false;
            AABB box = new AABB(next, next).inflate(p.radius);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, owner::isValidVictim)) {
                hitEntity = true;
                break;
            }
            p.pos = next;
            if (blocked || hitEntity || p.life <= 0) {
                explode(sl, p.pos, p.blastRadius, p.dmg, p.color, p.color2, p.hitSound, p.magic);
                it.remove();
            }
        }
    }

    /** Small AoE explosion used by projectiles and zones. */
    public void explode(ServerLevel sl, Vec3 c, double r, float dmg, int color, int color2, SoundEvent snd, boolean magic) {
        Fx.send(sl, ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        Fx.burst(sl, ParticleTypes.END_ROD, c, 18, 0.35, rnd);
        Fx.burst(sl, Fx.dust(color, 1.8F), c, 20, 0.3, rnd);
        Fx.burst(sl, Fx.dust(color2, 1.4F), c, 12, 0.22, rnd);
        Fx.ring(sl, Fx.dust(color, 1.5F), c.x, c.y, c.z, r * 0.55, 18, 0.16);
        if (snd != null) Fx.sound(sl, c, snd, SoundSource.HOSTILE, 1.0F, 0.9F + rnd.nextFloat() * 0.3F);
        owner.aoeDamage(c, r, dmg, 0.7, 0.35, magic);
    }

    private void tickRings(ServerLevel sl) {
        Iterator<Ring> it = rings.iterator();
        while (it.hasNext()) {
            Ring g = it.next();
            if (g.delay > 0) {
                g.delay--;
                continue;
            }
            g.r += g.speed;
            int n = (int) Math.max(24, Math.min(160, g.r * 9));
            for (int i = 0; i < n; i++) {
                double a = (Math.PI * 2 * i) / n + (g.r * 0.7);
                double x = g.c.x + Math.cos(a) * g.r, z = g.c.z + Math.sin(a) * g.r;
                Fx.send(sl, Fx.dustFade(g.color, g.color2, 1.4F), x, g.c.y + 0.15, z, 1, 0.05, 0.05, 0.05, 0);
                if (i % 3 == 0) Fx.vel(sl, ParticleTypes.ELECTRIC_SPARK, x, g.c.y + 0.2, z, Math.cos(a) * 0.05, 0.12, Math.sin(a) * 0.05);
                if (i % 6 == 0) {
                    BlockPos bp = BlockPos.containing(x, g.c.y - 0.5, z);
                    BlockState st = sl.getBlockState(bp);
                    if (!st.isAir()) Fx.vel(sl, new BlockParticleOption(ParticleTypes.BLOCK, st), x, g.c.y + 0.3, z, 0, 0.25, 0);
                }
            }
            if (g.visual) {
                if (g.r >= g.maxR) it.remove();
                continue;
            }
            double lo = g.r - g.thickness * 0.5, hi = g.r + g.thickness * 0.5;
            AABB area = new AABB(g.c.x - hi, g.c.y - 1.0, g.c.z - hi, g.c.x + hi, g.c.y + g.height, g.c.z + hi);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, area, owner::isValidVictim)) {
                if (g.hit.contains(e.getUUID())) continue;
                double dx = e.getX() - g.c.x, dz = e.getZ() - g.c.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < lo - e.getBbWidth() * 0.5 || d > hi + e.getBbWidth() * 0.5) continue;
                if (e.getY() > g.c.y + g.height || e.getY() + e.getBbHeight() < g.c.y - 0.5) continue;
                g.hit.add(e.getUUID());
                owner.hurtTarget(e, g.dmg, false);
                owner.launch(e, new Vec3(dx, 0, dz).normalize(), g.kb, g.up);
            }
            if (g.r >= g.maxR) it.remove();
        }
    }

    private void tickZones(ServerLevel sl) {
        Iterator<Zone> it = zones.iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            z.age++;
            double k = Math.min(1.0, z.age / (double) Math.max(1, z.delay));
            if (z.track != null && z.track.isAlive() && z.age < z.delay - 12) {
                Vec3 tp = z.track.position();
                z.c = new Vec3(z.c.x + (tp.x - z.c.x) * 0.22, z.c.y, z.c.z + (tp.z - z.c.z) * 0.22);
            }
            if (z.age < z.delay) {
                if (z.lite) {
                    if (z.age % 4 == 0) {
                        Fx.ring(sl, Fx.dust(z.color, 1.1F), z.c.x, z.c.y + 0.12, z.c.z, z.radius, (int) Math.max(10, z.radius * 4), 0);
                        Fx.ring(sl, Fx.dust(z.color2, 1.0F), z.c.x, z.c.y + 0.12, z.c.z, z.radius * k, (int) Math.max(6, z.radius * 3 * k), 0);
                    }
                    continue;
                }
                // telegraph: outline + filling inner ring + converging motes
                if (z.age % 2 == 0) {
                    Fx.ring(sl, Fx.dust(z.color, 1.2F), z.c.x, z.c.y + 0.12, z.c.z, z.radius, (int) Math.max(18, z.radius * 10), 0);
                    Fx.ring(sl, Fx.dustFade(z.color2, z.color, 1.1F), z.c.x, z.c.y + 0.12, z.c.z, z.radius * k, (int) Math.max(10, z.radius * 6 * k), 0);
                    if (z.style == 1 || z.style == 3) {
                        Fx.column(sl, ParticleTypes.END_ROD, z.c.x, z.c.y, z.c.y + 2.0 + 6.0 * k, z.c.z, 0.9, z.radius * 0.15);
                    }
                }
                if (z.safeZone && z.age % 4 == 0) {
                    Fx.ring(sl, Fx.dust(0x19FF8A, 1.3F), z.c.x, z.c.y + 0.12, z.c.z, z.radius, (int) Math.max(18, z.radius * 10), 0);
                }
                continue;
            }
            detonateZone(sl, z);
            it.remove();
        }
    }

    private void detonateZone(ServerLevel sl, Zone z) {
        Vec3 c = z.c;
        if (z.sound != null) Fx.sound(sl, c, z.sound, SoundSource.HOSTILE, 1.1F, 0.9F + rnd.nextFloat() * 0.2F);
        switch (z.style) {
            case 1: // light pillar
                Fx.column(sl, ParticleTypes.END_ROD, c.x, c.y, c.y + z.height * 0.5, c.z, 0.5, z.radius * 0.35);
                Fx.column(sl, Fx.dust(z.color, 2.2F), c.x, c.y, c.y + z.height * 0.5, c.z, 0.6, z.radius * 0.45);
                Fx.send(sl, ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
                break;
            case 2: // lightning
                Fx.column(sl, ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.y + 24, c.z, 0.35, z.radius * 0.3);
                Fx.column(sl, Fx.dust(0xFFFFFF, 1.6F), c.x, c.y, c.y + 24, c.z, 0.5, 0.2);
                break;
            case 3: // annihilation column
                Fx.column(sl, Fx.dust(0xFFFFFF, 2.6F), c.x, c.y, c.y + z.height, c.z, 0.4, z.radius * 0.3);
                Fx.column(sl, Fx.dust(z.color, 2.4F), c.x, c.y, c.y + z.height, c.z, 0.5, z.radius * 0.6);
                Fx.column(sl, ParticleTypes.END_ROD, c.x, c.y, c.y + z.height, c.z, 0.7, z.radius * 0.4);
                Fx.send(sl, ParticleTypes.FLASH, c.x, c.y + 2, c.z, 1, 0, 0, 0, 0);
                break;
            default:
                break;
        }
        Fx.send(sl, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        Fx.burst(sl, Fx.dust(z.color, 2.0F), c.add(0, 0.5, 0), 40, 0.45, rnd);
        Fx.burst(sl, ParticleTypes.END_ROD, c.add(0, 0.5, 0), 30, 0.4, rnd);
        Fx.ring(sl, Fx.dustFade(z.color, z.color2, 1.8F), c.x, c.y + 0.2, c.z, z.radius, (int) Math.max(24, z.radius * 12), 0.25);
        ModNetwork.shake(sl, c, 30, 0.5F, 8);

        if (z.visual) return;
        if (z.safeZone) {
            // damage everything that is NOT inside the safe circle (within 60 blocks of the centre of the arena is handled by owner)
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(80), owner::isValidVictim)) {
                double dx = e.getX() - c.x, dz = e.getZ() - c.z;
                if (Math.sqrt(dx * dx + dz * dz) > z.radius) {
                    owner.hurtTarget(e, z.dmg, true);
                    owner.launch(e, new Vec3(dx, 0, dz).normalize(), z.kb, z.up);
                }
            }
        } else {
            double h = z.style == 0 ? 3.5 : 40.0;
            AABB box = new AABB(c.x - z.radius, c.y - 1, c.z - z.radius, c.x + z.radius, c.y + h, c.z + z.radius);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, owner::isValidVictim)) {
                double dx = e.getX() - c.x, dz = e.getZ() - c.z;
                if (Math.sqrt(dx * dx + dz * dz) <= z.radius + e.getBbWidth() * 0.5) {
                    owner.hurtTarget(e, z.dmg, true);
                    owner.launch(e, new Vec3(dx, 0, dz).normalize(), z.kb, z.up);
                }
            }
        }
    }

    private void tickWalls(ServerLevel sl) {
        Iterator<Wall> it = walls.iterator();
        while (it.hasNext()) {
            Wall w = it.next();
            w.life--;
            w.pos = w.pos.add(w.dir.scale(w.speed));
            double half = w.length * 0.5;
            for (double s = -half; s <= half; s += 1.5) {
                if (Math.abs(s - w.gapCenter) < w.gapWidth * 0.5) {
                    // safe gap marker
                    if (w.life % 4 == 0) {
                        Vec3 g = w.pos.add(w.along.scale(s));
                        Fx.send(sl, Fx.dust(0x19FF8A, 1.2F), g.x, g.y + 0.3, g.z, 1, 0.1, 0.5, 0.1, 0);
                    }
                    continue;
                }
                Vec3 p = w.pos.add(w.along.scale(s));
                double flick = rnd.nextDouble();
                if (rnd.nextInt(3) == 0) Fx.vel(sl, ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.1, p.z, 0, 0.12 + flick * 0.12, 0);
                Fx.send(sl, Fx.dustFade(w.color, w.color2, 2.2F), p.x, p.y + 0.6 + flick * 1.2, p.z, 2, 0.2, 0.5, 0.2, 0);
                if (rnd.nextInt(6) == 0) Fx.vel(sl, ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1.0, p.z, 0, 0.2, 0);
            }
            // damage: entities within 1.2 of the wall plane and inside the length but outside the gap
            AABB box = new AABB(w.pos, w.pos).inflate(half + 2, 3, half + 2);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, owner::isValidVictim)) {
                Vec3 rel = e.position().subtract(w.pos);
                double along = rel.x * w.along.x + rel.z * w.along.z;
                double across = rel.x * w.dir.x + rel.z * w.dir.z;
                if (Math.abs(across) > 1.3 + w.speed || Math.abs(along) > half) continue;
                if (Math.abs(along - w.gapCenter) < w.gapWidth * 0.5) continue;
                if (e.getY() > w.pos.y + 3.0) continue;
                if (e.invulnerableTime > 0) continue;
                owner.hurtTarget(e, w.dmg, true);
                e.setSecondsOnFire(3);
                owner.launch(e, w.dir, 0.9, 0.5);
            }
            if (w.life <= 0) it.remove();
        }
    }

    private void tickVortices(ServerLevel sl) {
        Iterator<Vortex> it = vortices.iterator();
        while (it.hasNext()) {
            Vortex v = it.next();
            v.life++;
            double k = v.life / (double) v.maxLife;
            // swirling accretion disc
            for (int arm = 0; arm < 4; arm++) {
                for (int s = 0; s < 6; s++) {
                    double rr = v.radius * (0.15 + 0.85 * (s / 6.0));
                    double a = v.life * 0.35 + arm * (Math.PI / 2) + (1.0 - s / 6.0) * 3.5;
                    Fx.send(sl, Fx.dustFade(v.color, 0x000000, 1.5F), v.c.x + Math.cos(a) * rr, v.c.y + Math.sin(v.life * 0.1 + arm) * 0.4, v.c.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                }
            }
            Fx.send(sl, ParticleTypes.SQUID_INK, v.c.x, v.c.y, v.c.z, 3, 0.4, 0.4, 0.4, 0.02);
            Fx.send(sl, ParticleTypes.REVERSE_PORTAL, v.c.x, v.c.y, v.c.z, 4, v.radius * 0.5, 0.6, v.radius * 0.5, 0.2);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(v.c, v.c).inflate(v.radius * 1.6), owner::isValidVictim)) {
                Vec3 to = v.c.subtract(e.position());
                double d = Math.max(1.0, to.length());
                Vec3 pull = to.normalize().scale(v.pull * (0.4 + k) / Math.max(1.0, d * 0.25));
                e.setDeltaMovement(e.getDeltaMovement().add(pull.x, pull.y * 0.15, pull.z));
                e.hurtMarked = true;
                if (d < 2.0 && v.life % 10 == 0) owner.hurtTarget(e, v.dmg * 0.15F, true);
            }
            if (v.life >= v.maxLife) {
                Fx.send(sl, ParticleTypes.EXPLOSION_EMITTER, v.c.x, v.c.y, v.c.z, 1, 0, 0, 0, 0);
                Fx.send(sl, ParticleTypes.FLASH, v.c.x, v.c.y, v.c.z, 1, 0, 0, 0, 0);
                Fx.sphere(sl, Fx.dust(v.color, 2.2F), v.c, v.blastRadius * 0.7, 120, 0.5);
                Fx.sphere(sl, ParticleTypes.END_ROD, v.c, 1.0, 80, 0.7);
                Fx.ring(sl, Fx.dustFade(v.color, 0xFFFFFF, 2F), v.c.x, v.c.y, v.c.z, v.blastRadius * 0.5, 60, 0.5);
                ModNetwork.shake(sl, v.c, 40, 0.9F, 12);
                owner.aoeDamage(v.c, v.blastRadius, v.dmg, 1.6, 0.9, true);
                it.remove();
            }
        }
    }

    // ================================================================== spawn helpers

    public Proj bolt(Vec3 from, Vec3 vel, float dmg, int life, int color, int color2, LivingEntity target, double turn, SoundEvent hit) {
        Proj p = new Proj();
        p.pos = from;
        p.vel = vel;
        p.dmg = dmg;
        p.life = life;
        p.color = color;
        p.color2 = color2;
        p.target = target;
        p.turn = turn;
        p.hitSound = hit;
        projectiles.add(p);
        return p;
    }

    public Ring ring(Vec3 c, double speed, double maxR, float dmg, int color, int color2, int delay) {
        Ring g = new Ring();
        g.c = c;
        g.speed = speed;
        g.maxR = maxR;
        g.dmg = dmg;
        g.color = color;
        g.color2 = color2;
        g.delay = delay;
        rings.add(g);
        return g;
    }

    public Zone zone(Vec3 c, double radius, int delay, float dmg, int style, int color, int color2, SoundEvent sound) {
        Zone z = new Zone();
        z.c = c;
        z.radius = radius;
        z.delay = delay;
        z.dmg = dmg;
        z.style = style;
        z.color = color;
        z.color2 = color2;
        z.sound = sound;
        zones.add(z);
        return z;
    }

    public Wall wall(Vec3 start, Vec3 dir, double length, double gapCenter, double gapWidth, double speed, int life, float dmg, int color, int color2) {
        Wall w = new Wall();
        w.pos = start;
        w.dir = dir.normalize();
        w.along = new Vec3(-w.dir.z, 0, w.dir.x);
        w.length = length;
        w.gapCenter = gapCenter;
        w.gapWidth = gapWidth;
        w.speed = speed;
        w.life = life;
        w.dmg = dmg;
        w.color = color;
        w.color2 = color2;
        walls.add(w);
        return w;
    }

    public Vortex vortex(Vec3 c, double radius, int life, double pull, float dmg, double blastRadius, int color) {
        Vortex v = new Vortex();
        v.c = c;
        v.radius = radius;
        v.maxLife = life;
        v.pull = pull;
        v.dmg = dmg;
        v.blastRadius = blastRadius;
        v.color = color;
        vortices.add(v);
        return v;
    }
}
