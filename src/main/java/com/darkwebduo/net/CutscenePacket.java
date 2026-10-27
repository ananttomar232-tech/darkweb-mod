package com.darkwebduo.net;

import com.darkwebduo.client.cutscene.CutsceneClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: start a cinematic cutscene. */
public class CutscenePacket {
    public final int id;
    public final int arg;
    public final int bossId;
    public final double x, y, z;

    public CutscenePacket(int id, int arg, int bossId, double x, double y, double z) {
        this.id = id; this.arg = arg; this.bossId = bossId; this.x = x; this.y = y; this.z = z;
    }

    public static void encode(CutscenePacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.id); b.writeVarInt(m.arg); b.writeInt(m.bossId);
        b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z);
    }

    public static CutscenePacket decode(FriendlyByteBuf b) {
        return new CutscenePacket(b.readVarInt(), b.readVarInt(), b.readInt(), b.readDouble(), b.readDouble(), b.readDouble());
    }

    public static void handle(CutscenePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> CutsceneClient.start(m.id, m.arg, m.bossId, m.x, m.y, m.z)));
        ctx.get().setPacketHandled(true);
    }
}
