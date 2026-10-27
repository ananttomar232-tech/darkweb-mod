package com.darkwebduo.client.render;

import com.darkwebduo.Config;
import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.block.GiantMonitorBlock;
import com.darkwebduo.block.entity.GiantMonitorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Draws the 5x3 block bezel and the live screen of the giant monitor.
 * Screen space: 468 x 268 virtual pixels, origin top-left, 100 virtual px = 1 block.
 */
public class GiantMonitorRenderer implements BlockEntityRenderer<GiantMonitorBlockEntity> {
    private static final float SW = 468F;
    private static final float SH = 268F;
    private static final int FULL = LightTexture.FULL_BRIGHT;

    private static final String[] VERBS = {"GET", "POST", "SYN", "ACK", "TLS", "ssh", "tor", "nmap", "scp", "curl"};
    private static final String TYPED = "sudo ./deliver --box --anonymous --encrypt=aes256 && ssh -D 9050 gate.onion && ls /keycards ; ";

    private final Font font;

    public GiantMonitorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    // ------------------------------------------------------------------ helpers
    private static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(new ResourceLocation(DarkWebDuoMod.MODID, path));
    }

    private static float rnd(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static int argb(float r, float g, float b, float a) {
        int ia = Math.max(0, Math.min(255, (int) (a * 255F)));
        int ir = Math.max(0, Math.min(255, (int) (r * 255F)));
        int ig = Math.max(0, Math.min(255, (int) (g * 255F)));
        int ib = Math.max(0, Math.min(255, (int) (b * 255F)));
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        RenderUtil.glowQuad(vc, m, x0, y1, 0, a, x1, y1, 0, a, x1, y0, 0, a, x0, y0, 0, a, r, g, b);
    }

    private static void rectV(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float b, float aTop, float aBottom) {
        RenderUtil.glowQuad(vc, m, x0, y1, 0, aBottom, x1, y1, 0, aBottom, x1, y0, 0, aTop, x0, y0, 0, aTop, r, g, b);
    }

    private static void border(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float w, float r, float g, float b, float a) {
        rect(vc, m, x0, y0, x1, y0 + w, r, g, b, a);
        rect(vc, m, x0, y1 - w, x1, y1, r, g, b, a);
        rect(vc, m, x0, y0, x0 + w, y1, r, g, b, a);
        rect(vc, m, x1 - w, y0, x1, y1, r, g, b, a);
    }

    private static void line(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float w, float r, float g, float b, float a) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001F) return;
        float nx = -dy / len * w * 0.5F, ny = dx / len * w * 0.5F;
        RenderUtil.glowQuad(vc, m, x0 - nx, y0 - ny, 0, a, x1 - nx, y1 - ny, 0, a, x1 + nx, y1 + ny, 0, a, x0 + nx, y0 + ny, 0, a, r, g, b);
    }

    private static void circle(VertexConsumer vc, Matrix4f m, float cx, float cy, float rad, float w, int seg, float r, float g, float b, float a) {
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (Math.PI * 2 * i / seg), a1 = (float) (Math.PI * 2 * (i + 1) / seg);
            line(vc, m, cx + (float) Math.cos(a0) * rad, cy + (float) Math.sin(a0) * rad,
                    cx + (float) Math.cos(a1) * rad, cy + (float) Math.sin(a1) * rad, w, r, g, b, a);
        }
    }

    private void text(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float scale, int color) {
        ps.pushPose();
        ps.translate(x, y, 0.5F);
        ps.scale(scale, scale, 1F);
        font.drawInBatch(s, 0F, 0F, color, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, FULL);
        ps.popPose();
    }

    private void textCentered(PoseStack ps, MultiBufferSource buf, String s, float cx, float y, float scale, int color) {
        float w = font.width(s) * scale;
        text(ps, buf, s, cx - w / 2F, y, scale, color);
    }

    private static String fakeLog(int idx) {
        float r = rnd(idx * 31 + 7);
        String hex = Integer.toHexString((int) (rnd(idx * 17 + 3) * 0xFFFFFF) | 0x100000).toUpperCase();
        int a = 10 + (int) (rnd(idx * 5 + 1) * 240), b = (int) (rnd(idx * 9 + 2) * 255);
        String verb = VERBS[(int) (rnd(idx * 13 + 5) * VERBS.length) % VERBS.length];
        if (r < 0.12F) return "[WARN] node " + hex + " drop";
        if (r < 0.2F) return "[ OK ] tunnel " + a + "." + b + " up";
        if (r < 0.3F) return "[ >> ] relay 0x" + hex;
        if (r < 0.4F) return "[SEC ] key " + hex + hex.substring(0, 2);
        return verb + " " + a + "." + b + ".x.x :" + (1000 + (int) (rnd(idx) * 8999F));
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GiantMonitorBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof GiantMonitorBlock)) return;
        Level level = be.getLevel();
        if (level == null) return;
        float time = (level.getGameTime() % 1000000L) + partialTick;

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-state.getValue(GiantMonitorBlock.FACING).toYRot()));

        // ---------------- bezel
        TextureAtlasSprite bez = sprite("block/monitor_bezel");
        RenderUtil.Uv u = RenderUtil.Uv.of(bez, 0F, 0F, 1F, 1F);
        VertexConsumer solid = buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        float z0 = -0.125F, z1 = 0.035F;
        RenderUtil.cuboid(solid, ps.last(), -2.5F, 2.84F, z0, 2.5F, 3.0F, z1, u, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        RenderUtil.cuboid(solid, ps.last(), -2.5F, 0.0F, z0, 2.5F, 0.20F, z1, u, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        RenderUtil.cuboid(solid, ps.last(), -2.5F, 0.20F, z0, -2.34F, 2.84F, z1, u, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        RenderUtil.cuboid(solid, ps.last(), 2.34F, 0.20F, z0, 2.5F, 2.84F, z1, u, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        // inner dark glass plate
        TextureAtlasSprite glass = sprite("block/monitor_glass");
        RenderUtil.Uv ug = RenderUtil.Uv.of(glass, 0F, 0F, 1F, 1F);
        RenderUtil.quad(solid, ps.last(), -2.34F, 0.20F, -0.112F, 2.34F, 0.20F, -0.112F, 2.34F, 2.84F, -0.112F, -2.34F, 2.84F, -0.112F,
                ug, 0, 0, 1, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);

        // ---------------- screen space
        ps.pushPose();
        ps.translate(-2.34F, 2.84F, -0.105F);
        ps.scale(0.01F, -0.01F, 0.01F);

        VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();

        if (!be.isPowered()) {
            drawOff(glow, m, time);
            ps.popPose();
            ps.popPose();
            return;
        }
        float boot = time - be.getBootStart();
        if (boot < 70F) {
            drawBootShapes(glow, m, boot, time);
            drawBootTexts(ps, buffer, boot, time);
        } else {
            drawDesktopShapes(glow, m, be, time);
            drawDesktopTexts(ps, buffer, be, time);
        }
        ps.popPose();
        ps.popPose();
    }

    // ------------------------------------------------------------------ OFF
    private void drawOff(VertexConsumer vc, Matrix4f m, float time) {
        // glass sheen
        RenderUtil.glowQuad(vc, m, 0, SH, 0, 0.0F, SW * 0.55F, SH, 0, 0.05F, SW * 0.85F, 0, 0, 0.05F, SW * 0.30F, 0, 0, 0.0F, 0.6F, 0.7F, 1F);
        // standby LED
        float pulse = 0.4F + 0.6F * (0.5F + 0.5F * (float) Math.sin(time * 0.06F));
        rect(vc, m, SW - 22F, SH - 14F, SW - 14F, SH - 8F, 1F, 0.1F, 0.1F, pulse);
    }

    // ------------------------------------------------------------------ BOOT
    private void drawBootShapes(VertexConsumer vc, Matrix4f m, float t, float time) {
        if (t < 10F) {
            float k = t / 10F;
            float h = 2F + k * k * SH;
            float w = SW * (0.2F + 0.8F * k);
            rect(vc, m, SW / 2F - w / 2F, SH / 2F - h / 2F, SW / 2F + w / 2F, SH / 2F + h / 2F, 0.8F, 0.95F, 1F, 0.5F + 0.5F * (1F - k));
            return;
        }
        float k = (t - 10F) / 60F;
        // progress bar
        border(vc, m, 94F, 214F, 374F, 230F, 1.5F, 0.1F, 0.9F, 1F, 0.8F);
        rect(vc, m, 97F, 217F, 97F + 274F * k, 227F, 0.1F, 0.9F, 1F, 0.75F);
        // glitch bars
        for (int i = 0; i < 4; i++) {
            float y = rnd((int) time * 7 + i) * SH;
            float g = rnd((int) time * 3 + i * 11);
            if (g > 0.7F) rect(vc, m, 0, y, SW, y + 2F + g * 6F, 1F, 0.1F, 0.8F, 0.18F);
        }
        // scanlines
        for (float y = 0; y < SH; y += 6F) {
            rect(vc, m, 0, y, SW, y + 1F, 0.1F, 0.8F, 1F, 0.035F);
        }
    }

    private void drawBootTexts(PoseStack ps, MultiBufferSource buf, float t, float time) {
        if (t < 10F) return;
        float jx = rnd((int) (time * 2F)) > 0.8F ? (rnd((int) time + 5) - 0.5F) * 8F : 0F;
        textCentered(ps, buf, "DARKWEB", SW / 2F + jx, 84F, 6F, argb(0.1F, 0.95F, 1F, 1F));
        textCentered(ps, buf, "O S   v 6 . 6 . 6", SW / 2F, 140F, 1.6F, argb(1F, 0.2F, 0.85F, 0.9F));
        String[] lines = {"POST ........ OK", "TOR ROUTE ... 3 HOPS", "ENTROPY ..... 4096 bit", "MOUNT /keycards ... LOCKED"};
        for (int i = 0; i < lines.length; i++) {
            if (t > 18F + i * 11F) text(ps, buf, lines[i], 94F, 166F + i * 10F, 1F, argb(0.3F, 1F, 0.5F, 0.95F));
        }
        text(ps, buf, "LOADING " + (int) ((t - 10F) / 60F * 100F) + "%", 94F, 236F, 1.2F, argb(0.1F, 0.9F, 1F, 1F));
    }

    // ------------------------------------------------------------------ DESKTOP
    private static final float[] PANEL_L = {6F, 30F, 150F, 250F};
    private static final float[] PANEL_C = {160F, 30F, 310F, 250F};
    private static final float[] PANEL_R = {320F, 30F, 462F, 250F};

    private void drawDesktopShapes(VertexConsumer vc, Matrix4f m, GiantMonitorBlockEntity be, float time) {
        // background gradient
        rectV(vc, m, 0, 0, SW, SH, 0.0F, 0.12F, 0.2F, 0.10F, 0.0F);
        // scanlines
        for (float y = 0; y < SH; y += 4F) {
            rect(vc, m, 0, y, SW, y + 1F, 0.1F, 0.7F, 1F, 0.03F);
        }
        // header
        rect(vc, m, 0, 0, SW, 22F, 0.0F, 0.55F, 0.7F, 0.16F);
        rect(vc, m, 0, 21F, SW, 22.5F, 0.1F, 0.9F, 1F, 0.8F);
        float rec = 0.5F + 0.5F * (float) Math.sin(time * 0.15F);
        rect(vc, m, 452F, 7F, 460F, 15F, 1F, 0.1F, 0.15F, 0.4F + 0.6F * rec);

        // panels
        float[][] panels = {PANEL_L, PANEL_C, PANEL_R};
        for (int i = 0; i < 3; i++) {
            float[] p = panels[i];
            border(vc, m, p[0], p[1], p[2], p[3], 1F, 0.1F, 0.9F, 1F, 0.55F);
            // corner ticks
            rect(vc, m, p[0] - 2F, p[1] - 2F, p[0] + 8F, p[1], 0.1F, 0.9F, 1F, 0.9F);
            rect(vc, m, p[2] - 8F, p[3], p[2] + 2F, p[3] + 2F, 0.1F, 0.9F, 1F, 0.9F);
        }

        // ---- network graph in the centre
        float cx = (PANEL_C[0] + PANEL_C[2]) / 2F, cy = (PANEL_C[1] + PANEL_C[3]) / 2F + 6F;
        for (int ring = 1; ring <= 3; ring++) {
            circle(vc, m, cx, cy, ring * 32F, 0.8F, 40, 0.1F, 0.7F, 0.9F, 0.35F);
        }
        float sweep = time * 0.045F;
        for (int k = 0; k < 14; k++) {
            float fa = sweep - k * 0.045F;
            line(vc, m, cx, cy, cx + (float) Math.cos(fa) * 96F, cy + (float) Math.sin(fa) * 96F, 3F, 0.2F, 1F, 0.5F, 0.30F * (1F - k / 14F));
        }
        int n = 14;
        float[] nx = new float[n], ny = new float[n];
        for (int i = 0; i < n; i++) {
            float ang = rnd(i * 3 + 1) * 6.2832F + time * 0.004F * (1F + rnd(i + 40));
            float rad = 18F + rnd(i * 5 + 2) * 74F;
            nx[i] = cx + (float) Math.cos(ang) * rad;
            ny[i] = cy + (float) Math.sin(ang) * rad * 0.9F;
        }
        for (int i = 0; i < n; i++) {
            int j = (i * 5 + 3) % n, k2 = (i * 3 + 7) % n;
            line(vc, m, nx[i], ny[i], nx[j], ny[j], 0.8F, 0.1F, 0.8F, 1F, 0.35F);
            line(vc, m, nx[i], ny[i], nx[k2], ny[k2], 0.8F, 0.1F, 0.8F, 1F, 0.25F);
            // packets travelling along the link
            float tp = (time * 0.02F + rnd(i + 90)) % 1F;
            float px = nx[i] + (nx[j] - nx[i]) * tp, py = ny[i] + (ny[j] - ny[i]) * tp;
            rect(vc, m, px - 1.5F, py - 1.5F, px + 1.5F, py + 1.5F, 1F, 0.9F, 0.2F, 0.95F);
        }
        for (int i = 0; i < n; i++) {
            float pulse = 0.6F + 0.4F * (float) Math.sin(time * 0.1F + i);
            boolean red = rnd(i + 200) > 0.85F;
            rect(vc, m, nx[i] - 3F, ny[i] - 3F, nx[i] + 3F, ny[i] + 3F, red ? 1F : 0.2F, red ? 0.15F : 1F, red ? 0.2F : 0.55F, pulse);
        }
        rect(vc, m, cx - 4F, cy - 4F, cx + 4F, cy + 4F, 1F, 1F, 1F, 0.9F);

        // ---- right panel: order card
        float rx0 = PANEL_R[0], ry0 = PANEL_R[1], rx1 = PANEL_R[2];
        rect(vc, m, rx0 + 8F, ry0 + 22F, rx1 - 8F, ry0 + 92F, 0.55F, 0.35F, 0.1F, 0.18F);
        border(vc, m, rx0 + 8F, ry0 + 22F, rx1 - 8F, ry0 + 92F, 1F, 1F, 0.75F, 0.2F, 0.7F);
        // little 3D-ish box icon
        float bx = rx0 + 22F, by = ry0 + 36F;
        rect(vc, m, bx, by + 10F, bx + 28F, by + 38F, 0.75F, 0.5F, 0.2F, 0.85F);
        rect(vc, m, bx - 2F, by + 4F, bx + 30F, by + 12F, 0.9F, 0.65F, 0.25F, 0.95F);
        rect(vc, m, bx + 12F, by + 4F, bx + 16F, by + 38F, 0.1F, 0.9F, 1F, 0.9F);
        boolean ready = be.hasKeyboard() && be.hasMouse();
        long since = (long) time - be.getLastOrderTime();
        float bpulse = 0.55F + 0.45F * (float) Math.sin(time * 0.2F);
        float[] btn = {rx0 + 14F, ry0 + 150F, rx1 - 14F, ry0 + 178F};
        if (since < 80L && be.getLastOrderTime() > 0L) {
            rect(vc, m, btn[0], btn[1], btn[2], btn[3], 0.2F, 1F, 0.4F, 0.35F + 0.3F * bpulse);
            border(vc, m, btn[0], btn[1], btn[2], btn[3], 1.5F, 0.3F, 1F, 0.5F, 1F);
        } else if (ready) {
            rect(vc, m, btn[0], btn[1], btn[2], btn[3], 1F, 0.2F, 0.7F, 0.18F + 0.18F * bpulse);
            border(vc, m, btn[0], btn[1], btn[2], btn[3], 1.5F, 1F, 0.3F, 0.8F, 0.6F + 0.4F * bpulse);
        } else {
            border(vc, m, btn[0], btn[1], btn[2], btn[3], 1F, 0.5F, 0.5F, 0.5F, 0.5F);
        }
        // device LEDs
        rect(vc, m, rx0 + 16F, ry0 + 106F, rx0 + 22F, ry0 + 112F, be.hasKeyboard() ? 0.2F : 1F, be.hasKeyboard() ? 1F : 0.2F, 0.3F, 0.95F);
        rect(vc, m, rx0 + 16F, ry0 + 120F, rx0 + 22F, ry0 + 126F, be.hasMouse() ? 0.2F : 1F, be.hasMouse() ? 1F : 0.2F, 0.3F, 0.95F);

        // ---- log panel border glow bar (activity meter)
        for (int i = 0; i < 8; i++) {
            float h = 6F + rnd((int) (time * 0.2F) + i * 7) * 30F;
            rect(vc, m, PANEL_L[0] + 6F + i * 16F, PANEL_L[3] - 6F - h, PANEL_L[0] + 16F + i * 16F, PANEL_L[3] - 6F, 0.1F, 1F, 0.5F, 0.35F);
        }

        // ---- command line bar
        rect(vc, m, 6F, 252F, SW - 6F, 266F, 0.0F, 0.35F, 0.45F, 0.20F);

        // ---- mouse cursor + click ripple
        drawCursor(vc, m, be, time);
    }

    private void drawCursor(VertexConsumer vc, Matrix4f m, GiantMonitorBlockEntity be, float time) {
        int clicks = be.getMouseClicks();
        float dt = Math.min(1F, (time - be.getLastClickTime()) / 12F);
        float e = dt * dt * (3F - 2F * dt);
        float fx = 40F + rnd(clicks * 7 + 1) * 380F, fy = 40F + rnd(clicks * 7 + 2) * 190F;
        float px = 40F + rnd((clicks - 1) * 7 + 1) * 380F, py = 40F + rnd((clicks - 1) * 7 + 2) * 190F;
        if (clicks == 0) { px = fx = 300F + (float) Math.sin(time * 0.02F) * 20F; py = fy = 200F; }
        float x = px + (fx - px) * e, y = py + (fy - py) * e;
        // arrow: triangle + tail
        RenderUtil.glowQuad(vc, m, x, y, 0, 1F, x + 10F, y + 12F, 0, 1F, x + 4F, y + 12F, 0, 1F, x, y + 16F, 0, 1F, 1F, 1F, 1F);
        line(vc, m, x + 4F, y + 11F, x + 8F, y + 19F, 2F, 1F, 1F, 1F, 1F);
        float since = time - be.getLastClickTime();
        if (clicks > 0 && since < 14F) {
            float k = since / 14F;
            circle(vc, m, fx, fy, 4F + k * 26F, 1.5F, 20, 1F, 0.9F, 0.2F, 1F - k);
        }
    }

    private void drawDesktopTexts(PoseStack ps, MultiBufferSource buf, GiantMonitorBlockEntity be, float time) {
        int cyan = argb(0.15F, 0.95F, 1F, 1F);
        int green = argb(0.35F, 1F, 0.55F, 0.95F);
        int amber = argb(1F, 0.78F, 0.2F, 1F);
        int red = argb(1F, 0.25F, 0.3F, 1F);
        int white = argb(1F, 1F, 1F, 1F);

        text(ps, buf, "DARKWEB // ONION-ROUTED MARKET", 8F, 7F, 1.25F, cyan);
        text(ps, buf, "NODES 7,412  LAT " + (30 + (int) (rnd((int) (time * 0.1F)) * 20F)) + "ms", 300F, 8F, 1F, amber);

        // log panel
        text(ps, buf, "SYSLOG", PANEL_L[0] + 6F, PANEL_L[1] + 4F, 1F, white);
        int base = (int) (time * 0.2F);
        float frac = (time * 0.2F) - base;
        for (int i = 0; i < 15; i++) {
            String ln = fakeLog(base + i);
            int col = ln.startsWith("[WARN]") ? red : (ln.startsWith("[SEC") ? amber : green);
            float y = PANEL_L[1] + 18F + i * 11.5F - frac * 11.5F;
            float fade = Math.min(1F, Math.min((y - PANEL_L[1] - 14F) / 8F, (PANEL_L[3] - 26F - y) / 8F));
            if (fade <= 0.05F) continue;
            int c = (col & 0x00FFFFFF) | (((int) (255 * Math.max(0.1F, fade))) << 24);
            text(ps, buf, ln, PANEL_L[0] + 6F, y, 0.78F, c);
        }

        // centre title
        text(ps, buf, "TOPOLOGY", PANEL_C[0] + 6F, PANEL_C[1] + 4F, 1F, white);
        text(ps, buf, "SCAN " + (int) ((time * 0.6F) % 100F) + "%", PANEL_C[2] - 56F, PANEL_C[1] + 4F, 0.9F, green);

        // order card
        float rx0 = PANEL_R[0], ry0 = PANEL_R[1];
        text(ps, buf, "MARKETPLACE", rx0 + 6F, ry0 + 4F, 1F, white);
        text(ps, buf, "ITEM #4F2A", rx0 + 56F, ry0 + 30F, 0.85F, amber);
        text(ps, buf, "DELIVERY BOX", rx0 + 56F, ry0 + 44F, 1.15F, white);
        text(ps, buf, "contains: KEYCARD", rx0 + 56F, ry0 + 60F, 0.8F, cyan);
        text(ps, buf, "price: " + Config.orderCost() + " EMERALDS", rx0 + 56F, ry0 + 72F, 0.85F, amber);
        text(ps, buf, "KEYBOARD  " + (be.hasKeyboard() ? "[OK]" : "[--]"), rx0 + 28F, ry0 + 105F, 0.9F, be.hasKeyboard() ? green : red);
        text(ps, buf, "MOUSE     " + (be.hasMouse() ? "[OK]" : "[--]"), rx0 + 28F, ry0 + 119F, 0.9F, be.hasMouse() ? green : red);
        long since = (long) time - be.getLastOrderTime();
        if (since < 80L && be.getLastOrderTime() > 0L) {
            textCentered(ps, buf, "ORDER CONFIRMED", (rx0 + PANEL_R[2]) / 2F, ry0 + 158F, 1.05F, argb(0.3F, 1F, 0.5F, 1F));
            text(ps, buf, "drone en route...", rx0 + 36F, ry0 + 186F, 0.85F, green);
        } else if (be.hasKeyboard() && be.hasMouse()) {
            textCentered(ps, buf, "[ RIGHT-CLICK + EMERALDS ]", (rx0 + PANEL_R[2]) / 2F, ry0 + 160F, 0.8F, argb(1F, 0.5F, 0.9F, 1F));
            textCentered(ps, buf, "ORDER", (rx0 + PANEL_R[2]) / 2F, ry0 + 186F, 0.9F, white);
        } else {
            textCentered(ps, buf, "CONNECT KEYBOARD + MOUSE", (rx0 + PANEL_R[2]) / 2F, ry0 + 160F, 0.75F, red);
        }

        // command line
        int len = TYPED.length();
        int keys = be.getKeyPresses();
        int shown = 0;
        if (keys > 0) {
            int partial = (int) Math.min(4F, (time - be.getLastKeyTime()) * 1.5F);
            shown = (keys * 4 - 4 + Math.max(0, partial)) % (len + 1);
        }
        String typed = TYPED.substring(0, Math.min(len, shown));
        boolean caret = ((int) (time / 8F)) % 2 == 0;
        text(ps, buf, "root@darkweb:~# " + typed + (caret ? "_" : ""), 10F, 255F, 0.95F, green);
    }

    // ------------------------------------------------------------------ misc
    @Override
    public boolean shouldRenderOffScreen(GiantMonitorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
