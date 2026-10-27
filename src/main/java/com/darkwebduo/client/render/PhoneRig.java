package com.darkwebduo.client.render;

import com.darkwebduo.DarkWebDuoMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The 3D model of the iPhone Duo, assembled from real geometry every frame (no JSON model, no flat placeholder):
 * two rounded titanium panels joined by a barrel hinge, ceramic outer glass, a two-lens camera plateau, side buttons with a
 * Touch ID cap, speaker grilles and a USB-C port. Fold angle is continuous: fold=0 is the flat 7.6" inner display,
 * fold=1 is the closed 5.4" book with the outer display facing the viewer (+z).
 *
 * Coordinates: model origin = bottom of the hinge, +x right, +y up, +z towards the viewer when the phone is unfolded.
 * 1 unit = 1 block; one panel is 0.46 x 0.69 (2:3, so two panels make the 4:3 inner display).
 */
public final class PhoneRig {
    public static final float PW = 0.46F;
    public static final float PH = 0.69F;
    public static final float T = 0.030F;
    public static final float GAP = 0.0016F;
    public static final float R_OUT = 0.050F;
    public static final float R_HINGE = 0.008F;
    public static final float LIP = 0.0024F;

    private static final float ZF = -GAP;
    private static final float ZB = -T;

    // inner display (both halves) and outer display rectangles, panel-local x (hinge at 0)
    private static final float ID_X0 = 0.0015F;
    private static final float ID_X1 = PW - 0.0115F;
    private static final float ID_Y0 = 0.0098F;
    private static final float ID_Y1 = PH - 0.0098F;
    private static final float OD_X0 = 0.0115F;
    private static final float OD_X1 = PW - 0.0115F;
    private static final float OD_Y0 = 0.01725F;
    private static final float OD_Y1 = PH - 0.01725F;

    public static final int SCREEN_OFF = 0;
    public static final int SCREEN_BOOT = 1;
    public static final int SCREEN_ON = 2;

    private static final int FULL = LightTexture.FULL_BRIGHT;
    private static final int NO_OVERLAY = OverlayTexture.NO_OVERLAY;

    private static ResourceLocation res(String n) {
        return new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/iphone/" + n + ".png");
    }

    private static final ResourceLocation[] ATLAS = {res("atlas_star"), res("atlas_night")};
    private static final ResourceLocation[] BACK = {res("back_star"), res("back_night")};
    private static final ResourceLocation INNER_L = res("inner_left");
    private static final ResourceLocation INNER_R = res("inner_right");
    private static final ResourceLocation OUTER_GLASS = res("outer_glass");
    private static final ResourceLocation SCR_OUTER_LOCK = res("screen_outer_lock");
    private static final ResourceLocation SCR_INNER_HOME = res("screen_inner_home");
    private static final ResourceLocation SCR_BOOT_OUTER = res("screen_boot_outer");
    private static final ResourceLocation SCR_BOOT_INNER = res("screen_boot_inner");

    private static RenderUtil.Uv uv(float[] r) {
        return new RenderUtil.Uv(r[0], r[1], r[2], r[3]);
    }

    private static final RenderUtil.Uv UV_BRUSHED = uv(PhoneUv.BRUSHED);
    private static final RenderUtil.Uv UV_TOUCHID = uv(PhoneUv.TOUCHID);

    /** Everything the renderer decides per frame. */
    public static final class State {
        public float fold = 1F;
        public int colorway;
        public int screen = SCREEN_ON;
        public float screenLevel = 1F;
        public float bodyAlpha = 1F;
        public String clock = "9:41";
        public String date = "";
        public String info = "";
    }

    // ------------------------------------------------------------------ outlines (computed once)
    private static final float[][] OUT = rrOutline(0F, 0F, PW, PH, R_OUT, R_HINGE, 8, 2);
    private static final float[][] INSET = rrOutline(0F, LIP, PW - LIP, PH - LIP, R_OUT - LIP, R_HINGE, 8, 2);

