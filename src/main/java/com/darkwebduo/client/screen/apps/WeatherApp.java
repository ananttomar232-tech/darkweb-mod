package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneData;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Random;

/** Live weather for the player's position: sky, sun/moon, rain/snow, temperature from the biome, hourly strip, moon phase. */
public class WeatherApp extends PhoneApp {
    private static final String[] MOON = {"Full Moon", "Waning Gibbous", "Third Quarter", "Waning Crescent",
            "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"};
    private static final float[] LIT = {1F, 0.75F, 0.5F, 0.25F, 0F, 0.25F, 0.5F, 0.75F};

    private final float[] starX = new float[46];
    private final float[] starY = new float[46];
    private final float[] starP = new float[46];
    private int tick;

    public WeatherApp(PhoneHost host) {
        super(host);
        Random r = new Random(1337L);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = r.nextFloat() * 240F;
            starY[i] = r.nextFloat() * 200F;
            starP[i] = r.nextFloat() * 6.28F;
        }
    }

    @Override
    public String id() {
        return "weather";
    }

    @Override
    public void tick() {
        tick++;
    }

    /** translated name of the biome at pos */
    public static String biomeName(Level level, BlockPos pos) {
        Holder<Biome> h = level.getBiome(pos);
        return h.unwrapKey()
                .map(k -> I18n.get("biome." + k.location().getNamespace() + "." + k.location().getPath()))
                .orElse("Unknown");
    }

    private static float clamp01(float v) {
        return Math.max(0F, Math.min(1F, v));
    }

    private static String hourLabel(long tod) {
        long h = (tod / 1000L + 6L) % 24L;
        if (PhoneData.twentyFour) return (h < 10 ? "0" : "") + h;
        long hh = h % 12L;
        if (hh == 0L) hh = 12L;
        return hh + (h < 12L ? "AM" : "PM");
    }

    private static String time(long hour24) {
        if (PhoneData.twentyFour) return (hour24 < 10 ? "0" : "") + hour24 + ":00";
        long hh = hour24 % 12L;
        if (hh == 0L) hh = 12L;
        return hh + ":00 " + (hour24 < 12L ? "AM" : "PM");
    }

    private void cloud(GuiGraphics g, float x, float y, float s, int color) {
        PhoneUi.disc(g, x, y, 9F * s, color);
        PhoneUi.disc(g, x + 11F * s, y - 5F * s, 12F * s, color);
        PhoneUi.disc(g, x + 24F * s, y, 10F * s, color);
        PhoneUi.rr(g, x - 2F * s, y - 1F * s, x + 30F * s, y + 9F * s, 5F * s, color);
    }

    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Minecraft mc = host.mc();
        Level level = mc.level;
        Player pl = mc.player;
        Font f = host.font();
        if (level == null || pl == null) {
            g.fill(0, 0, W, H, 0xFF000000);
            PhoneUi.textC(g, f, "No weather data", 120, 170, 1.2F, 0xFF8E8E93);
            return;
        }
        BlockPos pos = pl.blockPosition();
        Biome biome = level.getBiome(pos).value();
        long tod = ((level.getDayTime() % 24000L) + 24000L) % 24000L;
        double th = (tod - 6000.0) / 24000.0 * Math.PI * 2.0;
        float cosT = (float) Math.cos(th);
        float d = clamp01(cosT * 2F + 0.5F);
        float dusk = 1F - Math.abs(d * 2F - 1F);
        float rain = level.getRainLevel(1F);
        float thunder = level.getThunderLevel(1F);
        boolean nether = level.dimension() == Level.NETHER;
        boolean end = level.dimension() == Level.END;
        Biome.Precipitation prec = biome.getPrecipitationAt(pos);

        // ---------------- sky
        int top;
        int bot;
        if (nether) {
            top = 0xFF260805;
            bot = 0xFF8A2208;
        } else if (end) {
            top = 0xFF04020A;
            bot = 0xFF2B1B4D;
        } else {
            top = PhoneUi.mix(0xFF050A1E, 0xFF2F6FD6, d);
            bot = PhoneUi.mix(0xFF16275A, 0xFF8CC8FF, d);
            top = PhoneUi.mix(top, 0xFF3A3A7A, dusk * 0.65F);
            bot = PhoneUi.mix(bot, 0xFFFF9A5A, dusk * 0.85F);
            float gr = clamp01(rain * 0.85F + thunder * 0.1F);
            top = PhoneUi.mix(top, PhoneUi.mix(0xFF1B1E26, 0xFF626A76, d), gr);
            bot = PhoneUi.mix(bot, PhoneUi.mix(0xFF2A2F3A, 0xFF8C95A2, d), gr);
        }
        g.fillGradient(0, 0, W, H, top, bot);

        // ---------------- stars
        if (!nether) {
            float sa = (end ? 1F : (1F - d)) * (1F - rain * 0.9F);
            if (sa > 0.02F) {
                for (int i = 0; i < starX.length; i++) {
                    float tw = 0.55F + 0.45F * (float) Math.sin(tick * 0.06F + starP[i]);
                    PhoneUi.disc(g, starX[i], starY[i], i % 5 == 0 ? 1.1F : 0.6F, PhoneUi.alpha(0xFFFFFFFF, sa * tw));
                }
            }
        }

        // ---------------- sun and moon
        if (!nether && !end) {
            if (cosT > -0.12F) {
                float sx = 120F + (float) Math.sin(th) * 96F;
                float sy = 150F - cosT * 106F;
                float vis = clamp01((cosT + 0.12F) * 5F) * (1F - rain * 0.8F);
                int sc = PhoneUi.mix(0xFFFFE27A, 0xFFFF9A3C, dusk);
                for (int i = 5; i >= 1; i--) {
                    PhoneUi.disc(g, sx, sy, 12F + i * 7F, PhoneUi.alpha(sc, 0.05F * vis));
                }
                PhoneUi.disc(g, sx, sy, 13F, PhoneUi.alpha(0xFFFFF6D0, vis));
            }
            if (cosT < 0.12F) {
                float mxp = 120F + (float) Math.sin(th + Math.PI) * 96F;
                float myp = 150F - (float) Math.cos(th + Math.PI) * 106F;
                float vis = clamp01((0.12F - cosT) * 5F) * (1F - rain * 0.85F);
                drawMoon(g, mxp, myp, 12F, level.getMoonPhase(), vis);
            }
        }

        // ---------------- clouds
        float cloudy = Math.max(rain, thunder * 0.9F);
        int ccount = 3 + Math.round(cloudy * 6F);
        for (int i = 0; i < ccount; i++) {
            float x = ((i * 83F + tick * (0.10F + (i % 3) * 0.05F)) % 330F) - 50F;
            float y = 40F + (i * 47F) % 130F;
            float lum = nether || end ? 0.25F : (0.35F + 0.65F * d);
            int base = PhoneUi.mix(0xFF4A505C, 0xFFFFFFFF, lum * (1F - cloudy * 0.45F));
            cloud(g, x, y, 0.9F + (i % 3) * 0.25F, PhoneUi.alpha(base, 0.28F + cloudy * 0.5F));
        }

        // ---------------- precipitation
        if (rain > 0.05F && prec != Biome.Precipitation.NONE) {
            int n = (int) (70F * rain);
            for (int i = 0; i < n; i++) {
                float x = (i * 37.7F) % 244F;
                if (prec == Biome.Precipitation.SNOW) {
                    float y = ((tick * 1.4F + i * 53F) % 380F) - 10F;
                    float sway = (float) Math.sin(tick * 0.05F + i) * 4F;
                    PhoneUi.disc(g, x + sway, y, 1.3F, PhoneUi.alpha(0xFFFFFFFF, 0.8F));
                } else {
                    float y = ((tick * 7F + i * 53F) % 380F) - 10F;
                    PhoneUi.line(g, x, y, x - 2.2F, y + 9F, 0.35F, PhoneUi.alpha(0xFFBFD9FF, 0.55F));
                }
            }
        }
        if (thunder > 0.5F && ((tick * 7919) % 211) < 3) {
            g.fill(0, 0, W, H, 0x40FFFFFF);
        }

        // ---------------- text
        float base = biome.getBaseTemperature() * 25F;
        base -= Math.max(0F, pos.getY() - 80F) * 0.12F;
        if (nether) base = 38F;
        if (end) base = -8F;
        float now = base + 4F * cosT - (rain > 0.3F ? 3F : 0F);
        float hi = base + 4F - (rain > 0.3F ? 3F : 0F);
        float lo = base - 4F - (rain > 0.3F ? 3F : 0F);
        String cond;
        if (nether) cond = "Ash & Embers";
        else if (end) cond = "Void Calm";
        else if (thunder > 0.5F) cond = "Thunderstorm";
        else if (rain > 0.3F) cond = prec == Biome.Precipitation.SNOW ? "Snow" : (prec == Biome.Precipitation.RAIN ? "Rain" : "Overcast");
        else cond = d > 0.5F ? "Sunny" : "Clear";

        PhoneUi.textC(g, f, biomeName(level, pos), 120, 44, 1.5F, 0xFFFFFFFF);
        PhoneUi.textC(g, f, Math.round(now) + "°", 120, 66, 5.2F, 0xFFFFFFFF);
        PhoneUi.textC(g, f, cond, 120, 116, 1.2F, 0xE6FFFFFF);
        PhoneUi.textC(g, f, "H:" + Math.round(hi) + "°   L:" + Math.round(lo) + "°", 120, 131, 1.0F, 0xCCFFFFFF);

        // ---------------- hourly card
        card(g, 12, 154, 228, 226);
        PhoneUi.text(g, f, "HOURLY FORECAST", 22, 160, 0.72F, 0xA0FFFFFF);
        g.fill(22, 172, 218, 173, 0x33FFFFFF);
        for (int i = 0; i < 6; i++) {
            long t2 = (tod + i * 2000L) % 24000L;
            double th2 = (t2 - 6000.0) / 24000.0 * Math.PI * 2.0;
            float c2 = (float) Math.cos(th2);
            float cx = 36F + i * 34F;
            PhoneUi.textC(g, f, i == 0 ? "Now" : hourLabel(t2), cx, 178, 0.8F, 0xE6FFFFFF);
            boolean dayNow = c2 > -0.05F;
            if (rain > 0.3F) {
                cloud(g, cx - 11F, 199F, 0.36F, 0xDDDDE3EC);
                if (prec != Biome.Precipitation.NONE) {
                    PhoneUi.line(g, cx - 4F, 204F, cx - 6F, 209F, 0.4F, 0xFF9CC8FF);
                    PhoneUi.line(g, cx + 2F, 204F, cx, 209F, 0.4F, 0xFF9CC8FF);
                }
            } else if (dayNow) {
                PhoneUi.disc(g, cx, 199F, 6.5F, 0xFFFFD84A);
            } else {
                drawMoon(g, cx, 199F, 6F, level.getMoonPhase(), 1F);
            }
            float tmp = base + 4F * c2 - (rain > 0.3F ? 3F : 0F);
            PhoneUi.textC(g, f, Math.round(tmp) + "°", cx, 211, 0.95F, 0xFFFFFFFF);
        }

        // ---------------- moon + sun cards
        card(g, 12, 234, 122, 296);
        PhoneUi.text(g, f, "MOON", 20, 240, 0.72F, 0xA0FFFFFF);
        drawMoon(g, 36F, 268F, 13F, level.getMoonPhase(), 1F);
        PhoneUi.text(g, f, MOON[level.getMoonPhase() & 7], 54, 259, 0.78F, 0xFFFFFFFF);
        PhoneUi.text(g, f, Math.round(LIT[level.getMoonPhase() & 7] * 100F) + "% lit", 54, 271, 0.72F, 0xB4FFFFFF);

        card(g, 130, 234, 228, 296);
        PhoneUi.text(g, f, "SUN", 138, 240, 0.72F, 0xA0FFFFFF);
        PhoneUi.disc(g, 150F, 266F, 6F, 0xFFFFD84A);
        PhoneUi.text(g, f, "Rise " + time(5L), 162, 259, 0.78F, 0xFFFFFFFF);
        PhoneUi.text(g, f, "Set  " + time(19L), 162, 271, 0.78F, 0xFFFFFFFF);

        // ---------------- conditions strip
        card(g, 12, 304, 228, 344);
        int light = level.getMaxLocalRawBrightness(pos);
        boolean sky = level.canSeeSky(pos);
        PhoneUi.text(g, f, "Light", 22, 311, 0.72F, 0xA0FFFFFF);
        PhoneUi.text(g, f, light + " / 15", 22, 323, 1.0F, 0xFFFFFFFF);
        PhoneUi.text(g, f, "Sky", 96, 311, 0.72F, 0xA0FFFFFF);
        PhoneUi.text(g, f, sky ? "Open" : "Covered", 96, 323, 1.0F, 0xFFFFFFFF);
        PhoneUi.text(g, f, "Altitude", 164, 311, 0.72F, 0xA0FFFFFF);
        PhoneUi.text(g, f, "Y " + pos.getY(), 164, 323, 1.0F, 0xFFFFFFFF);
    }

    private void card(GuiGraphics g, float x0, float y0, float x1, float y1) {
        PhoneUi.rr(g, x0, y0, x1, y1, 14F, 0x3A000000);
        PhoneUi.rrStroke(g, x0, y0, x1, y1, 14F, 0.6F, 0x33FFFFFF);
    }

    /** moon with a phase terminator (phase 0 = full, 4 = new); the unlit part keeps a faint earthshine */
    private void drawMoon(GuiGraphics g, float cx, float cy, float r, int phase, float vis) {
        phase &= 7;
        float lit = LIT[phase];
        PhoneUi.disc(g, cx, cy, r + 5F, PhoneUi.alpha(0xFFDDE6FF, 0.07F * vis * (0.3F + lit)));
        PhoneUi.disc(g, cx, cy, r, PhoneUi.alpha(0xFF8A94B0, 0.30F * vis));
        if (phase == 0) {
            PhoneUi.disc(g, cx, cy, r, PhoneUi.alpha(0xFFE6ECFA, vis));
            PhoneUi.disc(g, cx - r * 0.3F, cy - r * 0.25F, r * 0.22F, PhoneUi.alpha(0xFFC9D2E6, 0.8F * vis));
            PhoneUi.disc(g, cx + r * 0.35F, cy + r * 0.3F, r * 0.16F, PhoneUi.alpha(0xFFC9D2E6, 0.8F * vis));
            return;
        }
        if (phase == 4) return;
        litMoon(g, cx, cy, r, r * 2F * lit, phase < 4, PhoneUi.alpha(0xFFE6ECFA, vis));
    }

    /** the lit part of a moon: the disc minus a same-sized shadow disc displaced by (2r - off), drawn per scanline */
    private static void litMoon(GuiGraphics g, float cx, float cy, float r, float off, boolean litLeft, int color) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0F);
        ps.scale(0.25F, 0.25F, 1F);
        int rr = Math.round(r * 4F);
        int o = Math.round(off * 4F);
        for (int yy = -rr; yy < rr; yy++) {
            double dy = yy + 0.5;
            int hw = (int) Math.round(Math.sqrt(Math.max(0.0, (double) rr * rr - dy * dy)));
            int a;
            int b;
            if (litLeft) {
                a = -hw;
                b = Math.min(hw, o - hw);
            } else {
                a = Math.max(-hw, hw - o);
                b = hw;
            }
            if (b > a) g.fill(a, yy, b, yy + 1, color);
        }
        ps.popPose();
    }
}
