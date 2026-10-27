package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneData;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Settings: wallpaper, brightness, volume, toggles and the About sheet with the real iPhone Duo specifications. */
public class SettingsApp extends PhoneApp {
    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF1C1C1E;
    private static final int SEP = 0xFF38383A;
    private static final int BLUE = 0xFF0A84FF;
    private static final int GREEN = 0xFF30D158;
    private static final float ROW = 29F;
    private static final String[] WALLS = {"wall_aurora", "wall_ember", "wall_mono"};
    private static final String[][] ABOUT = {
            {"Model Name", "iPhone Duo"},
            {"Software Version", "iOS 27"},
            {"Chip", "A20 Pro"},
            {"Outer Display", "5.4-inch Super Retina XDR"},
            {"Inner Display", "7.6-inch, nano-texture"},
            {"Refresh Rate", "120 Hz ProMotion"},
            {"Peak Brightness", "3,000 nits"},
            {"Rear Cameras", "48MP Fusion Main + 48MP Ultra Wide"},
            {"Front Camera", "Under-display"},
            {"Authentication", "Touch ID side button"},
            {"Frame", "Polished grade 5 titanium"},
            {"Outer Glass", "Ceramic Shield"},
            {"Hinge", "100+ parts, carbon fibre plates"},
            {"Battery", "Split dual-cell, vapour chamber"},
            {"Water & Dust", "IP68"}};

    private static final int K_WALL = 1;
    private static final int K_BRIGHT = 2;
    private static final int K_VOL = 3;
    private static final int K_TAP = 4;
    private static final int K_24H = 5;
    private static final int K_RESET = 6;

    private static final class Hit {
        final int kind;
        final int arg;
        final float x0;
        final float y0;
        final float x1;
        final float y1;

        Hit(int kind, int arg, float x0, float y0, float x1, float y1) {
            this.kind = kind;
            this.arg = arg;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final List<Hit> hits = new ArrayList<Hit>();
    private float scroll;
    private float contentH = 600F;
    private int dragging;
    private boolean dirty;

    public SettingsApp(PhoneHost host) {
        super(host);
    }

    @Override
    public String id() {
        return "settings";
    }

    private void clampScroll() {
        float max = Math.max(0F, contentH - (H - 8F));
        scroll = Math.max(0F, Math.min(max, scroll));
    }

    private void toggle(GuiGraphics g, float x, float y, boolean on) {
        PhoneUi.rr(g, x, y, x + 38F, y + 22F, 11F, on ? GREEN : 0xFF39393D);
        PhoneUi.disc(g, on ? x + 27F : x + 11F, y + 11F, 9F, 0xFFFFFFFF);
    }

    private float group(GuiGraphics g, float y, float h) {
        PhoneUi.rr(g, 12F, y, 228F, y + h, 12F, CARD);
        return y;
    }

    private void header(GuiGraphics g, Font f, String s, float y) {
        PhoneUi.text(g, f, s, 20F, y, 0.74F, 0xFF8E8E93);
    }

    private void slider(GuiGraphics g, float y, float v, int kind) {
        float x0 = 54F;
        float x1 = 206F;
        PhoneUi.rr(g, x0, y + 8F, x1, y + 12F, 2F, 0xFF3A3A3C);
        PhoneUi.rr(g, x0, y + 8F, x0 + (x1 - x0) * v, y + 12F, 2F, 0xFFFFFFFF);
        PhoneUi.disc(g, x0 + (x1 - x0) * v, y + 10F, 7F, 0xFFFFFFFF);
        hits.add(new Hit(kind, 0, x0 - 8F, y, x1 + 8F, y + 20F));
    }

    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Font f = host.font();
        hits.clear();
        g.fill(0, 0, W, H, BG);
        pane.scissor(g, 0, 26F, W, H);
        float y = 34F - scroll;
        PhoneUi.text(g, f, "Settings", 18F, y, 1.7F, 0xFFFFFFFF);
        y += 28F;

        // profile card
        group(g, y, 54F);
        PhoneUi.image(g, PhoneUi.gui("logo_duo"), 22F, y + 9F, 36F, 36F, 256, 256);
        PhoneUi.text(g, f, "iPhone Duo", 68F, y + 14F, 1.15F, 0xFFFFFFFF);
        PhoneUi.text(g, f, "iOS 27 · A20 Pro", 68F, y + 28F, 0.8F, 0xFF8E8E93);
        y += 54F + 14F;

        // wallpaper
        header(g, f, "WALLPAPER", y);
        y += 12F;
        group(g, y, 76F);
        for (int i = 0; i < 3; i++) {
            float x = 20F + i * 68F;
            float w = 60F;
            float h = 45F;
            PhoneUi.image(g, PhoneUi.gui(WALLS[i]), x, y + 8F, w, h, 512, 384);
            if (PhoneData.wallpaper == i) {
                PhoneUi.rrStroke(g, x - 2F, y + 6F, x + w + 2F, y + 8F + h + 2F, 3F, 1.4F, BLUE);
            }
            hits.add(new Hit(K_WALL, i, x, y + 8F, x + w, y + 8F + h));
        }
        String[] names = {"Aurora", "Ember", "Liquid Metal"};
        for (int i = 0; i < 3; i++) {
            PhoneUi.textC(g, f, names[i], 20F + i * 68F + 30F, y + 58F, 0.72F, PhoneData.wallpaper == i ? 0xFFFFFFFF : 0xFF8E8E93);
        }
        y += 76F + 14F;

        // display & sound
        header(g, f, "DISPLAY & SOUND", y);
        y += 12F;
        group(g, y, ROW * 2F + 6F);
        float by = y + 4F;
        PhoneUi.disc(g, 31F, by + 14.5F, 4F, 0xFFFFD60A);
        slider(g, by + 4F, (PhoneData.brightness - 0.35F) / 0.65F, K_BRIGHT);
        PhoneUi.disc(g, 218F, by + 14.5F, 6F, 0xFFFFD60A);
        g.fill(36, (int) (y + 3F + ROW), 228, (int) (y + 4F + ROW), SEP);
        float vy = y + 4F + ROW;
        PhoneUi.rr(g, 25F, vy + 11F, 29F, vy + 19F, 1F, 0xFFFFFFFF);
        PhoneUi.tri(g, 29F, vy + 11F, 36F, vy + 6F, 36F, vy + 24F, 0xFFFFFFFF);
        PhoneUi.tri(g, 29F, vy + 19F, 36F, vy + 24F, 36F, vy + 11F, 0xFFFFFFFF);
        slider(g, vy + 4F, PhoneData.volume, K_VOL);
        y += ROW * 2F + 6F + 14F;

        // toggles
        header(g, f, "GENERAL", y);
        y += 12F;
        group(g, y, ROW * 2F);
        PhoneUi.text(g, f, "Tap Sounds", 24F, y + 10F, 1.0F, 0xFFFFFFFF);
        toggle(g, 182F, y + 3.5F, PhoneData.tapSounds);
        hits.add(new Hit(K_TAP, 0, 12F, y, 228F, y + ROW));
        g.fill(24, (int) (y + ROW), 228, (int) (y + ROW) + 1, SEP);
        PhoneUi.text(g, f, "24-Hour Time", 24F, y + ROW + 10F, 1.0F, 0xFFFFFFFF);
        toggle(g, 182F, y + ROW + 3.5F, PhoneData.twentyFour);
        hits.add(new Hit(K_24H, 0, 12F, y + ROW, 228F, y + ROW * 2F));
        y += ROW * 2F + 14F;

        // about
        header(g, f, "ABOUT", y);
        y += 12F;
        float ah = ABOUT.length * 26F;
        group(g, y, ah);
        for (int i = 0; i < ABOUT.length; i++) {
            float ry = y + i * 26F;
            PhoneUi.text(g, f, ABOUT[i][0], 24F, ry + 9F, 0.86F, 0xFFFFFFFF);
            PhoneUi.textR(g, f, shorten(f, ABOUT[i][1], 0.78F, 104F), 220F, ry + 9.5F, 0.78F, 0xFF8E8E93);
            if (i < ABOUT.length - 1) g.fill(24, (int) (ry + 26F), 228, (int) (ry + 26F) + 1, SEP);
        }
        y += ah + 14F;

        // reset
        group(g, y, ROW);
        PhoneUi.textC(g, f, "Reset Settings", 120F, y + 10F, 1.0F, 0xFFFF453A);
        hits.add(new Hit(K_RESET, 0, 12F, y, 228F, y + ROW));
        y += ROW + 30F;
        contentH = y + scroll;
        clampScroll();
        pane.unscissor(g);

        // scroll indicator
        float view = H - 8F;
        if (contentH > view) {
            float th = Math.max(20F, view * view / contentH);
            float ty = 8F + (view - th) * (scroll / Math.max(1F, contentH - view));
            PhoneUi.rr(g, 234.5F, ty, 236.5F, ty + th, 1F, 0x55FFFFFF);
        }
    }

