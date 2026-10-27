package com.darkwebduo.block.entity;

import com.darkwebduo.block.DeliveryBoxBlock;
import com.darkwebduo.item.KeycardItem;
import com.darkwebduo.net.ModNetwork;
import com.darkwebduo.registry.ModBlockEntities;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.CardFlight;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;

/**
 * Runs the whole opening cinematic. Time is measured against the game clock (startTime) so the client renderer and the
 * server agree without any extra packets. The server hands the keycard over at CardFlight.T_DELIVER.
 */
public class DeliveryBoxBlockEntity extends BlockEntity {
    private boolean opening;
    private boolean delivered;
    private long startTime;
    @Nullable
    private UUID opener;
    private final Random rnd = new Random();

    public DeliveryBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DELIVERY_BOX.get(), pos, state);
    }

    public int getTier() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof DeliveryBoxBlock ? ((DeliveryBoxBlock) s.getBlock()).getTier() : 1;
    }

    public boolean isOpening() {
        return opening;
    }

    public boolean isDelivered() {
        return delivered;
    }

    @Nullable
    public UUID getOpener() {
        return opener;
    }

    /** Animation time in ticks (fractional). Large value once the sequence is finished. */
    public float animTime(float partialTick) {
        if (!opening || level == null) return 10000F;
        return Math.max(0F, (float) (level.getGameTime() - startTime) + partialTick);
    }

    // ------------------------------------------------------------------ server

    public void startOpening(Player player) {
        if (level == null || level.isClientSide || opening || delivered) return;
        opening = true;
        delivered = false;
        startTime = level.getGameTime();
        opener = player.getUUID();
        setChanged();
        sync();
        if (level instanceof ServerLevel) {
            Vec3 c = center();
            Fx.sound((ServerLevel) level, c, ModSounds.BOX_CHARGE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private void sync() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private Vec3 center() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
    }

    private int primary() {
        return getTier() == 1 ? 0x19E6FF : 0xFFC43A;
    }

    private int secondary() {
        return getTier() == 1 ? 0xFF2BD6 : 0xFF2A3C;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DeliveryBoxBlockEntity be) {
        if (!be.opening) return;
        if (!(level instanceof ServerLevel)) return;
        be.serverTick((ServerLevel) level);
    }

    private void serverTick(ServerLevel sl) {
        int t = (int) (sl.getGameTime() - startTime);
        Vec3 base = center();
        Vec3 mid = base.add(0, 0.55, 0);

        // ---- charge: energy spirals into the box, sparks leak out of the seams
        if (t < CardFlight.T_CHARGE_END) {
            double k = t / (double) CardFlight.T_CHARGE_END;
            double r = 1.9 - 1.5 * k;
            for (int arm = 0; arm < 3; arm++) {
                double a = t * 0.42 + arm * (Math.PI * 2 / 3);
                double y = base.y + 0.2 + k * 0.7 + Math.sin(t * 0.3 + arm) * 0.15;
                Fx.send(sl, Fx.dustFade(primary(), secondary(), 1.1F), base.x + Math.cos(a) * r, y, base.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (t % 2 == 0) {
                Fx.send(sl, ParticleTypes.ELECTRIC_SPARK, base.x, base.y + 0.7, base.z, 3, 0.3, 0.25, 0.3, 0.25);
            }
            if (t == 12) {
                Fx.sound(sl, base, ModSounds.BOX_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

        // ---- lid pops, light bursts out
        if (t == CardFlight.T_LID_START) {
            Fx.ring(sl, ParticleTypes.CLOUD, base.x, base.y + 0.7, base.z, 0.4, 24, 0.12);
        }
        if (t == CardFlight.T_BURST) {
            Fx.sound(sl, base, ModSounds.BOX_BURST.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
            Fx.send(sl, ParticleTypes.FLASH, base.x, base.y + 0.9, base.z, 1, 0, 0, 0, 0);
            Fx.burst(sl, ParticleTypes.END_ROD, mid.add(0, 0.4, 0), 70, 0.35, rnd);
            Fx.burst(sl, Fx.dust(primary(), 1.6F), mid.add(0, 0.4, 0), 40, 0.25, rnd);
            Fx.ring(sl, ParticleTypes.END_ROD, base.x, base.y + 0.6, base.z, 0.5, 36, 0.22);
            Fx.ring(sl, Fx.dust(secondary(), 1.4F), base.x, base.y + 0.9, base.z, 0.6, 28, 0.18);
            ModNetwork.shake(sl, base, 12.0, 0.35F, 8);
        }

        // ---- light column + ambient glitter while the beam is up
        if (t >= CardFlight.T_BURST && t < CardFlight.T_VISUAL_END - 10) {
            if (t % 2 == 0) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double rr = 0.15 + rnd.nextDouble() * 0.35;
                Fx.vel(sl, ParticleTypes.END_ROD, base.x + Math.cos(a) * rr, base.y + 0.8, base.z + Math.sin(a) * rr,
                        0, 0.12 + rnd.nextDouble() * 0.1, 0);
            }
            Fx.helix(sl, Fx.dustFade(primary(), secondary(), 0.9F), base.x, base.y + 0.8, base.y + 3.2, base.z, 0.55, 12, 1.5, t * 0.3);
        }

        // ---- card trail
        if (CardFlight.cardVisible(t)) {
            Vec3 target = targetFor(sl);
            Vec3 p = CardFlight.cardPos(t, base, target);
            Fx.send(sl, ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.06, 0.06, 0.06, 0.01);
            Fx.send(sl, Fx.dustFade(primary(), secondary(), 1.0F), p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0);
            if (t >= CardFlight.T_RISE_END) {
                Fx.send(sl, ParticleTypes.ENCHANT, p.x, p.y, p.z, 3, 0.2, 0.2, 0.2, 0.4);
            }
        }
        if (t == CardFlight.T_RISE_END) {
            Fx.sound(sl, base.add(0, 2, 0), ModSounds.CARD_WHOOSH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        // ---- hand-over
        if (t >= CardFlight.T_DELIVER && !delivered) {
            deliver(sl);
        }

        // ---- end of the show
        if (t >= CardFlight.T_VISUAL_END) {
            opening = false;
            setChanged();
            sync();
        }
    }

    @Nullable
    private ServerPlayer opener(ServerLevel sl) {
        if (opener == null || sl.getServer() == null) return null;
        ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(opener);
        if (sp == null || sp.level() != sl || sp.distanceToSqr(center()) > 64.0 * 64.0) return null;
        return sp;
    }

    private Vec3 targetFor(ServerLevel sl) {
        ServerPlayer sp = opener(sl);
        if (sp == null) return center().add(0, 2.2, 0);
        return sp.position().add(0, sp.getBbHeight() * 0.62, 0);
    }

    private void deliver(ServerLevel sl) {
        delivered = true;
        ItemStack card = new ItemStack(KeycardItem.itemFor(getTier()));
        ServerPlayer sp = opener(sl);
        if (sp != null) {
            Vec3 pp = sp.position().add(0, sp.getBbHeight() * 0.62, 0);
            if (!sp.getInventory().add(card)) {
                sp.drop(card, false);
            }
            Fx.sound(sl, pp, ModSounds.KEYCARD_PICKUP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            Fx.burst(sl, ParticleTypes.END_ROD, pp, 30, 0.22, rnd);
            Fx.burst(sl, Fx.dust(primary(), 1.2F), pp, 24, 0.2, rnd);
            Fx.ring(sl, ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 0.1, sp.getZ(), 0.9, 20, 0.1);
            sp.displayClientMessage(Component.translatable(getTier() == 1
                    ? "message.darkweb_duo.got_dark_keycard" : "message.darkweb_duo.got_omega_keycard"), true);
        } else {
            Block.popResource(sl, worldPosition.above(), card);
        }
        setChanged();
        sync();
    }

    // ------------------------------------------------------------------ persistence + sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Opening", opening);
        tag.putBoolean("Delivered", delivered);
        tag.putLong("Start", startTime);
        if (opener != null) tag.putUUID("Opener", opener);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        opening = tag.getBoolean("Opening");
        delivered = tag.getBoolean("Delivered");
        startTime = tag.getLong("Start");
        opener = tag.hasUUID("Opener") ? tag.getUUID("Opener") : null;
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
        return new AABB(worldPosition).inflate(4.0, 6.0, 4.0);
    }
}
