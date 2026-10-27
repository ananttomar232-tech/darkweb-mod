package com.darkwebduo.block.entity;

import com.darkwebduo.block.OmegaTerminalBlock;
import com.darkwebduo.cutscene.CutsceneIds;
import com.darkwebduo.cutscene.CutsceneServer;
import com.darkwebduo.entity.OmegaRootEntity;
import com.darkwebduo.net.ModNetwork;
import com.darkwebduo.registry.ModBlockEntities;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;

/**
 * Brain of the Omega Terminal. Time is measured against the game clock (startTime) so client renderer and server agree.
 * Timeline (identical to CutsceneIds.OMEGA_SUMMON):  0 card in / 30 sky / 60 light pillars / 120 rift / 160 boss spawns.
 */
public class OmegaTerminalBlockEntity extends BlockEntity {
    public static final int STAGE_IDLE = 0, STAGE_SUMMON = 1, STAGE_FIGHT = 2, STAGE_DEFEATED = 3;
    public static final int RIFT_HEIGHT = 28;
    public static final int PILLARS = 6;

    private int stage;
    private long startTime;
    @Nullable
    private UUID activator;
    @Nullable
    private UUID boss;
    private int missing;
    private final Random rnd = new Random();

    public OmegaTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OMEGA_TERMINAL.get(), pos, state);
    }

    public int getStage() {
        return stage;
    }

    /** ticks since the card went in (fractional); large when the terminal is not in its summon sequence */
    public float animTime(float partial) {
        if (stage != STAGE_SUMMON || level == null) return 100000F;
        return Math.max(0F, (float) (level.getGameTime() - startTime) + partial);
    }

    /** radius of the ring of light pillars at summon time t (shared with the renderer) */
    public static float pillarRadius(float t) {
        if (t < 90F) return 10F;
        return Mth.lerp(Mth.clamp((t - 90F) / 30F, 0F, 1F), 10F, 4F);
    }

    public static float pillarAngle(int i, float t) {
        return i * (float) (Math.PI * 2 / PILLARS) + t * 0.012F;
    }

    // ------------------------------------------------------------------ server API

    /** is there room above the terminal for the rift and the descent of the boss? */
    public boolean hasClearance() {
        if (level == null) return false;
        for (int dy = 2; dy <= RIFT_HEIGHT + 3; dy++) {
            BlockPos p = worldPosition.above(dy);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    public boolean isBusy() {
        return stage == STAGE_SUMMON || stage == STAGE_FIGHT;
    }

    public void activate(ServerPlayer player) {
        if (!(level instanceof ServerLevel) || isBusy()) return;
        ServerLevel sl = (ServerLevel) level;
        stage = STAGE_SUMMON;
        startTime = sl.getGameTime();
        activator = player.getUUID();
        boss = null;
        missing = 0;
        sl.setBlock(worldPosition, getBlockState().setValue(OmegaTerminalBlock.ACTIVE, Boolean.TRUE), 3);
        setChanged();
        sync();
        Vec3 c = center();
        CutsceneServer.start(sl, c.add(0, 1.0, 0), 70.0, CutsceneIds.OMEGA_SUMMON, 0, -1);
        Fx.sound(sl, c, ModSounds.TERMINAL_INSERT.get(), SoundSource.BLOCKS, 1.4F, 1.0F);
    }

    /** called by the boss at the moment the iPhone Duo materialises */
    public void onBossDefeated() {
        stage = STAGE_DEFEATED;
        boss = null;
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(OmegaTerminalBlock.ACTIVE, Boolean.FALSE), 3);
        }
        setChanged();
        sync();
    }

    private void reset() {
        stage = STAGE_IDLE;
        boss = null;
        activator = null;
        missing = 0;
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(OmegaTerminalBlock.ACTIVE, Boolean.FALSE), 3);
        }
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private Vec3 center() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(Level level, BlockPos pos, BlockState state, OmegaTerminalBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        be.serverTick((ServerLevel) level);
    }

    private void serverTick(ServerLevel sl) {
        long gt = sl.getGameTime();
        if (stage == STAGE_SUMMON) {
            summonTick(sl, (int) (gt - startTime));
        } else if (stage == STAGE_FIGHT) {
            if ((gt & 7L) == 0L) watchBoss(sl);
        } else if ((gt & 7L) == 0L) {
            Vec3 c = center();
            double a = gt * 0.21;
            Fx.send(sl, stage == STAGE_IDLE ? Fx.dust(0xFFD87A, 1.0F) : Fx.dust(0x8A5CFF, 1.0F),
                    c.x + Math.cos(a) * 0.5, c.y + 1.2 + rnd.nextDouble() * 0.6, c.z + Math.sin(a) * 0.5, 1, 0.02, 0.05, 0.02, 0);
        }
    }

    private void watchBoss(ServerLevel sl) {
        Entity e = boss == null ? null : sl.getEntity(boss);
        if (e == null || e.isRemoved()) {
            if (++missing > 25) {
                for (ServerPlayer p : sl.players()) {
                    if (p.distanceToSqr(center()) < 80.0 * 80.0) {
                        p.displayClientMessage(Component.translatable("message.darkweb_duo.terminal_reset"), true);
                    }
                }
                reset();
            }
        } else {
            missing = 0;
        }
    }

    private void bolt(ServerLevel sl, double x, double y, double z) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(sl);
        if (b == null) return;
        b.moveTo(x, y, z);
        b.setVisualOnly(true);
        sl.addFreshEntity(b);
    }

    private void summonTick(ServerLevel sl, int t) {
        Vec3 c = center();
        Vec3 slot = c.add(0, 0.95, 0);
        Vec3 rift = c.add(0, RIFT_HEIGHT + 0.5, 0);

        // ---- 0..30: the card slides in, the terminal powers up
        if (t < 30) {
            double k = t / 30.0;
            for (int arm = 0; arm < 4; arm++) {
                double a = t * 0.5 + arm * Math.PI / 2;
                double r = 3.2 - 2.4 * k;
                Fx.send(sl, Fx.dustFade(0xFFD87A, 0x8A5CFF, 1.3F), c.x + Math.cos(a) * r, c.y + 0.1 + k * 1.6, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (t == 6) Fx.burst(sl, ParticleTypes.END_ROD, slot, 24, 0.2, rnd);
            if (t == 8) Fx.sound(sl, c, ModSounds.TERMINAL_ACTIVATE.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
        }

        // ---- 30..60: the sky turns, thunder walks across the arena
        if (t == 30) {
            Fx.sound(sl, c, ModSounds.TERMINAL_HUM.get(), SoundSource.BLOCKS, 3.0F, 0.7F);
            ModNetwork.shake(sl, c, 50.0, 0.3F, 40);
            bolt(sl, c.x + 17, c.y, c.z - 9);
        }
        if (t == 40 || t == 50 || t == 56) {
            double a = rnd.nextDouble() * Math.PI * 2;
            bolt(sl, c.x + Math.cos(a) * (14 + rnd.nextInt(10)), c.y, c.z + Math.sin(a) * (14 + rnd.nextInt(10)));
        }
        if (t >= 30 && t < 60 && t % 3 == 0) {
            Fx.ring(sl, Fx.dust(0xFFD87A, 1.5F), c.x, c.y + 0.1, c.z, 1.0 + (t - 30) * 0.35, 40, 0);
        }

        // ---- 60..120: pillars of light hammer down onto the arena, then close in on the terminal
        if (t >= 60 && t < 160) {
            float tt = t;
            float pr = pillarRadius(tt);
            for (int i = 0; i < PILLARS; i++) {
                float ang = pillarAngle(i, tt);
                double px = c.x + Math.cos(ang) * pr;
                double pz = c.z + Math.sin(ang) * pr;
                int landed = 66 + i * 2;
                if (t == landed) {
                    Fx.sound(sl, new Vec3(px, c.y, pz), ModSounds.BOSS2_SLAM.get(), SoundSource.HOSTILE, 1.4F, 1.0F + i * 0.04F);
                    Fx.send(sl, ParticleTypes.EXPLOSION, px, c.y + 0.5, pz, 1, 0, 0, 0, 0);
                    ModNetwork.shake(sl, c, 50.0, 0.5F, 8);
                }
                if (t >= landed && t % 2 == 0) {
                    Fx.send(sl, Fx.dust(0xFFD87A, 2.0F), px, c.y + 0.3, pz, 5, 0.7, 0.1, 0.7, 0);
                    Fx.send(sl, ParticleTypes.END_ROD, px, c.y + 0.3, pz, 2, 0.4, 0.1, 0.4, 0.12);
                }
                if (t >= landed && t % 8 == i) {
                    Fx.column(sl, Fx.dust(i % 2 == 0 ? 0xFFD87A : 0x8A5CFF, 1.6F), px, c.y, c.y + 36.0, pz, 1.4, 0.3);
                    BlockState st = sl.getBlockState(BlockPos.containing(px, c.y - 0.5, pz));
                    if (!st.isAir()) {
                        for (int k = 0; k < 6; k++) {
                            Fx.vel(sl, new BlockParticleOption(ParticleTypes.BLOCK, st), px + rnd.nextGaussian() * 0.7, c.y + 0.2, pz + rnd.nextGaussian() * 0.7,
                                    rnd.nextGaussian() * 0.12, 0.25 + rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.12);
                        }
                    }
                }
            }
            if (t % 4 == 0) {
                double a = rnd.nextDouble() * Math.PI * 2;
                bolt(sl, c.x + Math.cos(a) * (16 + rnd.nextInt(14)), c.y, c.z + Math.sin(a) * (16 + rnd.nextInt(14)));
            }
        }

        // ---- 118..: the rift tears open above the terminal
        if (t == 118) Fx.sound(sl, rift, ModSounds.BOSS2_SUMMON.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
        if (t == 120) {
            Fx.send(sl, ParticleTypes.FLASH, rift.x, rift.y, rift.z, 1, 0, 0, 0, 0);
            Fx.sphere(sl, ParticleTypes.REVERSE_PORTAL, rift, 3.0, 220, 0.35);
            Fx.sphere(sl, Fx.dust(0xFFFFFF, 2.6F), rift, 2.0, 120, 0.4);
            ModNetwork.flash(sl, c, 70.0, 0xA0FFFFFF, 30);
            ModNetwork.shake(sl, c, 60.0, 1.0F, 30);
        }
        if (t >= 120 && t < 206) {
            float k = Mth.clamp((t - 120) / 40F, 0F, 1F);
            double r = 2.0 + 10.0 * k;
            if (t % 2 == 0) {
                Fx.ring(sl, ParticleTypes.REVERSE_PORTAL, rift.x, rift.y, rift.z, r, 48, 0.0);
                Fx.ring(sl, Fx.dust(0x8A5CFF, 2.2F), rift.x, rift.y, rift.z, r * 0.75, 36, 0.0);
            }
            if (t % 3 == 0) {
                Fx.send(sl, ParticleTypes.ELECTRIC_SPARK, rift.x, rift.y, rift.z, 10, r * 0.5, 0.4, r * 0.5, 0.4);
                Fx.send(sl, ParticleTypes.END_ROD, rift.x, rift.y - 1.0, rift.z, 4, r * 0.3, 0.3, r * 0.3, 0.2);
            }
            if (t % 6 == 0) ModNetwork.shake(sl, c, 60.0, 0.25F + 0.6F * k, 10);
        }
        // light spilling down the shaft onto the pedestal
        if (t >= 125 && t < 205 && t % 2 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double yy = c.y + 2.0 + rnd.nextDouble() * (RIFT_HEIGHT - 3);
            Fx.vel(sl, Fx.dustFade(0xFFD87A, 0xFFFFFF, 1.6F), c.x + Math.cos(a) * 1.2, yy, c.z + Math.sin(a) * 1.2, 0, -0.35, 0);
        }

        // ---- 158: the flash that hides the boss spawning
        if (t == 158) ModNetwork.flash(sl, c, 70.0, 0xC0FFFFFF, 14);

        // ---- 160: the Omega Root steps through
        if (t >= 160 && stage == STAGE_SUMMON) {
            ServerPlayer p = activator == null || sl.getServer() == null ? null : sl.getServer().getPlayerList().getPlayer(activator);
            OmegaRootEntity e = OmegaRootEntity.beginSummon(sl, worldPosition, p);
            if (e != null) {
                boss = e.getUUID();
                stage = STAGE_FIGHT;
                missing = 0;
            } else {
                reset();
                return;
            }
            setChanged();
            sync();
        }
    }

    // ------------------------------------------------------------------ persistence + sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Stage", stage);
        tag.putLong("Start", startTime);
        if (activator != null) tag.putUUID("Activator", activator);
        if (boss != null) tag.putUUID("Boss", boss);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stage = tag.getInt("Stage");
        startTime = tag.getLong("Start");
        activator = tag.hasUUID("Activator") ? tag.getUUID("Activator") : null;
        boss = tag.hasUUID("Boss") ? tag.getUUID("Boss") : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(26.0, 90.0, 26.0);
    }
}
