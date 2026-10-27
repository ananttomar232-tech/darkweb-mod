package com.darkwebduo.net;

import com.darkwebduo.client.cutscene.CameraFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: camera shake (intensity ~0.2 light, 1 strong, 3 earth-shattering). */
public class ShakePacket {
    public final float intensity;
    public final int ticks;

    public ShakePacket(float intensity, int ticks) { this.intensity = intensity; this.ticks = ticks; }

    public static void encode(ShakePacket m, FriendlyByteBuf b) { b.writeFloat(m.intensity); b.writeVarInt(m.ticks); }

    public static ShakePacket decode(FriendlyByteBuf b) { return new ShakePacket(b.readFloat(), b.readVarInt()); }

    public static void handle(ShakePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CameraFx.shake(m.intensity, m.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
