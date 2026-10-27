package com.darkwebduo.client;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.entity.BossBase;
import com.darkwebduo.entity.CipherWardenEntity;
import com.darkwebduo.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Plays each boss's theme (looping, streamed, ignores distance) while the fight is on, dips it during cutscenes,
 * fades it out when the boss dies or the player leaves, and keeps vanilla music quiet meanwhile.
 */
@Mod.EventBusSubscriber(modid = DarkWebDuoMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BossMusic {
    private static ThemeSound current;
    private static int scanTimer;
    private static SoundEvent wanted;
    private static boolean wantDip;

    private BossMusic() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (current != null) {
                current.fadeOut();
                current = null;
            }
            wanted = null;
            return;
        }
        if (scanTimer-- <= 0) {
            scanTimer = 8;
            scan(mc);
        }
        if (wanted != null) {
            if (current == null || current.isStopped() || current.event != wanted) {
                if (current != null) current.fadeOut();
                current = new ThemeSound(wanted);
                mc.getSoundManager().play(current);
            }
            current.setDip(wantDip);
            mc.getMusicManager().stopPlaying();
        } else if (current != null) {
            current.fadeOut();
            current = null;
        }
    }

    private static void scan(Minecraft mc) {
        wanted = null;
        wantDip = false;
        List<BossBase> bosses = mc.level.getEntitiesOfClass(BossBase.class, mc.player.getBoundingBox().inflate(110.0));
        double best = Double.MAX_VALUE;
        for (BossBase b : bosses) {
            if (!b.isAlive()) continue;
            int mode = b.getMode();
            boolean fighting = mode == BossBase.MODE_FIGHT;
            boolean phaseCut = false;
            // cutscene ids: 2 = Cipher phase, 5 = Omega phase (theme keeps playing quietly during those)
            int cid = b.getCutsceneId();
            if (mode == BossBase.MODE_CUTSCENE && (cid == 2 || cid == 5)) phaseCut = true;
            if (!fighting && !phaseCut) continue;
            double d = b.distanceToSqr(mc.player);
            if (d < best) {
                best = d;
                wanted = b instanceof CipherWardenEntity ? ModSounds.BOSS1_THEME.get() : ModSounds.BOSS2_THEME.get();
                wantDip = phaseCut;
            }
        }
    }

    /** looping, non-positional music track that can fade and duck. */
    private static final class ThemeSound extends AbstractTickableSoundInstance {
        final SoundEvent event;
        private float level;
        private boolean fading;
        private boolean dip;

        ThemeSound(SoundEvent event) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.event = event;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
        }

        void fadeOut() {
            fading = true;
        }

        void setDip(boolean dip) {
            this.dip = dip;
        }

        @Override
        public void tick() {
            float target = fading ? 0F : (dip ? 0.45F : 1.0F);
            if (level < target) level = Math.min(target, level + 0.02F);
            else if (level > target) level = Math.max(target, level - (fading ? 0.03F : 0.04F));
            this.volume = Math.max(0.0001F, level);
            if (fading && level <= 0.001F) this.stop();
        }
    }
}
