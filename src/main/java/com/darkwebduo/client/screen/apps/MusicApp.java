package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneData;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import com.darkwebduo.client.screen.TrackInfo;
import com.darkwebduo.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Plays the two boss themes ("Cipher Protocol" and "Omega Ascension") through the music channel at the phone's volume.
 * Playback state is static so the music keeps going when the phone is closed and reopened.
 */
public class MusicApp extends PhoneApp {
    private static final int BLUE = 0xFF0A84FF;

    /** a non-positional music instance whose volume follows the phone's volume slider */
    private static final class TrackSound extends AbstractSoundInstance implements TickableSoundInstance {
        private boolean stopped;

        TrackSound(SoundEvent event) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.looping = false;
            this.volume = Math.max(0.0001F, PhoneData.volume);
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void tick() {
            this.volume = Math.max(0.0001F, PhoneData.volume);
        }

        void halt() {
            stopped = true;
        }
    }

    private static TrackSound sound;
    private static int track;
    private static boolean playing;
    private static long startMs;

    private float anim;
    private int ticks;

    public MusicApp(PhoneHost host) {
        super(host);
        anim = playing ? 1F : 0F;
    }

    @Override
    public String id() {
        return "music";
    }

    // ------------------------------------------------------------------ playback
    private static SoundEvent event(int t) {
        return t == 0 ? ModSounds.BOSS1_THEME.get() : ModSounds.BOSS2_THEME.get();
    }

    private void play(int t) {
        stop();
        track = ((t % 2) + 2) % 2;
        sound = new TrackSound(event(track));
        host.mc().getSoundManager().play(sound);
        startMs = System.currentTimeMillis();
        playing = true;
    }

    private void stop() {
        if (sound != null) {
            sound.halt();
            host.mc().getSoundManager().stop(sound);
            sound = null;
        }
        playing = false;
    }

    private static float elapsed() {
        return playing ? (System.currentTimeMillis() - startMs) / 1000F : 0F;
    }

    @Override
    public void tick() {
        ticks++;
        anim += ((playing ? 1F : 0F) - anim) * 0.18F;
        if (playing && sound != null && System.currentTimeMillis() - startMs > 3000L) {
            Minecraft mc = host.mc();
            if (!mc.getSoundManager().isActive(sound)) {
                // track ended (or the world was left): go on to the next one while we are still in a world
                if (mc.level != null) play(track + 1);
                else stop();
            }
        }
    }

    // ------------------------------------------------------------------ render
    private static String mmss(float secs) {
        int s = Math.max(0, (int) secs);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Font f = host.font();
        String album = "album_" + (track + 1);
        g.fill(0, 0, W, H, 0xFF08080C);
        PhoneUi.image(g, PhoneUi.gui(album), -40F, -10F, 320F, 380F, 256, 256);
        g.fill(0, 0, W, H, 0xE608080C);
        g.fillGradient(0, 200, W, H, 0x00000000, 0xFF000000);

        PhoneUi.textC(g, f, "Now Playing", 120F, 34F, 0.82F, 0xB4FFFFFF);

        float size = 150F + 22F * anim;
        float ax = 120F - size * 0.5F;
        float ay = 56F + (172F - size) * 0.5F;
        PhoneUi.rr(g, ax + 2F, ay + 8F, ax + size - 2F, ay + size + 8F, 14F, 0x66000000);
        PhoneUi.image(g, PhoneUi.gui(album), ax, ay, size, size, 256, 256);
        PhoneUi.cornerMask(g, ax, ay, ax + size, ay + size, 14F, 0xFF08080C);
        PhoneUi.rrStroke(g, ax, ay, ax + size, ay + size, 14F, 0.6F, 0x44FFFFFF);

        PhoneUi.textC(g, f, I18n.get("phone.darkweb_duo.track." + (track + 1)), 120F, 236F, 1.35F, 0xFFFFFFFF);
        PhoneUi.textC(g, f, "Darkweb Duo Original Score", 120F, 252F, 0.82F, 0xFFB0B0B8);

        // visualiser
        int bars = 26;
        for (int i = 0; i < bars; i++) {
            float x = 22F + i * 7.9F;
            float e = 0.22F + 0.78F * Math.abs((float) Math.sin(ticks * 0.11F * (1F + i * 0.07F) + i * 0.9F))
                    * Math.abs((float) Math.sin(ticks * 0.041F + i * 1.7F));
            float hgt = (3F + 17F * e) * anim + 2F;
            PhoneUi.rr(g, x, 280F - hgt, x + 4.6F, 280F, 2.3F, PhoneUi.alpha(0xFFFFFFFF, 0.35F + 0.5F * e * anim));
        }

        // progress
        float total = TrackInfo.SECONDS[track];
        float el = playing ? elapsed() % total : 0F;
        PhoneUi.rr(g, 24F, 290F, 216F, 294F, 2F, 0x55FFFFFF);
        PhoneUi.rr(g, 24F, 290F, 24F + 192F * (el / total), 294F, 2F, 0xFFFFFFFF);
        PhoneUi.text(g, f, mmss(el), 24F, 298F, 0.72F, 0xFFB0B0B8);
        PhoneUi.textR(g, f, "-" + mmss(total - el), 216F, 298F, 0.72F, 0xFFB0B0B8);

        // transport
        float cy = 322F;
        boolean hp = (mx - 120) * (mx - 120) + (my - cy) * (my - cy) <= 21F * 21F;
        PhoneUi.disc(g, 120F, cy, hp ? 21F : 20F, 0xFFFFFFFF);
        if (playing) {
            PhoneUi.rr(g, 112F, cy - 8F, 117.5F, cy + 8F, 1.5F, 0xFF08080C);
            PhoneUi.rr(g, 122.5F, cy - 8F, 128F, cy + 8F, 1.5F, 0xFF08080C);
        } else {
            PhoneUi.tri(g, 113F, cy - 9F, 113F, cy + 9F, 128F, cy, 0xFF08080C);
        }
        // previous / next
        PhoneUi.tri(g, 76F, cy, 88F, cy - 8F, 88F, cy + 8F, 0xFFFFFFFF);
        PhoneUi.tri(g, 64F, cy, 76F, cy - 8F, 76F, cy + 8F, 0xFFFFFFFF);
        PhoneUi.tri(g, 164F, cy, 152F, cy - 8F, 152F, cy + 8F, 0xFFFFFFFF);
        PhoneUi.tri(g, 176F, cy, 164F, cy - 8F, 164F, cy + 8F, 0xFFFFFFFF);
        PhoneUi.textR(g, f, (track + 1) + " / 2", 226F, 342F, 0.66F, 0x88FFFFFF);
        if (playing) {
            PhoneUi.disc(g, 20F, 344F, 2F, BLUE);
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        float cy = 322F;
        if ((x - 120D) * (x - 120D) + (y - cy) * (y - cy) <= 22D * 22D) {
            host.tap();
            if (playing) stop();
            else play(track);
            return true;
        }
        if (x >= 58D && x <= 92D && Math.abs(y - cy) <= 16D) {
            host.tap();
            play(track - 1);
            return true;
        }
        if (x >= 148D && x <= 182D && Math.abs(y - cy) <= 16D) {
            host.tap();
            play(track + 1);
            return true;
        }
        return false;
    }
}
