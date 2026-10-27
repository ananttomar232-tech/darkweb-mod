package com.darkwebduo.client.screen;

import com.darkwebduo.block.IPhoneDuoBlock;
import com.darkwebduo.client.render.PhoneInfo;
import com.darkwebduo.client.screen.apps.HomeApp;
import com.darkwebduo.registry.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * The full iPhone Duo experience: a titanium-framed foldable with a 5.4" outer display (one 240x360 pane) that unfolds
 * into a 7.6" inner display (two panes side by side, each running its own app). Lock screen with a Touch ID side button,
 * volume buttons, a fold animation with a hinge crease, status bars, home indicators and swipe-to-home.
 */
public class IPhoneDuoScreen extends Screen {
    private static final float PW = PhoneApp.W;
    private static final float PH = PhoneApp.H;
    private static final float M = 10F;
    private static final float R_OUT = 38F;
    private static final float R_IN = 28F;
    private static final int BEZEL = 0xFF040405;
    private static final String[] WALLS = {"wall_aurora", "wall_ember", "wall_mono"};
    /** highlight, light, mid, dark per colourway */
    private static final int[][] TI = {
            {0xFFF4F2EE, 0xFFDAD8D2, 0xFFB9B7B0, 0xFF8F8D87},
            {0xFF70757F, 0xFF4A4E58, 0xFF2E3139, 0xFF1B1D22}};

    @Nullable
    private final BlockPos pos;
    private final int colorway;
    private final Slot[] slots = new Slot[2];

    private boolean setup;
    private boolean prevHideGui;
    private boolean dual;
    private float open0;
    private float open1;
    private boolean locked = true;
    private float lock0;
    private float lock1;
    private int scan;
    private int wiggle;
    private int focus;
    private int dragSlot = -1;
    private int volShow;
    private int shotStage;
    private int ticks;

    // layout (recomputed every frame)
    private float s = 1F;
    private float fx;
    private float fy;
    private float fw;
    private float fh;
    private float ox;
    private float oy;
    private float areaW = PW;
    private float openE;
    private float pillX0;
    private float pillY0;
    private float pillX1;
    private float pillY1;

    public IPhoneDuoScreen(@Nullable BlockPos pos, int colorway) {
        super(Component.translatable("item.darkweb_duo.iphone_duo"));
        this.pos = pos;
        this.colorway = colorway & 1;
        slots[0] = new Slot(0);
        slots[1] = new Slot(1);
        this.dual = pos != null;
        boolean physicallyOpen = false;
        Minecraft mc = Minecraft.getInstance();
        if (pos != null && mc.level != null) {
            BlockState st = mc.level.getBlockState(pos);
            if (st.hasProperty(IPhoneDuoBlock.OPEN)) physicallyOpen = st.getValue(IPhoneDuoBlock.OPEN);
        }
        this.open0 = physicallyOpen ? 1F : 0F;
        this.open1 = this.open0;
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    protected void init() {
        if (!setup) {
            setup = true;
            prevHideGui = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
        }
        layout(1F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        for (int i = 0; i < 2; i++) {
            slots[i].resetHome();
            slots[i].home.onClose();
        }
        CameraView.resetZoom();
        CameraView.armed = false;
        if (setup) Minecraft.getInstance().options.hideGui = prevHideGui;
        super.removed();
    }

    @Override
    public void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            onClose();
            return;
        }
        if (pos != null) {
            BlockState st = mc.level.getBlockState(pos);
            if (!(st.getBlock() instanceof IPhoneDuoBlock) || mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > 81D) {
                onClose();
                return;
            }
        }
        open0 = open1;
        float target = dual ? 1F : 0F;
        if (open1 < target) open1 = Math.min(target, open1 + 0.07F);
        else if (open1 > target) open1 = Math.max(target, open1 - 0.07F);
        lock0 = lock1;
        float lt = locked ? 0F : 1F;
        if (lock1 < lt) lock1 = Math.min(lt, lock1 + 0.075F);
        else if (lock1 > lt) lock1 = Math.max(lt, lock1 - 0.12F);
        if (scan > 0 && --scan == 0) unlock();
        if (wiggle > 0) wiggle--;
        if (volShow > 0) volShow--;
        slots[0].tickSlot();
        if (open1 > 0.001F || dual) slots[1].tickSlot();
        if (!dual && open1 <= 0.001F) slots[1].resetHome();
        if (!dual && focus == 1) focus = 0;
    }

