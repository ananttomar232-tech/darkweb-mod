package com.darkwebduo.client.render;

import com.darkwebduo.block.entity.OmegaTerminalBlockEntity;
import com.darkwebduo.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/**
 * Everything that moves on the Omega Terminal: floating hologram, the card sliding in, ground sigil, six light pillars
 * hammering down and closing in, the rift tearing open 28 blocks up, the light shaft and the cracks radiating from it.
 * Time is the block entity's activation clock, identical on server and client.
 */
public class OmegaTerminalRenderer implements BlockEntityRenderer<OmegaTerminalBlockEntity> {
    private static final float[] GOLD = {1.00F, 0.85F, 0.48F};
    private static final float[] VIOLET = {0.54F, 0.36F, 1.00F};
    private static final float[] RED = {1.00F, 0.30F, 0.20F};
    private static final float RIFT_Y = OmegaTerminalBlockEntity.RIFT_HEIGHT + 0.5F;

    public OmegaTerminalRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private static float ramp(float v, float a, float b) {
        return Mth.clamp((v - a) / (b - a), 0F, 1F);
    }

    private static float rnd(long seed) {
        long x = seed * 0x9E3779B97F4A7C15L;
        x ^= (x >>> 29);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 32);
        return ((x & 0xFFFFL) / 65535.0F) * 2F - 1F;
    }

    @Override
    public void render(OmegaTerminalBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffer, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        int stage = be.getStage();
        float time = (level.getGameTime() % 100000L) + partial;
        float t = be.animTime(partial);
        boolean summon = stage == OmegaTerminalBlockEntity.STAGE_SUMMON;

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());

        // ---------------- floating hologram above the slot (always there; reacts to the stage)
        float holoA;
        float[] hc1 = GOLD, hc2 = VIOLET;
        float spinMul = 1F;
        if (summon) {
            holoA = 1F;
            spinMul = 3F;
        } else if (stage == OmegaTerminalBlockEntity.STAGE_FIGHT) {
            holoA = 0.8F;
            hc1 = RED;
            hc2 = GOLD;
            spinMul = 2F;
        } else if (stage == OmegaTerminalBlockEntity.STAGE_DEFEATED) {
            holoA = 0.35F;
        } else {
            holoA = 0.85F + 0.15F * Mth.sin(time * 0.1F);
        }
        float hy = 1.75F + 0.07F * Mth.sin(time * 0.06F);
        float sp = time * 0.03F * spinMul;
        RenderUtil.shell(vc, m, 0F, hy, 0F, 0.26F, 0.22F * holoA, 6, 12, sp * 1.5F, hc1[0], hc1[1], hc1[2]);
        RenderUtil.shell(vc, m, 0F, hy, 0F, 0.38F, 0.12F * holoA, 5, 14, -sp, hc2[0], hc2[1], hc2[2]);
        RenderUtil.disc(vc, m, 0F, hy, 0F, 0.50F, 0.54F, 0.8F * holoA, 0.8F * holoA, 32, sp, hc1[0], hc1[1], hc1[2]);
        RenderUtil.disc(vc, m, 0F, hy - 0.22F, 0F, 0.30F, 0.33F, 0.7F * holoA, 0.7F * holoA, 24, -sp * 1.7F, hc2[0], hc2[1], hc2[2]);
        RenderUtil.disc(vc, m, 0F, hy + 0.22F, 0F, 0.30F, 0.33F, 0.7F * holoA, 0.7F * holoA, 24, sp * 1.7F, hc2[0], hc2[1], hc2[2]);
        RenderUtil.flare(vc, m, 0F, hy, 0F, 0.30F + 0.05F * Mth.sin(time * 0.2F), 0.8F * holoA, time * 0.1F, 1F, 1F, 1F);
        RenderUtil.beam(vc, m, 0F, 0.96F, 0F, hy - 0.2F, 0.05F, 0.2F, 0.35F * holoA, 0.05F, 6, time * 0.1F, hc1[0], hc1[1], hc1[2]);
        // glowing card slot
        RenderUtil.flare(vc, m, 0F, 0.96F, 0F, 0.22F, 0.7F * holoA, 0F, hc1[0], hc1[1], hc1[2]);

        if (summon) {
            renderSummon(m, vc, ps, buffer, level, t, time);
        }
        ps.popPose();
    }

    private void renderSummon(Matrix4f m, VertexConsumer vc, PoseStack ps, MultiBufferSource buffer, Level level, float t, float time) {
        // ---------------- the keycard slides into the slot
        if (t < 24F) {
            float k = t / 22F;
            float y = Mth.lerp(k * k, 2.7F, 1.02F);
            ps.pushPose();
            ps.translate(0.0, y, 0.0);
            ps.mulPose(Axis.YP.rotationDegrees(t * 16F));
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.4F) * 10F));
            ps.scale(1.1F, 1.1F, 1.1F);
            RenderUtil.flare(vc, ps.last().pose(), 0F, 0F, 0F, 0.7F, 0.6F, time * 0.2F, GOLD[0], GOLD[1], GOLD[2]);
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(ModItems.OMEGA_KEYCARD.get()), ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffer, level, 0);
            ps.popPose();
        }
        if (t >= 20F && t < 34F) {
            float p = 1F - (t - 20F) / 14F;
            RenderUtil.flare(vc, m, 0F, 1.0F, 0F, 3.5F * p + 0.5F, p, time * 0.3F, 1F, 1F, 1F);
            RenderUtil.disc(vc, m, 0F, 1.0F, 0F, (1F - p) * 4F, (1F - p) * 4F + 0.25F, 0.9F * p, 0.9F * p, 40, 0F, GOLD[0], GOLD[1], GOLD[2]);
        }

        // ---------------- ground sigil, grows from the terminal outwards
        float sig = ramp(t, 10F, 60F) * (1F - ramp(t, 196F, 214F));
        if (sig > 0.01F) {
            float sp = time * 0.016F;
            float s = 0.04F;
            RenderUtil.disc(vc, m, 0F, s, 0F, 0F, 14.5F * sig, 0.05F * sig, 0.14F * sig, 64, sp, GOLD[0], GOLD[1], GOLD[2]);
            RenderUtil.disc(vc, m, 0F, s, 0F, 14.0F * sig, 14.6F * sig, 0.9F * sig, 0.9F * sig, 80, sp, GOLD[0], GOLD[1], GOLD[2]);
            RenderUtil.disc(vc, m, 0F, s, 0F, 10.0F * sig, 10.3F * sig, 0.8F * sig, 0.8F * sig, 72, -sp * 1.4F, VIOLET[0], VIOLET[1], VIOLET[2]);
            RenderUtil.disc(vc, m, 0F, s, 0F, 6.0F * sig, 6.25F * sig, 0.75F * sig, 0.75F * sig, 56, sp * 2F, GOLD[0], GOLD[1], GOLD[2]);
            RenderUtil.disc(vc, m, 0F, s, 0F, 2.8F * sig, 3.0F * sig, 0.7F * sig, 0.7F * sig, 40, -sp * 3F, VIOLET[0], VIOLET[1], VIOLET[2]);
            for (int i = 0; i < 12; i++) {
                float a0 = sp * 3F + i * (float) (Math.PI / 6);
                float c = Mth.cos(a0), sn = Mth.sin(a0);
                float px = -sn * 0.1F, pz = c * 0.1F;
                RenderUtil.glowQuad(vc, m,
                        c * 3.0F * sig - px, s + 0.01F, sn * 3.0F * sig - pz, 0.7F * sig,
                        c * 14.0F * sig - px, s + 0.01F, sn * 14.0F * sig - pz, 0.12F * sig,
                        c * 14.0F * sig + px, s + 0.01F, sn * 14.0F * sig + pz, 0.12F * sig,
                        c * 3.0F * sig + px, s + 0.01F, sn * 3.0F * sig + pz, 0.7F * sig,
                        VIOLET[0], VIOLET[1], VIOLET[2]);
            }
        }

        // ---------------- six pillars of light
        float pfade = 1F - ramp(t, 180F, 206F);
        if (t > 50F && pfade > 0.01F) {
            float pr = OmegaTerminalBlockEntity.pillarRadius(t);
            for (int i = 0; i < OmegaTerminalBlockEntity.PILLARS; i++) {
                float landed = 66F + i * 2F;
                float fall = ramp(t, landed - 14F, landed);
                if (fall <= 0F) continue;
                float ease = fall * fall;
                float y0 = Mth.lerp(ease, 84F, 0F);
                float y1 = Mth.lerp(ramp(t, landed, landed + 6F), Math.min(84F, y0 + 26F), 84F);
                float ang = OmegaTerminalBlockEntity.pillarAngle(i, t);
                float cx = Mth.cos(ang) * pr, cz = Mth.sin(ang) * pr;
                float[] col = i % 2 == 0 ? GOLD : VIOLET;
                RenderUtil.beam(vc, m, cx, y0, cz, y1, 1.5F, 1.1F, 0.5F * pfade, 0.14F * pfade, 10, time * 0.05F + i, col[0], col[1], col[2]);
                RenderUtil.beam(vc, m, cx, y0, cz, y1, 0.8F, 0.55F, 0.75F * pfade, 0.3F * pfade, 8, -time * 0.1F, 1F, 1F, 1F);
                if (t >= landed) {
                    float pulse = 1F - ramp(t, landed, landed + 12F);
                    RenderUtil.flare(vc, m, cx, 0.4F, cz, 2.2F + 3.5F * pulse, 0.8F * pfade, time * 0.3F + i, col[0], col[1], col[2]);
                    RenderUtil.disc(vc, m, cx, 0.06F, cz, 0.0F, 2.4F + 4F * pulse, 0.6F * pfade, 0.0F, 24, time * 0.1F, col[0], col[1], col[2]);
                }
            }
        }

        // ---------------- the rift
        float close = 1F - ramp(t, 196F, 214F);
        float k = ramp(t, 120F, 152F) * close;
        if (t >= 118F && k > 0.01F) {
            float tt = time;
            // glare + core
            RenderUtil.flare(vc, m, 0F, RIFT_Y, 0F, 13F * k, 0.55F, tt * 0.05F, VIOLET[0], VIOLET[1], VIOLET[2]);
            RenderUtil.flare(vc, m, 0F, RIFT_Y, 0F, 8F * k, 0.8F, -tt * 0.07F, GOLD[0], GOLD[1], GOLD[2]);
            RenderUtil.flare(vc, m, 0F, RIFT_Y, 0F, 4F * k, 1.0F, tt * 0.1F, 1F, 1F, 1F);
            RenderUtil.shell(vc, m, 0F, RIFT_Y, 0F, 6F * k, 0.14F, 10, 24, tt * 0.04F, VIOLET[0], VIOLET[1], VIOLET[2]);
            // horizontal event-horizon rings
            float[] radii = {3.5F, 6.5F, 9.5F, 12.5F};
            for (int i = 0; i < radii.length; i++) {
                float r = radii[i] * k;
                float[] col = i % 2 == 0 ? GOLD : VIOLET;
                RenderUtil.disc(vc, m, 0F, RIFT_Y + (i - 1.5F) * 0.12F, 0F, r, r + 0.35F + 0.12F * i, 0.9F * k, 0.9F * k, 64, (i % 2 == 0 ? 1F : -1F) * tt * (0.03F + 0.015F * i), col[0], col[1], col[2]);
            }
            RenderUtil.disc(vc, m, 0F, RIFT_Y, 0F, 0F, 13F * k, 0.45F * k, 0.0F, 64, tt * 0.02F, VIOLET[0], VIOLET[1], VIOLET[2]);
            // cracks in reality
            for (int i = 0; i < 14; i++) {
                float a = i * (float) (Math.PI * 2 / 14) + tt * 0.004F * (i % 2 == 0 ? 1 : -1);
                float el = 0.55F * Mth.sin(i * 1.9F + tt * 0.02F);
                float flick = 0.55F + 0.45F * Math.abs(rnd((long) (tt * 0.5F) * 17L + i));
                float len = (7F + 9F * Math.abs(Mth.sin(i * 2.3F + tt * 0.05F))) * k;
                float cxd = Mth.cos(a) * Mth.cos(el), czd = Mth.sin(a) * Mth.cos(el), cyd = Mth.sin(el);
                RenderUtil.prism(vc, m, 0F, RIFT_Y, 0F, cxd, cyd, czd, len, 0.3F, 0.02F, 0.9F * flick * k, 0.0F, 4, tt * 0.3F, i % 3 == 0 ? 1F : GOLD[0], i % 3 == 0 ? 1F : GOLD[1], i % 3 == 0 ? 1F : GOLD[2]);
                RenderUtil.prism(vc, m, 0F, RIFT_Y, 0F, cxd, cyd, czd, len, 0.9F, 0.06F, 0.25F * flick * k, 0.0F, 6, -tt * 0.2F, VIOLET[0], VIOLET[1], VIOLET[2]);
            }
            // the shaft of light that falls from the rift onto the terminal
            float sh = ramp(t, 124F, 144F) * close;
            RenderUtil.beam(vc, m, 0F, 1.0F, 0F, RIFT_Y, 0.4F, 2.4F, 0.55F * sh, 0.2F * sh, 12, time * 0.08F, GOLD[0], GOLD[1], GOLD[2]);
            RenderUtil.beam(vc, m, 0F, 1.0F, 0F, RIFT_Y, 0.18F, 1.0F, 0.8F * sh, 0.35F * sh, 8, -time * 0.14F, 1F, 1F, 1F);
        }
        // white-out pulse at the moment the boss steps through
        if (t >= 150F && t < 172F) {
            float p = 1F - Math.abs(t - 160F) / 12F;
            if (p > 0F) RenderUtil.flare(vc, m, 0F, RIFT_Y, 0F, 18F * p, 0.95F * p, time * 0.4F, 1F, 1F, 1F);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(OmegaTerminalBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
