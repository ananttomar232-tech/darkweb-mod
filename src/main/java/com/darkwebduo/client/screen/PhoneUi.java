package com.darkwebduo.client.screen;

import com.darkwebduo.DarkWebDuoMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Drawing helpers for the phone UI (rounded rectangles, discs, rotated lines, scaled text, textures). */
public final class PhoneUi {
    private PhoneUi() {}

    public static ResourceLocation gui(String name) {
        return new ResourceLocation(DarkWebDuoMod.MODID, "textures/gui/iphone/" + name + ".png");
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    public static int alpha(int color, float f) {
        int a = (int) (((color >>> 24) & 255) * Math.max(0F, Math.min(1F, f)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int c0, int c1, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int a = (int) (((c0 >>> 24) & 255) * (1F - t) + ((c1 >>> 24) & 255) * t);
        int r = (int) (((c0 >> 16) & 255) * (1F - t) + ((c1 >> 16) & 255) * t);
        int gr = (int) (((c0 >> 8) & 255) * (1F - t) + ((c1 >> 8) & 255) * t);
        int b = (int) ((c0 & 255) * (1F - t) + (c1 & 255) * t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    // ------------------------------------------------------------------ shapes
    /** rounded rectangle with half-pixel precision */
    public static void rr(GuiGraphics g, float x0, float y0, float x1, float y1, float r, int c) {
        if (x1 <= x0 || y1 <= y0) return;
        r = Math.min(r, Math.min(x1 - x0, y1 - y0) * 0.5F);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.scale(0.5F, 0.5F, 1F);
        int X0 = Math.round(x0 * 2F), Y0 = Math.round(y0 * 2F), X1 = Math.round(x1 * 2F), Y1 = Math.round(y1 * 2F);
        int R = Math.round(r * 2F);
        if (R <= 1) {
            g.fill(X0, Y0, X1, Y1, c);
        } else {
            g.fill(X0, Y0 + R, X1, Y1 - R, c);
            for (int i = 0; i < R; i++) {
                double dy = R - i - 0.5;
                int inset = (int) Math.round(R - Math.sqrt(Math.max(0.0, (double) R * R - dy * dy)));
                g.fill(X0 + inset, Y0 + i, X1 - inset, Y0 + i + 1, c);
                g.fill(X0 + inset, Y1 - i - 1, X1 - inset, Y1 - i, c);
            }
        }
        ps.popPose();
    }

    /** rounded-rectangle outline of the given thickness (drawn as an outer rounded rect minus an inner one, scanline-wise) */
    public static void rrStroke(GuiGraphics g, float x0, float y0, float x1, float y1, float r, float w, int c) {
        if (x1 <= x0 || y1 <= y0) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.scale(0.5F, 0.5F, 1F);
        int X0 = Math.round(x0 * 2F), Y0 = Math.round(y0 * 2F), X1 = Math.round(x1 * 2F), Y1 = Math.round(y1 * 2F);
        int R = Math.max(1, Math.round(Math.min(r, Math.min(x1 - x0, y1 - y0) * 0.5F) * 2F));
        int W2 = Math.max(1, Math.round(w * 2F));
        int RI = Math.max(0, R - W2);
        int hgt = Y1 - Y0;
        for (int row = 0; row < hgt; row++) {
            int fromTop = row;
            int fromBottom = hgt - 1 - row;
            int d = Math.min(fromTop, fromBottom);
            int inset = 0;
            if (d < R) {
                double dy = R - d - 0.5;
                inset = (int) Math.round(R - Math.sqrt(Math.max(0.0, (double) R * R - dy * dy)));
            }
            int left = X0 + inset;
            int right = X1 - inset;
            if (d < W2) {
                g.fill(left, Y0 + row, right, Y0 + row + 1, c);
            } else {
                int insetI = 0;
                int dI = d - W2;
                if (dI < RI) {
                    double dy2 = RI - dI - 0.5;
                    insetI = (int) Math.round(RI - Math.sqrt(Math.max(0.0, (double) RI * RI - dy2 * dy2)));
                }
                int il = X0 + W2 + insetI;
                int ir = X1 - W2 - insetI;
                g.fill(left, Y0 + row, Math.max(left, il), Y0 + row + 1, c);
                g.fill(Math.min(right, ir), Y0 + row, right, Y0 + row + 1, c);
            }
        }
        ps.popPose();
    }

    public static void disc(GuiGraphics g, float cx, float cy, float r, int c) {
        if (r <= 0F) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0F);
        ps.scale(0.25F, 0.25F, 1F);
        int R = Math.max(1, Math.round(r * 4F));
        for (int yy = -R; yy < R; yy++) {
            double dy = yy + 0.5;
            int hw = (int) Math.round(Math.sqrt(Math.max(0.0, (double) R * R - dy * dy)));
            g.fill(-hw, yy, hw, yy + 1, c);
        }
        ps.popPose();
    }

    public static void ring(GuiGraphics g, float cx, float cy, float r, float thick, int c) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0F);
        ps.scale(0.25F, 0.25F, 1F);
        int R = Math.max(2, Math.round(r * 4F));
        int Ri = Math.max(0, Math.round((r - thick) * 4F));
        for (int yy = -R; yy < R; yy++) {
            double dy = yy + 0.5;
            int ho = (int) Math.round(Math.sqrt(Math.max(0.0, (double) R * R - dy * dy)));
            if (Math.abs(dy) < Ri) {
                int hi = (int) Math.round(Math.sqrt(Math.max(0.0, (double) Ri * Ri - dy * dy)));
                g.fill(-ho, yy, -hi, yy + 1, c);
                g.fill(hi, yy, ho, yy + 1, c);
            } else {
                g.fill(-ho, yy, ho, yy + 1, c);
            }
        }
        ps.popPose();
    }

    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float w, int c) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x0, y0, 0F);
        ps.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        ps.scale(0.25F, 0.25F, 1F);
        g.fill(0, Math.round(-w * 2F), Math.round(len * 4F), Math.round(w * 2F), c);
        ps.popPose();
    }

    /** filled triangle (quarter-pixel scanlines) */
    public static void tri(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, int c) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.scale(0.25F, 0.25F, 1F);
        double[][] e = {
                {x0 * 4.0, y0 * 4.0, x1 * 4.0, y1 * 4.0},
                {x1 * 4.0, y1 * 4.0, x2 * 4.0, y2 * 4.0},
                {x2 * 4.0, y2 * 4.0, x0 * 4.0, y0 * 4.0}};
        int ymin = (int) Math.floor(Math.min(y0, Math.min(y1, y2)) * 4F);
        int ymax = (int) Math.ceil(Math.max(y0, Math.max(y1, y2)) * 4F);
        for (int y = ymin; y < ymax; y++) {
            double yy = y + 0.5;
            double xl = Double.MAX_VALUE;
            double xr = -Double.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                double ay = e[i][1];
                double by = e[i][3];
                if (yy >= Math.min(ay, by) && yy < Math.max(ay, by)) {
                    double t = (yy - ay) / (by - ay);
                    double x = e[i][0] + t * (e[i][2] - e[i][0]);
                    xl = Math.min(xl, x);
                    xr = Math.max(xr, x);
                }
            }
            if (xr > xl) g.fill((int) Math.round(xl), y, (int) Math.round(xr), y + 1, c);
        }
        ps.popPose();
    }

    /** the four outside corners of a rectangle (everything the rounded rect would NOT cover) */
    public static void cornerMask(GuiGraphics g, float x0, float y0, float x1, float y1, float r, int c) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.scale(0.5F, 0.5F, 1F);
        int X0 = Math.round(x0 * 2F), Y0 = Math.round(y0 * 2F), X1 = Math.round(x1 * 2F), Y1 = Math.round(y1 * 2F);
        int R = Math.round(r * 2F);
        for (int i = 0; i < R; i++) {
            double dy = R - i - 0.5;
            int inset = (int) Math.round(R - Math.sqrt(Math.max(0.0, (double) R * R - dy * dy)));
            if (inset <= 0) continue;
            g.fill(X0, Y0 + i, X0 + inset, Y0 + i + 1, c);
            g.fill(X1 - inset, Y0 + i, X1, Y0 + i + 1, c);
            g.fill(X0, Y1 - i - 1, X0 + inset, Y1 - i, c);
            g.fill(X1 - inset, Y1 - i - 1, X1, Y1 - i, c);
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------ text
    public static void text(GuiGraphics g, Font f, String s, float x, float y, float sc, int color) {
        if (s == null || s.isEmpty()) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0F);
        ps.scale(sc, sc, 1F);
        g.drawString(f, s, 0, 0, color, false);
        ps.popPose();
    }

    public static void textShadow(GuiGraphics g, Font f, String s, float x, float y, float sc, int color) {
        if (s == null || s.isEmpty()) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0F);
        ps.scale(sc, sc, 1F);
        g.drawString(f, s, 0, 0, color, true);
        ps.popPose();
    }

    public static float width(Font f, String s, float sc) {
        return f.width(s) * sc;
    }

    public static void textC(GuiGraphics g, Font f, String s, float cx, float y, float sc, int color) {
        text(g, f, s, cx - width(f, s, sc) * 0.5F, y, sc, color);
    }

    public static void textR(GuiGraphics g, Font f, String s, float rx, float y, float sc, int color) {
        text(g, f, s, rx - width(f, s, sc), y, sc, color);
    }

    // ------------------------------------------------------------------ textures
    /** draws a whole texture into a float rectangle */
    public static void image(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, int texW, int texH) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0F);
        ps.scale(w / texW, h / texH, 1F);
        g.blit(tex, 0, 0, 0, 0F, 0F, texW, texH, texW, texH);
        ps.popPose();
    }

    /** draws a sub-rectangle (u,v,uw,vh in texels) of a texture into a float rectangle */
    public static void imageRegion(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h,
                                   float u, float v, int uw, int vh, int texW, int texH) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0F);
        ps.scale(w / uw, h / vh, 1F);
        g.blit(tex, 0, 0, 0, u, v, uw, vh, texW, texH);
        ps.popPose();
    }

    public static boolean inside(double x, double y, float x0, float y0, float x1, float y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }
}
