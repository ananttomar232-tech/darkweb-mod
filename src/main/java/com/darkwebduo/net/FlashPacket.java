package com.darkwebduo.net;

import com.darkwebduo.client.cutscene.CameraFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: full-screen colour flash that fades out. argb = 0xAARRGGBB (alpha = peak opacity). */
public class FlashPacket {
    public final int argb;
    public final int ticks;

    public FlashPacket(int argb, int ticks) { this.argb = argb; this.ticks = ticks; }

    public static void encode(FlashPacket m, FriendlyByteBuf b) { b.writeInt(m.argb); b.writeVarInt(m.ticks); }

    public static FlashPacket decode(FriendlyByteBuf b) { return new FlashPacket(b.readInt(), b.readVarInt()); }

    public static void handle(FlashPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CameraFx.flash(m.argb, m.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
