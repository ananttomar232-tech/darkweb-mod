package com.darkwebduo.cutscene;

import com.darkwebduo.net.CutscenePacket;
import com.darkwebduo.net.ModNetwork;
import com.darkwebduo.net.StopCutscenePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side entry point for cutscenes. See {@link CutsceneIds} for the beat timelines. */
public final class CutsceneServer {
    private CutsceneServer() {}

    /**
     * Starts a cutscene for every non-spectator player within {@code radius} of {@code origin}.
     * Players get the camera takeover (client side) and total damage immunity for the cutscene's length.
     *
     * @param origin     focus point (boss feet for Cipher cutscenes, terminal block for Omega cutscenes)
     * @param bossId     entity id of the boss, or -1 if it does not exist yet
     * @param arg        phase number for the *_PHASE cutscenes, 0 otherwise
     * @return the players that are watching
     */
    public static List<ServerPlayer> start(ServerLevel level, Vec3 origin, double radius, int cutsceneId, int arg, int bossId) {
        List<ServerPlayer> watchers = new ArrayList<>();
        int dur = CutsceneIds.duration(cutsceneId);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (p.distanceToSqr(origin) > radius * radius) continue;
            watchers.add(p);
            ModNetwork.sendTo(p, new CutscenePacket(cutsceneId, arg, bossId, origin.x, origin.y, origin.z));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur + 40, 4, false, false, false));
        }
        return watchers;
    }

    public static void stopAll(ServerLevel level, Vec3 origin, double radius) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(origin) <= radius * radius) ModNetwork.sendTo(p, new StopCutscenePacket());
        }
    }
}