    /** clips a string with an ellipsis so it fits in maxW logical px */
    private static String shorten(Font f, String s, float sc, float maxW) {
        if (PhoneUi.width(f, s, sc) <= maxW) return s;
        String t = s;
        while (t.length() > 2 && PhoneUi.width(f, t + "...", sc) > maxW) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    // ------------------------------------------------------------------ input
    private Hit find(double x, double y) {
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).contains(x, y)) return hits.get(i);
        }
        return null;
    }

    private void setFromX(Hit h, double x) {
        float t = (float) ((x - 54D) / 152D);
        t = Math.max(0F, Math.min(1F, t));
        if (h.kind == K_BRIGHT) PhoneData.brightness = 0.35F + 0.65F * t;
        else PhoneData.volume = t;
        dirty = true;
    }

    @Override
    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        Hit h = find(x, y);
        if (h == null) return false;
        if (h.kind == K_BRIGHT || h.kind == K_VOL) {
            dragging = h.kind;
            setFromX(h, x);
            return true;
        }
        host.tap();
        dirty = true;
        if (h.kind == K_WALL) PhoneData.wallpaper = h.arg;
        else if (h.kind == K_TAP) PhoneData.tapSounds = !PhoneData.tapSounds;
        else if (h.kind == K_24H) PhoneData.twentyFour = !PhoneData.twentyFour;
        else if (h.kind == K_RESET) {
            PhoneData.wallpaper = 0;
            PhoneData.brightness = 1.0F;
            PhoneData.volume = 0.8F;
            PhoneData.tapSounds = true;
            PhoneData.twentyFour = false;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(PhonePane pane, double x, double y, int button, double dx, double dy) {
        if (dragging != 0) {
            for (int i = 0; i < hits.size(); i++) {
                if (hits.get(i).kind == dragging) {
                    setFromX(hits.get(i), x);
                    break;
                }
            }
            return true;
        }
        if (Math.abs(dy) > 0.01D) {
            scroll -= (float) dy;
            clampScroll();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(PhonePane pane, double x, double y, int button) {
        boolean was = dragging != 0;
        dragging = 0;
        if (dirty) {
            PhoneData.save();
            dirty = false;
        }
        return was;
    }

    @Override
    public boolean mouseScrolled(PhonePane pane, double x, double y, double delta) {
        scroll -= (float) delta * 26F;
        clampScroll();
        return true;
    }

    @Override
    public void onClose() {
        if (dirty) {
            PhoneData.save();
            dirty = false;
        }
    }
}