    private static float[][] rrOutline(float x0, float y0, float x1, float y1, float rOut, float rLeft, int nBig, int nSmall) {
        List<float[]> pts = new ArrayList<float[]>();
        arc(pts, x0 + rLeft, y0 + rLeft, rLeft, 180F, 270F, nSmall);
        arc(pts, x1 - rOut, y0 + rOut, rOut, 270F, 360F, nBig);
        arc(pts, x1 - rOut, y1 - rOut, rOut, 0F, 90F, nBig);
        arc(pts, x0 + rLeft, y1 - rLeft, rLeft, 90F, 180F, nSmall);
        return pts.toArray(new float[0][]);
    }

    private static float[][] plateOutline(float x0, float y0, float x1, float y1, float r) {
        return rrOutline(x0, y0, x1, y1, r, r, 6, 6);
    }

    private static void arc(List<float[]> pts, float cx, float cy, float r, float a0, float a1, int n) {
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / (float) n);
            pts.add(new float[] {cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r});
        }
    }

    private static float dist(float[] p, float[] q) {
        float dx = q[0] - p[0], dy = q[1] - p[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ------------------------------------------------------------------ vertex helpers
    private static void vtx(VertexConsumer vc, PoseStack.Pose pose, float sx, float x, float y, float z, float u, float v,
                            float nx, float ny, float nz, int light, float a) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        RenderUtil.vertex(vc, m, n, sx * x, y, z, u, v, light, NO_OVERLAY, 1F, 1F, 1F, a, sx * nx, ny, nz);
    }

    /** titanium side wall of an outline between z=zb (back) and z=zf (front) */
    private static void walls(VertexConsumer vc, PoseStack.Pose pose, float[][] ol, float sx, float zf, float zb,
                              float[] reg, int light, float a) {
        int n = ol.length;
        float per = 0F;
        for (int i = 0; i < n; i++) per += dist(ol[i], ol[(i + 1) % n]);
        float acc = 0F;
        for (int i = 0; i < n; i++) {
            float[] p = ol[i];
            float[] q = ol[(i + 1) % n];
            float len = dist(p, q);
            if (len < 1.0E-5F) continue;
            float nx = (q[1] - p[1]) / len;
            float ny = -(q[0] - p[0]) / len;
            float ua = Mth.lerp(acc / per, reg[0], reg[2]);
            float ub = Mth.lerp((acc + len) / per, reg[0], reg[2]);
            vtx(vc, pose, sx, p[0], p[1], zb, ua, reg[3], nx, ny, 0F, light, a);
            vtx(vc, pose, sx, q[0], q[1], zb, ub, reg[3], nx, ny, 0F, light, a);
            vtx(vc, pose, sx, q[0], q[1], zf, ub, reg[1], nx, ny, 0F, light, a);
            vtx(vc, pose, sx, p[0], p[1], zf, ua, reg[1], nx, ny, 0F, light, a);
            acc += len;
        }
    }

    /** flat metal lip between an outline and its inset (the bright rim you see from the front) */
    private static void lip(VertexConsumer vc, PoseStack.Pose pose, float[][] outer, float[][] inner, float sx, float z, float nz,
                            float[] reg, int light, float a) {
        int n = outer.length;
        float per = 0F;
        for (int i = 0; i < n; i++) per += dist(outer[i], outer[(i + 1) % n]);
        float acc = 0F;
        float vA = Mth.lerp(0.10F, reg[1], reg[3]);
        float vB = Mth.lerp(0.26F, reg[1], reg[3]);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float len = dist(outer[i], outer[j]);
            float ua = Mth.lerp(acc / per, reg[0], reg[2]);
            float ub = Mth.lerp((acc + len) / per, reg[0], reg[2]);
            vtx(vc, pose, sx, outer[i][0], outer[i][1], z, ua, vA, 0F, 0F, nz, light, a);
            vtx(vc, pose, sx, outer[j][0], outer[j][1], z, ub, vA, 0F, 0F, nz, light, a);
            vtx(vc, pose, sx, inner[j][0], inner[j][1], z, ub, vB, 0F, 0F, nz, light, a);
            vtx(vc, pose, sx, inner[i][0], inner[i][1], z, ua, vB, 0F, 0F, nz, light, a);
            acc += len;
        }
    }

    /** triangle fan over a convex outline with a planar (linear) UV map: u = ua + ub*x, v = va + vb*y */
    private static void fan(VertexConsumer vc, PoseStack.Pose pose, float[][] ol, float sx, float z, float nz,
                            float ua, float ub, float va, float vb, int light, float a) {
        int n = ol.length;
        float cx = 0F, cy = 0F;
        for (int i = 0; i < n; i++) {
            cx += ol[i][0];
            cy += ol[i][1];
        }
        cx /= n;
        cy /= n;
        for (int i = 0; i < n; i++) {
            float[] p = ol[i];
            float[] q = ol[(i + 1) % n];
            vtx(vc, pose, sx, cx, cy, z, ua + ub * cx, va + vb * cy, 0F, 0F, nz, light, a);
            vtx(vc, pose, sx, p[0], p[1], z, ua + ub * p[0], va + vb * p[1], 0F, 0F, nz, light, a);
            vtx(vc, pose, sx, q[0], q[1], z, ua + ub * q[0], va + vb * q[1], 0F, 0F, nz, light, a);
            vtx(vc, pose, sx, q[0], q[1], z, ua + ub * q[0], va + vb * q[1], 0F, 0F, nz, light, a);
        }
    }

    /** fan whose UVs fill an atlas region across the bounding box of the outline (used for the plateau top) */
    private static void fanRegion(VertexConsumer vc, PoseStack.Pose pose, float[][] ol, float sx, float z, float nz,
                                  float x0, float y0, float x1, float y1, float[] reg, int light, float a) {
        float du = reg[2] - reg[0];
        float dv = reg[3] - reg[1];
        float ua = reg[0] - du * x0 / (x1 - x0);
        float ub = du / (x1 - x0);
        float vb = -dv / (y1 - y0);
        float va = reg[1] - vb * y1;
        fan(vc, pose, ol, sx, z, nz, ua, ub, va, vb, light, a);
    }

    private static void discZ(VertexConsumer vc, PoseStack.Pose pose, float sx, float cx, float cy, float z, float r, float nz,
                              float[] reg, int seg, int light, float a) {
        float uc = (reg[0] + reg[2]) * 0.5F, vc0 = (reg[1] + reg[3]) * 0.5F;
        float hw = (reg[2] - reg[0]) * 0.5F, hh = (reg[3] - reg[1]) * 0.5F;
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vtx(vc, pose, sx, cx, cy, z, uc, vc0, 0F, 0F, nz, light, a);
            vtx(vc, pose, sx, cx + c0 * r, cy + s0 * r, z, uc + c0 * hw, vc0 - s0 * hh, 0F, 0F, nz, light, a);
            vtx(vc, pose, sx, cx + c1 * r, cy + s1 * r, z, uc + c1 * hw, vc0 - s1 * hh, 0F, 0F, nz, light, a);
            vtx(vc, pose, sx, cx + c1 * r, cy + s1 * r, z, uc + c1 * hw, vc0 - s1 * hh, 0F, 0F, nz, light, a);
        }
    }

    /** cylinder whose axis is parallel to z (lens barrels) */
    private static void cylZ(VertexConsumer vc, PoseStack.Pose pose, float sx, float cx, float cy, float r, float zA, float zB,
                             int seg, float[] reg, int light, float a) {
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float ua = Mth.lerp(k / (float) seg, reg[0], reg[2]);
            float ub = Mth.lerp((k + 1) / (float) seg, reg[0], reg[2]);
            vtx(vc, pose, sx, cx + c0 * r, cy + s0 * r, zA, ua, reg[3], c0, s0, 0F, light, a);
            vtx(vc, pose, sx, cx + c1 * r, cy + s1 * r, zA, ub, reg[3], c1, s1, 0F, light, a);
            vtx(vc, pose, sx, cx + c1 * r, cy + s1 * r, zB, ub, reg[1], c1, s1, 0F, light, a);
            vtx(vc, pose, sx, cx + c0 * r, cy + s0 * r, zB, ua, reg[1], c0, s0, 0F, light, a);
        }
    }

    /** cylinder whose axis is parallel to y (the hinge barrel) */
    private static void cylY(VertexConsumer vc, PoseStack.Pose pose, float cx, float cz, float r, float y0, float y1, int seg,
                             float[] reg, int light, float a) {
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float ua = Mth.lerp(k / (float) seg, reg[0], reg[2]);
            float ub = Mth.lerp((k + 1) / (float) seg, reg[0], reg[2]);
            vtx(vc, pose, 1F, cx + c0 * r, y0, cz + s0 * r, ua, reg[3], c0, 0F, s0, light, a);
            vtx(vc, pose, 1F, cx + c1 * r, y0, cz + s1 * r, ub, reg[3], c1, 0F, s1, light, a);
            vtx(vc, pose, 1F, cx + c1 * r, y1, cz + s1 * r, ub, reg[1], c1, 0F, s1, light, a);
            vtx(vc, pose, 1F, cx + c0 * r, y1, cz + s0 * r, ua, reg[1], c0, 0F, s0, light, a);
        }
    }

    private static RenderType type(ResourceLocation tex, boolean translucent) {
        return translucent ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex);
    }

    // ------------------------------------------------------------------ the phone
    public static void draw(PoseStack ps, MultiBufferSource buf, Font font, int light, State st) {
        final float fold = Mth.clamp(st.fold, 0F, 1F);
        final float theta = (float) Math.PI * fold;
        final float a = st.bodyAlpha;
        final boolean tr = a < 0.995F;
        final int cw = st.colorway == 1 ? 1 : 0;

        ps.pushPose();
        ps.translate(PW * 0.5F * fold, 0F, T * 0.5F * (1F - fold));

        // ---------------------------------------------------------- pass 1: titanium, hinge, buttons, camera (atlas)
        VertexConsumer vc = buf.getBuffer(type(ATLAS[cw], tr));
        // left panel A (camera panel), x grows to -x
        panelMetal(vc, ps.last(), -1F, light, a);
        platea(vc, ps.last(), light, a);
        bottomDetails(vc, ps.last(), light, a);
        volumeButtons(vc, ps.last(), light, a);
        hinge(vc, ps.last(), fold, light, a);
        // right panel B (outer display panel) swings by theta around the hinge
        ps.pushPose();
        ps.mulPose(Axis.YP.rotation(-theta));
        panelMetal(vc, ps.last(), 1F, light, a);
        touchIdButton(vc, ps.last(), light, a);
        ps.popPose();

        // ---------------------------------------------------------- pass 2/3: glass panels
        VertexConsumer inl = buf.getBuffer(type(INNER_L, tr));
        fan(inl, ps.last(), INSET, -1F, ZF, 1F, 1F, -1F / PW, 1F, -1F / PH, light, a);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotation(-theta));
        VertexConsumer inr = buf.getBuffer(type(INNER_R, tr));
        fan(inr, ps.last(), INSET, 1F, ZF, 1F, 0F, 1F / PW, 1F, -1F / PH, light, a);
        ps.popPose();

        VertexConsumer back = buf.getBuffer(type(BACK[cw], tr));
        fan(back, ps.last(), INSET, -1F, ZB, -1F, 0F, 1F / PW, 1F, -1F / PH, light, a);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotation(-theta));
        VertexConsumer outer = buf.getBuffer(type(OUTER_GLASS, tr));
        fan(outer, ps.last(), INSET, 1F, ZB, -1F, 1F, -1F / PW, 1F, -1F / PH, light, a);
        ps.popPose();

        // ---------------------------------------------------------- pass 4: displays (additive, emissive)
        if (st.screen != SCREEN_OFF && st.screenLevel > 0.01F) {
            float br = st.screenLevel;
            boolean boot = st.screen == SCREEN_BOOT;
            ps.pushPose();
            ps.mulPose(Axis.YP.rotation(-theta));
            VertexConsumer e1 = buf.getBuffer(RenderType.eyes(boot ? SCR_BOOT_OUTER : SCR_OUTER_LOCK));
            float zo = ZB - 0.0004F;
            RenderUtil.quad(e1, ps.last(), OD_X1, OD_Y0, zo, OD_X0, OD_Y0, zo, OD_X0, OD_Y1, zo, OD_X1, OD_Y1, zo,
                    RenderUtil.Uv.full(), 0F, 0F, -1F, FULL, NO_OVERLAY, br, br, br, 1F);
            ps.popPose();
            if (fold < 0.985F) {
                VertexConsumer e2 = buf.getBuffer(RenderType.eyes(boot ? SCR_BOOT_INNER : SCR_INNER_HOME));
                float zi = ZF + 0.0004F;
                RenderUtil.quad(e2, ps.last(), -ID_X1, ID_Y0, zi, -ID_X0, ID_Y0, zi, -ID_X0, ID_Y1, zi, -ID_X1, ID_Y1, zi,
                        new RenderUtil.Uv(0F, 0F, 0.5F, 1F), 0F, 0F, 1F, FULL, NO_OVERLAY, br, br, br, 1F);
                ps.pushPose();
                ps.mulPose(Axis.YP.rotation(-theta));
                RenderUtil.quad(e2, ps.last(), ID_X0, ID_Y0, zi, ID_X1, ID_Y0, zi, ID_X1, ID_Y1, zi, ID_X0, ID_Y1, zi,
                        new RenderUtil.Uv(0.5F, 0F, 1F, 1F), 0F, 0F, 1F, FULL, NO_OVERLAY, br, br, br, 1F);
                ps.popPose();
            }
            // ---------------------------------------------------------- pass 5: live text on the glass
            if (!boot && font != null) liveText(ps, buf, font, st, theta, fold, br);
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------ parts
    /** side walls + metal lips of one panel (sx=+1 right panel B, -1 left panel A) */
    private static void panelMetal(VertexConsumer vc, PoseStack.Pose pose, float sx, int light, float a) {
        walls(vc, pose, OUT, sx, ZF, ZB, PhoneUv.FRAME, light, a);
        lip(vc, pose, OUT, INSET, sx, ZF, 1F, PhoneUv.FRAME, light, a);
        lip(vc, pose, OUT, INSET, sx, ZB, -1F, PhoneUv.FRAME, light, a);
    }

    /** rear camera plateau (two 48MP lenses + flash + mic) on the back of the left panel */
    private static void platea(VertexConsumer vc, PoseStack.Pose pose, int light, float a) {
        final float hp = 0.0105F;
        final float lh = 0.0042F;
        float x0 = PhoneUv.BACK_PLATEAU[0] * PW, x1 = PhoneUv.BACK_PLATEAU[2] * PW;
        float y1 = PH * (1F - PhoneUv.BACK_PLATEAU[1]), y0 = PH * (1F - PhoneUv.BACK_PLATEAU[3]);
        float[][] pl = plateOutline(x0, y0, x1, y1, 0.026F);
        float zTop = ZB - hp;
        walls(vc, pose, pl, -1F, ZB, zTop, PhoneUv.FRAME, light, a);
        fanRegion(vc, pose, pl, -1F, zTop, -1F, x0, y0, x1, y1, PhoneUv.PLATEAU, light, a);
        // lenses
        lens(vc, pose, PhoneUv.LENS_MAIN_C, PhoneUv.LENS_MAIN, zTop, lh, light, a);
        lens(vc, pose, PhoneUv.LENS_WIDE_C, PhoneUv.LENS_WIDE, zTop, lh, light, a);
        // flash + microphone
        float fx = PhoneUv.FLASH_C[0] * PW, fy = PH * (1F - PhoneUv.FLASH_C[1]), fr = PhoneUv.FLASH_C[2] * PW;
        discZ(vc, pose, -1F, fx, fy, zTop - 0.0007F, fr, -1F, PhoneUv.FLASH, 14, light, a);
        float mx = PhoneUv.MIC_C[0] * PW, my = PH * (1F - PhoneUv.MIC_C[1]), mr = PhoneUv.MIC_C[2] * PW;
        discZ(vc, pose, -1F, mx, my, zTop - 0.0005F, mr, -1F, PhoneUv.MIC, 8, light, a);
    }

    private static void lens(VertexConsumer vc, PoseStack.Pose pose, float[] c, float[] topReg, float zTop, float h, int light, float a) {
        float cx = c[0] * PW, cy = PH * (1F - c[1]), r = c[2] * PW;
        float zl = zTop - h;
        cylZ(vc, pose, -1F, cx, cy, r, zTop, zl, 24, PhoneUv.FRAME, light, a);
        discZ(vc, pose, -1F, cx, cy, zl, r, -1F, topReg, 28, light, a);
        // thin metal collar at the base
        cylZ(vc, pose, -1F, cx, cy, r * 1.10F, zTop, zTop - 0.0012F, 24, PhoneUv.FRAME, light, a);
    }

    /** USB-C port and speaker / microphone grilles on the bottom edge of the left panel */
    private static void bottomDetails(VertexConsumer vc, PoseStack.Pose pose, int light, float a) {
        float zc = (ZB + ZF) * 0.5F;
        float hz = 0.0054F;
        float y = -0.0004F;
        quadBottom(vc, pose, -(PW * 0.5F + 0.026F), -(PW * 0.5F - 0.026F), zc - hz, zc + hz, y, PhoneUv.PORT, light, a);
        quadBottom(vc, pose, -(PW * 0.5F - 0.058F), -(PW * 0.5F - 0.158F), zc - hz, zc + hz, y, PhoneUv.GRILLE, light, a);
        quadBottom(vc, pose, -(PW * 0.5F + 0.058F), -(PW * 0.5F + 0.158F), zc - hz, zc + hz, y, PhoneUv.GRILLE, light, a);
    }

    private static void quadBottom(VertexConsumer vc, PoseStack.Pose pose, float xa, float xb, float za, float zb, float y,
                                   float[] reg, int light, float a) {
        vtx(vc, pose, 1F, xa, y, za, reg[0], reg[1], 0F, -1F, 0F, light, a);
        vtx(vc, pose, 1F, xb, y, za, reg[2], reg[1], 0F, -1F, 0F, light, a);
        vtx(vc, pose, 1F, xb, y, zb, reg[2], reg[3], 0F, -1F, 0F, light, a);
        vtx(vc, pose, 1F, xa, y, zb, reg[0], reg[3], 0F, -1F, 0F, light, a);
    }

    /** two volume keys and the action key on the outer edge of the left panel */
    private static void volumeButtons(VertexConsumer vc, PoseStack.Pose pose, int light, float a) {
        float zc = (ZB + ZF) * 0.5F;
        float hz = 0.0075F;
        float[][] keys = {{0.46F, 0.52F}, {0.375F, 0.445F}, {0.285F, 0.355F}};
        for (int i = 0; i < keys.length; i++) {
            RenderUtil.cuboid(vc, pose, -PW - 0.0042F, keys[i][0] - 0.0F, zc - hz, -PW + 0.0008F, keys[i][1], zc + hz,
                    UV_BRUSHED, light, NO_OVERLAY, 1F, 1F, 1F, a);
        }
    }

    /** the Touch ID side button on the outer edge of the right panel */
    private static void touchIdButton(VertexConsumer vc, PoseStack.Pose pose, int light, float a) {
        float zc = (ZB + ZF) * 0.5F;
        float hz = 0.0105F;
        RenderUtil.cuboid(vc, pose, PW - 0.0008F, 0.405F, zc - hz, PW + 0.0050F, 0.510F, zc + hz,
                UV_BRUSHED, UV_BRUSHED, UV_BRUSHED, UV_BRUSHED, UV_BRUSHED, UV_TOUCHID, light, NO_OVERLAY, 1F, 1F, 1F, a);
    }

    /** barrel hinge: polished knuckles alternating with carbon-fibre plates; grows into the spine as the phone closes */
    private static void hinge(VertexConsumer vc, PoseStack.Pose pose, float fold, int light, float a) {
        float rOpen = (T - GAP) * 0.5F - 0.0006F;
        float r = Mth.lerp(fold, rOpen, T);
        float zc = Mth.lerp(fold, -(T + GAP) * 0.5F, 0F);
        float y0 = 0.010F, y1 = PH - 0.010F;
        float[][] bands = {{0.010F, 0.095F, 1F}, {0.095F, 0.255F, 0F}, {0.255F, 0.325F, 1F}, {0.325F, 0.365F, 0F},
                {0.365F, 0.435F, 1F}, {0.435F, 0.595F, 0F}, {0.595F, 0.680F, 1F}};
        for (int i = 0; i < bands.length; i++) {
            boolean knuckle = bands[i][2] > 0.5F;
            float rr = knuckle ? r + 0.0014F : r;
            cylY(vc, pose, 0F, zc, rr, Math.max(y0, bands[i][0]), Math.min(y1, bands[i][1]), 16,
                    knuckle ? PhoneUv.HINGE_TI : PhoneUv.CARBON, light, a);
        }
    }

    // ------------------------------------------------------------------ live text
    private static void text(PoseStack ps, MultiBufferSource buf, Font font, String s, float x, float y, float z, boolean back,
                             float k, int argb, boolean center) {
        if (s == null || s.isEmpty()) return;
        ps.pushPose();
        ps.translate(x, y, z);
        if (back) ps.mulPose(Axis.YP.rotationDegrees(180F));
        ps.scale(k, -k, k);
        float w = font.width(s);
        font.drawInBatch(s, center ? -w * 0.5F : 0F, 0F, argb, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, FULL);
        ps.popPose();
    }

    private static int white(float alpha) {
        int al = Mth.clamp((int) (alpha * 255F), 0, 255);
        return (al << 24) | 0xFFFFFF;
    }

    private static void liveText(PoseStack ps, MultiBufferSource buf, Font font, State st, float theta, float fold, float br) {
        // ---- outer display (back of the right panel, faces the viewer when closed)
        ps.pushPose();
        ps.mulPose(Axis.YP.rotation(-theta));
        float lu = (OD_X1 - OD_X0) / 240F;
        float zt = ZB - 0.0010F;
        float left = OD_X1;
        float top = OD_Y1;
        text(ps, buf, font, st.date, left - 120F * lu, top - 70F * lu, zt, true, lu * 10.5F / 8F, white(0.92F * br), true);
        text(ps, buf, font, st.clock, left - 120F * lu, top - 84F * lu, zt, true, lu * 58F / 8F, white(0.97F * br), true);
        ps.popPose();
        // ---- inner display, left pane (home screen widget + status time)
        if (fold < 0.985F) {
            float liu = (ID_X1 - ID_X0) / 240F;
            float zi = ZF + 0.0010F;
            float x0 = -ID_X1;
            float yTop = ID_Y1;
            text(ps, buf, font, st.clock, x0 + 22F * liu, yTop - 9.5F * liu, zi, false, liu * 10.5F / 8F, white(0.95F * br), false);
            text(ps, buf, font, st.clock, x0 + 28F * liu, yTop - 42F * liu, zi, false, liu * 36F / 8F, white(0.97F * br), false);
            text(ps, buf, font, st.date, x0 + 28F * liu, yTop - 84F * liu, zi, false, liu * 9.5F / 8F, white(0.90F * br), false);
            text(ps, buf, font, st.info, x0 + 28F * liu, yTop - 97F * liu, zi, false, liu * 8.5F / 8F, white(0.70F * br), false);
        }
    }
}
