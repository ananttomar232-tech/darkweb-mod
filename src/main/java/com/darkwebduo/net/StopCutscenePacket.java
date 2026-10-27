package com.darkwebduo.net;

import com.darkwebduo.client.cutscene.CutsceneClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: abort any running cutscene immediately. */
public class StopCutscenePacket {
    public StopCutscenePacket() {}

    public static void encode(StopCutscenePacket m, FriendlyByteBuf b) {}

    public static StopCutscenePacket decode(FriendlyByteBuf b) { return new StopCutscenePacket(); }

    public static void handle(StopCutscenePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> CutsceneClient::stop));
        ctx.get().setPacketHandled(true);
    }
}
