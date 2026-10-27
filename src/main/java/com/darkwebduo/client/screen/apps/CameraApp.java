package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.screen.CameraView;
import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * A see-through camera: the viewfinder is the real world behind the glass. Drag to aim, wheel or lens buttons to zoom
 * (0.5x ultra wide to 5x), flip to a selfie view, self-timer, grid. The shutter saves a clean screenshot of the world.
 */
public class CameraApp extends PhoneApp {
    private static final float[] LENSES = {0.5F, 1.0F, 2.0F, 5.0F};
    private static final String[] LENS_NAMES = {".5", "1×", "2", "5"};
    private static final int[] TIMERS = {0, 3, 10};
    private static final int YELLOW = 0xFFFFD60A;
    private static final float CY = 318F;

    private boolean grid = true;
    private boolean selfie;
    private int timerIdx;
    private int countdown;
    private int flash;
    private int pressAnim;
    private int ticks;
    private int focusAge = 999;
    private float focusX;
    private float focusY;
    private boolean draggingView;
    private CameraType saved;

    public CameraApp(PhoneHost host) {
        super(host);
        saved = host.mc().options.getCameraType();
        CameraView.resetZoom();
    }

    @Override
    public String id() {
        return "camera";
    }

    @Override
    public boolean seeThrough() {
        return true;
    }

    @Override
    public boolean hideStatusBar() {
        return true;
    }

    @Override
    public void onClose() {
        Minecraft mc = host.mc();
        if (saved != null) mc.options.setCameraType(saved);
        CameraView.resetZoom();
    }

    // ------------------------------------------------------------------ actions
    private void shutter() {
        if (countdown > 0) {
            countdown = 0;
            return;
        }
        if (TIMERS[timerIdx] > 0) {
            countdown = TIMERS[timerIdx] * 20;
            host.tap();
            return;
        }
        fire();
    }

    private void fire() {
        pressAnim = 6;
        flash = 9;
        host.capturePhoto();
    }

    private void setLens(int i) {
        CameraView.zoomTarget = LENSES[i];
        host.tap();
    }

