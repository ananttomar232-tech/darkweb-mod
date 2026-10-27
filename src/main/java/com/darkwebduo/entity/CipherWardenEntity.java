package com.darkwebduo.entity;

import com.darkwebduo.Config;
import com.darkwebduo.cutscene.CutsceneIds;
import com.darkwebduo.registry.ModEntities;
import com.darkwebduo.registry.ModItems;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * BOSS 1 - the Cipher Warden. A 6.4 block tall cyber-titan that sleeps as a kneeling statue in a sealed vault.
 * 13 attacks, 3 phases (66% / 33% HP), cutscene intro / phase changes / death, drops 700 emeralds and the Omega Delivery Box.
 */
public class CipherWardenEntity extends BossBase {
    public static final int A_SLAM = 1, A_COMBO = 2, A_BARRAGE = 3, A_LASER = 4, A_BLINK = 5, A_RAIN = 6, A_FIREWALL = 7,
            A_SAFE = 8, A_VORTEX = 9, A_DAEMONS = 10, A_NOVA = 11, A_SPIRAL = 12, A_LEAP = 13;
    /** attack lengths in ticks, indexed by id - MUST match the animation clips in CipherWardenModel */
    private static final int[] DURATIONS = {0, 44, 62, 72, 112, 44, 84, 96, 100, 120, 70, 140, 96, 56};

    private int lastAtk;
    private int lastAtk2;
    private Vec3 savedPos = Vec3.ZERO;
    private BossFx.Zone leapZone;
    private double hoverBaseY;
    private int emeraldsLeft = -1;
    private boolean lootBoxDropped;