    // ------------------------------------------------------------------ actions
    private void sound(SoundEvent ev, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, Math.max(0.05F, PhoneData.volume)));
    }

    private void startScan() {
        if (scan > 0 || !locked) return;
        scan = 14;
        sound(ModSounds.PHONE_TAP.get(), 0.8F);
    }

    private void unlock() {
        locked = false;
        sound(ModSounds.PHONE_UNLOCK.get(), 1F);
    }

    private void lockNow() {
        locked = true;
        scan = 0;
        for (int i = 0; i < 2; i++) {
            if (slots[i].current.seeThrough()) slots[i].resetHome();
        }
        if (PhoneData.tapSounds) sound(ModSounds.PHONE_TAP.get(), 0.7F);
    }

    private void toggleFold() {
        if (locked) return;
        dual = !dual;
        sound(dual ? ModSounds.PHONE_UNFOLD.get() : ModSounds.PHONE_FOLD.get(), 1F);
        if (!dual) {
            focus = 0;
            dragSlot = Math.min(dragSlot, 0);
        }
    }

    private void volume(int dir) {
        PhoneData.volume = Mth.clamp(Math.round((PhoneData.volume + dir * 0.1F) * 10F) / 10F, 0F, 1F);
        volShow = 45;
        PhoneData.save();
        sound(ModSounds.PHONE_TAP.get(), 1.0F + PhoneData.volume * 0.3F);
    }

    /** called by the camera app: draw one frame with no phone at all, then let the render hook grab it */
    private void beginShot() {
        if (shotStage != 0) return;
        shotStage = 1;
        sound(ModSounds.PHONE_SHUTTER.get(), 1F);
    }

    // ------------------------------------------------------------------ layout
    private void layout(float pt) {
        float o = Mth.lerp(pt, open0, open1);
        openE = o * o * (3F - 2F * o);
        areaW = PW * (1F + openE);
        float fullW = 2F * M + 2F * PW;
        float fullH = 2F * M + PH;
        s = Math.max(0.35F, Math.min((width - 36F) / fullW, (height - 46F) / fullH));
        fw = (areaW + 2F * M) * s;
        fh = fullH * s;
        fx = (width - fw) * 0.5F;
        fy = (height - fh) * 0.5F - 8F;
        ox = fx + M * s;
        oy = fy + M * s;
        slots[0].pane.ox = ox;
        slots[0].pane.oy = oy;
        slots[0].pane.s = s;
        slots[0].pane.sx = 1F;
        slots[1].pane.ox = ox + PW * s;
        slots[1].pane.oy = oy;
        slots[1].pane.s = s;
        slots[1].pane.sx = Math.max(0.001F, openE);
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (shotStage == 1) {
            // the one frame without any phone: the render-tick hook saves what the world looks like right now
            shotStage = 2;
            CameraView.armed = true;
            return;
        }
        if (shotStage == 2) shotStage = 0;
        layout(pt);
        float lockE = Mth.lerp(pt, lock0, lock1);
        boolean seeL = lockE > 0.6F && slots[0].current.seeThrough();
        boolean seeR = lockE > 0.6F && openE > 0.5F && slots[1].current.seeThrough();
        if (!seeL && !seeR) {
            g.fillGradient(0, 0, width, height, 0x88000000, 0x99000000);
        }
        drawButtons(g, mx, my);
        drawFrame(g);
        drawDisplay(g, mx, my, pt, lockE, seeL, seeR);
        drawChrome(g, mx, my);
    }

    private void drawFrame(GuiGraphics g) {
        int[] c = TI[colorway];
        float r = R_OUT * s;
        for (int i = 0; i < 7; i++) {
            float d = (i + 1) * 3.2F;
            PhoneUi.rr(g, fx - d, fy - d + 9F, fx + fw + d, fy + fh + d + 9F, r + d, 0x0B000000);
        }
        PhoneUi.rr(g, fx, fy, fx + fw, fy + fh, r, c[3]);
        PhoneUi.rr(g, fx + 0.7F * s, fy + 0.7F * s, fx + fw - 0.7F * s, fy + fh - 0.7F * s, r - 0.7F * s, c[2]);
        PhoneUi.rr(g, fx + 1.7F * s, fy + 1.7F * s, fx + fw - 1.7F * s, fy + fh - 1.7F * s, r - 1.7F * s, c[1]);
        PhoneUi.rrStroke(g, fx, fy, fx + fw, fy + fh, r, 0.8F * s, PhoneUi.alpha(c[0], 0.85F));
        PhoneUi.rrStroke(g, fx + 2.6F * s, fy + 2.6F * s, fx + fw - 2.6F * s, fy + fh - 2.6F * s, r - 2.6F * s, 0.5F * s, PhoneUi.alpha(c[3], 0.6F));
        PhoneUi.rr(g, fx + 3.3F * s, fy + 3.3F * s, fx + fw - 3.3F * s, fy + fh - 3.3F * s, r - 3.3F * s, BEZEL);
    }

    private void button(GuiGraphics g, float x0, float y0, float x1, float y1, int glow) {
        int[] c = TI[colorway];
        float r = 1.5F * s;
        PhoneUi.rr(g, x0, y0, x1, y1, r, c[3]);
        PhoneUi.rr(g, x0 + 0.4F * s, y0 + 0.4F * s, x1 - 0.4F * s, y1 - 0.4F * s, r, c[1]);
        PhoneUi.rr(g, (x0 + x1) * 0.5F - 0.2F * s, y0 + 1F * s, (x0 + x1) * 0.5F + 0.2F * s, y1 - 1F * s, 0.2F * s, PhoneUi.alpha(c[0], 0.6F));
        if (glow != 0) PhoneUi.rr(g, x0, y0, x1, y1, r, glow);
    }

    // button rectangles (GUI px) for drawing and hit testing
    private float[] sideRect() {
        return new float[] {fx + fw - 0.8F * s, fy + (M + 116F) * s, fx + fw + 3.2F * s, fy + (M + 178F) * s};
    }

    private float[] volRect(int i) {
        float y0 = i == 0 ? M + 90F : M + 138F;
        float y1 = i == 0 ? M + 130F : M + 178F;
        return new float[] {fx - 3.2F * s, fy + y0 * s, fx + 0.8F * s, fy + y1 * s};
    }

    private void drawButtons(GuiGraphics g, int mx, int my) {
        float[] sd = sideRect();
        int glow = scan > 0 ? PhoneUi.alpha(0xFF38A0FF, 0.35F + 0.4F * (scan % 4 < 2 ? 1F : 0.4F)) : 0;
        if (locked && scan == 0 && (ticks / 14) % 2 == 0) glow = 0x2238A0FF;
        button(g, sd[0], sd[1], sd[2], sd[3], glow);
        for (int i = 0; i < 2; i++) {
            float[] v = volRect(i);
            button(g, v[0], v[1], v[2], v[3], 0);
        }
        button(g, fx - 3.0F * s, fy + (M + 52F) * s, fx + 0.8F * s, fy + (M + 70F) * s, 0);
    }

    private void drawDisplay(GuiGraphics g, int mx, int my, float pt, float lockE, boolean seeL, boolean seeR) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(ox, oy, 0F);
        ps.scale(s, s, 1F);

        // wallpaper (two halves so a see-through camera pane stays clear)
        String wall = WALLS[Mth.clamp(PhoneData.wallpaper, 0, 2)];
        int uw = Math.max(1, Math.round(512F * areaW / 480F));
        if (!seeL) {
            PhoneUi.imageRegion(g, PhoneUi.gui(wall), 0F, 0F, PW, PH, 0F, 0F, 256, 384, 512, 384);
        } else {
            g.fill(0, 0, (int) PW, (int) PH, 0xFF000000);
        }
        if (areaW > PW + 0.5F && !seeR) {
            PhoneUi.imageRegion(g, PhoneUi.gui(wall), PW, 0F, areaW - PW, PH, 256F, 0F, Math.max(1, uw - 256), 384, 512, 384);
        }

        // panes
        ps.popPose();
        if (lockE > 0.02F) {
            slots[0].render(g, mx, my, pt);
            if (openE > 0.01F) slots[1].render(g, mx, my, pt);
        }
        ps.pushPose();
        ps.translate(ox, oy, 0F);
        ps.scale(s, s, 1F);

        // hinge crease
        if (openE > 0.05F) {
            for (int i = 0; i < 7; i++) {
                int a = (int) (0x2C * (1F - i / 7F) * openE);
                int col = a << 24;
                PhoneUi.rr(g, PW - 0.8F * (i + 1), 0F, PW - 0.8F * i, PH, 0F, col);
                PhoneUi.rr(g, PW + 0.8F * i, 0F, PW + 0.8F * (i + 1), PH, 0F, col);
            }
            PhoneUi.rr(g, PW - 0.25F, 0F, PW + 0.25F, PH, 0F, PhoneUi.alpha(0xFFFFFFFF, 0.07F * openE));
        }

        drawStatusBar(g, lockE);

        // lock screen
        float la = 1F - lockE;
        if (la > 0.01F) drawLock(g, la, lockE);

        // brightness
        float dim = (1F - PhoneData.brightness) * 0.75F;
        if (dim > 0.01F) PhoneUi.rr(g, 0F, 0F, areaW, PH, 0F, ((int) (dim * 255F)) << 24);

        // rounded display corners and the outer punch-hole camera
        PhoneUi.cornerMask(g, 0F, 0F, areaW, PH, R_IN, BEZEL);
        if (openE < 0.6F) {
            float a = 1F - openE / 0.6F;
            PhoneUi.disc(g, PW * 0.5F, 12F, 4.4F, PhoneUi.alpha(0xFF000000, a));
            PhoneUi.ring(g, PW * 0.5F, 12F, 3.6F, 0.5F, PhoneUi.alpha(0xFF2A3550, a));
            PhoneUi.disc(g, PW * 0.5F - 1F, 11F, 0.9F, PhoneUi.alpha(0xFF6F86C8, 0.7F * a));
        }
        ps.popPose();
    }

    private void drawStatusBar(GuiGraphics g, float lockE) {
        Font f = font;
        boolean lockedView = lockE < 0.5F;
        PhoneApp l = slots[0].current;
        PhoneApp r = openE > 0.5F ? slots[1].current : slots[0].current;
        boolean hideL = !lockedView && l.hideStatusBar();
        boolean hideR = !lockedView && r.hideStatusBar();
        int colL = !lockedView && l.lightStatusBar() ? 0xFF000000 : 0xFFFFFFFF;
        int colR = !lockedView && r.lightStatusBar() ? 0xFF000000 : 0xFFFFFFFF;
        if (!hideL) {
            PhoneUi.text(g, f, PhoneInfo.clock(), 24F, 8.5F, 0.95F, colL);
        }
        if (!hideR) {
            float xe = areaW - 24F;
            // battery
            PhoneUi.rr(g, xe - 24F, 9F, xe - 2F, 19F, 3F, PhoneUi.alpha(colR, 0.38F));
            PhoneUi.rr(g, xe - 22.6F, 10.4F, xe - 22.6F + 19.2F * 0.87F, 17.6F, 2F, colR);
            PhoneUi.rr(g, xe - 1.4F, 12.2F, xe, 15.8F, 0.8F, PhoneUi.alpha(colR, 0.5F));
            // 5G
            PhoneUi.text(g, f, "5G", xe - 44F, 9.5F, 0.72F, colR);
            // signal
            for (int i = 0; i < 4; i++) {
                float h = 3F + i * 2.1F;
                PhoneUi.rr(g, xe - 66F + i * 4F, 18.6F - h, xe - 63.2F + i * 4F, 18.6F, 0.8F, colR);
            }
        }
    }

    private void drawLock(GuiGraphics g, float a, float lockE) {
        Font f = font;
        float cx = areaW * 0.5F;
        float rise = -lockE * 50F;
        float wig = wiggle > 0 ? (float) Math.sin(wiggle * 1.6F) * wiggle * 0.5F : 0F;
        g.fill(0, 0, Math.round(areaW), Math.round(PH), PhoneUi.alpha(0xFF000000, 0.22F * a));
        // padlock
        float ly = 44F + rise;
        PhoneUi.ring(g, cx, ly - 2F, 5.2F, 1.5F, PhoneUi.alpha(0xFFFFFFFF, a));
        PhoneUi.rr(g, cx - 7F, ly + 1F, cx + 7F, ly + 12F, 2.6F, PhoneUi.alpha(0xFFFFFFFF, a));
        PhoneUi.disc(g, cx, ly + 6F, 1.4F, PhoneUi.alpha(0xFF222222, a));
        PhoneUi.textC(g, f, PhoneInfo.date(), cx, 68F + rise, 1.05F, PhoneUi.alpha(0xFFFFFFFF, 0.88F * a));
        PhoneUi.textC(g, f, PhoneInfo.clock(), cx, 82F + rise, 5.4F, PhoneUi.alpha(0xFFFFFFFF, a));
        // notification card
        float ny = 230F + rise * 1.4F;
        PhoneUi.rr(g, cx - 104F, ny, cx + 104F, ny + 38F, 14F, PhoneUi.alpha(0xFF1C1C1E, 0.62F * a));
        PhoneUi.image(g, PhoneUi.gui("logo_duo"), cx - 96F, ny + 7F, 24F, 24F, 256, 256);
        PhoneUi.text(g, f, "iPhone Duo", cx - 66F, ny + 8F, 0.88F, PhoneUi.alpha(0xFFFFFFFF, a));
        PhoneUi.text(g, f, "Touch the side button to unlock", cx - 66F, ny + 20F, 0.74F, PhoneUi.alpha(0xFFFFFFFF, 0.75F * a));
        // Touch ID glyph
        float gy = 296F + rise * 0.6F;
        float pulse = 0.55F + 0.45F * (float) Math.sin(ticks * 0.12F);
        float ga = (scan > 0 ? 1F : 0.55F + 0.3F * pulse) * a;
        g.setColor(1F, 1F, 1F, Mth.clamp(ga, 0F, 1F));
        PhoneUi.image(g, PhoneUi.gui("touchid"), cx - 20F + wig, gy - 20F, 40F, 40F, 128, 128);
        g.setColor(1F, 1F, 1F, 1F);
        if (scan > 0) {
            float prog = 1F - scan / 14F;
            int seg = (int) (prog * 36F);
            for (int i = 0; i < seg; i++) {
                double an = Math.toRadians(i * 10.0 - 90.0);
                PhoneUi.line(g, cx + (float) Math.cos(an) * 25F, gy + (float) Math.sin(an) * 25F,
                        cx + (float) Math.cos(an) * 28F, gy + (float) Math.sin(an) * 28F, 0.6F, PhoneUi.alpha(0xFF4FB0FF, a));
            }
            float sy = gy - 18F + 36F * ((scan % 7) / 7F);
            PhoneUi.rr(g, cx - 17F, sy, cx + 17F, sy + 1.2F, 0.6F, PhoneUi.alpha(0xFF7FD0FF, 0.9F * a));
        }
        PhoneUi.textC(g, f, scan > 0 ? "Scanning..." : "Touch ID", cx, 326F + rise * 0.4F, 0.82F, PhoneUi.alpha(0xFFFFFFFF, 0.8F * a));
        PhoneUi.textC(g, f, "Press the side button", cx, 337F + rise * 0.4F, 0.7F, PhoneUi.alpha(0xFFFFFFFF, 0.5F * a));
    }

    private void drawChrome(GuiGraphics g, int mx, int my) {
        Font f = font;
        // close button
        boolean hc = dist(mx, my, width - 16F, 16F) <= 10F;
        PhoneUi.disc(g, width - 16F, 16F, 9F, hc ? 0x66FFFFFF : 0x33FFFFFF);
        PhoneUi.line(g, width - 19.5F, 12.5F, width - 12.5F, 19.5F, 0.6F, 0xFFFFFFFF);
        PhoneUi.line(g, width - 12.5F, 12.5F, width - 19.5F, 19.5F, 0.6F, 0xFFFFFFFF);

        // fold pill
        String label = dual ? "Fold" : "Unfold";
        float pw = 78F;
        pillX0 = (width - pw) * 0.5F;
        pillX1 = pillX0 + pw;
        pillY0 = Math.min(height - 28F, fy + fh + 7F);
        pillY1 = pillY0 + 15F;
        boolean hp = mx >= pillX0 && mx < pillX1 && my >= pillY0 && my < pillY1;
        PhoneUi.rr(g, pillX0, pillY0, pillX1, pillY1, 7.5F, locked ? 0x22FFFFFF : (hp ? 0x55FFFFFF : 0x33FFFFFF));
        // book icon
        float ix = pillX0 + 10F;
        float iy = pillY0 + 7.5F;
        int ic = locked ? 0x88FFFFFF : 0xFFFFFFFF;
        PhoneUi.rrStroke(g, ix - 4F, iy - 3.5F, ix + 4F, iy + 3.5F, 1.2F, 0.5F, ic);
        PhoneUi.rr(g, ix - 0.2F, iy - 3.5F, ix + 0.2F, iy + 3.5F, 0F, ic);
        PhoneUi.text(g, f, label, pillX0 + 20F, pillY0 + 3.6F, 0.9F, ic);
        PhoneUi.textC(g, f, locked ? "Enter or the side button unlocks" : "Tab folds  ·  right-click = home  ·  Esc closes",
                width * 0.5F, pillY1 + 4F, 0.7F, 0x88FFFFFF);

        // volume HUD
        if (volShow > 0) {
            float a = Math.min(1F, volShow / 10F);
            float x0 = width * 0.5F - 46F;
            PhoneUi.rr(g, x0, 8F, x0 + 92F, 24F, 8F, PhoneUi.alpha(0xFF1C1C1E, 0.9F * a));
            PhoneUi.rr(g, x0 + 9F, 14F, x0 + 12.5F, 18F, 0.6F, PhoneUi.alpha(0xFFFFFFFF, a));
            PhoneUi.tri(g, x0 + 12.5F, 14F, x0 + 17.5F, 10.8F, x0 + 17.5F, 21.2F, PhoneUi.alpha(0xFFFFFFFF, a));
            PhoneUi.tri(g, x0 + 12.5F, 18F, x0 + 17.5F, 21.2F, x0 + 17.5F, 10.8F, PhoneUi.alpha(0xFFFFFFFF, a));
            PhoneUi.rr(g, x0 + 26F, 14.2F, x0 + 82F, 17.8F, 1.8F, PhoneUi.alpha(0xFF555555, a));
            PhoneUi.rr(g, x0 + 26F, 14.2F, x0 + 26F + 56F * PhoneData.volume, 17.8F, 1.8F, PhoneUi.alpha(0xFFFFFFFF, a));
        }
    }

    private static float dist(double ax, double ay, double bx, double by) {
        return (float) Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by));
    }

    // ------------------------------------------------------------------ input
    private int paneAt(double mx, double my) {
        if (my < oy || my >= oy + PH * s) return -1;
        if (mx >= ox && mx < ox + PW * s) return 0;
        if (dual && openE > 0.97F && mx >= ox + PW * s && mx < ox + 2F * PW * s) return 1;
        return -1;
    }

    private static boolean in(double x, double y, float[] r, float pad) {
        return x >= r[0] - pad && x < r[2] + pad && y >= r[1] - pad && y < r[3] + pad;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (shotStage != 0) return true;
        if (dist(mx, my, width - 16F, 16F) <= 10F) {
            onClose();
            return true;
        }
        if (mx >= pillX0 && mx < pillX1 && my >= pillY0 && my < pillY1) {
            toggleFold();
            return true;
        }
        if (button == 0) {
            if (in(mx, my, sideRect(), 2F)) {
                if (locked) startScan();
                else lockNow();
                return true;
            }
            for (int i = 0; i < 2; i++) {
                if (in(mx, my, volRect(i), 2F)) {
                    volume(i == 0 ? 1 : -1);
                    return true;
                }
            }
        }
        if (locked) {
            if (button == 0 && mx >= ox && mx < ox + areaW * s && my >= oy && my < oy + PH * s) {
                float gx = ox + areaW * 0.5F * s;
                float gy = oy + 296F * s;
                if (dist(mx, my, gx, gy) <= 30F * s) startScan();
                else wiggle = 12;
            }
            return true;
        }
        int idx = paneAt(mx, my);
        if (idx >= 0) {
            focus = idx;
            dragSlot = idx;
            slots[idx].click(mx, my, button);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragSlot >= 0 && !locked) {
            int d = dragSlot;
            dragSlot = -1;
            slots[d].release(mx, my, button);
        }
        dragSlot = -1;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragSlot >= 0 && !locked && shotStage == 0) slots[dragSlot].drag(mx, my, button, dx, dy);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (locked || shotStage != 0) return true;
        int idx = paneAt(mx, my);
        if (idx < 0) idx = focus;
        slots[idx].scroll(mx, my, delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int mods) {
        if (shotStage != 0) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!locked) {
                PhoneApp a = slots[focus].current;
                if (a.wantsEscape() && a.keyPressed(key, scanCode, mods)) return true;
            }
            onClose();
            return true;
        }
        if (locked) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) startScan();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            toggleFold();
            return true;
        }
        if (slots[focus].current.keyPressed(key, scanCode, mods)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (locked || shotStage != 0) return true;
        return slots[focus].current.charTyped(c, mods);
    }

    // ------------------------------------------------------------------ one pane
    /** one pane of the display: its own home screen and running app, plus the zoom-open/close animation */
    private final class Slot implements PhoneHost {
        final int index;
        final PhonePane pane = new PhonePane();
        final PhonePane appPane = new PhonePane();
        final PhoneApp home;
        PhoneApp current;
        float anim = 1F;
        float animPrev = 1F;
        int mode;
        float fromX = 120F;
        float fromY = 180F;
        boolean gesture;

        Slot(int index) {
            this.index = index;
            this.home = new HomeApp(this);
            this.current = home;
        }

        // ---- PhoneHost
        @Override
        public Font font() {
            return Minecraft.getInstance().font;
        }

        @Override
        public Minecraft mc() {
            return Minecraft.getInstance();
        }

        @Override
        public void launch(String appId) {
            if (current != home || locked) return;
            PhoneApp a = PhoneApps.create(appId, this);
            float[] c = HomeApp.center(appId);
            fromX = c[0];
            fromY = c[1];
            current = a;
            a.onOpen();
            anim = 0F;
            animPrev = 0F;
            mode = 1;
            focus = index;
        }

        @Override
        public void home() {
            if (current != home && mode != -1) {
                mode = -1;
                if (anim >= 1F) animPrev = 1F;
            }
        }

        @Override
        public void tap() {
            if (PhoneData.tapSounds) IPhoneDuoScreen.this.sound(ModSounds.PHONE_TAP.get(), 1F);
        }

        @Override
        public void sound(SoundEvent event, float pitch) {
            IPhoneDuoScreen.this.sound(event, pitch);
        }

        @Override
        public void capturePhoto() {
            IPhoneDuoScreen.this.beginShot();
        }

        @Override
        public boolean isDual() {
            return dual && openE > 0.9F;
        }

        // ---- lifecycle
        void tickSlot() {
            animPrev = anim;
            if (mode == 1) {
                anim = Math.min(1F, anim + 0.11F);
                if (anim >= 1F) mode = 0;
            } else if (mode == -1) {
                anim = Math.max(0F, anim - 0.14F);
                if (anim <= 0F) finishClose();
            }
            home.tick();
            if (current != home) current.tick();
        }

        void finishClose() {
            PhoneApp old = current;
            current = home;
            mode = 0;
            anim = 1F;
            animPrev = 1F;
            gesture = false;
            if (old != home) old.onClose();
        }

        void resetHome() {
            if (current != home) finishClose();
        }

        // ---- drawing
        private void draw(GuiGraphics g, PhoneApp app, PhonePane map, int imx, int imy, float pt) {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(map.ox, map.oy, 0F);
            ps.scale(map.s * map.sx, map.s, 1F);
            app.render(g, map, imx, imy, pt);
            ps.popPose();
        }

        void render(GuiGraphics g, int mx, int my, float pt) {
            PhonePane p = pane;
            int imx = Math.round((float) ((mx - p.ox) / (p.s * p.sx)));
            int imy = Math.round((float) ((my - p.oy) / p.s));
            float a = Mth.lerp(pt, animPrev, anim);
            float e = 1F - (1F - a) * (1F - a) * (1F - a);
            p.scissor(g, 0, 0, PW, PH);
            if (current == home) {
                draw(g, home, p, imx, imy, pt);
            } else {
                if (e < 0.999F) {
                    draw(g, home, p, imx, imy, pt);
                    int x0 = Math.round(p.ox);
                    int y0 = Math.round(p.oy);
                    int x1 = Math.round(p.ox + PW * p.s * p.sx);
                    int y1 = Math.round(p.oy + PH * p.s);
                    g.fill(x0, y0, x1, y1, ((int) (0x70 * e)) << 24);
                    float k = Mth.lerp(e, 0.16F, 1F);
                    float cx = Mth.lerp(e, fromX, 120F);
                    float cy = Mth.lerp(e, fromY, 180F);
                    appPane.s = p.s * k;
                    appPane.sx = p.sx;
                    appPane.ox = p.ox + (cx - 120F * k) * p.s * p.sx;
                    appPane.oy = p.oy + (cy - 180F * k) * p.s;
                    appPane.scissor(g, 0, 0, PW, PH);
                    draw(g, current, appPane, imx, imy, pt);
                    appPane.unscissor(g);
                } else {
                    draw(g, current, p, imx, imy, pt);
                }
            }
            // home indicator
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(p.ox, p.oy, 0F);
            ps.scale(p.s * p.sx, p.s, 1F);
            boolean light = current.lightStatusBar();
            int col = light ? 0xCC000000 : (current == home ? 0x99FFFFFF : 0xDDFFFFFF);
            PhoneUi.rr(g, 78F, 350.4F, 162F, 353.6F, 1.6F, col);
            ps.popPose();
            p.unscissor(g);
        }

        // ---- input
        private float lx(double mx) {
            return (float) ((mx - pane.ox) / (pane.s * pane.sx));
        }

        private float ly(double my) {
            return (float) ((my - pane.oy) / pane.s);
        }

        void click(double mx, double my, int button) {
            if (mode != 0 && current != home) return;
            if (button == 1) {
                home();
                return;
            }
            float y = ly(my);
            if (current != home && y >= 349F) {
                gesture = true;
                return;
            }
            current.mouseClicked(pane, lx(mx), y, button);
        }

        void release(double mx, double my, int button) {
            if (gesture) {
                gesture = false;
                home();
                return;
            }
            if (mode != 0 && current != home) return;
            current.mouseReleased(pane, lx(mx), ly(my), button);
        }

        void drag(double mx, double my, int button, double dx, double dy) {
            if (gesture || (mode != 0 && current != home)) return;
            current.mouseDragged(pane, lx(mx), ly(my), button, dx / (pane.s * pane.sx), dy / pane.s);
        }

        void scroll(double mx, double my, double delta) {
            if (mode != 0 && current != home) return;
            current.mouseScrolled(pane, lx(mx), ly(my), delta);
        }
    }
}