    private int lensIndex() {
        int best = 1;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < LENSES.length; i++) {
            float d = Math.abs(LENSES[i] - CameraView.zoomTarget);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private void flip() {
        selfie = !selfie;
        host.mc().options.setCameraType(selfie ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        host.tap();
    }

    @Override
    public void tick() {
        ticks++;
        focusAge++;
        if (pressAnim > 0) pressAnim--;
        if (flash > 0) flash--;
        CameraView.tickZoom();
        if (countdown > 0 && --countdown == 0) fire();
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Font f = host.font();
        // top bar
        g.fill(0, 0, W, 38, 0x88000000);
        PhoneUi.rr(g, 14F, 12F, 52F, 30F, 9F, 0x55FFFFFF);
        PhoneUi.textC(g, f, "48MP", 33F, 17F, 0.85F, 0xFFFFFFFF);
        // grid chip
        PhoneUi.rr(g, 104F, 12F, 136F, 30F, 9F, grid ? 0x88FFD60A : 0x55FFFFFF);
        for (int i = 1; i <= 2; i++) {
            PhoneUi.line(g, 112F + i * 5.3F, 16F, 112F + i * 5.3F, 26F, 0.35F, 0xFFFFFFFF);
            PhoneUi.line(g, 109F, 16F + i * 3.3F, 131F, 16F + i * 3.3F, 0.35F, 0xFFFFFFFF);
        }
        // timer chip
        PhoneUi.rr(g, 188F, 12F, 226F, 30F, 9F, timerIdx > 0 ? 0x88FFD60A : 0x55FFFFFF);
        PhoneUi.ring(g, 201F, 21.5F, 5.4F, 0.9F, 0xFFFFFFFF);
        PhoneUi.line(g, 201F, 21.5F, 201F, 18.4F, 0.4F, 0xFFFFFFFF);
        PhoneUi.text(g, f, timerIdx > 0 ? TIMERS[timerIdx] + "s" : "off", 209F, 17F, 0.8F, 0xFFFFFFFF);

        // viewfinder
        float vx0 = 8F;
        float vy0 = 44F;
        float vx1 = 232F;
        float vy1 = 286F;
        if (grid) {
            int c = 0x55FFFFFF;
            float w = vx1 - vx0;
            float h = vy1 - vy0;
            g.fill(Math.round(vx0 + w / 3F), Math.round(vy0), Math.round(vx0 + w / 3F) + 1, Math.round(vy1), c);
            g.fill(Math.round(vx0 + w * 2F / 3F), Math.round(vy0), Math.round(vx0 + w * 2F / 3F) + 1, Math.round(vy1), c);
            g.fill(Math.round(vx0), Math.round(vy0 + h / 3F), Math.round(vx1), Math.round(vy0 + h / 3F) + 1, c);
            g.fill(Math.round(vx0), Math.round(vy0 + h * 2F / 3F), Math.round(vx1), Math.round(vy0 + h * 2F / 3F) + 1, c);
        }
        int bc = 0xCCFFFFFF;
        float b = 14F;
        corner(g, vx0, vy0, b, 1, 1, bc);
        corner(g, vx1, vy0, b, -1, 1, bc);
        corner(g, vx0, vy1, b, 1, -1, bc);
        corner(g, vx1, vy1, b, -1, -1, bc);

        // focus square
        if (focusAge < 50) {
            float k = focusAge < 6 ? 1.35F - focusAge * 0.058F : 1F;
            float a = focusAge > 36 ? (50 - focusAge) / 14F : 1F;
            float r = 24F * k;
            PhoneUi.rrStroke(g, focusX - r, focusY - r, focusX + r, focusY + r, 2F, 0.9F, PhoneUi.alpha(YELLOW, a));
            PhoneUi.line(g, focusX, focusY - r, focusX, focusY - r + 4F, 0.5F, PhoneUi.alpha(YELLOW, a));
            PhoneUi.line(g, focusX, focusY + r, focusX, focusY + r - 4F, 0.5F, PhoneUi.alpha(YELLOW, a));
        }

        // zoom readout + lens pills
        String zr = String.format(java.util.Locale.ENGLISH, "%.1f×", CameraView.zoom);
        PhoneUi.textC(g, f, zr, 120F, 52F, 0.95F, YELLOW);
        int cur = lensIndex();
        float total = LENSES.length * 30F;
        float sx = 120F - total * 0.5F;
        PhoneUi.rr(g, sx - 4F, 258F, sx + total + 4F, 282F, 12F, 0x66000000);
        for (int i = 0; i < LENSES.length; i++) {
            float cx = sx + i * 30F + 15F;
            boolean on = i == cur;
            PhoneUi.disc(g, cx, 270F, on ? 11F : 9F, on ? 0xCC000000 : 0x00000000);
            PhoneUi.textC(g, f, LENS_NAMES[i], cx, 266F, on ? 0.95F : 0.82F, on ? YELLOW : 0xFFFFFFFF);
        }

        // countdown number
        if (countdown > 0) {
            int sec = (countdown + 19) / 20;
            PhoneUi.textC(g, f, Integer.toString(sec), 120F, 130F, 7F, 0xCCFFFFFF);
        }
        // saved toast
        if (CameraView.lastSaved != null && System.currentTimeMillis() - CameraView.lastSavedAt < 2600L) {
            PhoneUi.rr(g, 60F, 62F, 180F, 82F, 10F, 0xAA000000);
            PhoneUi.textC(g, f, "Saved to Photos", 120F, 68F, 0.9F, 0xFFFFFFFF);
        }

        // bottom bar
        g.fill(0, 290, W, H, 0xAA000000);
        PhoneUi.textC(g, f, "PHOTO", 120F, 296F, 0.85F, YELLOW);
        float cy = CY;
        float inner = 19F - (pressAnim > 0 ? 3F : 0F);
        PhoneUi.ring(g, 120F, cy, 25F, 2.2F, 0xFFFFFFFF);
        PhoneUi.disc(g, 120F, cy, inner, countdown > 0 ? 0xFFFF453A : 0xFFFFFFFF);
        if (countdown > 0) PhoneUi.rr(g, 113F, cy - 7F, 127F, cy + 7F, 2F, 0xFFFFFFFF);
        // photos shortcut
        PhoneUi.image(g, PhoneUi.gui("icon_photos"), 30F, cy - 17F, 34F, 34F, 128, 128);
        PhoneUi.cornerMask(g, 30F, cy - 17F, 64F, cy + 17F, 8F, 0xAA000000);
        PhoneUi.rrStroke(g, 30F, cy - 17F, 64F, cy + 17F, 8F, 0.7F, 0x88FFFFFF);
        // flip
        PhoneUi.disc(g, 198F, cy, 17F, 0x66FFFFFF);
        PhoneUi.ring(g, 198F, cy, 8F, 1.1F, 0xFFFFFFFF);
        PhoneUi.tri(g, 205F, cy - 10F, 212F, cy - 4F, 204F, cy - 3F, 0xFFFFFFFF);
        PhoneUi.tri(g, 191F, cy + 10F, 184F, cy + 4F, 192F, cy + 3F, 0xFFFFFFFF);
        if (selfie) PhoneUi.disc(g, 198F, cy + 22F, 1.6F, YELLOW);

        if (flash > 0) {
            g.fill(0, 0, W, H, PhoneUi.alpha(0xFFFFFFFF, flash / 9F));
        }
    }

    private static void corner(GuiGraphics g, float x, float y, float len, int sx, int sy, int c) {
        PhoneUi.line(g, x, y, x + sx * len, y, 0.7F, c);
        PhoneUi.line(g, x, y, x, y + sy * len, 0.7F, c);
    }

    // ------------------------------------------------------------------ input
    private static boolean inCircle(double x, double y, float cx, float cy, float r) {
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
    }

    @Override
    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        draggingView = false;
        if (y < 38D) {
            if (x >= 104D && x <= 136D) {
                grid = !grid;
                host.tap();
            } else if (x >= 188D && x <= 226D) {
                timerIdx = (timerIdx + 1) % TIMERS.length;
                host.tap();
            }
            return true;
        }
        if (y >= 290D) {
            if (inCircle(x, y, 120F, CY, 27F)) {
                shutter();
                return true;
            }
            if (inCircle(x, y, 198F, CY, 18F)) {
                flip();
                return true;
            }
            if (x >= 30D && x <= 64D && Math.abs(y - CY) <= 17D) {
                host.tap();
                host.launch("photos");
                return true;
            }
            return true;
        }
        if (y >= 258D && y <= 282D) {
            float total = LENSES.length * 30F;
            float sx = 120F - total * 0.5F;
            int i = (int) Math.floor((x - sx) / 30D);
            if (i >= 0 && i < LENSES.length) {
                setLens(i);
                return true;
            }
        }
        if (y >= 44D && y < 258D) {
            focusX = (float) x;
            focusY = (float) y;
            focusAge = 0;
            draggingView = true;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(PhonePane pane, double x, double y, int button, double dx, double dy) {
        if (!draggingView) return false;
        Minecraft mc = host.mc();
        if (mc.player != null) {
            double k = 0.55D / Math.max(0.6D, CameraView.zoom);
            mc.player.turn(dx * k, dy * k);
            focusAge = 999;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(PhonePane pane, double x, double y, int button) {
        draggingView = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(PhonePane pane, double x, double y, double delta) {
        float t = CameraView.zoomTarget + (float) delta * 0.25F;
        CameraView.zoomTarget = Math.max(0.5F, Math.min(5.0F, t));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            shutter();
            return true;
        }
        if (key == GLFW.GLFW_KEY_G) {
            grid = !grid;
            host.tap();
            return true;
        }
        if (key == GLFW.GLFW_KEY_T) {
            timerIdx = (timerIdx + 1) % TIMERS.length;
            host.tap();
            return true;
        }
        if (key == GLFW.GLFW_KEY_C) {
            flip();
            return true;
        }
        return false;
    }
}
