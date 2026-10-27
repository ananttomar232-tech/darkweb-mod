package com.darkwebduo.client.cutscene;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Draws letterbox bars, typewriter captions, glitch strips and the colour flash on top of everything. */
public final class CutsceneHud {
    private CutsceneHud() {}

    private static int withAlpha(int rgb, float a) {
        int ia = Math.max(0, Math.min(255, (int) (a * 255F)));
        return (ia << 24) | (rgb & 0x00FFFFFF);
    }

    public static void render(GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // ---- colour flash (works with and without a cutscene)
        float fa = CameraFx.flashAlpha(partial);
        if (fa > 0.003F) {
            g.fill(0, 0, w, h, withAlpha(CameraFx.flashRgb(), Math.min(1F, fa)));
        }

        if (!CutsceneClient.isActive()) return;
        CutsceneScripts.Script script = CutsceneClient.script();
        if (script == null) return;
        float t = CutsceneClient.time(partial);

        // ---- vignette: dark edges
        int edge = Math.max(8, h / 14);
        for (int i = 0; i < 6; i++) {
            float a = 0.20F * (1F - i / 6F);
            g.fill(0, i * edge / 6, w, (i + 1) * edge / 6, withAlpha(0x000000, a));
            g.fill(0, h - (i + 1) * edge / 6, w, h - i * edge / 6, withAlpha(0x000000, a));
        }

        // ---- glitch strips
        float gl = CutsceneClient.glitch(partial);
        if (gl > 0F) {
            Random r = new Random((long) (t * 2.0F) * 7919L + 13L);
            int strips = 2 + (int) (gl * 9F);
            for (int i = 0; i < strips; i++) {
                int y = r.nextInt(Math.max(1, h));
                int sh = 2 + r.nextInt(2 + (int) (gl * 14F));
                int off = (r.nextInt(41) - 20) * (int) Math.max(1F, gl * 2F);
                int col = r.nextBoolean() ? 0x19E6FF : 0xFF2BD6;
                g.fill(Math.max(0, off), y, w + Math.min(0, off), y + sh, withAlpha(col, 0.10F + 0.18F * gl));
            }
        }

        // ---- captions
        for (CutsceneScripts.Caption c : script.captions) {
            if (t < c.from || t > c.to) continue;
            float local = t - c.from;
            float fadeOut = Math.min(1F, (c.to - t) / 6F);
            float fadeIn = Math.min(1F, local / 4F);
            float alpha = Math.max(0F, Math.min(fadeIn, fadeOut));
            int chars = Math.min(c.title.length(), (int) (local * 1.4F));

            if (c.big) {
                float scale = Math.max(2F, Math.min(5F, w / (float) Math.max(1, font.width(c.title)) * 0.55F));
                String shown = c.title.substring(0, chars);
                int tw = (int) (font.width(c.title) * scale);
                float x = (w - tw) / 2F;
                float y = h * 0.74F;
                boolean glitching = gl > 0.2F || (local < 14F);
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(scale, scale, 1F);
                if (glitching) {
                    Random r = new Random((long) (t * 3F));
                    g.drawString(font, shown, r.nextInt(5) - 2, 0, withAlpha(0xFF2BD6, alpha * 0.55F), false);
                    g.drawString(font, shown, r.nextInt(5) - 2, 0, withAlpha(0x19E6FF, alpha * 0.55F), false);
                }
                g.drawString(font, shown, 0, 0, withAlpha(c.rgb, alpha), false);
                g.pose().popPose();
                if (c.sub != null && !c.sub.isEmpty() && local > 12F) {
                    int sc = (int) Math.min(c.sub.length(), (local - 12F) * 1.2F);
                    String ss = c.sub.substring(0, Math.max(0, sc));
                    float s2 = Math.max(1F, scale * 0.38F);
                    float sx = (w - font.width(c.sub) * s2) / 2F;
                    g.pose().pushPose();
                    g.pose().translate(sx, y + 9F * scale + 6F, 0);
                    g.pose().scale(s2, s2, 1F);
                    g.drawString(font, ss, 0, 0, withAlpha(0xFFFFFF, alpha * 0.9F), false);
                    g.pose().popPose();
                }
            } else {
                String shown = c.title.substring(0, chars);
                float s = Math.max(1.4F, h / 340F);
                float x = w * 0.08F;
                float y = h * 0.80F;
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(s, s, 1F);
                boolean caret = ((int) (t / 4F)) % 2 == 0;
                g.drawString(font, shown + (chars < c.title.length() && caret ? "_" : ""), 0, 0, withAlpha(c.rgb, alpha), false);
                if (c.sub != null && !c.sub.isEmpty() && chars >= c.title.length()) {
                    g.drawString(font, c.sub, 0, 11, withAlpha(0xFFFFFF, alpha * 0.6F), false);
                }
                g.pose().popPose();
            }
        }

        // ---- letterbox bars (drawn last so they sit over captions' edges)
        float lb = CutsceneClient.letterbox(partial);
        if (lb > 0F) {
            int bar = (int) (h * 0.115F * lb);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
    }
}
