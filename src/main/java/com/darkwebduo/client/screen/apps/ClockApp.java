package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.render.PhoneInfo;
import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneData;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import com.darkwebduo.registry.ModSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** World clock (analog + digital + the in-game day), stopwatch with laps, and a countdown timer. */
public class ClockApp extends PhoneApp {
    private static final int ORANGE = 0xFFFF9F0A;
    private static final int GREEN = 0xFF30D158;
    private static final int RED = 0xFFFF453A;
    private static final int GRAY = 0xFF3A3A3C;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);

    private int tab;
    private boolean swRunning;
    private long swStart;
    private long swAccum;
    private final List<Long> laps = new ArrayList<Long>();
    private int timerSet = 180;
    private boolean timerRunning;
    private long timerEnd;
    private long timerPausedLeft = -1L;
    private int ringFlash;

    public ClockApp(PhoneHost host) {
        super(host);
    }

    @Override
    public String id() {
        return "clock";
    }

    // ------------------------------------------------------------------ helpers
    private static String two(long v) {
        return v < 10 ? "0" + v : Long.toString(v);
    }

    private String fmt(LocalTime t, boolean seconds) {
        if (PhoneData.twentyFour) {
            return two(t.getHour()) + ":" + two(t.getMinute()) + (seconds ? ":" + two(t.getSecond()) : "");
        }
        int h = t.getHour() % 12;
        if (h == 0) h = 12;
        return h + ":" + two(t.getMinute()) + (seconds ? ":" + two(t.getSecond()) : "") + (t.getHour() < 12 ? " AM" : " PM");
    }

    private static String sw(long ms) {
        long cs = (ms / 10L) % 100L;
        long s = (ms / 1000L) % 60L;
        long m = ms / 60000L;
        return two(m) + ":" + two(s) + "." + two(cs);
    }

    private long swNow() {
        return swRunning ? swAccum + (System.currentTimeMillis() - swStart) : swAccum;
    }

    private long timerLeftMs() {
        if (timerRunning) return Math.max(0L, timerEnd - System.currentTimeMillis());
        if (timerPausedLeft >= 0L) return timerPausedLeft;
        return timerSet * 1000L;
    }

    private void circleButton(GuiGraphics g, Font f, float cx, float cy, float r, String label, int bg, int fg, int mx, int my) {
        boolean hover = (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= r * r;
        PhoneUi.disc(g, cx, cy, r + 2.5F, PhoneUi.alpha(bg, 0.35F));
        PhoneUi.disc(g, cx, cy, r, hover ? PhoneUi.mix(bg, 0xFFFFFFFF, 0.14F) : bg);
        PhoneUi.textC(g, f, label, cx, cy - 4F, 1.0F, fg);
    }

    private static boolean inCircle(double x, double y, float cx, float cy, float r) {
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Font f = host.font();
        g.fill(0, 0, W, H, 0xFF000000);
        String[] titles = {"World Clock", "Stopwatch", "Timer"};
        PhoneUi.text(g, f, titles[tab], 18, 30, 1.7F, 0xFFFFFFFF);
        if (tab == 0) renderWorld(g, f);
        else if (tab == 1) renderStopwatch(g, f, mx, my);
        else renderTimer(g, f, mx, my);

        // tab bar
        g.fill(0, 316, W, H, 0xFF121214);
        g.fill(0, 316, W, 317, 0xFF2A2A2D);
        String[] names = {"World", "Stopwatch", "Timer"};
        for (int i = 0; i < 3; i++) {
            float cx = 40F + i * 80F;
            int col = i == tab ? ORANGE : 0xFF8E8E93;
            PhoneUi.textC(g, f, names[i], cx, 326, 0.95F, col);
            PhoneUi.rr(g, cx - 14F, 338F, cx + 14F, 340F, 1F, i == tab ? ORANGE : 0x00000000);
        }
    }

    private void renderWorld(GuiGraphics g, Font f) {
        LocalTime now = LocalTime.now();
        float cx = 120F, cy = 124F, R = 76F;
        PhoneUi.disc(g, cx, cy, R + 3F, 0xFF2C2C2E);
        PhoneUi.disc(g, cx, cy, R, 0xFF151517);
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6.0 - 90.0);
            boolean major = i % 5 == 0;
            float r0 = R - (major ? 9F : 5F);
            PhoneUi.line(g, cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0,
                    cx + (float) Math.cos(a) * (R - 2F), cy + (float) Math.sin(a) * (R - 2F), major ? 1.0F : 0.5F, major ? 0xFFFFFFFF : 0xFF6C6C70);
        }
        String[] nums = {"12", "3", "6", "9"};
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90.0 - 90.0);
            PhoneUi.textC(g, f, nums[i], cx + (float) Math.cos(a) * (R - 19F), cy + (float) Math.sin(a) * (R - 19F) - 4F, 1.0F, 0xFFFFFFFF);
        }
        double sec = now.getSecond() + now.getNano() / 1.0E9;
        double min = now.getMinute() + sec / 60.0;
        double hour = (now.getHour() % 12) + min / 60.0;
        double ah = Math.toRadians(hour * 30.0 - 90.0);
        double am = Math.toRadians(min * 6.0 - 90.0);
        double as = Math.toRadians(sec * 6.0 - 90.0);
        PhoneUi.line(g, cx, cy, cx + (float) Math.cos(ah) * 42F, cy + (float) Math.sin(ah) * 42F, 2.2F, 0xFFFFFFFF);
        PhoneUi.line(g, cx, cy, cx + (float) Math.cos(am) * 62F, cy + (float) Math.sin(am) * 62F, 1.5F, 0xFFFFFFFF);
        PhoneUi.line(g, cx - (float) Math.cos(as) * 12F, cy - (float) Math.sin(as) * 12F,
                cx + (float) Math.cos(as) * 68F, cy + (float) Math.sin(as) * 68F, 0.6F, ORANGE);
        PhoneUi.disc(g, cx, cy, 3.6F, ORANGE);
        PhoneUi.disc(g, cx, cy, 1.4F, 0xFF000000);

        PhoneUi.textC(g, f, fmt(now, true), cx, 218, 2.2F, 0xFFFFFFFF);
        PhoneUi.textC(g, f, LocalDate.now().format(DATE), cx, 242, 0.95F, 0xFF8E8E93);

        // in-game day card
        PhoneUi.rr(g, 14, 262, 226, 306, 12, 0xFF1C1C1E);
        Level level = host.mc().level;
        if (level != null) {
            long dt = level.getDayTime();
            long ticks = dt % 24000L;
            long hh = (ticks / 1000L + 6L) % 24L;
            long mm = (ticks % 1000L) * 60L / 1000L;
            boolean day = ticks < 12000L;
            PhoneUi.disc(g, 36F, 284F, 9F, day ? 0xFFFFD60A : 0xFFD1D1D6);
            if (!day) PhoneUi.disc(g, 39F, 281F, 7.5F, 0xFF1C1C1E);
            PhoneUi.text(g, f, "Minecraft", 54, 271, 0.85F, 0xFF8E8E93);
            PhoneUi.text(g, f, "Day " + (dt / 24000L + 1L), 54, 284, 1.1F, 0xFFFFFFFF);
            PhoneUi.textR(g, f, two(hh) + ":" + two(mm), 214, 275, 1.9F, 0xFFFFFFFF);
        }
    }

    private void renderStopwatch(GuiGraphics g, Font f, int mx, int my) {
        PhoneUi.textC(g, f, sw(swNow()), 120, 74, 3.0F, 0xFFFFFFFF);
        circleButton(g, f, 62F, 150F, 30F, swRunning ? "Lap" : "Reset", GRAY, 0xFFFFFFFF, mx, my);
        circleButton(g, f, 178F, 150F, 30F, swRunning ? "Stop" : "Start", swRunning ? 0xFF5A1E1B : 0xFF123A1C, swRunning ? RED : GREEN, mx, my);
        g.fill(18, 196, 222, 197, 0xFF2C2C2E);
        int n = laps.size();
        for (int i = 0; i < Math.min(5, n); i++) {
            int idx = n - 1 - i;
            long total = laps.get(idx);
            long prev = idx > 0 ? laps.get(idx - 1) : 0L;
            float y = 204F + i * 22F;
            PhoneUi.text(g, f, "Lap " + (idx + 1), 18, y, 1.0F, 0xFFFFFFFF);
            PhoneUi.textR(g, f, sw(total - prev), 222, y, 1.0F, 0xFFFFFFFF);
            g.fill(18, (int) y + 14, 222, (int) y + 15, 0xFF1F1F21);
        }
        if (n == 0) PhoneUi.textC(g, f, "Press Start, then Lap", 120, 214, 0.95F, 0xFF636366);
    }

    private void renderTimer(GuiGraphics g, Font f, int mx, int my) {
        long left = timerLeftMs();
        long total = timerSet * 1000L;
        float frac = timerRunning || timerPausedLeft >= 0L ? Math.max(0F, Math.min(1F, left / (float) Math.max(1L, total))) : 1F;
        float cx = 120F, cy = 118F, R = 76F;
        int seg = 120;
        for (int i = 0; i < seg; i++) {
            double a = Math.toRadians(i * 360.0 / seg - 90.0);
            boolean on = i / (float) seg < frac;
            int col = on ? (ringFlash > 0 && (ringFlash / 3) % 2 == 0 ? 0xFFFFFFFF : ORANGE) : 0xFF2C2C2E;
            PhoneUi.line(g, cx + (float) Math.cos(a) * (R - 6F), cy + (float) Math.sin(a) * (R - 6F),
                    cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, 1.0F, col);
        }
        long secs = (left + 999L) / 1000L;
        String t = secs >= 3600L ? (secs / 3600L) + ":" + two((secs / 60L) % 60L) + ":" + two(secs % 60L) : two(secs / 60L) + ":" + two(secs % 60L);
        PhoneUi.textC(g, f, t, cx, cy - 12F, 2.9F, 0xFFFFFFFF);
        if (!timerRunning && timerPausedLeft < 0L) {
            circleButton(g, f, 98F, 214F, 14F, "-", GRAY, 0xFFFFFFFF, mx, my);
            circleButton(g, f, 142F, 214F, 14F, "+", GRAY, 0xFFFFFFFF, mx, my);
            PhoneUi.textC(g, f, "1 min", 120F, 211F, 0.8F, 0xFF8E8E93);
        } else {
            PhoneUi.textC(g, f, "Ends " + fmt(LocalTime.now().plusSeconds(secs), false), cx, 208, 0.95F, 0xFF8E8E93);
        }
        circleButton(g, f, 62F, 264F, 30F, "Cancel", GRAY, 0xFFFFFFFF, mx, my);
        circleButton(g, f, 178F, 264F, 30F, timerRunning ? "Pause" : "Start", timerRunning ? 0xFF4A3410 : 0xFF123A1C, timerRunning ? ORANGE : GREEN, mx, my);
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        if (y >= 316D) {
            int t = (int) Math.max(0, Math.min(2, Math.floor(x / 80.0)));
            if (t != tab) {
                tab = t;
                host.tap();
            }
            return true;
        }
        if (tab == 1) {
            if (inCircle(x, y, 62F, 150F, 30F)) {
                host.tap();
                if (swRunning) laps.add(swNow());
                else {
                    swAccum = 0L;
                    laps.clear();
                }
                return true;
            }
            if (inCircle(x, y, 178F, 150F, 30F)) {
                host.tap();
                if (swRunning) {
                    swAccum = swNow();
                    swRunning = false;
                } else {
                    swStart = System.currentTimeMillis();
                    swRunning = true;
                }
                return true;
            }
        } else if (tab == 2) {
            boolean idle = !timerRunning && timerPausedLeft < 0L;
            if (idle && inCircle(x, y, 98F, 214F, 14F)) {
                timerSet = Math.max(60, timerSet - 60);
                host.tap();
                return true;
            }
            if (idle && inCircle(x, y, 142F, 214F, 14F)) {
                timerSet = Math.min(5940, timerSet + 60);
                host.tap();
                return true;
            }
            if (inCircle(x, y, 62F, 264F, 30F)) {
                timerRunning = false;
                timerPausedLeft = -1L;
                host.tap();
                return true;
            }
            if (inCircle(x, y, 178F, 264F, 30F)) {
                host.tap();
                if (timerRunning) {
                    timerPausedLeft = timerLeftMs();
                    timerRunning = false;
                } else {
                    long left = timerPausedLeft >= 0L ? timerPausedLeft : timerSet * 1000L;
                    timerEnd = System.currentTimeMillis() + left;
                    timerPausedLeft = -1L;
                    timerRunning = true;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        if (ringFlash > 0) ringFlash--;
        if (timerRunning && System.currentTimeMillis() >= timerEnd) {
            timerRunning = false;
            timerPausedLeft = -1L;
            ringFlash = 60;
            host.sound(ModSounds.PHONE_NOTIFY.get(), 1.0F);
        }
    }
}
