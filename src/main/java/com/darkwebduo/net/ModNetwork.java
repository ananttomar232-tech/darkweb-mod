package com.darkwebduo.net;

import com.darkwebduo.DarkWebDuoMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DarkWebDuoMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, CutscenePacket.class, CutscenePacket::encode, CutscenePacket::decode, CutscenePacket::handle);
        CHANNEL.registerMessage(id++, StopCutscenePacket.class, StopCutscenePacket::encode, StopCutscenePacket::decode, StopCutscenePacket::handle);
        CHANNEL.registerMessage(id++, ShakePacket.class, ShakePacket::encode, ShakePacket::decode, ShakePacket::handle);
        CHANNEL.registerMessage(id++, FlashPacket.class, FlashPacket::encode, FlashPacket::decode, FlashPacket::handle);
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** Camera shake for every player within radius; intensity falls off linearly with distance. */
    public static void shake(ServerLevel level, Vec3 pos, double radius, float intensity, int ticks) {
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(pos));
            if (d > radius) continue;
            float k = (float) (1.0 - d / radius * 0.75);
            sendTo(p, new ShakePacket(intensity * k, ticks));
        }
    }

    /** Colour flash for every player within radius. */
    public static void flash(ServerLevel level, Vec3 pos, double radius, int argb, int ticks) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos) <= radius * radius) sendTo(p, new FlashPacket(argb, ticks));
        }
    }
}