    public CipherWardenEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1500.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.FOLLOW_RANGE, 72.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 10.0);
    }

    /** Places a sleeping Warden (kneeling statue) that faces south (+z). */
    public static CipherWardenEntity spawnDormant(ServerLevel level, Vec3 pos) {
        CipherWardenEntity e = ModEntities.CIPHER_WARDEN.get().create(level);
        if (e == null) return null;
        e.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        e.setYBodyRot(0.0F);
        e.setYHeadRot(0.0F);
        e.setPersistenceRequired();
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return 5.6F;
    }

    /** the laser / light columns reach far beyond the hitbox: keep the renderer alive while they are on screen */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(32.0, 10.0, 32.0);
    }

    // ------------------------------------------------------------------ small helpers
    private float pd() {
        return 1.0F + 0.18F * (getPhase() - 1);
    }

    private int c1() {
        int p = getPhase();
        return p == 1 ? 0x19E6FF : (p == 2 ? 0xFF2BD6 : 0xFF4030);
    }

    private int c2() {
        int p = getPhase();
        return p == 1 ? 0xFF2BD6 : (p == 2 ? 0xA040FF : 0xFFB020);
    }

    private Vec3 fwd() {
        return Vec3.directionFromRotation(0.0F, getYRot());
    }

    private Vec3 right() {
        Vec3 f = fwd();
        return new Vec3(-f.z, 0.0, f.x);
    }

    private Vec3 eyePos() {
        return position().add(0.0, 5.7, 0.0).add(fwd().scale(0.9));
    }

    private Vec3 floorPos(Vec3 p, double fallbackY) {
        return new Vec3(p.x, floorY(p, fallbackY), p.z);
    }

    private double arenaR() {
        return Math.max(12.0, Math.min(Config.arenaRadius() - 3.0, 26.0));
    }

    private int minionCount() {
        return level().getEntitiesOfClass(Vex.class, getBoundingBox().inflate(70.0), v -> v.getTags().contains("darkweb_minion") && v.isAlive()).size();
    }

    private void converge(Vec3 center, double radius, int n, int color, double speed) {
        for (int i = 0; i < n; i++) {
            double x = rnd.nextGaussian(), y = rnd.nextGaussian(), z = rnd.nextGaussian();
            double l = Math.sqrt(x * x + y * y + z * z);
            if (l < 1e-6) continue;
            Vec3 p = center.add(x / l * radius, y / l * radius, z / l * radius);
            Vec3 v = center.subtract(p).normalize().scale(speed);
            Fx.vel(sl(), Fx.dustFade(color, 0xFFFFFF, 1.2F), p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    private void slashArc(Vec3 origin, double a0, double a1, double r0, double r1, double y, int color) {
        double step = Math.max(0.6, (r1 - r0) / 4.0);
        for (double a = a0; a <= a1; a += 5.0) {
            Vec3 dir = Vec3.directionFromRotation(0.0F, (float) (getYRot() + a));
            for (double r = r0; r <= r1; r += step) {
                Fx.send(sl(), Fx.dust(color, 1.7F), origin.x + dir.x * r, origin.y + y, origin.z + dir.z * r, 1, 0.1, 0.25, 0.1, 0);
            }
            Fx.vel(sl(), ParticleTypes.ELECTRIC_SPARK, origin.x + dir.x * r1, origin.y + y, origin.z + dir.z * r1, dir.x * 0.2, 0.05, dir.z * 0.2);
        }
        Fx.send(sl(), ParticleTypes.SWEEP_ATTACK, origin.x + fwd().x * (r0 + r1) * 0.5, origin.y + y, origin.z + fwd().z * (r0 + r1) * 0.5, 1, 0, 0, 0, 0);
    }

    private void glitchBurst(Vec3 p) {
        Vec3 c = p.add(0.0, 3.2, 0.0);
        Fx.burst(sl(), Fx.dust(c1(), 1.8F), c, 40, 0.35, rnd);
        Fx.burst(sl(), Fx.dust(c2(), 1.6F), c, 30, 0.3, rnd);
        Fx.sphere(sl(), ParticleTypes.REVERSE_PORTAL, c, 2.2, 50, 0.25);
        Fx.sphere(sl(), ParticleTypes.ELECTRIC_SPARK, c, 1.6, 30, 0.35);
        Fx.column(sl(), Fx.dust(c1(), 1.5F), p.x, p.y, p.y + 7.0, p.z, 0.5, 0.9);
    }

    private void crackBurst(Vec3 c, int n, double spread, double up) {
        BlockState st = level().getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
        if (st.isAir()) return;
        BlockParticleOption o = new BlockParticleOption(ParticleTypes.BLOCK, st);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = rnd.nextDouble() * spread;
            Fx.vel(sl(), o, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, Math.cos(a) * 0.15, up * (0.4 + rnd.nextDouble()), Math.sin(a) * 0.15);
        }
    }

    private void trackAim(LivingEntity tgt, float maxStep) {
        Vec3 eye = eyePos();
        Vec3 to = tgt.position().add(0.0, tgt.getBbHeight() * 0.5, 0.0).subtract(eye);
        double hd = Math.sqrt(to.x * to.x + to.z * to.z);
        float wy = (float) (Mth.atan2(to.z, to.x) * 57.29577951308232) - 90.0F;
        float wp = (float) (-(Mth.atan2(to.y, hd) * 57.29577951308232));
        float ny = Mth.approachDegrees(getAimYaw(), wy, maxStep);
        float np = Mth.approach(getAimPitch(), wp, maxStep);
        setAim(ny, np);
        this.yHeadRot = ny;
        setXRot(np);
    }

    private Vec3 beamEnd(Vec3 from, Vec3 dir, double len) {
        Vec3 to = from.add(dir.scale(len));
        BlockHitResult r = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return r.getType() == HitResult.Type.MISS ? to : r.getLocation();
    }

    // ------------------------------------------------------------------ BossBase hooks
    @Override protected int cutsceneIntroId() { return CutsceneIds.CIPHER_INTRO; }
    @Override protected int cutscenePhaseId() { return CutsceneIds.CIPHER_PHASE; }
    @Override protected int cutsceneDeathId() { return CutsceneIds.CIPHER_DEATH; }
    @Override protected double[] phaseThresholds() { return new double[]{0.66, 0.33}; }
    @Override protected int attackDuration(int id) { return id > 0 && id < DURATIONS.length ? DURATIONS[id] : 40; }
    @Override protected int attackGap() { return getPhase() == 1 ? 38 : (getPhase() == 2 ? 26 : 14); }
    @Override protected int deathDuration() { return CutsceneIds.duration(CutsceneIds.CIPHER_DEATH); }
    @Override protected double leashRadius() { return 38.0; }
    @Override protected float damageCapFraction() { return 0.05F; }
    @Override protected SoundEvent hurtSound() { return ModSounds.BOSS1_HURT.get(); }
    @Override protected SoundEvent deathSoundEvent() { return ModSounds.BOSS1_DEATH.get(); }
    @Override protected SoundEvent stepSound() { return ModSounds.BOSS1_STEP.get(); }

    @Override
    protected boolean faceTargetDuring(int id) {
        return id != A_NOVA && id != A_LEAP && id != A_FIREWALL;
    }

    @Override
    protected void applyConfigAttributes() {
        float frac = getMaxHealth() <= 0 ? 1F : getHealth() / getMaxHealth();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Config.cipherHealth());
        setHealth(Math.max(1F, (float) (Config.cipherHealth() * frac)));
    }

    @Override
    protected void tickDormant() {
        if (tickCount % 80 == 1) sound(ModSounds.BOSS1_AMBIENT.get(), 0.8F, 1.0F);
        if (tickCount % 10 == 0 && level() instanceof ServerLevel) {
            Vec3 h = position().add(0.0, 1.0 + rnd.nextDouble() * 4.5, 0.0);
            Fx.send(sl(), Fx.dust(0x19E6FF, 0.8F), h.x, h.y, h.z, 1, 0.9, 0.3, 0.9, 0);
            if (rnd.nextInt(4) == 0) Fx.send(sl(), ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 1, 0.8, 0.5, 0.8, 0.05);
        }
    }

    @Override
    protected void tickMovement(LivingEntity tgt, double dist) {
        double speed = 0.20 + 0.03 * getPhase();
        if (dist > 5.5) {
            walkToward(tgt.position(), speed);
        } else if (dist < 3.0) {
            Vec3 away = position().subtract(tgt.position());
            walkToward(position().add(away.x, 0.0, away.z), 0.08);
        } else {
            Vec3 mv = getDeltaMovement();
            setDeltaMovement(mv.x * 0.5, mv.y, mv.z * 0.5);
        }
    }

    @Override
    protected int pickAttack(LivingEntity tgt, double d) {
        int ph = getPhase();
        int[] w = new int[14];
        w[A_SLAM] = d < 14 ? 10 : 3;
        w[A_COMBO] = d < 9 ? 14 : (d < 20 ? 3 : 0);
        w[A_BARRAGE] = 8;
        w[A_LASER] = 6;
        w[A_BLINK] = d > 8 ? 7 : 3;
        w[A_RAIN] = 7;
        w[A_LEAP] = d > 8 ? 8 : 2;
        if (ph >= 2) {
            w[A_FIREWALL] = 6;
            w[A_SAFE] = 6;
            w[A_SPIRAL] = 6;
            w[A_VORTEX] = 5;
            w[A_DAEMONS] = minionCount() < 4 ? 4 : 0;
        }
        if (ph >= 3) {
            w[A_NOVA] = 8;
            w[A_SAFE] = 8;
            w[A_SPIRAL] = 8;
        }
        w[lastAtk] = 0;
        w[lastAtk2] = w[lastAtk2] / 3;
        int total = 0;
        for (int i = 1; i < w.length; i++) total += w[i];
        if (total <= 0) return A_SLAM;
        int r = rnd.nextInt(total);
        int pick = A_SLAM;
        for (int i = 1; i < w.length; i++) {
            if (r < w[i]) {
                pick = i;
                break;
            }
            r -= w[i];
        }
        lastAtk2 = lastAtk;
        lastAtk = pick;
        return pick;
    }

    @Override
    protected void endAttack() {
        super.endAttack();
        setNoGravity(false);
    }

    @Override
    protected void endAttackSilently() {
        super.endAttackSilently();
        setNoGravity(false);
    }

    // ------------------------------------------------------------------ attacks
    @Override
    protected void tickAttack(int id, int t, LivingEntity tgt) {
        if (t == 0) setAim(getYRot(), 0.0F);
        switch (id) {
            case A_SLAM: atkSlam(t, tgt); break;
            case A_COMBO: atkCombo(t, tgt); break;
            case A_BARRAGE: atkBarrage(t, tgt); break;
            case A_LASER: atkLaser(t, tgt); break;
            case A_BLINK: atkBlink(t, tgt); break;
            case A_RAIN: atkRain(t, tgt); break;
            case A_FIREWALL: atkFirewall(t, tgt); break;
            case A_SAFE: atkSafe(t, tgt); break;
            case A_VORTEX: atkVortex(t, tgt); break;
            case A_DAEMONS: atkDaemons(t, tgt); break;
            case A_NOVA: atkNova(t, tgt); break;
            case A_SPIRAL: atkSpiral(t, tgt); break;
            case A_LEAP: atkLeap(t, tgt); break;
            default: break;
        }
    }

    /** 1 - overhead double-fist slam: two jumpable shockwave rings. */
    private void atkSlam(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 1.6F, 1.0F);
        if (t >= 2 && t < 24 && t % 2 == 0) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 hand = position().add(right().scale(1.7 * s)).add(0.0, 6.9, 0.0);
                converge(hand, 2.8, 4, c1(), 0.22);
            }
            Fx.helix(sl(), Fx.dust(c2(), 1.1F), getX(), getY(), getY() + 7.0, getZ(), 1.8, 14, 2.0, t * 0.4);
        }
        if (t == 24) {
            Vec3 f = fwd();
            Vec3 ip = new Vec3(getX() + f.x * 3.4, floorY(position(), getY()), getZ() + f.z * 3.4);
            sound(ModSounds.BOSS1_SLAM.get(), 2.2F, 0.9F);
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, ip.x, ip.y + 0.5, ip.z, 1, 0, 0, 0, 0);
            Fx.burst(sl(), Fx.dust(c1(), 2.0F), ip.add(0, 0.6, 0), 40, 0.5, rnd);
            Fx.burst(sl(), ParticleTypes.END_ROD, ip.add(0, 0.6, 0), 30, 0.45, rnd);
            crackBurst(ip, 50, 5.0, 0.7);
            Fx.ring(sl(), Fx.dustFade(c1(), c2(), 2.0F), ip.x, ip.y + 0.2, ip.z, 4.0, 48, 0.3);
            shake(44, 1.0F, 12);
            flash(20, 0x3019E6FF, 6);
            aoeDamage(ip, 6.5, 15.0F * pd(), 1.6, 0.8, false);
            fx.ring(ip, 0.85, 24.0, 11.0F * pd(), c1(), c2(), 0);
            fx.ring(ip, 0.85, 24.0, 11.0F * pd(), c1(), c2(), 7);
            if (getPhase() >= 2) fx.ring(ip, 0.85, 24.0, 11.0F * pd(), c2(), c1(), 14);
        }
    }

    /** 2 - three-hit claw combo with lunges, finishing with a double-fist smash. */
    private void atkCombo(int t, LivingEntity tgt) {
        boolean lunge = (t >= 4 && t <= 12) || (t >= 20 && t <= 28) || (t >= 38 && t <= 46);
        if (lunge && distanceTo(tgt) > 3.5) walkToward(tgt.position(), 0.55);
        if (t == 4 || t == 20 || t == 38) sound(ModSounds.BOSS1_CHARGE.get(), 0.7F, 1.6F);
        if (t == 14 || t == 30) {
            sound(ModSounds.BOSS1_SLAM.get(), 1.2F, 1.4F);
            coneDamage(8.0, 70.0, 12.0F * pd(), 1.5, 0.6, false);
            slashArc(position(), -75.0, 75.0, 2.5, 8.0, 3.0, c1());
            slashArc(position(), -75.0, 75.0, 3.0, 7.0, 2.0, c2());
            shake(24, 0.35F, 5);
        }
        if (t == 48) {
            Vec3 f = fwd();
            Vec3 ip = new Vec3(getX() + f.x * 3.6, floorY(position(), getY()), getZ() + f.z * 3.6);
            sound(ModSounds.BOSS1_SLAM.get(), 2.0F, 0.8F);
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, ip.x, ip.y + 0.5, ip.z, 1, 0, 0, 0, 0);
            Fx.burst(sl(), Fx.dust(c1(), 2.0F), ip.add(0, 0.6, 0), 36, 0.5, rnd);
            crackBurst(ip, 40, 4.5, 0.6);
            shake(40, 0.9F, 10);
            aoeDamage(ip, 7.0, 18.0F * pd(), 1.9, 0.9, false);
            fx.ring(ip, 0.8, 16.0, 9.0F * pd(), c1(), c2(), 0);
        }
    }

    /** 3 - five volleys of homing data-bolts from both shoulders. */
    private void atkBarrage(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 1.4F, 1.1F);
        if (t < 14 && t % 3 == 0) {
            for (int s = -1; s <= 1; s += 2) converge(shoulder(s), 2.2, 4, c1(), 0.2);
        }
        if (t >= 14 && t <= 54 && (t - 14) % 10 == 0) {
            int per = 1 + (getPhase() + 1) / 2;
            sound(ModSounds.BOSS1_SHOOT.get(), 1.5F, 0.8F + rnd.nextFloat() * 0.3F);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 from = shoulder(s).add(0.0, 0.6, 0.0);
                for (int i = 0; i < per; i++) {
                    Vec3 aim = tgt.position().add(0.0, tgt.getBbHeight() * 0.55, 0.0).subtract(from);
                    Vec3 spread = new Vec3(rnd.nextGaussian() * 2.5, rnd.nextGaussian() * 1.8, rnd.nextGaussian() * 2.5);
                    Vec3 vel = aim.add(spread).normalize().scale(0.85 + 0.05 * getPhase());
                    fx.bolt(from, vel, 7.0F * pd(), 100, c1(), c2(), tgt, 0.05 + 0.02 * getPhase(), ModSounds.BOSS1_SHOOT.get());
                }
                Fx.burst(sl(), Fx.dust(c1(), 1.6F), from, 10, 0.25, rnd);
                Fx.send(sl(), ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            }
            shake(24, 0.2F, 3);
        }
    }

    private Vec3 shoulder(int side) {
        return position().add(right().scale(2.1 * side)).add(0.0, 5.0, 0.0);
    }

    /** 4 - Overclock Laser: tracks the player while charging, then sweeps a sustained beam. */
    private void atkLaser(int t, LivingEntity tgt) {
        Vec3 eye = eyePos();
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 2.0F, 0.8F);
        if (t < 100) trackAim(tgt, t < 42 ? 14.0F : (getPhase() >= 3 ? 5.5F : 4.0F));
        Vec3 dir = Vec3.directionFromRotation(getAimPitch(), getAimYaw());
        if (t < 42) {
            if (t % 3 == 0) converge(eye.add(dir.scale(1.3)), 3.8, 5, c1(), 0.28);
            if (t >= 20 && t % 4 == 0) {
                Vec3 end = beamEnd(eye, dir, 48.0);
                Fx.send(sl(), Fx.dust(c2(), 1.0F), end.x, end.y + 0.1, end.z, 3, 0.4, 0.05, 0.4, 0);
            }
        }
        if (t == 42) {
            sound(ModSounds.BOSS1_LASER.get(), 2.4F, 1.0F);
            flash(30, 0x3019E6FF, 5);
            shake(36, 0.6F, 8);
        }
        if (t == 86) sound(ModSounds.BOSS1_LASER.get(), 2.0F, 1.1F);
        if (t >= 42 && t < 102) {
            Vec3 end = beamEnd(eye, dir, 48.0);
            if (t % 4 == 0) lineDamage(eye, end, 1.4, 5.5F * pd(), true);
            Fx.burst(sl(), ParticleTypes.END_ROD, end, 6, 0.35, rnd);
            Fx.burst(sl(), Fx.dust(c1(), 2.0F), end, 6, 0.3, rnd);
            if (t % 3 == 0) Fx.send(sl(), ParticleTypes.FLAME, end.x, end.y, end.z, 4, 0.5, 0.1, 0.5, 0.05);
            if (t % 6 == 0) Fx.send(sl(), ParticleTypes.EXPLOSION, end.x, end.y + 0.3, end.z, 1, 0, 0, 0, 0);
            double len = end.subtract(eye).length();
            for (double d = 2.0; d < len; d += 3.5) {
                Vec3 p = eye.add(dir.scale(d));
                Fx.send(sl(), Fx.dustFade(c1(), c2(), 1.4F), p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0);
            }
            shake(28, 0.22F, 3);
        }
    }

    /** 5 - Packet Loss: blinks behind the player, leaving a detonating afterimage. */
    private void atkBlink(int t, LivingEntity tgt) {
        if (t == 2) {
            sound(ModSounds.BOSS1_TELEPORT.get(), 1.8F, 1.0F);
            savedPos = position();
            glitchBurst(savedPos);
        }
        if (t == 10) {
            Vec3 look = tgt.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            if (flat.lengthSqr() < 0.04) flat = position().subtract(tgt.position()).multiply(1.0, 0.0, 1.0).scale(-1.0);
            flat = flat.normalize();
            Vec3 dest = tgt.position().subtract(flat.scale(5.0));
            double y = floorY(dest, tgt.getY());
            fx.zone(floorPos(savedPos, getY()), 4.5, 26, 11.0F * pd(), 0, c1(), c2(), null);
            warpTo(dest.x, y, dest.z);
            setDeltaMovement(Vec3.ZERO);
            snapFace(tgt.position());
            glitchBurst(new Vec3(dest.x, y, dest.z));
            sound(ModSounds.BOSS1_TELEPORT.get(), 1.8F, 1.3F);
        }
        if (t == 26) {
            sound(ModSounds.BOSS1_SLAM.get(), 1.6F, 1.3F);
            coneDamage(9.0, 90.0, 16.0F * pd(), 1.6, 0.6, false);
            slashArc(position(), -90.0, 90.0, 2.5, 9.0, 3.2, c1());
            slashArc(position(), -90.0, 90.0, 3.0, 8.0, 1.6, c2());
            shake(30, 0.5F, 6);
        }
    }

    /** 6 - Segfault Rain: tracked lightning strikes and scattered blast zones. */
    private void atkRain(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 1.6F, 0.9F);
        if (t < 12 && t % 2 == 0) {
            for (int s = -1; s <= 1; s += 2) converge(position().add(right().scale(1.6 * s)).add(0.0, 7.0, 0.0), 3.0, 4, c1(), 0.25);
        }
        if (t >= 10 && t <= 66 && (t - 10) % 4 == 0) {
            int k = (t - 10) / 4;
            if (k % 2 == 0) {
                for (ServerPlayer p : playersNear(60.0)) {
                    if (!isValidVictim(p)) continue;
                    BossFx.Zone z = fx.zone(floorPos(p.position(), p.getY()), 3.2, 30, 15.0F * pd(), 2, c1(), c2(), ModSounds.BOSS1_SHOOT.get());
                    z.track = p;
                }
            }
            int n = 1 + getPhase();
            for (int i = 0; i < n; i++) {
                Vec3 s = scatter(home, 2.0, arenaR());
                BossFx.Zone z = fx.zone(floorPos(s, home.y), 3.0, 24, 13.0F * pd(), 2, c2(), c1(), null);
                z.lite = true;
            }
        }
    }

    private void spawnWalls(double angleOffset, LivingEntity tgt) {
        double R = arenaR();
        double base = Mth.atan2(tgt.getZ() - home.z, tgt.getX() - home.x) + angleOffset;
        for (int side = 0; side < 2; side++) {
            double ang = base + side * Math.PI;
            Vec3 start = new Vec3(home.x + Math.cos(ang) * R, home.y, home.z + Math.sin(ang) * R);
            Vec3 dir = new Vec3(-Math.cos(ang), 0.0, -Math.sin(ang));
            double gapCenter = (rnd.nextDouble() - 0.5) * R * 0.9;
            double gapW = getPhase() >= 3 ? 5.5 : 6.5;
            int life = (int) ((2 * R + 6) / 0.6);
            fx.wall(start, dir, 2 * R + 6, gapCenter, gapW, 0.6, life, 13.0F * pd(), c1(), c2());
        }
        sound(ModSounds.BOSS1_LASER.get(), 1.5F, 0.7F);
    }

    /** 7 - Firewall: punches the ground, sending walls of fire across the arena with one safe gap each. */
    private void atkFirewall(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 1.8F, 0.8F);
        if (t < 22 && t % 3 == 0) converge(position().add(0.0, 7.0, 0.0), 3.5, 6, c1(), 0.3);
        if (t == 22) {
            Vec3 f = fwd();
            Vec3 ip = new Vec3(getX() + f.x * 3.2, floorY(position(), getY()), getZ() + f.z * 3.2);
            sound(ModSounds.BOSS1_SLAM.get(), 2.0F, 0.8F);
            crackBurst(ip, 40, 4.0, 0.6);
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, ip.x, ip.y + 0.5, ip.z, 1, 0, 0, 0, 0);
            shake(44, 0.9F, 10);
            spawnWalls(0.0, tgt);
        }
        if (t == 52 && getPhase() >= 2) spawnWalls(Math.PI / 2, tgt);
        if (t == 76 && getPhase() >= 3) spawnWalls(Math.PI / 4, tgt);
    }

    /** 8 - Safe Tile: a green circle appears; everything outside it is annihilated. */
    private void atkSafe(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 2.0F, 0.7F);
        if (t < 10 && t % 2 == 0) converge(position().add(0.0, 4.5, 0.0), 3.0, 5, c1(), 0.2);
        if (t == 10) {
            double R = arenaR();
            double a = rnd.nextDouble() * Math.PI * 2;
            Vec3 c = tgt.position().add(Math.cos(a) * 7.0, 0.0, Math.sin(a) * 7.0);
            Vec3 off = new Vec3(c.x - home.x, 0.0, c.z - home.z);
            if (off.length() > R - 4.0) {
                off = off.normalize().scale(R - 4.0);
                c = new Vec3(home.x + off.x, c.y, home.z + off.z);
            }
            double radius = getPhase() == 1 ? 5.0 : (getPhase() == 2 ? 4.2 : 3.6);
            BossFx.Zone z = fx.zone(floorPos(c, tgt.getY()), radius, 72, 26.0F * pd(), 3, c1(), c2(), ModSounds.BOSS1_SLAM.get());
            z.safeZone = true;
            sound(ModSounds.BOSS1_LASER.get(), 1.4F, 1.5F);
        }
        if (t > 20 && t < 80 && t % 3 == 0) {
            Fx.sphere(sl(), Fx.dust(c1(), 1.0F), position().add(0.0, 4.5, 0.0), 3.5 - (t - 20) * 0.02, 10, 0.0);
        }
        if (t == 80) {
            shake(60, 1.4F, 14);
            flash(60, 0x50FFFFFF, 8);
        }
    }

    /** 9 - Core Dump: a gravity well that drags everything in and then detonates. */
    private void atkVortex(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 2.0F, 0.6F);
        if (t == 12) {
            Vec3 f = fwd();
            Vec3 c = new Vec3(getX() + f.x * 11.0, floorY(position(), getY()) + 1.4, getZ() + f.z * 11.0);
            fx.vortex(c, 9.0, 78, 0.17, 24.0F * pd(), 8.0, c1());
            sound(ModSounds.BOSS1_LASER.get(), 1.8F, 0.6F);
            flash(20, 0x30B020FF, 5);
        }
        if (t > 12 && t < 92 && t % 3 == 0) {
            Vec3 f = fwd();
            Vec3 c = new Vec3(getX() + f.x * 11.0, getY() + 2.0, getZ() + f.z * 11.0);
            Vec3 hand = position().add(right().scale(-1.6)).add(f.scale(1.8)).add(0.0, 3.5, 0.0);
            Fx.line(sl(), Fx.dustFade(c1(), 0xFFFFFF, 1.0F), hand, c, 1.2);
        }
    }

    /** 10 - Spawn Daemons: calls limited-life, glowing Vex minions. */
    private void atkDaemons(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 1.8F, 0.7F);
        if (t < 16 && t % 2 == 0) converge(position().add(0.0, 7.0, 0.0), 3.5, 6, c2(), 0.3);
        if (t == 16) {
            sound(ModSounds.BOSS1_ROAR.get(), 1.6F, 1.3F);
            shake(32, 0.6F, 8);
            spawnMinions(getPhase() >= 3 ? 5 : 3);
        }
    }

    private void spawnMinions(int n) {
        ServerLevel level = sl();
        int have = minionCount();
        for (int i = 0; i < n && have + i < 8; i++) {
            Vec3 p = scatter(position(), 4.0, 9.0).add(0.0, 2.5, 0.0);
            Vex v = EntityType.VEX.create(level);
            if (v == null) continue;
            v.moveTo(p.x, p.y, p.z, rnd.nextFloat() * 360.0F, 0.0F);
            v.setBoundOrigin(BlockPos.containing(position()));
            v.setLimitedLife(20 * 28);
            v.addTag("darkweb_minion");
            v.setGlowingTag(true);
            v.setCustomName(Component.literal("Daemon Process"));
            v.finalizeSpawn(level, level.getCurrentDifficultyAt(v.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            level.addFreshEntity(v);
            glitchBurst(new Vec3(p.x, p.y - 2.0, p.z));
        }
    }

    /** 11 - Overflow Nova (phase 3): rises, rains tracked strikes, then crashes down with huge rings. */
    private void atkNova(int t, LivingEntity tgt) {
        if (t == 1) {
            hoverBaseY = getY();
            setNoGravity(true);
            sound(ModSounds.BOSS1_CHARGE.get(), 2.4F, 0.6F);
        }
        if (t >= 1 && t < 100) {
            double wantY = hoverBaseY + 8.5 + (t > 40 ? Math.sin(t * 0.15) * 0.4 : 0.0);
            double dy = wantY - getY();
            double vy = t < 40 ? Math.min(0.28, Math.max(-0.1, dy * 0.2)) : dy * 0.25;
            setDeltaMovement(0.0, vy, 0.0);
        }
        if (t >= 10 && t < 100 && t % 2 == 0) {
            converge(position().add(0.0, 5.0, 0.0), 12.0, 10, c1(), 0.55);
            Fx.helix(sl(), Fx.dust(c2(), 1.4F), getX(), getY() - 1.0, getY() + 9.0, getZ(), 2.6, 18, 2.5, t * 0.35);
        }
        if (t >= 52 && t <= 88 && (t - 52) % 12 == 0) {
            for (ServerPlayer p : playersNear(60.0)) {
                if (!isValidVictim(p)) continue;
                BossFx.Zone z = fx.zone(floorPos(p.position(), p.getY()), 3.8, 26, 17.0F * pd(), 2, c1(), c2(), ModSounds.BOSS1_SHOOT.get());
                z.track = p;
            }
            for (int i = 0; i < 4; i++) {
                BossFx.Zone z = fx.zone(floorPos(scatter(home, 3.0, arenaR()), home.y), 3.2, 26, 14.0F * pd(), 2, c2(), c1(), null);
                z.lite = true;
            }
        }
        if (t == 94) {
            sound(ModSounds.BOSS1_LASER.get(), 2.4F, 0.5F);
            flash(60, 0x30FF4030, 6);
        }
        if (t == 100) {
            setNoGravity(false);
            Vec3 fl = new Vec3(getX(), floorY(position(), hoverBaseY), getZ());
            warpTo(fl.x, fl.y, fl.z);
            setDeltaMovement(Vec3.ZERO);
            sound(ModSounds.BOSS1_SLAM.get(), 3.0F, 0.6F);
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, fl.x, fl.y + 1.0, fl.z, 1, 0, 0, 0, 0);
            Fx.send(sl(), ParticleTypes.FLASH, fl.x, fl.y + 2.0, fl.z, 1, 0, 0, 0, 0);
            Fx.sphere(sl(), Fx.dust(c1(), 2.4F), fl.add(0, 2, 0), 5.0, 120, 0.6);
            Fx.sphere(sl(), ParticleTypes.END_ROD, fl.add(0, 2, 0), 2.0, 80, 0.8);
            crackBurst(fl, 80, 9.0, 0.9);
            shake(70, 1.8F, 20);
            flash(70, 0x60FFFFFF, 10);
            aoeDamage(fl, 10.0, 26.0F * pd(), 2.2, 1.0, false);
            for (int i = 0; i < 3; i++) {
                BossFx.Ring r = fx.ring(fl, 1.1, 36.0, 20.0F * pd(), i == 1 ? c2() : c1(), i == 1 ? c1() : c2(), i * 8);
                r.thickness = 2.4;
            }
        }
    }

    /** 12 - Packet Storm: light pillars spiral outwards from the boss. */
    private void atkSpiral(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 1.8F, 0.9F);
        if (t >= 14 && t <= 74 && t % 2 == 0) {
            int arms = getPhase() >= 3 ? 4 : 3;
            double base = (t - 14) * 0.18;
            double r = 4.0 + (t - 14) * 0.38;
            for (int a = 0; a < arms; a++) {
                double ang = base + a * (Math.PI * 2 / arms);
                Vec3 p = new Vec3(getX() + Math.cos(ang) * r, getY(), getZ() + Math.sin(ang) * r);
                BossFx.Zone z = fx.zone(floorPos(p, getY()), 2.4, 22, 14.0F * pd(), 1, c1(), c2(), null);
                z.lite = true;
            }
        }
        if (t == 76) {
            sound(ModSounds.BOSS1_SLAM.get(), 1.8F, 1.0F);
            BossFx.Ring r = fx.ring(new Vec3(getX(), floorY(position(), getY()), getZ()), 0.9, 26.0, 12.0F * pd(), c1(), c2(), 0);
            r.thickness = 2.0;
            shake(40, 0.8F, 8);
        }
    }

    /** 13 - Kernel Leap: a telegraphed jump onto the player's position with a crushing landing. */
    private void atkLeap(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS1_CHARGE.get(), 1.4F, 1.2F);
        if (t < 12) {
            faceTowards(tgt.position(), 20.0F);
            Fx.ring(sl(), Fx.dust(c1(), 1.2F), getX(), getY() + 0.2, getZ(), 2.6 - t * 0.12, 20, 0.0);
        }
        if (t == 12) {
            savedPos = position();
            Vec3 land = floorPos(tgt.position(), tgt.getY());
            leapZone = fx.zone(land, 7.5, 22, 20.0F * pd(), 0, c1(), c2(), ModSounds.BOSS1_SLAM.get());
            leapZone.track = tgt;
            sound(ModSounds.BOSS1_TELEPORT.get(), 1.4F, 0.6F);
            Fx.burst(sl(), ParticleTypes.CLOUD, position().add(0, 0.3, 0), 30, 0.5, rnd);
            crackBurst(position(), 30, 3.0, 0.8);
            shake(36, 0.7F, 8);
        }
        if (t > 12 && t <= 32 && leapZone != null) {
            double u = (t - 12) / 20.0;
            Vec3 dest = leapZone.c;
            double x = savedPos.x + (dest.x - savedPos.x) * u;
            double z = savedPos.z + (dest.z - savedPos.z) * u;
            double y = savedPos.y + (dest.y - savedPos.y) * u + Math.sin(Math.PI * u) * 9.0;
            setPos(x, y, z);
            setDeltaMovement(Vec3.ZERO);
            faceTowards(dest, 40.0F);
            Fx.send(sl(), Fx.dustFade(c1(), c2(), 2.0F), x, y + 2.0, z, 6, 0.8, 1.5, 0.8, 0);
        }
        if (t == 33 && leapZone != null) {
            Vec3 dest = leapZone.c;
            setPos(dest.x, dest.y, dest.z);
            setDeltaMovement(Vec3.ZERO);
            sound(ModSounds.BOSS1_SLAM.get(), 2.0F, 0.8F);
            crackBurst(dest, 60, 7.0, 0.9);
            fx.ring(dest, 0.9, 20.0, 10.0F * pd(), c1(), c2(), 0);
            leapZone = null;
        }
    }

    // ------------------------------------------------------------------ cutscenes
    @Override
    protected void onCutsceneTick(int id, int t) {
        if (!(level() instanceof ServerLevel)) return;
        if (id == CutsceneIds.CIPHER_INTRO) {
            Vec3 head = position().add(0.0, 5.7, 0.0).add(fwd().scale(0.9));
            if (t == 20) sound(ModSounds.BOSS1_AWAKEN.get(), 2.4F, 1.0F);
            if (t >= CutsceneIds.CIPHER_INTRO_EYES && t < CutsceneIds.CIPHER_INTRO_ROAR && t % 3 == 0) {
                Fx.send(sl(), ParticleTypes.END_ROD, head.x, head.y, head.z, 3, 0.35, 0.2, 0.35, 0.03);
                Fx.send(sl(), Fx.dust(0x19E6FF, 1.4F), head.x, head.y - 0.3, head.z, 3, 0.3, 0.1, 0.3, 0);
            }
            if (t == CutsceneIds.CIPHER_INTRO_RISE) {
                Vec3 base = new Vec3(getX(), floorY(position(), getY()), getZ());
                sound(ModSounds.BOSS1_SLAM.get(), 2.6F, 0.6F);
                for (int i = 0; i < 2; i++) {
                    BossFx.Ring r = fx.ring(base, 0.7, 26.0, 0.0F, 0x19E6FF, 0xFF2BD6, i * 6);
                    r.visual = true;
                }
                crackBurst(base, 90, 8.0, 1.0);
                Fx.column(sl(), Fx.dust(0x19E6FF, 2.4F), base.x, base.y, base.y + 24.0, base.z, 0.5, 1.2);
                Fx.column(sl(), ParticleTypes.END_ROD, base.x, base.y, base.y + 24.0, base.z, 0.8, 0.8);
                flash(60, 0x6019E6FF, 8);
                shake(60, 1.0F, 20);
                pushAway(base, 9.0, 1.3, 0.8);
            }
            if (t > CutsceneIds.CIPHER_INTRO_RISE && t < CutsceneIds.CIPHER_INTRO_ROAR && t % 3 == 0) {
                Vec3 p = scatter(position(), 1.5, 8.0);
                crackBurst(new Vec3(p.x, floorY(p, getY()), p.z), 6, 1.0, 0.6);
                Fx.send(sl(), ParticleTypes.ELECTRIC_SPARK, p.x, getY() + 0.5, p.z, 4, 0.4, 0.4, 0.4, 0.2);
                shake(40, 0.35F, 4);
            }
            if (t == CutsceneIds.CIPHER_INTRO_ROAR) {
                sound(ModSounds.BOSS1_ROAR.get(), 3.2F, 1.0F);
                Fx.burst(sl(), Fx.dust(0x19E6FF, 2.2F), head, 60, 0.7, rnd);
                Fx.burst(sl(), ParticleTypes.END_ROD, head, 50, 0.8, rnd);
                Fx.vring(sl(), Fx.dustFade(0x19E6FF, 0xFFFFFF, 2.0F), head.add(fwd().scale(1.5)), fwd().x, fwd().z, 2.0, 36);
                Fx.vring(sl(), Fx.dustFade(0xFF2BD6, 0xFFFFFF, 2.0F), head.add(fwd().scale(3.0)), fwd().x, fwd().z, 3.5, 48);
                shake(70, 1.4F, 40);
                flash(60, 0x4019E6FF, 6);
            }
        } else if (id == CutsceneIds.CIPHER_PHASE) {
            if (t == 1) sound(ModSounds.BOSS1_PHASE.get(), 2.4F, 1.0F);
            if (t < CutsceneIds.CIPHER_PHASE_BEAT && t % 2 == 0) {
                Vec3 p = position().add(0.0, 1.0 + rnd.nextDouble() * 5.0, 0.0);
                Fx.send(sl(), ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 6, 0.9, 0.5, 0.9, 0.3);
                Fx.send(sl(), Fx.dust(c1(), 1.6F), p.x, p.y, p.z, 4, 0.9, 0.5, 0.9, 0);
            }
            if (t == CutsceneIds.CIPHER_PHASE_BEAT) {
                Vec3 base = new Vec3(getX(), floorY(position(), getY()), getZ());
                int np = pendingPhase == 3 ? 0xFF4030 : 0xFF2BD6;
                int np2 = pendingPhase == 3 ? 0xFFB020 : 0xA040FF;
                BossFx.Ring r = fx.ring(base, 1.0, 30.0, 0.0F, np, np2, 0);
                r.visual = true;
                Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, base.x, base.y + 2.0, base.z, 1, 0, 0, 0, 0);
                Fx.sphere(sl(), Fx.dust(np, 2.4F), base.add(0, 3, 0), 3.0, 100, 0.7);
                Fx.sphere(sl(), ParticleTypes.END_ROD, base.add(0, 3, 0), 1.5, 60, 0.8);
                Fx.column(sl(), Fx.dust(np, 2.4F), base.x, base.y, base.y + 26.0, base.z, 0.5, 1.0);
                flash(60, pendingPhase == 3 ? 0x60FF4030 : 0x60FF2BD6, 8);
                shake(60, 1.2F, 16);
                pushAway(base, 10.0, 1.6, 0.8);
            }
        }
    }

    @Override
    protected void onCutsceneEnd(int id) {
        if (id == CutsceneIds.CIPHER_PHASE) {
            heal(getMaxHealth() * 0.03F);
            bossEvent.setColor(getPhase() >= 3 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PINK);
            lastAtk = 0;
            lastAtk2 = 0;
        }
    }

    // ------------------------------------------------------------------ death
    @Override
    protected void onDeathTick(int t) {
        if (!(level() instanceof ServerLevel)) return;
        if (t == 1) {
            sound(ModSounds.BOSS1_DEATH.get(), 3.0F, 1.0F);
            for (Vex v : level().getEntitiesOfClass(Vex.class, getBoundingBox().inflate(80.0), x -> x.getTags().contains("darkweb_minion"))) {
                Fx.burst(sl(), Fx.dust(c1(), 1.5F), v.position(), 20, 0.3, rnd);
                v.discard();
            }
            setNoGravity(false);
        }
        if (t < CutsceneIds.CIPHER_DEATH_EXPLODE) {
            if (t % 2 == 0) {
                int n = t < 100 ? 4 : 10;
                for (int i = 0; i < n; i++) {
                    Vec3 p = position().add((rnd.nextDouble() - 0.5) * 3.0, 0.5 + rnd.nextDouble() * 5.5, (rnd.nextDouble() - 0.5) * 3.0);
                    Fx.send(sl(), ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.2, 0.2, 0.2, 0.4);
                    Fx.send(sl(), rnd.nextBoolean() ? Fx.dust(0x19E6FF, 1.5F) : Fx.dust(0xFF2BD6, 1.5F), p.x, p.y, p.z, 2, 0.3, 0.3, 0.3, 0);
                }
                if (t % 10 == 0) Fx.send(sl(), ParticleTypes.EXPLOSION, getX() + (rnd.nextDouble() - 0.5) * 3, getY() + 1 + rnd.nextDouble() * 4, getZ() + (rnd.nextDouble() - 0.5) * 3, 1, 0, 0, 0, 0);
            }
            if (t % 8 == 0) shake(40, 0.3F + t * 0.006F, 6);
        }
        if (t == CutsceneIds.CIPHER_DEATH_EXPLODE) {
            Vec3 c = position().add(0.0, 3.2, 0.0);
            sound(ModSounds.BOSS1_SLAM.get(), 3.0F, 0.5F);
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            Fx.send(sl(), ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            Fx.sphere(sl(), Fx.dust(0x19E6FF, 2.6F), c, 3.0, 160, 0.8);
            Fx.sphere(sl(), Fx.dust(0xFF2BD6, 2.2F), c, 2.0, 100, 0.55);
            Fx.sphere(sl(), ParticleTypes.END_ROD, c, 1.0, 100, 1.0);
            Fx.burst(sl(), ParticleTypes.FIREWORK, c, 80, 1.0, rnd);
            for (int i = 0; i < 3; i++) {
                BossFx.Ring r = fx.ring(new Vec3(getX(), getY() + 0.2, getZ()), 1.0, 28.0, 0.0F, 0x19E6FF, 0xFF2BD6, i * 5);
                r.visual = true;
            }
            flash(80, 0xC0FFFFFF, 20);
            shake(80, 2.0F, 30);
        }
        dropLoot(t);
    }

    private void dropLoot(int t) {
        ServerLevel level = sl();
        if (t == CutsceneIds.CIPHER_DEATH_LOOT) {
            if (!lootBoxDropped) {
                lootBoxDropped = true;
                ItemEntity box = new ItemEntity(level, getX(), getY() + 3.0, getZ(), new ItemStack(ModItems.OMEGA_DELIVERY_BOX.get()));
                box.setDeltaMovement(0.0, 0.55, 0.0);
                box.setUnlimitedLifetime();
                box.setGlowingTag(true);
                box.setInvulnerable(true);
                level.addFreshEntity(box);
                ExperienceOrb.award(level, position().add(0.0, 2.0, 0.0), 1500);
                Fx.column(level, Fx.dust(0xFFD54A, 2.0F), getX(), getY(), getY() + 22.0, getZ(), 0.5, 0.6);
                Fx.column(level, ParticleTypes.END_ROD, getX(), getY(), getY() + 22.0, getZ(), 0.7, 0.4);
                Fx.send(level, ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 3.0, getZ(), 60, 1.5, 1.5, 1.5, 0.5);
            }
            emeraldsLeft = Config.cipherEmeralds();
        }
        if (t >= CutsceneIds.CIPHER_DEATH_LOOT && emeraldsLeft > 0) {
            int batches = t >= deathDuration() - 4 ? 99 : 3;
            for (int k = 0; k < batches && emeraldsLeft > 0; k++) {
                int n = Math.min(64, emeraldsLeft);
                emeraldsLeft -= n;
                ItemEntity ie = new ItemEntity(level, getX(), getY() + 3.5, getZ(), new ItemStack(Items.EMERALD, n));
                ie.setDeltaMovement(rnd.nextGaussian() * 0.25, 0.45 + rnd.nextDouble() * 0.4, rnd.nextGaussian() * 0.25);
                level.addFreshEntity(ie);
                Fx.send(level, ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 3.5, getZ(), 12, 0.8, 0.8, 0.8, 0.2);
            }
        }
    }
}
