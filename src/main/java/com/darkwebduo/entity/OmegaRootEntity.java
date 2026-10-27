package com.darkwebduo.entity;

import com.darkwebduo.Config;
import com.darkwebduo.block.IPhoneDuoBlock;
import com.darkwebduo.block.entity.IPhoneDuoBlockEntity;
import com.darkwebduo.block.entity.OmegaTerminalBlockEntity;
import com.darkwebduo.cutscene.CutsceneIds;
import com.darkwebduo.registry.ModBlocks;
import com.darkwebduo.registry.ModEntities;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * BOSS 2 - Omega Root. 8.6 blocks tall, floating celestial war-machine with four arms, three gyroscope rings and a crown of blades.
 * 18 attacks, 5 phases (80 / 60 / 40 / 20 % HP), summoned by the Omega Terminal, dies in a supernova and leaves the iPhone Duo.
 */
public class OmegaRootEntity extends BossBase {
    public static final int A_REND = 1, A_FISSURE = 2, A_SEEKER = 3, A_BEAM = 4, A_TWIN = 5, A_SKIP = 6, A_METEORS = 7, A_GRID = 8,
            A_FLOOR = 9, A_SINGULARITY = 10, A_LEGION = 11, A_CLOCK = 12, A_COMETS = 13, A_CHARGE = 14, A_JUDGMENT = 15,
            A_HANDS = 16, A_PRISM = 17, A_NOVA = 18;
    /** attack lengths in ticks, indexed by id - MUST match the animation clips in OmegaRootModel */
    private static final int[] DURATIONS = {0, 70, 60, 90, 150, 120, 90, 110, 120, 100, 140, 80, 130, 90, 70, 120, 100, 100, 160};
    private static final int[] C1 = {0xFFD87A, 0x6FE8FF, 0xFF4FD8, 0xFF5030, 0xFFFFFF};
    private static final int[] C2 = {0x8A5CFF, 0xFFFFFF, 0xFFD87A, 0xFFB020, 0x8AF0FF};

    private BlockPos terminal = BlockPos.ZERO;
    private boolean terminalKnown;
    private int lastAtk;
    private int lastAtk2;
    private Vec3 savedPos = Vec3.ZERO;
    private double hoverBaseY;
    private float sweepDir = 1F;
    private float baseYaw;
    private Vec3 dashDir = Vec3.ZERO;
    private final List<Vec3> skipSpots = new ArrayList<>();
    private boolean phoneDone;

