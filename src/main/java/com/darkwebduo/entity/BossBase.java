package com.darkwebduo.entity;

import com.darkwebduo.Config;
import com.darkwebduo.cutscene.CutsceneServer;
import com.darkwebduo.net.ModNetwork;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * Shared brain of both bosses: dormant statue -> cutscene intro -> multi-phase fight -> death cutscene.
 * Subclasses provide the attacks, movement, cutscene beats and loot.
 */
public abstract class BossBase extends Monster {
    public static final int MODE_DORMANT = 0;
    public static final int MODE_CUTSCENE = 1;
    public static final int MODE_FIGHT = 2;
    public static final int MODE_DYING = 3;

    protected static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Long> DATA_ATTACK_START = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.LONG);
    protected static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Long> DATA_MODE_START = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.LONG);
    protected static final EntityDataAccessor<Integer> DATA_CUTSCENE = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> DATA_AIM_YAW = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> DATA_AIM_PITCH = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.FLOAT);

    protected final Random rnd = new Random();
    protected final BossFx fx = new BossFx(this);
    protected final ServerBossEvent bossEvent;

    // server state
    protected int attackT;
    protected int attackCooldown = 40;
    protected int cutsceneT;
    protected int pendingPhase;
    protected int deathT;
    protected int wakeTimer;
    protected boolean configApplied;
    protected Vec3 home = Vec3.ZERO;
    protected boolean homeSet;

    protected BossBase(EntityType<? extends Monster> type, Level level, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        super(type, level);
        this.bossEvent = new ServerBossEvent(this.getDisplayName(), color, overlay);
        this.bossEvent.setVisible(false);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.setMaxUpStep(1.7F);
    }

    // ------------------------------------------------------------------ abstract API
    protected abstract int cutsceneIntroId();
    protected abstract int cutscenePhaseId();
    protected abstract int cutsceneDeathId();
    /** HP fractions at which phase 2, 3, ... start, e.g. {0.66, 0.33} */
    protected abstract double[] phaseThresholds();
    protected abstract int pickAttack(LivingEntity target, double dist);
    protected abstract int attackDuration(int id);
    protected abstract int attackGap();
    protected abstract void tickAttack(int id, int t, LivingEntity target);
    protected abstract void tickMovement(LivingEntity target, double dist);
    protected abstract void tickDormant();
    protected abstract void onCutsceneTick(int cutsceneId, int t);
    protected abstract void onCutsceneEnd(int cutsceneId);
    protected abstract void onDeathTick(int t);
    protected abstract int deathDuration();
    protected abstract double leashRadius();
    protected abstract float damageCapFraction();
    protected abstract void applyConfigAttributes();
    @Nullable protected abstract SoundEvent hurtSound();
    @Nullable protected abstract SoundEvent deathSoundEvent();
    @Nullable protected abstract SoundEvent stepSound();
    protected abstract boolean faceTargetDuring(int attackId);

    // ------------------------------------------------------------------ data
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MODE, MODE_DORMANT);
        this.entityData.define(DATA_ATTACK, 0);
        this.entityData.define(DATA_ATTACK_START, 0L);
        this.entityData.define(DATA_PHASE, 1);
        this.entityData.define(DATA_MODE_START, 0L);
        this.entityData.define(DATA_CUTSCENE, 0);
        this.entityData.define(DATA_AIM_YAW, 0F);
        this.entityData.define(DATA_AIM_PITCH, 0F);
    }

    public int getMode() { return this.entityData.get(DATA_MODE); }
    public int getAttack() { return this.entityData.get(DATA_ATTACK); }
    public int getPhase() { return this.entityData.get(DATA_PHASE); }
    public int getCutsceneId() { return this.entityData.get(DATA_CUTSCENE); }
    public float getAimYaw() { return this.entityData.get(DATA_AIM_YAW); }
    public float getAimPitch() { return this.entityData.get(DATA_AIM_PITCH); }
    public boolean isDormant() { return getMode() == MODE_DORMANT; }

    protected void setMode(int mode) {
        this.entityData.set(DATA_MODE, mode);
        this.entityData.set(DATA_MODE_START, level().getGameTime());
        this.bossEvent.setVisible(mode == MODE_FIGHT || mode == MODE_CUTSCENE);
    }

    /** ticks since the current mode (cutscene / dying) began, with partial tick (client + server). */
    public float modeTime(float partial) {
        return (float) (level().getGameTime() - this.entityData.get(DATA_MODE_START)) + partial;
    }

    /** ticks since the current attack began (client + server). */
    public float attackTime(float partial) {
        return (float) (level().getGameTime() - this.entityData.get(DATA_ATTACK_START)) + partial;
    }

    protected void setAim(float yaw, float pitch) {
        this.entityData.set(DATA_AIM_YAW, yaw);
        this.entityData.set(DATA_AIM_PITCH, pitch);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ------------------------------------------------------------------ vanilla overrides
    @Override
    public boolean isPushable() { return false; }

    @Override
    protected boolean shouldDespawnInPeaceful() { return false; }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public boolean canChangeDimensions() { return false; }

    @Override
    public boolean canBeLeashed(Player player) { return false; }

    @Override
    protected boolean isImmobile() {
        int m = getMode();
        return m == MODE_DORMANT || m == MODE_CUTSCENE || m == MODE_DYING || super.isImmobile();
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) { return false; }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource src) { return hurtSound(); }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() { return null; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundEvent s = stepSound();
        if (s != null && getMode() == MODE_FIGHT) {
            this.playSound(s, 1.0F, 0.9F + random.nextFloat() * 0.2F);
            if (level() instanceof ServerLevel) ModNetwork.shake((ServerLevel) level(), position(), 18, 0.12F, 4);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        int mode = getMode();
        if (mode == MODE_DORMANT) {
            if (source.getEntity() instanceof Player) wake();
            return false;
        }
        if (mode != MODE_FIGHT) return false;
        amount = Math.min(amount, getMaxHealth() * damageCapFraction());
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && getMode() != MODE_DYING) {
            enterDying();
        }
        super.die(source);
    }

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
        // loot is dropped by the death cutscene at the right beat
    }

    @Override
    protected void tickDeath() {
        if (level().isClientSide) return;
        deathT++;
        onDeathTick(deathT);
        if (deathT >= deathDuration()) {
            this.bossEvent.setVisible(false);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    // ------------------------------------------------------------------ main tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel)) return;
        ServerLevel sl = (ServerLevel) level();
        if (!configApplied) {
            applyConfigAttributes();
            configApplied = true;
        }
        if (!homeSet) {
            home = position();
            homeSet = true;
        }
        this.bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
        fx.tick(sl);

        switch (getMode()) {
            case MODE_DORMANT:
                tickDormant();
                wakeCheck(sl);
                break;
            case MODE_CUTSCENE:
                cutsceneT++;
                onCutsceneTick(getCutsceneId(), cutsceneT);
                if (cutsceneT >= com.darkwebduo.cutscene.CutsceneIds.duration(getCutsceneId())) {
                    finishCutscene();
                }
                break;
            case MODE_FIGHT:
                fightTick(sl);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ wake
    protected void wakeCheck(ServerLevel sl) {
        if (tickCount % 5 != 0) return;
        boolean survivalOnly = Config.wakeOnlySurvival();
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (survivalOnly && p.isCreative()) continue;
            double d = p.distanceTo(this);
            if (d > 14.0) continue;
            boolean near = d < 8.0;
            Vec3 to = p.position().subtract(position()).normalize();
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            double dot = to.x * look.x + to.z * look.z;
            boolean infront = dot > -0.35; // ~110 degrees either side
            if ((near || infront) && hasLineOfSight(p)) {
                wake();
                return;
            }
        }
    }

    public void wake() {
        if (level().isClientSide || getMode() != MODE_DORMANT) return;
        startCutscene(cutsceneIntroId(), 0);
    }

    // ------------------------------------------------------------------ cutscenes
    protected void startCutscene(int id, int arg) {
        if (!(level() instanceof ServerLevel)) return;
        ServerLevel sl = (ServerLevel) level();
        cutsceneT = 0;
        pendingPhase = arg;
        this.entityData.set(DATA_CUTSCENE, id);
        setMode(MODE_CUTSCENE);
        endAttackSilently();
        fx.clear();
        CutsceneServer.start(sl, position(), 64.0, id, arg, getId());
    }

    /** Joins an already running (externally started) cutscene at tick startT: no packets are sent. Used by the Omega summon. */
    protected void resumeCutscene(int id, int startT) {
        cutsceneT = startT;
        pendingPhase = 0;
        this.entityData.set(DATA_CUTSCENE, id);
        setMode(MODE_CUTSCENE);
        endAttackSilently();
        fx.clear();
    }

    protected void finishCutscene() {
        int id = getCutsceneId();
        if (pendingPhase > 0 && id == cutscenePhaseId()) {
            this.entityData.set(DATA_PHASE, pendingPhase);
        }
        setMode(MODE_FIGHT);
        attackCooldown = 20;
        onCutsceneEnd(id);
    }

    protected void enterDying() {
        deathT = 0;
        fx.clear();
        endAttackSilently();
        this.entityData.set(DATA_CUTSCENE, cutsceneDeathId());
        setMode(MODE_DYING);
        if (level() instanceof ServerLevel) {
            CutsceneServer.start((ServerLevel) level(), position(), 64.0, cutsceneDeathId(), 0, getId());
        }
    }

    // ------------------------------------------------------------------ fight
    protected void fightTick(ServerLevel sl) {
        // phase thresholds
        double[] th = phaseThresholds();
        int ph = getPhase();
        double frac = getHealth() / getMaxHealth();
        if (ph - 1 < th.length && frac <= th[ph - 1] && getAttack() >= 0) {
            startCutscene(cutscenePhaseId(), ph + 1);
            return;
        }

        // leash
        if (homeSet && position().distanceTo(home) > leashRadius()) {
            Fx.sphere(sl, ParticleTypes.REVERSE_PORTAL, position().add(0, getBbHeight() * 0.5, 0), 2.0, 40, 0.3);
            warpTo(home.x, home.y, home.z);
            Fx.sphere(sl, ParticleTypes.REVERSE_PORTAL, position().add(0, getBbHeight() * 0.5, 0), 2.0, 40, 0.3);
            setDeltaMovement(Vec3.ZERO);
        }

        LivingEntity tgt = findTarget(sl);
        if (tgt == null) {
            // nobody around: stand and slowly heal
            if (getAttack() != 0) endAttack();
            if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(getMaxHealth() * 0.002F);
            return;
        }
        setTarget(tgt);
        double dist = distanceTo(tgt);
        int cur = getAttack();
        if (cur == 0) {
            faceTowards(tgt.position(), 14F);
            tickMovement(tgt, dist);
            if (attackCooldown > 0) {
                attackCooldown--;
            } else {
                int id = pickAttack(tgt, dist);
                if (id > 0) beginAttack(id);
            }
        } else {
            if (faceTargetDuring(cur)) faceTowards(tgt.position(), 10F);
            tickAttack(cur, attackT, tgt);
            attackT++;
            if (attackT >= attackDuration(cur)) endAttack();
        }
    }

    protected void beginAttack(int id) {
        attackT = 0;
        this.entityData.set(DATA_ATTACK, id);
        this.entityData.set(DATA_ATTACK_START, level().getGameTime());
    }

    protected void endAttack() {
        this.entityData.set(DATA_ATTACK, 0);
        attackT = 0;
        attackCooldown = attackGap();
    }

    protected void endAttackSilently() {
        this.entityData.set(DATA_ATTACK, 0);
        attackT = 0;
    }

    @Nullable
    protected LivingEntity findTarget(ServerLevel sl) {
        LivingEntity best = null;
        double bd = 70.0 * 70.0;
        for (ServerPlayer p : sl.players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            double d = p.distanceToSqr(this);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ helpers for subclasses
    protected void faceTowards(Vec3 target, float maxStep) {
        double dx = target.x - getX(), dz = target.z - getZ();
        float want = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float y = Mth.approachDegrees(getYRot(), want, maxStep);
        setYRot(y);
        this.yBodyRot = y;
        this.yHeadRot = y;
    }

    /** Instant reposition (keeps rotation); uses moveTo, which exists on every Entity in 1.20.1. */
    protected void warpTo(double x, double y, double z) {
        this.moveTo(x, y, z, this.getYRot(), this.getXRot());
    }

    protected void snapFace(Vec3 target) {
        faceTowards(target, 360F);
    }

    protected void walkToward(Vec3 goal, double speed) {
        double dx = goal.x - getX(), dz = goal.z - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        Vec3 mv = getDeltaMovement();
        if (d < 0.4) {
            setDeltaMovement(mv.x * 0.5, mv.y, mv.z * 0.5);
            return;
        }
        setDeltaMovement(dx / d * speed, mv.y, dz / d * speed);
    }

    public boolean isValidVictim(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof BossBase) return false;
        if (e.getTags().contains("darkweb_minion")) return false;
        if (e instanceof Player && ((Player) e).isCreative()) return false;
        return true;
    }

    public boolean hurtTarget(LivingEntity e, float dmg, boolean magic) {
        float amount = dmg * (float) Config.damageMult();
        DamageSource src = magic ? level().damageSources().indirectMagic(this, this) : level().damageSources().mobAttack(this);
        return e.hurt(src, amount);
    }

    public void launch(LivingEntity e, Vec3 dir, double horizontal, double up) {
        Vec3 d = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 0) : dir.normalize();
        e.setDeltaMovement(e.getDeltaMovement().add(d.x * horizontal, up, d.z * horizontal));
        e.hurtMarked = true;
    }

    /** Sphere-ish AoE (cylinder, 4 blocks tall) damage with knockback away from the centre. */
    public void aoeDamage(Vec3 c, double r, float dmg, double kb, double up, boolean magic) {
        AABB box = new AABB(c.x - r, c.y - 1.5, c.z - r, c.x + r, c.y + 4.0, c.z + r);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidVictim)) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + e.getBbWidth() * 0.5) continue;
            float falloff = (float) (1.0 - 0.45 * (d / Math.max(0.01, r)));
            hurtTarget(e, dmg * falloff, magic);
            launch(e, new Vec3(dx, 0, dz), kb, up);
        }
    }

    /** Pure knockback (no damage) on everything around a centre. */
    public void pushAway(Vec3 c, double r, double kb, double up) {
        AABB box = new AABB(c.x - r, c.y - 2.0, c.z - r, c.x + r, c.y + 5.0, c.z + r);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidVictim)) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (Math.sqrt(dx * dx + dz * dz) <= r) launch(e, new Vec3(dx, 0, dz), kb, up);
        }
    }

    /** Cone in front of the boss (yaw-based), used for melee swings. */
    public void coneDamage(double range, double halfAngleDeg, float dmg, double kb, double up, boolean magic) {
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        AABB box = new AABB(getX() - range, getY() - 1.0, getZ() - range, getX() + range, getY() + getBbHeight(), getZ() + range);
        double cosLimit = Math.cos(Math.toRadians(halfAngleDeg));
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidVictim)) {
            Vec3 to = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
            double d = to.length();
            if (d > range + e.getBbWidth() * 0.5) continue;
            if (d > 0.01 && to.normalize().dot(new Vec3(look.x, 0, look.z).normalize()) < cosLimit) continue;
            hurtTarget(e, dmg, magic);
            launch(e, look, kb, up);
        }
    }

    /** Damages everything close to the segment a-b. */
    public void lineDamage(Vec3 a, Vec3 b, double width, float dmg, boolean magic) {
        AABB box = new AABB(a, b).inflate(width + 1.0);
        Vec3 ab = b.subtract(a);
        double len2 = Math.max(1e-6, ab.lengthSqr());
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidVictim)) {
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
            Vec3 closest = a.add(ab.scale(t));
            if (p.distanceTo(closest) <= width + e.getBbWidth() * 0.5) {
                hurtTarget(e, dmg, magic);
            }
        }
    }

    protected List<ServerPlayer> playersNear(double r) {
        List<ServerPlayer> out = new java.util.ArrayList<>();
        if (!(level() instanceof ServerLevel)) return out;
        for (ServerPlayer p : ((ServerLevel) level()).players()) {
            if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) <= r * r) out.add(p);
        }
        return out;
    }

    protected void shake(double radius, float intensity, int ticks) {
        if (level() instanceof ServerLevel) ModNetwork.shake((ServerLevel) level(), position(), radius, intensity, ticks);
    }

    protected void flash(double radius, int argb, int ticks) {
        if (level() instanceof ServerLevel) ModNetwork.flash((ServerLevel) level(), position(), radius, argb, ticks);
    }

    protected void sound(SoundEvent s, float vol, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), s, SoundSource.HOSTILE, vol, pitch);
    }

    protected void soundAt(Vec3 p, SoundEvent s, float vol, float pitch) {
        level().playSound(null, p.x, p.y, p.z, s, SoundSource.HOSTILE, vol, pitch);
    }

    protected ServerLevel sl() {
        return (ServerLevel) level();
    }

    /** Random point on the floor around a centre; keeps to the y of the centre. */
    protected Vec3 scatter(Vec3 c, double minR, double maxR) {
        double a = rnd.nextDouble() * Math.PI * 2;
        double r = minR + rnd.nextDouble() * (maxR - minR);
        return new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
    }

    /** Highest solid floor below a position (searches 12 blocks down, returns y of the top surface). */
    protected double floorY(Vec3 p, double fallback) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(Mth.floor(p.x), Mth.floor(p.y) + 3, Mth.floor(p.z));
        for (int i = 0; i < 24; i++) {
            BlockState s = level().getBlockState(mp);
            if (!s.isAir() && !s.getCollisionShape(level(), mp).isEmpty()) {
                return mp.getY() + 1.0;
            }
            mp.move(0, -1, 0);
        }
        return fallback;
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BossMode", getMode());
        tag.putInt("BossPhase", getPhase());
        tag.putDouble("HomeX", home.x);
        tag.putDouble("HomeY", home.y);
        tag.putDouble("HomeZ", home.z);
        tag.putBoolean("HomeSet", homeSet);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int mode = tag.getInt("BossMode");
        if (mode == MODE_CUTSCENE) mode = MODE_FIGHT; // never resume a cutscene
        if (mode == MODE_DYING) mode = MODE_FIGHT;
        this.entityData.set(DATA_MODE, mode);
        this.entityData.set(DATA_PHASE, Math.max(1, tag.getInt("BossPhase")));
        home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        homeSet = tag.getBoolean("HomeSet");
        this.bossEvent.setVisible(mode == MODE_FIGHT);
        if (this.hasCustomName()) this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                                                    net.minecraft.world.DifficultyInstance difficulty, MobSpawnType reason,
                                                                    @Nullable net.minecraft.world.entity.SpawnGroupData data,
                                                                    @Nullable CompoundTag tag) {
        home = position();
        homeSet = true;
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }
}
