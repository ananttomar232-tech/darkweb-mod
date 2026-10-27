package com.darkwebduo.block.entity;

import com.darkwebduo.block.IPhoneDuoBlock;
import com.darkwebduo.net.ModNetwork;
import com.darkwebduo.registry.ModBlockEntities;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * The iPhone Duo display piece. All animation is a pure function of the game clock (appearStart / foldStart), so client
 * renderer and server logic stay in step without extra packets.
 *
 * Arrival timeline (ticks after {@link #triggerAppear()}): 0-24 materialise from a scan-line of light, 0-96 descend while
 * spinning, 96 land, 118-142 unfold, 150-190 boot, 200 done.
 */
public class IPhoneDuoBlockEntity extends BlockEntity {
    public static final int FOLD_TICKS = 24;
    public static final int T_SOLID = 24;
    public static final int T_LAND = 96;
    public static final int T_UNFOLD = 118;
    public static final int T_BOOT = 150;
    public static final int T_BOOT_END = 190;
    public static final int T_END = 200;
    public static final float HOVER = 0.14F;
    public static final float DROP_HEIGHT = 2.9F;

    private long appearStart = -1L;
    private long foldStart = -100000L;
    private float foldFrom = 1F;
    private int colorway;
    private final Random rnd = new Random();

    public IPhoneDuoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IPHONE_DUO.get(), pos, state);
    }

    // ------------------------------------------------------------------ state
    public int getColorway() {
        return colorway;
    }

    public void setColorway(int c) {
        this.colorway = c == 1 ? 1 : 0;
        setChanged();
        sync();
    }

    public boolean isOpenState() {
        BlockState s = getBlockState();
        return s.hasProperty(IPhoneDuoBlock.OPEN) && s.getValue(IPhoneDuoBlock.OPEN);
    }

    /** ticks since the arrival began (with partial tick), or -1 when no arrival is running */
    public float appearTime(float partial) {
        if (appearStart < 0L || level == null) return -1F;
        float t = (float) (level.getGameTime() - appearStart) + partial;
        if (t < 0F) return 0F;
        return t >= T_END ? -1F : t;
    }

    public boolean isAppearing() {
        return appearTime(0F) >= 0F;
    }

    /** vertical offset above the stand (blocks) for a given arrival time */
    public static float heightAt(float t) {
        if (t >= T_LAND) return HOVER;
        float k = Mth.clamp(t / (float) T_LAND, 0F, 1F);
        float e = 1F - (1F - k) * (1F - k) * (1F - k);
        return HOVER + DROP_HEIGHT * (1F - e);
    }

    /** 0 = unfolded flat, 1 = closed */
    public float foldFraction(float partial) {
        float target = isOpenState() ? 0F : 1F;
        if (level == null) return target;
        float t = ((float) (level.getGameTime() - foldStart) + partial) / (float) FOLD_TICKS;
        t = Mth.clamp(t, 0F, 1F);
        float e = t * t * (3F - 2F * t);
        return Mth.lerp(e, foldFrom, target);
    }

    /** closed fraction the arrival animation wants at time t (client uses this instead of waiting for the state packet) */
    public static float appearFold(float t) {
        if (t < T_UNFOLD) return 1F;
        float k = Mth.clamp((t - T_UNFOLD) / (float) FOLD_TICKS, 0F, 1F);
        float e = k * k * (3F - 2F * k);
        return 1F - e;
    }

    // ------------------------------------------------------------------ server actions
    /** called by the boss when it dies: starts the arrival show */
    public void triggerAppear() {
        if (level == null || level.isClientSide) return;
        appearStart = level.getGameTime();
        foldFrom = 1F;
        foldStart = appearStart - 1000L;
        BlockState st = getBlockState();
        if (st.hasProperty(IPhoneDuoBlock.OPEN) && st.getValue(IPhoneDuoBlock.OPEN)) {
            level.setBlock(worldPosition, st.setValue(IPhoneDuoBlock.OPEN, Boolean.FALSE), 3);
        }
        setChanged();
        sync();
    }

    /** the block flips its OPEN property and calls this first so the fold animation starts from the current angle */
    public void beginFoldChange() {
        if (level == null) return;
        foldFrom = foldFraction(0F);
        foldStart = level.getGameTime();
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ ticking (server only)
    public static void tick(Level level, BlockPos pos, BlockState state, IPhoneDuoBlockEntity be) {
        if (be.appearStart < 0L || !(level instanceof ServerLevel)) return;
        be.serverTick((ServerLevel) level);
    }

    private void serverTick(ServerLevel sl) {
        int t = (int) (sl.getGameTime() - appearStart);
        if (t < 0) return;
        Vec3 base = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
        double y = base.y + heightAt(t) + 0.34;

        if (t == 1) {
            Fx.sound(sl, base, ModSounds.CARD_WHOOSH.get(), SoundSource.BLOCKS, 1.6F, 0.55F);
            Fx.send(sl, ParticleTypes.FLASH, base.x, base.y + 1.2, base.z, 1, 0, 0, 0, 0);
        }
        if (t == 10) {
            Fx.sound(sl, base, ModSounds.BOX_BURST.get(), SoundSource.BLOCKS, 1.0F, 1.5F);
        }
        if (t < T_LAND && t % 2 == 0) {
            // a tight spiral of light follows the phone down
            double a = t * 0.55;
            double r = 0.34 + 0.06 * Math.sin(t * 0.3);
            Fx.send(sl, ParticleTypes.END_ROD, base.x + Math.cos(a) * r, y + 0.1, base.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            Fx.send(sl, ParticleTypes.END_ROD, base.x + Math.cos(a + Math.PI) * r, y - 0.1, base.z + Math.sin(a + Math.PI) * r, 1, 0, 0, 0, 0);
            Fx.send(sl, Fx.dustFade(0xBFE3FF, 0xFFFFFF, 0.9F), base.x, y, base.z, 2, 0.28, 0.35, 0.28, 0);
        }
        if (t == T_LAND) {
            Fx.sound(sl, base, ModSounds.TERMINAL_INSERT.get(), SoundSource.BLOCKS, 1.2F, 0.75F);
            Fx.sound(sl, base, ModSounds.PHONE_FOLD.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            Fx.ring(sl, ParticleTypes.END_ROD, base.x, base.y + 0.12, base.z, 0.4, 36, 0.14);
            Fx.ring(sl, Fx.dust(0xFFFFFF, 1.3F), base.x, base.y + 0.15, base.z, 0.5, 28, 0.1);
            Fx.burst(sl, ParticleTypes.ELECTRIC_SPARK, new Vec3(base.x, base.y + 0.45, base.z), 40, 0.25, rnd);
            ModNetwork.shake(sl, base, 10.0, 0.25F, 6);
        }
        if (t == T_UNFOLD) {
            BlockState st = getBlockState();
            if (st.hasProperty(IPhoneDuoBlock.OPEN) && !st.getValue(IPhoneDuoBlock.OPEN)) {
                foldFrom = 1F;
                foldStart = appearStart + T_UNFOLD;
                sl.setBlock(worldPosition, st.setValue(IPhoneDuoBlock.OPEN, Boolean.TRUE), 3);
                setChanged();
                sync();
            }
            Fx.sound(sl, base, ModSounds.PHONE_UNFOLD.get(), SoundSource.BLOCKS, 1.3F, 1.0F);
        }
        if (t > T_UNFOLD && t < T_UNFOLD + FOLD_TICKS && t % 3 == 0) {
            Fx.send(sl, ParticleTypes.ELECTRIC_SPARK, base.x, y, base.z, 3, 0.35, 0.3, 0.05, 0.05);
        }
        if (t == T_BOOT) {
            Fx.sound(sl, base, ModSounds.PHONE_BOOT.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
        }
        if (t == T_BOOT_END - 6) {
            Fx.sound(sl, base, ModSounds.PHONE_UNLOCK.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            Fx.burst(sl, ParticleTypes.END_ROD, new Vec3(base.x, y, base.z), 24, 0.18, rnd);
        }
        if (t >= T_END) {
            setChanged();
            sync();
        }
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0, 0.0, 2.0).expandTowards(0, 4.2, 0);
    }

    // ------------------------------------------------------------------ persistence + sync
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Appear", appearStart);
        tag.putLong("FoldStart", foldStart);
        tag.putFloat("FoldFrom", foldFrom);
        tag.putInt("Color", colorway);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        appearStart = tag.contains("Appear") ? tag.getLong("Appear") : -1L;
        foldStart = tag.contains("FoldStart") ? tag.getLong("FoldStart") : -100000L;
        foldFrom = tag.contains("FoldFrom") ? tag.getFloat("FoldFrom") : 1F;
        colorway = tag.getInt("Color") == 1 ? 1 : 0;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