    public OmegaRootEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 15000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 24.0)
                .add(Attributes.FOLLOW_RANGE, 90.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 16.0);
    }

    /**
     * Called by the Omega Terminal at tick 160 of the OMEGA_SUMMON cutscene (the terminal started the cutscene itself).
     * The boss appears inside the rift ~28 blocks above the terminal and descends.
     */
    public static OmegaRootEntity beginSummon(ServerLevel level, BlockPos terminalPos, ServerPlayer summoner) {
        OmegaRootEntity e = ModEntities.OMEGA_ROOT.get().create(level);
        if (e == null) return null;
        e.terminal = terminalPos;
        e.terminalKnown = true;
        double x = terminalPos.getX() + 0.5;
        double z = terminalPos.getZ() + 0.5;
        e.moveTo(x, terminalPos.getY() + 29.0, z, 0.0F, 0.0F);
        e.setYBodyRot(0.0F);
        e.setYHeadRot(0.0F);
        e.home = new Vec3(x, terminalPos.getY(), z);
        e.homeSet = true;
        e.setPersistenceRequired();
        e.setNoGravity(true);
        level.addFreshEntity(e);
        e.resumeCutscene(CutsceneIds.OMEGA_SUMMON, 160);
        return e;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return 7.4F;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(48.0, 14.0, 48.0);
    }

    // ------------------------------------------------------------------ small helpers
    private float pd() {
        return 1.0F + 0.14F * (getPhase() - 1);
    }

    private int c1() {
        return C1[Mth.clamp(getPhase(), 1, 5) - 1];
    }

    private int c2() {
        return C2[Mth.clamp(getPhase(), 1, 5) - 1];
    }

    private Vec3 fwd() {
        return Vec3.directionFromRotation(0.0F, getYRot());
    }

    private Vec3 right() {
        Vec3 f = fwd();
        return new Vec3(-f.z, 0.0, f.x);
    }

    private Vec3 eyePos() {
        return position().add(0.0, 7.4, 0.0).add(fwd().scale(1.0));
    }

    private Vec3 chestPos() {
        return position().add(0.0, 5.6, 0.0).add(fwd().scale(1.5));
    }

    private Vec3 floorPos(Vec3 p, double fallbackY) {
        return new Vec3(p.x, floorY(p, fallbackY), p.z);
    }

    private double arenaR() {
        return 31.0;
    }

    private int minionCount() {
        return level().getEntitiesOfClass(Vex.class, getBoundingBox().inflate(90.0), v -> v.getTags().contains("darkweb_minion") && v.isAlive()).size();
    }

    private void converge(Vec3 center, double radius, int n, int color, double speed) {
        for (int i = 0; i < n; i++) {
            double x = rnd.nextGaussian(), y = rnd.nextGaussian(), z = rnd.nextGaussian();
            double l = Math.sqrt(x * x + y * y + z * z);
            if (l < 1e-6) continue;
            Vec3 p = center.add(x / l * radius, y / l * radius, z / l * radius);
            Vec3 v = center.subtract(p).normalize().scale(speed);
            Fx.vel(sl(), Fx.dustFade(color, 0xFFFFFF, 1.3F), p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    private void slashArc(Vec3 origin, double a0, double a1, double r0, double r1, double y, int color) {
        double step = Math.max(0.7, (r1 - r0) / 4.0);
        for (double a = a0; a <= a1; a += 5.0) {
            Vec3 dir = Vec3.directionFromRotation(0.0F, (float) (getYRot() + a));
            for (double r = r0; r <= r1; r += step) {
                Fx.send(sl(), Fx.dust(color, 2.0F), origin.x + dir.x * r, origin.y + y, origin.z + dir.z * r, 1, 0.1, 0.3, 0.1, 0);
            }
            Fx.vel(sl(), ParticleTypes.END_ROD, origin.x + dir.x * r1, origin.y + y, origin.z + dir.z * r1, dir.x * 0.25, 0.05, dir.z * 0.25);
        }
        Vec3 f = fwd();
        Fx.send(sl(), ParticleTypes.SWEEP_ATTACK, origin.x + f.x * (r0 + r1) * 0.5, origin.y + y, origin.z + f.z * (r0 + r1) * 0.5, 1, 0, 0, 0, 0);
    }

    private void glitchBurst(Vec3 p) {
        Vec3 c = p.add(0.0, 4.2, 0.0);
        Fx.burst(sl(), Fx.dust(c1(), 2.0F), c, 50, 0.4, rnd);
        Fx.burst(sl(), Fx.dust(c2(), 1.8F), c, 36, 0.35, rnd);
        Fx.sphere(sl(), ParticleTypes.REVERSE_PORTAL, c, 3.0, 60, 0.3);
        Fx.sphere(sl(), ParticleTypes.END_ROD, c, 2.0, 30, 0.4);
        Fx.column(sl(), Fx.dust(c1(), 1.8F), p.x, p.y, p.y + 9.0, p.z, 0.5, 1.2);
    }

    private void crackBurst(Vec3 c, int n, double spread, double up) {
        BlockState st = level().getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
        if (st.isAir()) return;
        BlockParticleOption o = new BlockParticleOption(ParticleTypes.BLOCK, st);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = rnd.nextDouble() * spread;
            Fx.vel(sl(), o, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, Math.cos(a) * 0.18, up * (0.4 + rnd.nextDouble()), Math.sin(a) * 0.18);
        }
    }

    private Vec3 beamEnd(Vec3 from, Vec3 dir, double len) {
        Vec3 to = from.add(dir.scale(len));
        BlockHitResult r = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return r.getType() == HitResult.Type.MISS ? to : r.getLocation();
    }

    private float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * 57.29577951308232) - 90.0F;
    }

    private void aimAt(Vec3 from, Vec3 to, float maxStep) {
        Vec3 d = to.subtract(from);
        double hd = Math.sqrt(d.x * d.x + d.z * d.z);
        float wy = (float) (Mth.atan2(d.z, d.x) * 57.29577951308232) - 90.0F;
        float wp = (float) (-(Mth.atan2(d.y, hd) * 57.29577951308232));
        setAim(Mth.approachDegrees(getAimYaw(), wy, maxStep), Mth.approach(getAimPitch(), wp, maxStep));
        this.yHeadRot = getAimYaw();
    }

    /** scissor angle (degrees) of the twin lasers, shared with the renderer; tFire = ticks since the beams ignited. */
    public static float twinSpread(float tFire) {
        return 75.0F * Mth.cos(tFire * 0.04488F);
    }

    /** sweep offset (degrees) of the omega beam at fire-time tFire (0..80), shared with the renderer. */
    public static float beamSweep(float tFire) {
        return -70.0F + 140.0F * Mth.clamp(tFire / 80.0F, 0F, 1F);
    }

    /** pitch (degrees, positive = down) of the sweeping omega beam. */
    public static float beamPitch(float tFire) {
        return 15.0F + 6.0F * Mth.sin(tFire * 0.12F);
    }

    // ------------------------------------------------------------------ BossBase hooks
    @Override protected int cutsceneIntroId() { return CutsceneIds.OMEGA_SUMMON; }
    @Override protected int cutscenePhaseId() { return CutsceneIds.OMEGA_PHASE; }
    @Override protected int cutsceneDeathId() { return CutsceneIds.OMEGA_DEATH; }
    @Override protected double[] phaseThresholds() { return new double[]{0.80, 0.60, 0.40, 0.20}; }
    @Override protected int attackDuration(int id) { return id > 0 && id < DURATIONS.length ? DURATIONS[id] : 40; }
    @Override protected int attackGap() { int p = getPhase(); return p == 1 ? 34 : (p == 2 ? 26 : (p == 3 ? 20 : (p == 4 ? 14 : 8))); }
    @Override protected int deathDuration() { return CutsceneIds.duration(CutsceneIds.OMEGA_DEATH); }
    @Override protected double leashRadius() { return 48.0; }
    @Override protected float damageCapFraction() { return 0.03F; }
    @Override protected SoundEvent hurtSound() { return ModSounds.BOSS2_HURT.get(); }
    @Override protected SoundEvent deathSoundEvent() { return ModSounds.BOSS2_DEATH.get(); }
    @Override protected SoundEvent stepSound() { return ModSounds.BOSS2_STEP.get(); }

    @Override
    protected boolean faceTargetDuring(int id) {
        return id != A_NOVA && id != A_CHARGE && id != A_FISSURE && id != A_GRID && id != A_SINGULARITY && id != A_TWIN && id != A_BEAM;
    }

    @Override
    protected void applyConfigAttributes() {
        float frac = getMaxHealth() <= 0 ? 1F : getHealth() / getMaxHealth();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Config.omegaHealth());
        setHealth(Math.max(1F, (float) (Config.omegaHealth() * frac)));
    }

    @Override
    protected void tickDormant() {
        // Omega is never dormant (it is summoned straight into its cutscene); a reloaded dormant one just wakes up
        if (tickCount > 20) wake();
    }

    @Override
    protected void tickMovement(LivingEntity tgt, double dist) {
        double speed = 0.17 + 0.025 * getPhase();
        if (dist > 7.0) {
            walkToward(tgt.position(), speed);
        } else if (dist < 3.5) {
            Vec3 away = position().subtract(tgt.position());
            walkToward(position().add(away.x, 0.0, away.z), 0.1);
        } else {
            Vec3 mv = getDeltaMovement();
            setDeltaMovement(mv.x * 0.5, mv.y, mv.z * 0.5);
        }
    }

    @Override
    protected int pickAttack(LivingEntity tgt, double d) {
        int ph = getPhase();
        int[] w = new int[19];
        w[A_REND] = d < 10 ? 14 : (d < 20 ? 3 : 0);
        w[A_FISSURE] = d < 16 ? 10 : 4;
        w[A_SEEKER] = 8;
        w[A_METEORS] = 7;
        w[A_SKIP] = d > 6 ? 6 : 3;
        w[A_CHARGE] = d > 12 ? 7 : 1;
        w[A_COMETS] = 6;
        if (ph >= 2) {
            w[A_BEAM] = 7;
            w[A_TWIN] = 6;
            w[A_PRISM] = 7;
        }
        if (ph >= 3) {
            w[A_GRID] = 7;
            w[A_FLOOR] = 7;
            w[A_SINGULARITY] = 6;
            w[A_CLOCK] = 6;
        }
        if (ph >= 4) {
            w[A_LEGION] = minionCount() < 4 ? 5 : 0;
            w[A_JUDGMENT] = 7;
            w[A_HANDS] = 7;
        }
        if (ph >= 5) {
            w[A_NOVA] = lastAtk == A_NOVA || lastAtk2 == A_NOVA ? 0 : 10;
        }
        w[lastAtk] = 0;
        w[lastAtk2] = w[lastAtk2] / 3;
        int total = 0;
        for (int i = 1; i < w.length; i++) total += w[i];
        if (total <= 0) return A_REND;
        int r = rnd.nextInt(total);
        int pick = A_REND;
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

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel && getMode() == MODE_FIGHT) meteorStreaks();
    }

    /** falling-meteor visuals for every armed annihilation zone */
    private void meteorStreaks() {
        for (BossFx.Zone z : fx.zones) {
            if (z.style != 3 || z.safeZone || z.visual || z.age >= z.delay) continue;
            double rem = z.delay - z.age;
            double h = rem * 1.25;
            Vec3 p = new Vec3(z.c.x, z.c.y + h + 1.5, z.c.z);
            Fx.send(sl(), Fx.dustFade(0xFFB020, 0xFF3010, 3.0F), p.x, p.y, p.z, 3, 0.7, 0.7, 0.7, 0);
            Fx.send(sl(), ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.5, 0.5, 0.5, 0.02);
            Fx.send(sl(), ParticleTypes.LARGE_SMOKE, p.x, p.y + 1.2, p.z, 1, 0.4, 0.8, 0.4, 0.01);
        }
    }

    // ------------------------------------------------------------------ attacks
    @Override
    protected void tickAttack(int id, int t, LivingEntity tgt) {
        if (t == 0) setAim(getYRot(), 0.0F);
        switch (id) {
            case A_REND: atkRend(t, tgt); break;
            case A_FISSURE: atkFissure(t, tgt); break;
            case A_SEEKER: atkSeeker(t, tgt); break;
            case A_BEAM: atkBeam(t, tgt); break;
            case A_TWIN: atkTwin(t, tgt); break;
            case A_SKIP: atkSkip(t, tgt); break;
            case A_METEORS: atkMeteors(t, tgt); break;
            case A_GRID: atkGrid(t, tgt); break;
            case A_FLOOR: atkFloor(t, tgt); break;
            case A_SINGULARITY: atkSingularity(t, tgt); break;
            case A_LEGION: atkLegion(t, tgt); break;
            case A_CLOCK: atkClock(t, tgt); break;
            case A_COMETS: atkComets(t, tgt); break;
            case A_CHARGE: atkCharge(t, tgt); break;
            case A_JUDGMENT: atkJudgment(t, tgt); break;
            case A_HANDS: atkHands(t, tgt); break;
            case A_PRISM: atkPrism(t, tgt); break;
            case A_NOVA: atkNova(t, tgt); break;
            default: break;
        }
    }

    /** 1 - four-armed combo: left claws, right claws, a double crossing cut and a crushing finisher. */
    private void atkRend(int t, LivingEntity tgt) {
        boolean lunge = (t >= 6 && t <= 14) || (t >= 22 && t <= 30) || (t >= 38 && t <= 46);
        if (lunge && distanceTo(tgt) > 4.5) walkToward(tgt.position(), 0.6);
        if (t == 6 || t == 22 || t == 38 || t == 54) sound(ModSounds.BOSS2_CHARGE.get(), 0.7F, 1.7F);
        if (t == 16 || t == 32 || t == 48) {
            sound(ModSounds.BOSS2_SLAM.get(), 1.4F, 1.3F);
            coneDamage(10.0, 80.0, 16.0F * pd(), 1.7, 0.6, false);
            slashArc(position(), -85.0, 85.0, 3.0, 10.0, 3.5, c1());
            slashArc(position(), -85.0, 85.0, 3.5, 9.0, 5.0, c2());
            slashArc(position(), -85.0, 85.0, 3.0, 8.5, 1.8, 0xFFFFFF);
            shake(30, 0.5F, 6);
        }
        if (t == 60) {
            Vec3 f = fwd();
            Vec3 ip = new Vec3(getX() + f.x * 4.2, floorY(position(), getY()), getZ() + f.z * 4.2);
            sound(ModSounds.BOSS2_SLAM.get(), 2.4F, 0.8F);
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, ip.x, ip.y + 0.5, ip.z, 1, 0, 0, 0, 0);
            Fx.burst(sl(), Fx.dust(c1(), 2.4F), ip.add(0, 0.6, 0), 40, 0.6, rnd);
            crackBurst(ip, 50, 5.5, 0.8);
            shake(44, 1.1F, 10);
            aoeDamage(ip, 8.0, 22.0F * pd(), 2.0, 1.0, false);
            fx.ring(ip, 0.9, 20.0, 12.0F * pd(), c1(), c2(), 0);
        }
    }

    /** 2 - four-fist slam: shockwave rings plus eight fissures of light racing across the floor. */
    private void atkFissure(int t, LivingEntity tgt) {
        if (t == 1) {
            sound(ModSounds.BOSS2_CHARGE.get(), 1.8F, 1.0F);
            baseYaw = rnd.nextFloat() * 360F;
        }
        if (t >= 2 && t < 26 && t % 2 == 0) {
            for (int s = -1; s <= 1; s += 2) {
                converge(position().add(right().scale(2.6 * s)).add(0.0, 8.5, 0.0), 3.4, 4, c1(), 0.25);
            }
        }
        Vec3 base = new Vec3(getX(), floorY(position(), getY()), getZ());
        int lines = getPhase() >= 4 ? 12 : 8;
        if (t >= 10 && t < 28 && t % 3 == 0) {
            for (int i = 0; i < lines; i++) {
                Vec3 dir = Vec3.directionFromRotation(0.0F, baseYaw + i * (360.0F / lines));
                Fx.line(sl(), Fx.dust(c2(), 1.0F), base.add(dir.scale(3.0)).add(0, 0.15, 0), base.add(dir.scale(arenaR())).add(0, 0.15, 0), 2.2);
            }
        }
        if (t == 28) {
            sound(ModSounds.BOSS2_SLAM.get(), 2.8F, 0.8F);
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, base.x, base.y + 1.0, base.z, 1, 0, 0, 0, 0);
            crackBurst(base, 70, 7.0, 0.9);
            shake(60, 1.4F, 16);
            flash(30, 0x40FFFFFF, 6);
            aoeDamage(base, 8.0, 22.0F * pd(), 2.0, 1.0, false);
            fx.ring(base, 0.95, 28.0, 13.0F * pd(), c1(), c2(), 0);
            fx.ring(base, 0.95, 28.0, 13.0F * pd(), c2(), c1(), 8);
        }
        if (t >= 28 && t < 48) {
            double len = 3.0 + (t - 28) * 1.5;
            for (int i = 0; i < lines; i++) {
                Vec3 dir = Vec3.directionFromRotation(0.0F, baseYaw + i * (360.0F / lines));
                Vec3 a = base.add(dir.scale(Math.max(0.0, len - 1.6)));
                Vec3 b = base.add(dir.scale(len));
                lineDamage(a.add(0, 0.8, 0), b.add(0, 0.8, 0), 1.3, 16.0F * pd(), true);
                Fx.send(sl(), Fx.dustFade(c1(), 0xFFFFFF, 2.4F), b.x, b.y + 0.2, b.z, 3, 0.2, 0.3, 0.2, 0);
                Fx.vel(sl(), ParticleTypes.END_ROD, b.x, b.y + 0.2, b.z, 0, 0.35, 0);
                if (t % 2 == 0) crackBurst(b, 3, 0.8, 0.5);
            }
        }
    }

    /** 3 - Seeker Storm: waves of homing bolts launched from the orbiting rings. */
    private void atkSeeker(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 1.6F, 1.1F);
        if (t < 14 && t % 3 == 0) {
            for (int s = -1; s <= 1; s += 2) converge(position().add(right().scale(4.0 * s)).add(0.0, 6.0, 0.0), 2.6, 4, c1(), 0.2);
        }
        if (t >= 14 && t <= 62 && (t - 14) % 8 == 0) {
            int per = 2 + getPhase();
            sound(ModSounds.BOSS2_SHOOT.get(), 1.6F, 0.8F + rnd.nextFloat() * 0.3F);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 from = position().add(right().scale(4.4 * s)).add(0.0, 5.4 + rnd.nextDouble() * 2.5, 0.0);
                for (int i = 0; i < per; i++) {
                    Vec3 aim = tgt.position().add(0.0, tgt.getBbHeight() * 0.55, 0.0).subtract(from);
                    Vec3 spread = new Vec3(rnd.nextGaussian() * 3.2, rnd.nextGaussian() * 2.2, rnd.nextGaussian() * 3.2);
                    Vec3 vel = aim.add(spread).normalize().scale(0.9 + 0.04 * getPhase());
                    fx.bolt(from, vel, 9.0F * pd(), 110, c1(), c2(), tgt, 0.05 + 0.015 * getPhase(), ModSounds.BOSS2_SHOOT.get());
                }
                Fx.burst(sl(), Fx.dust(c1(), 1.8F), from, 12, 0.3, rnd);
                Fx.send(sl(), ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            }
            shake(30, 0.25F, 3);
        }
    }

    /** 4 - Omega Beam: a colossal ray from the chest core sweeps through 140 degrees. */
    private void atkBeam(int t, LivingEntity tgt) {
        Vec3 eye = chestPos();
        if (t == 1) {
            sound(ModSounds.BOSS2_CHARGE.get(), 2.4F, 0.7F);
            sweepDir = rnd.nextBoolean() ? 1F : -1F;
        }
        if (t < 50) {
            aimAt(eye, tgt.position().add(0.0, 1.0, 0.0), 12.0F);
            if (t % 2 == 0) converge(eye.add(Vec3.directionFromRotation(getAimPitch(), getAimYaw()).scale(1.5)), 4.5, 6, c1(), 0.3);
            if (t == 49) baseYaw = getAimYaw();
        }
        if (t == 50) {
            sound(ModSounds.BOSS2_BEAM.get(), 3.0F, 1.0F);
            flash(40, 0x40FFFFFF, 6);
            shake(50, 0.9F, 10);
        }
        if (t >= 50 && t < 130) {
            float tf = t - 50;
            float yaw = baseYaw + beamSweep(tf) * sweepDir;
            float pitch = beamPitch(tf);
            setAim(yaw, pitch);
            this.yHeadRot = yaw;
            Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
            Vec3 end = beamEnd(eye, dir, 64.0);
            if (t % 3 == 0) lineDamage(eye, end, 1.9, 8.5F * pd(), true);
            Fx.burst(sl(), ParticleTypes.END_ROD, end, 8, 0.5, rnd);
            Fx.burst(sl(), Fx.dust(c1(), 2.6F), end, 8, 0.4, rnd);
            Fx.send(sl(), ParticleTypes.FLAME, end.x, end.y, end.z, 6, 0.8, 0.1, 0.8, 0.06);
            if (t % 4 == 0) Fx.send(sl(), ParticleTypes.EXPLOSION, end.x, end.y + 0.3, end.z, 1, 0, 0, 0, 0);
            if (t % 3 == 0) crackBurst(end, 6, 1.5, 0.6);
            shake(40, 0.3F, 3);
        }
    }

    /** 5 - Twin Lasers: two beams from the eyes scissor across the arena. */
    private void atkTwin(int t, LivingEntity tgt) {
        Vec3 eye = eyePos();
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 2.2F, 0.8F);
        if (t < 40) {
            aimAt(eye, tgt.position().add(0.0, 1.0, 0.0), 14.0F);
            if (t % 3 == 0) converge(eye.add(0.0, 0.0, 0.0), 4.0, 5, c2(), 0.3);
        }
        if (t == 40) {
            sound(ModSounds.BOSS2_BEAM.get(), 3.0F, 1.2F);
            setAim(getAimYaw(), 11.0F);
            flash(40, 0x40FFFFFF, 6);
        }
        if (t >= 40 && t < 110) {
            float tf = t - 40;
            float spread = twinSpread(tf);
            for (int s = -1; s <= 1; s += 2) {
                float yaw = getAimYaw() + spread * s;
                Vec3 dir = Vec3.directionFromRotation(11.0F, yaw);
                Vec3 end = beamEnd(eye, dir, 64.0);
                if (t % 3 == 0) lineDamage(eye, end, 1.4, 8.0F * pd(), true);
                Fx.burst(sl(), ParticleTypes.END_ROD, end, 5, 0.4, rnd);
                Fx.burst(sl(), Fx.dust(s > 0 ? c1() : c2(), 2.2F), end, 6, 0.35, rnd);
                if (t % 3 == 0) Fx.send(sl(), ParticleTypes.FLAME, end.x, end.y, end.z, 4, 0.6, 0.1, 0.6, 0.05);
            }
            shake(36, 0.25F, 3);
        }
    }

    /** 6 - Reality Skip: three blinks around the player, each followed by a heavy strike. */
    private void atkSkip(int t, LivingEntity tgt) {
        int k = -1;
        if (t == 10) k = 0;
        else if (t == 40) k = 1;
        else if (t == 70) k = 2;
        if (t == 2) sound(ModSounds.BOSS2_TELEPORT.get(), 2.0F, 0.9F);
        if (k >= 0) {
            savedPos = position();
            glitchBurst(savedPos);
            double ang = Math.atan2(tgt.getZ() - home.z, tgt.getX() - home.x) + Math.PI + k * 2.094;
            Vec3 dest = new Vec3(tgt.getX() + Math.cos(ang) * 6.5, 0.0, tgt.getZ() + Math.sin(ang) * 6.5);
            Vec3 off = new Vec3(dest.x - home.x, 0, dest.z - home.z);
            if (off.length() > arenaR() - 2) {
                off = off.normalize().scale(arenaR() - 2);
                dest = new Vec3(home.x + off.x, 0, home.z + off.z);
            }
            double y = floorY(new Vec3(dest.x, tgt.getY(), dest.z), tgt.getY());
            fx.zone(floorPos(savedPos, getY()), 5.0, 22, 13.0F * pd(), 0, c1(), c2(), null);
            warpTo(dest.x, y, dest.z);
            setDeltaMovement(Vec3.ZERO);
            snapFace(tgt.position());
            glitchBurst(new Vec3(dest.x, y, dest.z));
            sound(ModSounds.BOSS2_TELEPORT.get(), 2.0F, 1.3F);
            BossFx.Zone z = fx.zone(floorPos(tgt.position(), tgt.getY()), 3.4, 14, 14.0F * pd(), 2, c1(), c2(), null);
            z.track = tgt;
        }
        if (t == 24 || t == 54 || t == 84) {
            sound(ModSounds.BOSS2_SLAM.get(), 1.8F, 1.2F);
            coneDamage(11.0, 95.0, 20.0F * pd(), 1.8, 0.7, false);
            slashArc(position(), -95.0, 95.0, 3.0, 11.0, 3.4, c1());
            slashArc(position(), -95.0, 95.0, 3.0, 10.0, 5.2, c2());
            slashArc(position(), -95.0, 95.0, 3.0, 9.0, 1.6, 0xFFFFFF);
            shake(34, 0.6F, 6);
        }
    }

    /** 7 - Meteor Fall: annihilation columns crash down on every player and across the arena. */
    private void atkMeteors(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 2.0F, 0.6F);
        if (t == 12) {
            sound(ModSounds.BOSS2_ROAR.get(), 1.4F, 1.4F);
            shake(40, 0.6F, 8);
        }
        if (t >= 12 && t <= 80 && (t - 12) % 7 == 0) {
            for (ServerPlayer p : playersNear(70.0)) {
                if (!isValidVictim(p)) continue;
                BossFx.Zone z = fx.zone(floorPos(p.position(), p.getY()), 4.4, 38, 20.0F * pd(), 3, 0xFFB020, 0xFF3010, ModSounds.BOSS2_NOVA.get());
                z.track = p;
            }
            int n = 2 + getPhase() / 2;
            for (int i = 0; i < n; i++) {
                Vec3 s = scatter(home, 2.0, arenaR());
                fx.zone(floorPos(s, home.y), 4.0, 36, 17.0F * pd(), 3, 0xFFB020, 0xFF3010, null);
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
            double gapW = getPhase() >= 5 ? 5.0 : 6.0;
            int life = (int) ((2 * R + 6) / 0.7);
            fx.wall(start, dir, 2 * R + 6, gapCenter, gapW, 0.7, life, 16.0F * pd(), c1(), c2());
        }
        sound(ModSounds.BOSS2_BEAM.get(), 1.6F, 0.8F);
    }

    /** 8 - Light Grid: crossing walls of fire from four sides, each with a single gap. */
    private void atkGrid(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 2.0F, 0.7F);
        if (t < 24 && t % 3 == 0) converge(position().add(0.0, 8.0, 0.0), 4.0, 6, c1(), 0.3);
        if (t == 24) {
            Vec3 base = new Vec3(getX(), floorY(position(), getY()), getZ());
            sound(ModSounds.BOSS2_SLAM.get(), 2.4F, 0.8F);
            crackBurst(base, 50, 5.0, 0.7);
            shake(50, 1.0F, 12);
            spawnWalls(0.0, tgt);
        }
        if (t == 56) spawnWalls(Math.PI / 2, tgt);
        if (t == 86 && getPhase() >= 4) spawnWalls(Math.PI / 4, tgt);
    }

    /** 9 - Bullet Floor: the whole floor is armed in hex cells; only a few cells are safe. */
    private void atkFloor(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 2.2F, 0.6F);
        if (t == 12) {
            double s = 8.0;
            double R = arenaR() - 1.0;
            List<Vec3> cells = new ArrayList<>();
            for (int gz = -5; gz <= 5; gz++) {
                for (int gx = -5; gx <= 5; gx++) {
                    double x = home.x + gx * s + ((gz & 1) != 0 ? s * 0.5 : 0.0);
                    double z = home.z + gz * s * 0.866;
                    if (Math.sqrt((x - home.x) * (x - home.x) + (z - home.z) * (z - home.z)) <= R) cells.add(new Vec3(x, home.y, z));
                }
            }
            int safe = getPhase() >= 4 ? 2 : 3;
            int safeIdxA = -1;
            // make sure at least one safe cell is near the player
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < cells.size(); i++) {
                double d = cells.get(i).distanceToSqr(tgt.position().x, home.y, tgt.position().z);
                if (d < bd) {
                    bd = d;
                    safeIdxA = i;
                }
            }
            List<Integer> safeSet = new ArrayList<>();
            safeSet.add(safeIdxA);
            while (safeSet.size() < safe && cells.size() > safe) {
                int r = rnd.nextInt(cells.size());
                if (!safeSet.contains(r)) safeSet.add(r);
            }
            for (int i = 0; i < cells.size(); i++) {
                Vec3 c = cells.get(i);
                if (safeSet.contains(i)) {
                    Fx.ring(sl(), Fx.dust(0x19FF8A, 1.6F), c.x, floorY(c, home.y) + 0.2, c.z, 3.0, 20, 0);
                    continue;
                }
                BossFx.Zone z = fx.zone(floorPos(c, home.y), 4.6, 62, 22.0F * pd(), 0, c1(), c2(), null);
                z.lite = true;
            }
            sound(ModSounds.BOSS2_BEAM.get(), 1.6F, 1.6F);
        }
        if (t == 74) {
            shake(60, 1.2F, 14);
            flash(60, 0x30FFFFFF, 6);
        }
    }

    /** 10 - Singularity: a monstrous black hole drags the whole arena inward, flanked by small satellites. */
    private void atkSingularity(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 2.4F, 0.5F);
        if (t == 16) {
            Vec3 f = fwd();
            Vec3 c = new Vec3(getX() + f.x * 13.0, floorY(position(), getY()) + 2.2, getZ() + f.z * 13.0);
            fx.vortex(c, 14.0, 96, 0.22, 36.0F * pd(), 12.0, c1());
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + 0.7;
                Vec3 sc = new Vec3(c.x + Math.cos(a) * 16.0, c.y, c.z + Math.sin(a) * 16.0);
                fx.vortex(sc, 5.0, 70, 0.08, 14.0F * pd(), 5.0, c2());
            }
            sound(ModSounds.BOSS2_BEAM.get(), 2.0F, 0.5F);
            flash(40, 0x40000000, 10);
            shake(60, 0.8F, 20);
        }
        if (t > 16 && t < 112 && t % 4 == 0) {
            Vec3 f = fwd();
            Vec3 c = new Vec3(getX() + f.x * 13.0, getY() + 3.0, getZ() + f.z * 13.0);
            Vec3 hand = position().add(0.0, 6.0, 0.0).add(f.scale(2.0));
            Fx.line(sl(), Fx.dustFade(c1(), 0xFFFFFF, 1.2F), hand, c, 1.4);
        }
    }

    /** 11 - Legion: calls in glowing Vex daemons. */
    private void atkLegion(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 2.0F, 0.8F);
        if (t < 18 && t % 2 == 0) converge(position().add(0.0, 8.5, 0.0), 4.0, 6, c2(), 0.3);
        if (t == 18) {
            sound(ModSounds.BOSS2_ROAR.get(), 2.0F, 1.2F);
            shake(40, 0.7F, 10);
            spawnMinions(getPhase() >= 5 ? 7 : 5);
        }
    }

    private void spawnMinions(int n) {
        ServerLevel level = sl();
        int have = minionCount();
        for (int i = 0; i < n && have + i < 10; i++) {
            Vec3 p = scatter(position(), 5.0, 12.0).add(0.0, 3.0, 0.0);
            Vex v = EntityType.VEX.create(level);
            if (v == null) continue;
            v.moveTo(p.x, p.y, p.z, rnd.nextFloat() * 360.0F, 0.0F);
            v.setBoundOrigin(BlockPos.containing(position()));
            v.setLimitedLife(20 * 30);
            v.addTag("darkweb_minion");
            v.setGlowingTag(true);
            v.setCustomName(Component.literal("Root Daemon"));
            v.finalizeSpawn(level, level.getCurrentDifficultyAt(v.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            level.addFreshEntity(v);
            glitchBurst(new Vec3(p.x, p.y - 3.0, p.z));
        }
    }

    /** distance in the XZ plane from point (px,pz) to the segment a->b */
    private static double segDist2d(double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax, abz = bz - az;
        double l2 = Math.max(1e-6, abx * abx + abz * abz);
        double t = Mth.clamp(((px - ax) * abx + (pz - az) * abz) / l2, 0.0, 1.0);
        double cx = ax + abx * t, cz = az + abz * t;
        return Math.sqrt((px - cx) * (px - cx) + (pz - cz) * (pz - cz));
    }

    /** 12 - Clockwork: glowing blades rotate around the arena at ground level; jump them. */
    private void atkClock(int t, LivingEntity tgt) {
        if (t == 1) {
            sound(ModSounds.BOSS2_CHARGE.get(), 2.0F, 0.9F);
            baseYaw = rnd.nextFloat() * 360F;
            sweepDir = rnd.nextBoolean() ? 1F : -1F;
        }
        int blades = getPhase() >= 5 ? 4 : (getPhase() >= 4 ? 3 : 2);
        double speed = getPhase() >= 5 ? 2.9 : 2.2;
        Vec3 base = new Vec3(home.x, home.y, home.z);
        double R = arenaR();
        if (t >= 10 && t < 130) {
            boolean live = t >= 40;
            double ang0 = baseYaw + (live ? (t - 40) * speed * sweepDir : 0.0);
            for (int i = 0; i < blades; i++) {
                double ang = Math.toRadians(ang0 + i * (360.0 / blades));
                Vec3 dir = new Vec3(-Math.sin(ang), 0.0, Math.cos(ang));
                Vec3 end = base.add(dir.scale(R));
                for (double d = 2.0; d <= R; d += live ? 1.4 : 3.5) {
                    Vec3 p = base.add(dir.scale(d));
                    if (live) {
                        Fx.send(sl(), Fx.dustFade(c1(), c2(), 2.4F), p.x, p.y + 0.5, p.z, 1, 0.1, 0.35, 0.1, 0);
                        if (((int) d) % 3 == 0) Fx.vel(sl(), ParticleTypes.END_ROD, p.x, p.y + 0.3, p.z, 0, 0.22, 0);
                    } else {
                        Fx.send(sl(), Fx.dust(c2(), 1.0F), p.x, p.y + 0.15, p.z, 1, 0.05, 0.0, 0.05, 0);
                    }
                }
                if (live) {
                    AABB box = new AABB(base.x - R, base.y - 1.0, base.z - R, base.x + R, base.y + 3.0, base.z + R);
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidVictim)) {
                        if (e.getY() > base.y + 1.05) continue; // jumped over it
                        double dd = segDist2d(e.getX(), e.getZ(), base.x, base.z, end.x, end.z);
                        if (dd <= 1.25 + e.getBbWidth() * 0.5) {
                            hurtTarget(e, 15.0F * pd(), true);
                            launch(e, new Vec3(e.getX() - base.x, 0, e.getZ() - base.z), 0.7, 0.55);
                        }
                    }
                }
            }
            if (t == 40) {
                sound(ModSounds.BOSS2_BEAM.get(), 1.8F, 1.0F);
                shake(40, 0.6F, 8);
            }
        }
    }

    /** 13 - Comet Rain: bolts fall from the sky at every player. */
    private void atkComets(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 1.8F, 1.0F);
        if (t == 10) sound(ModSounds.BOSS2_ROAR.get(), 1.2F, 1.5F);
        if (t >= 12 && t <= 70 && t % 3 == 0) {
            for (ServerPlayer p : playersNear(70.0)) {
                if (!isValidVictim(p)) continue;
                Vec3 lead = p.position().add(p.getDeltaMovement().scale(9.0));
                for (int i = 0; i < 2; i++) {
                    Vec3 aimAt = new Vec3(lead.x + rnd.nextGaussian() * 3.5, p.getY(), lead.z + rnd.nextGaussian() * 3.5);
                    Vec3 from = new Vec3(aimAt.x + rnd.nextGaussian() * 6.0, p.getY() + 30.0, aimAt.z + rnd.nextGaussian() * 6.0);
                    Vec3 vel = aimAt.subtract(from).normalize().scale(1.25);
                    BossFx.Proj pr = fx.bolt(from, vel, 12.0F * pd(), 60, c1(), c2(), null, 0.0, ModSounds.BOSS2_SHOOT.get());
                    pr.blastRadius = 3.6;
                }
            }
        }
    }

    /** 14 - Rocket Charge: a straight dash across the arena that smashes everything on the way. */
    private void atkCharge(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 2.0F, 1.0F);
        if (t < 20) {
            faceTowards(tgt.position(), 30.0F);
            Vec3 f = fwd();
            if (t % 2 == 0) {
                Fx.line(sl(), Fx.dust(c2(), 1.4F), position().add(0, 0.2, 0), position().add(f.x * 26.0, 0.2, f.z * 26.0), 1.5);
                converge(position().add(0.0, 3.0, 0.0), 5.0, 6, c1(), 0.3);
            }
        }
        if (t == 20) {
            dashDir = fwd();
            sound(ModSounds.BOSS2_TELEPORT.get(), 2.0F, 0.6F);
            shake(40, 0.7F, 8);
        }
        if (t >= 20 && t < 40) {
            setDeltaMovement(dashDir.x * 1.7, getDeltaMovement().y, dashDir.z * 1.7);
            Fx.send(sl(), Fx.dustFade(c1(), c2(), 3.0F), getX(), getY() + 3.0, getZ(), 10, 1.2, 2.5, 1.2, 0);
            Fx.send(sl(), ParticleTypes.END_ROD, getX(), getY() + 3.0, getZ(), 3, 1.2, 2.5, 1.2, 0.05);
            if (t % 2 == 0) {
                coneDamage(6.5, 120.0, 20.0F * pd(), 1.8, 0.8, false);
                crackBurst(new Vec3(getX(), floorY(position(), getY()), getZ()), 5, 2.0, 0.5);
            }
        }
        if (t == 40) {
            setDeltaMovement(Vec3.ZERO);
            sound(ModSounds.BOSS2_SLAM.get(), 2.2F, 1.0F);
            Vec3 base = new Vec3(getX(), floorY(position(), getY()), getZ());
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, base.x, base.y + 1.0, base.z, 1, 0, 0, 0, 0);
            aoeDamage(base, 7.0, 16.0F * pd(), 1.8, 0.8, false);
            fx.ring(base, 0.9, 16.0, 9.0F * pd(), c1(), c2(), 0);
            shake(40, 0.8F, 8);
        }
    }

    /** 15 - Judgment: rings of light pillars erupt one after another with a rotating safe lane in each ring. */
    private void atkJudgment(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 2.2F, 0.7F);
        if (t == 16) {
            sound(ModSounds.BOSS2_NOVA.get(), 1.4F, 1.4F);
            double[] radii = {6.0, 12.5, 19.0, 25.5};
            int[] counts = {8, 15, 21, 28};
            for (int ring = 0; ring < radii.length; ring++) {
                double lane = rnd.nextDouble() * Math.PI * 2;
                for (int i = 0; i < counts[ring]; i++) {
                    double a = (Math.PI * 2 * i) / counts[ring] + 0.1;
                    double diff = Math.abs(((a - lane + Math.PI * 3) % (Math.PI * 2)) - Math.PI);
                    if (diff < 0.2 + 0.25 / (ring + 1) && ring < radii.length) {
                        // keep a clear lane that is wide enough to walk through
                        continue;
                    }
                    Vec3 p = new Vec3(home.x + Math.cos(a) * radii[ring], home.y, home.z + Math.sin(a) * radii[ring]);
                    BossFx.Zone z = fx.zone(floorPos(p, home.y), 2.8, 30 + ring * 14, 18.0F * pd(), 1, c1(), c2(), null);
                    z.lite = true;
                }
            }
        }
        if (t == 60) {
            Vec3 base = new Vec3(getX(), floorY(position(), getY()), getZ());
            BossFx.Ring r = fx.ring(base, 1.0, 30.0, 0.0F, c1(), c2(), 0);
            r.visual = true;
            shake(40, 0.6F, 8);
        }
    }

    /** 16 - Void Hands: giant fists of annihilation slam down on each player. */
    private void atkHands(int t, LivingEntity tgt) {
        if (t == 1) sound(ModSounds.BOSS2_CHARGE.get(), 2.2F, 0.6F);
        if (t == 14 || t == 40) {
            for (ServerPlayer p : playersNear(70.0)) {
                if (!isValidVictim(p)) continue;
                BossFx.Zone z = fx.zone(floorPos(p.position(), p.getY()), 5.0, 44, 30.0F * pd(), 3, c1(), c2(), ModSounds.BOSS2_SLAM.get());
                z.track = p;
            }
            for (int i = 0; i < 5; i++) {
                BossFx.Zone z = fx.zone(floorPos(scatter(home, 4.0, arenaR()), home.y), 4.0, 48, 24.0F * pd(), 3, c2(), c1(), null);
                z.lite = true;
            }
            sound(ModSounds.BOSS2_ROAR.get(), 1.4F, 0.7F);
        }
        if (t >= 14 && t <= 96) {
            for (BossFx.Zone z : fx.zones) {
                if (z.style != 3 || z.age >= z.delay || z.safeZone) continue;
                double k = z.age / (double) z.delay;
                double h = (1.0 - k) * 16.0 + 2.0;
                for (int f = 0; f < 5; f++) {
                    double a = f * 1.2566 + z.age * 0.2;
                    Fx.send(sl(), Fx.dustFade(c1(), 0xFFFFFF, 2.6F), z.c.x + Math.cos(a) * z.radius * 0.5, z.c.y + h, z.c.z + Math.sin(a) * z.radius * 0.5, 1, 0.2, 0.2, 0.2, 0);
                }
                Fx.send(sl(), ParticleTypes.END_ROD, z.c.x, z.c.y + h, z.c.z, 2, 0.8, 0.3, 0.8, 0.01);
            }
        }
    }

    /** 17 - Prism Burst: rotating spirals of bolts flood the arena. */
    private void atkPrism(int t, LivingEntity tgt) {
        if (t == 1) {
            sound(ModSounds.BOSS2_CHARGE.get(), 2.0F, 1.0F);
            baseYaw = rnd.nextFloat() * 360F;
            sweepDir = rnd.nextBoolean() ? 1F : -1F;
        }
        if (t >= 20 && t <= 84 && t % 4 == 0) {
            int arms = getPhase() >= 4 ? 8 : 6;
            double base = Math.toRadians(baseYaw) + (t - 20) * 0.075 * sweepDir;
            for (int a = 0; a < arms; a++) {
                double ang = base + a * (Math.PI * 2 / arms);
                Vec3 from = new Vec3(getX(), getY() + 1.1, getZ()).add(Math.cos(ang) * 2.8, 0.0, Math.sin(ang) * 2.8);
                Vec3 vel = new Vec3(Math.cos(ang) * 0.55, 0.0, Math.sin(ang) * 0.55);
                BossFx.Proj p = fx.bolt(from, vel, 8.0F * pd(), 70, (a & 1) == 0 ? c1() : c2(), (a & 1) == 0 ? c2() : c1(), null, 0.0, null);
                p.blastRadius = 2.2;
                p.radius = 0.75;
            }
            if (t % 8 == 0) sound(ModSounds.BOSS2_SHOOT.get(), 1.4F, 0.6F + rnd.nextFloat() * 0.2F);
        }
    }

    /** 18 - Omega Nova (phase 5): rises, then a world-ending blast - only the green circle is safe. */
    private void atkNova(int t, LivingEntity tgt) {
        if (t == 1) {
            hoverBaseY = getY();
            setNoGravity(true);
            sound(ModSounds.BOSS2_CHARGE.get(), 3.0F, 0.5F);
        }
        if (t >= 1 && t < 138) {
            double wantY = hoverBaseY + 9.0 + (t > 40 ? Math.sin(t * 0.12) * 0.5 : 0.0);
            double dy = wantY - getY();
            setDeltaMovement(0.0, t < 40 ? Math.min(0.3, Math.max(-0.1, dy * 0.2)) : dy * 0.25, 0.0);
        }
        if (t >= 8 && t < 138 && t % 2 == 0) {
            converge(position().add(0.0, 6.0, 0.0), 16.0, 12, c1(), 0.6);
            Fx.helix(sl(), Fx.dustFade(c2(), 0xFFFFFF, 1.6F), getX(), getY() - 1.0, getY() + 11.0, getZ(), 3.2, 22, 3.0, t * 0.3);
        }
        if (t == 44) {
            double R = arenaR();
            double a = rnd.nextDouble() * Math.PI * 2;
            Vec3 c = tgt.position().add(Math.cos(a) * 9.0, 0.0, Math.sin(a) * 9.0);
            Vec3 off = new Vec3(c.x - home.x, 0.0, c.z - home.z);
            if (off.length() > R - 5.0) {
                off = off.normalize().scale(R - 5.0);
                c = new Vec3(home.x + off.x, c.y, home.z + off.z);
            }
            BossFx.Zone z = fx.zone(floorPos(c, tgt.getY()), 5.2, 90, 40.0F * pd(), 3, 0xFFFFFF, c1(), ModSounds.BOSS2_NOVA.get());
            z.safeZone = true;
            sound(ModSounds.BOSS2_BEAM.get(), 2.4F, 0.6F);
        }
        if (t >= 50 && t <= 120 && t % 14 == 0) {
            for (ServerPlayer p : playersNear(70.0)) {
                if (!isValidVictim(p)) continue;
                BossFx.Zone z = fx.zone(floorPos(p.position(), p.getY()), 3.6, 28, 16.0F * pd(), 2, c1(), c2(), ModSounds.BOSS2_SHOOT.get());
                z.track = p;
            }
        }
        if (t == 120) {
            shake(80, 1.6F, 20);
            flash(80, 0x60FFFFFF, 14);
        }
        if (t == 136) {
            setNoGravity(false);
            Vec3 fl = new Vec3(getX(), floorY(position(), hoverBaseY), getZ());
            warpTo(fl.x, fl.y, fl.z);
            setDeltaMovement(Vec3.ZERO);
            sound(ModSounds.BOSS2_SLAM.get(), 3.2F, 0.6F);
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, fl.x, fl.y + 1.0, fl.z, 1, 0, 0, 0, 0);
            Fx.send(sl(), ParticleTypes.FLASH, fl.x, fl.y + 2.0, fl.z, 1, 0, 0, 0, 0);
            Fx.sphere(sl(), Fx.dust(c1(), 3.0F), fl.add(0, 3, 0), 6.0, 160, 0.7);
            crackBurst(fl, 90, 10.0, 1.0);
            shake(80, 2.0F, 24);
            flash(80, 0x80FFFFFF, 12);
            aoeDamage(fl, 11.0, 30.0F * pd(), 2.4, 1.1, false);
            for (int i = 0; i < 4; i++) {
                BossFx.Ring r = fx.ring(fl, 1.2, 40.0, 22.0F * pd(), i % 2 == 0 ? c1() : c2(), i % 2 == 0 ? c2() : c1(), i * 7);
                r.thickness = 2.6;
            }
        }
    }

    // ------------------------------------------------------------------ cutscenes
    @Override
    protected void onCutsceneTick(int id, int t) {
        if (!(level() instanceof ServerLevel)) return;
        if (id == CutsceneIds.OMEGA_SUMMON) {
            // t runs 160..280 for this entity (the terminal drives 0..160)
            double groundY = terminalKnown ? terminal.getY() + 1.0 : home.y;
            if (t < CutsceneIds.OMEGA_SUMMON_SLAM) {
                double u = Mth.clamp((t - CutsceneIds.OMEGA_SUMMON_DESCEND) / (double) (CutsceneIds.OMEGA_SUMMON_SLAM - CutsceneIds.OMEGA_SUMMON_DESCEND), 0.0, 1.0);
                double e = u * u * (3.0 - 2.0 * u);
                double top = groundY + 28.0;
                setPos(getX(), top + (groundY - top) * e, getZ());
                setDeltaMovement(Vec3.ZERO);
                Fx.send(sl(), Fx.dustFade(0xFFD87A, 0xFFFFFF, 3.0F), getX(), getY() + 4.0, getZ(), 14, 1.8, 4.0, 1.8, 0);
                Fx.send(sl(), ParticleTypes.END_ROD, getX(), getY() + 4.0, getZ(), 6, 1.8, 4.0, 1.8, 0.05);
                if (t % 3 == 0) shake(60, 0.3F + (float) u * 0.5F, 4);
            }
            if (t == CutsceneIds.OMEGA_SUMMON_DESCEND) sound(ModSounds.BOSS2_CHARGE.get(), 3.0F, 0.5F);
            if (t == CutsceneIds.OMEGA_SUMMON_SLAM) {
                setNoGravity(false);
                setPos(getX(), groundY, getZ());
                setDeltaMovement(Vec3.ZERO);
                Vec3 base = new Vec3(getX(), groundY, getZ());
                sound(ModSounds.BOSS2_SLAM.get(), 3.4F, 0.6F);
                Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, base.x, base.y + 1.0, base.z, 1, 0, 0, 0, 0);
                Fx.send(sl(), ParticleTypes.FLASH, base.x, base.y + 2.0, base.z, 1, 0, 0, 0, 0);
                Fx.sphere(sl(), Fx.dust(0xFFD87A, 3.0F), base.add(0, 3, 0), 6.0, 160, 0.7);
                crackBurst(base, 100, 10.0, 1.0);
                for (int i = 0; i < 4; i++) {
                    BossFx.Ring r = fx.ring(base, 1.2, 40.0, 0.0F, i % 2 == 0 ? 0xFFD87A : 0x8A5CFF, i % 2 == 0 ? 0x8A5CFF : 0xFFFFFF, i * 6);
                    r.visual = true;
                }
                pushAway(base, 12.0, 1.8, 1.0);
                shake(90, 2.0F, 30);
                flash(80, 0x80FFFFFF, 14);
            }
            if (t == CutsceneIds.OMEGA_SUMMON_ROAR) {
                sound(ModSounds.BOSS2_ROAR.get(), 3.6F, 1.0F);
                Vec3 head = eyePos();
                Fx.burst(sl(), Fx.dust(0xFFD87A, 2.6F), head, 80, 0.9, rnd);
                Fx.burst(sl(), ParticleTypes.END_ROD, head, 60, 1.0, rnd);
                Fx.vring(sl(), Fx.dustFade(0xFFD87A, 0xFFFFFF, 2.4F), head.add(fwd().scale(2.0)), fwd().x, fwd().z, 3.0, 48);
                Fx.vring(sl(), Fx.dustFade(0x8A5CFF, 0xFFFFFF, 2.4F), head.add(fwd().scale(4.0)), fwd().x, fwd().z, 5.5, 64);
                shake(90, 1.6F, 40);
                flash(80, 0x50FFD87A, 8);
            }
        } else if (id == CutsceneIds.OMEGA_PHASE) {
            if (t == 1) sound(ModSounds.BOSS2_PHASE.get(), 3.0F, 1.0F);
            if (t < CutsceneIds.OMEGA_PHASE_BEAT && t % 2 == 0) {
                Vec3 p = position().add(0.0, 1.0 + rnd.nextDouble() * 7.0, 0.0);
                Fx.send(sl(), ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 8, 1.2, 0.6, 1.2, 0.35);
                Fx.send(sl(), Fx.dust(c1(), 2.0F), p.x, p.y, p.z, 5, 1.2, 0.6, 1.2, 0);
            }
            if (t == CutsceneIds.OMEGA_PHASE_BEAT) {
                Vec3 base = new Vec3(getX(), floorY(position(), getY()), getZ());
                int np = C1[Mth.clamp(pendingPhase, 1, 5) - 1];
                int np2 = C2[Mth.clamp(pendingPhase, 1, 5) - 1];
                for (int i = 0; i < 3; i++) {
                    BossFx.Ring r = fx.ring(base, 1.2, 40.0, 0.0F, np, np2, i * 4);
                    r.visual = true;
                }
                Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, base.x, base.y + 3.0, base.z, 1, 0, 0, 0, 0);
                Fx.sphere(sl(), Fx.dust(np, 3.0F), base.add(0, 4, 0), 4.0, 140, 0.8);
                Fx.sphere(sl(), ParticleTypes.END_ROD, base.add(0, 4, 0), 2.0, 80, 0.9);
                Fx.column(sl(), Fx.dust(np, 3.0F), base.x, base.y, base.y + 50.0, base.z, 0.5, 1.4);
                flash(80, 0x70000000 | (np & 0xFFFFFF), 10);
                shake(80, 1.6F, 18);
                pushAway(base, 13.0, 1.8, 0.9);
                // sky lightning around the arena
                for (int i = 0; i < 8; i++) {
                    Vec3 p = scatter(home, 4.0, arenaR());
                    fx.zone(floorPos(p, home.y), 3.0, 8 + i * 2, 0.0F, 2, np, np2, null).visual = true;
                }
            }
        }
    }

    @Override
    protected void onCutsceneEnd(int id) {
        if (id == CutsceneIds.OMEGA_PHASE) {
            heal(getMaxHealth() * 0.02F);
            bossEvent.setColor(getPhase() >= 4 ? BossEvent.BossBarColor.RED : (getPhase() == 3 ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.BLUE));
            lastAtk = 0;
            lastAtk2 = 0;
        }
        if (id == CutsceneIds.OMEGA_SUMMON) {
            setNoGravity(false);
            bossEvent.setColor(BossEvent.BossBarColor.WHITE);
        }
    }

    // ------------------------------------------------------------------ death
    private void findTerminal() {
        if (terminalKnown) return;
        BlockPos c = blockPosition();
        for (int dx = -6; dx <= 6 && !terminalKnown; dx++) {
            for (int dz = -6; dz <= 6 && !terminalKnown; dz++) {
                for (int dy = -4; dy <= 2; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (level().getBlockState(p).is(ModBlocks.OMEGA_TERMINAL.get())) {
                        terminal = p;
                        terminalKnown = true;
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void onDeathTick(int t) {
        if (!(level() instanceof ServerLevel)) return;
        if (t == 1) {
            findTerminal();
            sound(ModSounds.BOSS2_DEATH.get(), 3.6F, 1.0F);
            setNoGravity(false);
            for (Vex v : level().getEntitiesOfClass(Vex.class, getBoundingBox().inflate(90.0), x -> x.getTags().contains("darkweb_minion"))) {
                Fx.burst(sl(), Fx.dust(c1(), 1.6F), v.position(), 24, 0.35, rnd);
                v.discard();
            }
        }
        Vec3 core = position().add(0.0, 5.0, 0.0);
        if (t < CutsceneIds.OMEGA_DEATH_EXPLODE) {
            if (t % 2 == 0) {
                int n = t < 120 ? 5 : 12;
                for (int i = 0; i < n; i++) {
                    Vec3 p = position().add((rnd.nextDouble() - 0.5) * 4.0, 0.5 + rnd.nextDouble() * 8.0, (rnd.nextDouble() - 0.5) * 4.0);
                    Fx.send(sl(), ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.2, 0.2, 0.2, 0.5);
                    Fx.send(sl(), rnd.nextBoolean() ? Fx.dust(0xFFD87A, 1.8F) : Fx.dust(0x8A5CFF, 1.8F), p.x, p.y, p.z, 2, 0.4, 0.4, 0.4, 0);
                }
            }
            if (t >= CutsceneIds.OMEGA_DEATH_CRACK && t % 6 == 0) {
                // reality cracks: beams of light burst out of the core in random directions
                for (int i = 0; i < 3; i++) {
                    Vec3 dir = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.6, rnd.nextGaussian()).normalize();
                    Vec3 end = core.add(dir.scale(20.0 + rnd.nextDouble() * 20.0));
                    Fx.line(sl(), Fx.dustFade(0xFFFFFF, 0xFFD87A, 2.4F), core, end, 1.1);
                    Fx.send(sl(), ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
                }
                flash(80, 0x40FFFFFF, 3);
            }
            if (t % 8 == 0) shake(60, 0.4F + t * 0.006F, 6);
        }
        if (t == CutsceneIds.OMEGA_DEATH_EXPLODE) {
            sound(ModSounds.BOSS2_NOVA.get(), 4.0F, 0.6F);
            Fx.send(sl(), ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            Fx.send(sl(), ParticleTypes.FLASH, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            Fx.sphere(sl(), Fx.dust(0xFFFFFF, 3.4F), core, 4.0, 240, 1.0);
            Fx.sphere(sl(), Fx.dust(0xFFD87A, 3.0F), core, 3.0, 160, 0.7);
            Fx.sphere(sl(), Fx.dust(0x8A5CFF, 2.6F), core, 2.0, 120, 0.5);
            Fx.sphere(sl(), ParticleTypes.END_ROD, core, 1.0, 140, 1.4);
            Fx.burst(sl(), ParticleTypes.FIREWORK, core, 120, 1.4, rnd);
            for (int i = 0; i < 4; i++) {
                BossFx.Ring r = fx.ring(new Vec3(getX(), floorY(position(), getY()) + 0.2, getZ()), 1.4, 46.0, 0.0F, 0xFFD87A, 0x8A5CFF, i * 5);
                r.visual = true;
            }
            flash(100, 0xFFFFFFFF, 40);
            shake(100, 2.4F, 40);
            ExperienceOrb.award(sl(), core, 8000);
        }
        if (t == CutsceneIds.OMEGA_DEATH_PHONE) {
            findTerminal();
            placePhone();
        }
    }

    private void placePhone() {
        if (phoneDone) return;
        phoneDone = true;
        ServerLevel level = sl();
        BlockPos at = terminalKnown ? terminal.above() : blockPosition();
        BlockState st = ModBlocks.IPHONE_DUO.get().defaultBlockState().setValue(IPhoneDuoBlock.FACING, Direction.SOUTH);
        level.setBlock(at, st, 3);
        BlockEntity be = level.getBlockEntity(at);
        if (be instanceof IPhoneDuoBlockEntity) ((IPhoneDuoBlockEntity) be).triggerAppear();
        Vec3 c = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        Fx.column(level, Fx.dust(0xFFFFFF, 2.4F), c.x, c.y, c.y + 40.0, c.z, 0.4, 0.5);
        Fx.column(level, ParticleTypes.END_ROD, c.x, c.y, c.y + 40.0, c.z, 0.6, 0.3);
        Fx.sphere(level, ParticleTypes.END_ROD, c.add(0, 0.6, 0), 1.0, 60, 0.4);
        Fx.sphere(level, Fx.dustFade(0xFFD87A, 0xFFFFFF, 2.0F), c.add(0, 0.6, 0), 1.4, 80, 0.5);
        Fx.send(level, ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 0.6, c.z, 80, 1.0, 1.0, 1.0, 0.5);
        sound(ModSounds.PHONE_UNLOCK.get(), 2.0F, 1.0F);
        if (terminalKnown) {
            BlockEntity tbe = level.getBlockEntity(terminal);
            if (tbe instanceof OmegaTerminalBlockEntity) ((OmegaTerminalBlockEntity) tbe).onBossDefeated();
        }
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TermX", terminal.getX());
        tag.putInt("TermY", terminal.getY());
        tag.putInt("TermZ", terminal.getZ());
        tag.putBoolean("TermKnown", terminalKnown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        terminal = new BlockPos(tag.getInt("TermX"), tag.getInt("TermY"), tag.getInt("TermZ"));
        terminalKnown = tag.getBoolean("TermKnown");
    }
}
