package com.darkwebduo.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Invisible stand-in used as the client's camera entity during cutscenes. It is created on the client only,
 * never added to the world, never saved and never sent.
 */
public class CutsceneCameraEntity extends Entity {
    public CutsceneCameraEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /** Same as the player's standing eye height so the camera's eye-height smoothing never drifts. */
    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 1.62F;
    }
}
