package com.darkwebduo.client.render;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.cutscene.CutsceneIds;
import com.darkwebduo.entity.BossBase;
import com.darkwebduo.entity.CipherWardenEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Cipher Warden renderer: scaled generated model + emissive layer (per phase colourway) + additive VFX (laser, sigil, orbs, beams). */
public class CipherWardenRenderer extends MobRenderer<CipherWardenEntity, CipherWardenModel> {
    private static final ResourceLocation TEX = new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/cipher_warden.png");
    private static final ResourceLocation[] GLOW = {
            new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/cipher_warden_glow_1.png"),
            new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/cipher_warden_glow_2.png"),
            new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/cipher_warden_glow_3.png")};
    private static final float[][] C1 = {{0.10F, 0.90F, 1.00F}, {1.00F, 0.17F, 0.84F}, {1.00F, 0.25F, 0.19F}};
    private static final float[][] C2 = {{1.00F, 0.17F, 0.84F}, {0.63F, 0.25F, 1.00F}, {1.00F, 0.69F, 0.13F}};

    public CipherWardenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CipherWardenModel(ctx.bakeLayer(CipherWardenModel.LAYER_LOCATION)), 2.4F);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(CipherWardenEntity e) {
        return TEX;
    }

    @Override
    protected void scale(CipherWardenEntity e, PoseStack ps, float partial) {
        float s = CipherWardenModel.MODEL_SCALE;
        ps.scale(s, s, s);
    }

    @Override
    protected boolean shouldShowName(CipherWardenEntity e) {
        return false;
    }

    private static float rnd(long seed) {
        long x = seed * 0x9E3779B97F4A7C15L;
        x ^= (x >>> 29);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 32);
        return ((x & 0xFFFFL) / 65535.0F) * 2F - 1F;
    }

    private static float glitch(CipherWardenEntity e, float partial) {
        int mode = e.getMode();
        float mt = e.modeTime(partial);
        if (mode == BossBase.MODE_DYING) return Math.min(1F, mt / 100F) * 0.35F;
        if (mode == BossBase.MODE_CUTSCENE && e.getCutsceneId() == CutsceneIds.CIPHER_PHASE && mt < 24F) return 0.18F;
        if (mode == BossBase.MODE_CUTSCENE && e.getCutsceneId() == CutsceneIds.CIPHER_INTRO && mt > 60F && mt < 110F) return 0.04F;
        if (mode == BossBase.MODE_FIGHT && e.getAttack() == CipherWardenEntity.A_BLINK) {
            float at = e.attackTime(partial);
            if (at > 2F && at < 12F) return 0.25F;
        }
        return 0F;
    }

    @Override
    public void render(CipherWardenEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        this.model.partial = partial;
        int mode = e.getMode();
        boolean hidden = mode == BossBase.MODE_DYING && e.modeTime(partial) >= 112F;
        ps.pushPose();
        float g = glitch(e, partial);
        if (g > 0F) {
            long tick = e.level().getGameTime();
            ps.translate(rnd(tick * 3 + 1) * g, 0.0, rnd(tick * 3 + 2) * g);
        }
        if (!hidden) super.render(e, yaw, partial, ps, buf, light);
        ps.popPose();
        renderVfx(e, partial, ps, buf);
    }

    // ------------------------------------------------------------------ emissive layer
    static float glowAlpha(CipherWardenEntity e, float partial) {
        int mode = e.getMode();
        float time = e.tickCount + partial;
        float mt = e.modeTime(partial);
        if (mode == BossBase.MODE_DORMANT) return 0.06F + 0.04F * Mth.sin(time * 0.05F);
        if (mode == BossBase.MODE_CUTSCENE && e.getCutsceneId() == CutsceneIds.CIPHER_INTRO) {
            if (mt < 30F) return 0.06F;
            if (mt < 60F) {
                float flick = 0.5F + 0.5F * rnd((long) (mt * 0.7F));
                return 0.15F + 0.55F * flick * (mt - 30F) / 30F;
            }
            return Math.min(1F, 0.6F + (mt - 60F) / 50F);
        }
        if (mode == BossBase.MODE_DYING) {
            float flick = 0.5F + 0.5F * rnd((long) (mt * 1.7F) + 77);
            return mt < 100F ? 0.4F + 0.6F * flick : (mt < 112F ? 1F : 0F);
        }
        if (mode == BossBase.MODE_CUTSCENE) {
            return 0.5F + 0.5F * Math.abs(Mth.sin(mt * 0.9F));
        }
        return 0.88F + 0.12F * Mth.sin(time * 0.2F);
    }

    private static final class GlowLayer extends RenderLayer<CipherWardenEntity, CipherWardenModel> {
        GlowLayer(RenderLayerParent<CipherWardenEntity, CipherWardenModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, CipherWardenEntity e, float limbSwing, float limbSwingAmount,
                           float partial, float age, float netHeadYaw, float headPitch) {
            float a = glowAlpha(e, partial);
            if (a <= 0.01F) return;
            int ph = Mth.clamp(e.getPhase(), 1, 3);
            int idx = e.getMode() == BossBase.MODE_CUTSCENE && e.getCutsceneId() == CutsceneIds.CIPHER_PHASE && e.modeTime(partial) > 20F
                    ? Math.min(2, ph) : ph - 1;
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(GLOW[idx]));
            this.getParentModel().renderToBuffer(ps, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, a);
        }
    }

    // ------------------------------------------------------------------ additive VFX
    private Vec3 entityBase(CipherWardenEntity e, float partial) {
        return new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
    }

    private void renderVfx(CipherWardenEntity e, float partial, PoseStack ps, MultiBufferSource buf) {
        int mode = e.getMode();
        if (mode == BossBase.MODE_DORMANT) return;
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        ps.pushPose();
        Matrix4f m = ps.last().pose();
        float time = e.tickCount + partial;
        float mt = e.modeTime(partial);
        int ph = Mth.clamp(e.getPhase(), 1, 3);
        float[] k1 = C1[ph - 1];
        float[] k2 = C2[ph - 1];
        float yawBody = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        Vec3 fwd = Vec3.directionFromRotation(0.0F, yawBody);
        Vec3 rgt = new Vec3(-fwd.z, 0.0, fwd.x);
        float ga = glowAlpha(e, partial);

        // ---------- ground sigil
        float sig = 0F;
        if (mode == BossBase.MODE_FIGHT) sig = e.getAttack() != 0 ? 0.55F : 0.3F;
        else if (mode == BossBase.MODE_CUTSCENE && e.getCutsceneId() == CutsceneIds.CIPHER_INTRO) sig = Mth.clamp((mt - 55F) / 30F, 0F, 1F) * 0.8F;
        else if (mode == BossBase.MODE_CUTSCENE) sig = 0.8F;
        else if (mode == BossBase.MODE_DYING) sig = Math.max(0F, 1F - mt / 110F) * 0.7F;
        if (sig > 0.01F) {
            float spin = time * 0.02F;
            RenderUtil.disc(vc, m, 0F, 0.05F, 0F, 0F, 8.2F, sig * 0.06F, sig * 0.12F, 48, spin, k1[0], k1[1], k1[2]);
            RenderUtil.disc(vc, m, 0F, 0.06F, 0F, 7.7F, 8.2F, sig * 0.8F, sig * 0.8F, 64, spin, k1[0], k1[1], k1[2]);
            RenderUtil.disc(vc, m, 0F, 0.06F, 0F, 5.0F, 5.25F, sig * 0.7F, sig * 0.7F, 48, -spin * 1.6F, k2[0], k2[1], k2[2]);
            RenderUtil.disc(vc, m, 0F, 0.06F, 0F, 2.6F, 2.8F, sig * 0.6F, sig * 0.6F, 32, spin * 2.2F, k1[0], k1[1], k1[2]);
            for (int i = 0; i < 6; i++) {
                float a0 = spin * 3F + i * (float) (Math.PI / 3);
                float c = Mth.cos(a0), s = Mth.sin(a0);
                float px = -s * 0.09F, pz = c * 0.09F;
                RenderUtil.glowQuad(vc, m,
                        c * 2.8F - px, 0.07F, s * 2.8F - pz, sig * 0.7F,
                        c * 7.7F - px, 0.07F, s * 7.7F - pz, sig * 0.2F,
                        c * 7.7F + px, 0.07F, s * 7.7F + pz, sig * 0.2F,
                        c * 2.8F + px, 0.07F, s * 2.8F + pz, sig * 0.7F,
                        k2[0], k2[1], k2[2]);
            }
        }

        // ---------- attack-specific effects
        if (mode == BossBase.MODE_FIGHT) {
            int atk = e.getAttack();
            float at = e.attackTime(partial);
            if (atk == CipherWardenEntity.A_LASER) laser(e, partial, m, vc, at, time, k1, k2, yawBody);
            if (atk == CipherWardenEntity.A_SLAM && at < 25F) {
                float k = at / 24F;
                for (int s = -1; s <= 1; s += 2) {
                    RenderUtil.flare(vc, m, (float) rgt.x * 1.7F * s, 6.9F, (float) rgt.z * 1.7F * s, 0.4F + 1.5F * k, 0.9F, time * 0.3F, k1[0], k1[1], k1[2]);
                }
            }
            if (atk == CipherWardenEntity.A_BARRAGE) {
                float k = (at % 10F) / 10F;
                float size = 0.5F + 0.9F * (1F - k);
                for (int s = -1; s <= 1; s += 2) {
                    RenderUtil.flare(vc, m, (float) rgt.x * 2.1F * s, 5.1F, (float) rgt.z * 2.1F * s, size, 0.9F, time * 0.4F, k1[0], k1[1], k1[2]);
                }
            }
            if (atk == CipherWardenEntity.A_NOVA && at > 20F && at < 104F) {
                float k = Mth.clamp((at - 20F) / 70F, 0F, 1F);
                float fade = at > 98F ? (104F - at) / 6F : 1F;
                RenderUtil.shell(vc, m, 0F, 5.0F, 0F, 1.5F + 5.5F * k, 0.16F * fade, 10, 24, time * 0.05F, k1[0], k1[1], k1[2]);
                RenderUtil.shell(vc, m, 0F, 5.0F, 0F, 1.0F + 3.5F * k, 0.22F * fade, 8, 20, -time * 0.08F, k2[0], k2[1], k2[2]);
                RenderUtil.flare(vc, m, 0F, 5.0F, 0F, 1.0F + 2.5F * k, 0.95F * fade, time * 0.2F, 1F, 1F, 1F);
            }
            if (atk == CipherWardenEntity.A_RAIN || atk == CipherWardenEntity.A_DAEMONS || atk == CipherWardenEntity.A_SPIRAL) {
                float k = Mth.clamp(at / 14F, 0F, 1F);
                for (int s = -1; s <= 1; s += 2) {
                    RenderUtil.flare(vc, m, (float) rgt.x * 1.6F * s, 7.2F, (float) rgt.z * 1.6F * s, 0.5F + 0.6F * k, 0.8F, time * 0.3F, k2[0], k2[1], k2[2]);
                }
            }
            // heart-beat flare on the chest core
            RenderUtil.flare(vc, m, (float) fwd.x * 0.9F, 4.4F, (float) fwd.z * 0.9F, 0.55F + 0.15F * Mth.sin(time * 0.25F), 0.55F * ga, time * 0.1F, k1[0], k1[1], k1[2]);
        }

        // ---------- cutscene light columns
        if (mode == BossBase.MODE_CUTSCENE) {
            if (e.getCutsceneId() == CutsceneIds.CIPHER_INTRO && mt > 58F && mt < 112F) {
                float a = Mth.clamp((mt - 58F) / 10F, 0F, 1F) * (mt > 100F ? (112F - mt) / 12F : 1F);
                RenderUtil.beam(vc, m, 0F, 0F, 0F, 44F, 2.8F, 1.2F, 0.4F * a, 0.0F, 12, time * 0.06F, k1[0], k1[1], k1[2]);
                RenderUtil.beam(vc, m, 0F, 0F, 0F, 44F, 1.4F, 0.5F, 0.55F * a, 0.0F, 10, -time * 0.1F, 1F, 1F, 1F);
            }
            if (e.getCutsceneId() == CutsceneIds.CIPHER_PHASE && mt > 18F && mt < 45F) {
                float a = Mth.clamp((mt - 18F) / 4F, 0F, 1F) * (mt > 36F ? (45F - mt) / 9F : 1F);
                RenderUtil.beam(vc, m, 0F, 0F, 0F, 50F, 3.4F, 1.4F, 0.5F * a, 0.0F, 12, time * 0.08F, k1[0], k1[1], k1[2]);
                RenderUtil.beam(vc, m, 0F, 0F, 0F, 50F, 1.6F, 0.6F, 0.6F * a, 0.0F, 10, -time * 0.12F, k2[0], k2[1], k2[2]);
                RenderUtil.shell(vc, m, 0F, 3.2F, 0F, (mt - 18F) * 0.35F, 0.2F * a, 10, 24, time * 0.1F, k1[0], k1[1], k1[2]);
            }
        }

        // ---------- death implosion / supernova
        if (mode == BossBase.MODE_DYING) {
            if (mt > 90F && mt < 126F) {
                float build = Mth.clamp((mt - 90F) / 20F, 0F, 1F);
                float fade = mt > 112F ? Math.max(0F, (126F - mt) / 14F) : 1F;
                float size = (1.0F + 9.0F * build * build) * (mt > 112F ? 1.0F + (mt - 112F) * 0.9F : 1F);
                RenderUtil.flare(vc, m, 0F, 3.2F, 0F, size, 1.0F * fade, time * 0.3F, 1F, 1F, 1F);
                RenderUtil.flare(vc, m, 0F, 3.2F, 0F, size * 0.7F, 0.8F * fade, -time * 0.2F, k1[0], k1[1], k1[2]);
                RenderUtil.shell(vc, m, 0F, 3.2F, 0F, size * 0.55F, 0.35F * fade, 10, 24, time * 0.1F, k2[0], k2[1], k2[2]);
            }
            if (mt > 108F && mt < 140F) {
                float a = Mth.clamp((mt - 108F) / 3F, 0F, 1F) * Math.max(0F, (140F - mt) / 32F);
                RenderUtil.beam(vc, m, 0F, 0F, 0F, 60F, 4.0F, 1.0F, 0.7F * a, 0.0F, 14, time * 0.2F, 1F, 1F, 1F);
            }
        }
        ps.popPose();
    }

    private void laser(CipherWardenEntity e, float partial, Matrix4f m, VertexConsumer vc, float at, float time, float[] k1, float[] k2, float yawBody) {
        if (at < 10F || at >= 112F) return;
        Vec3 base = entityBase(e, partial);
        Vec3 fwd = Vec3.directionFromRotation(0.0F, yawBody);
        Vec3 eyeRel = new Vec3(fwd.x * 0.9, 5.7, fwd.z * 0.9);
        Vec3 eyeAbs = base.add(eyeRel);
        Vec3 dir = Vec3.directionFromRotation(e.getAimPitch(), e.getAimYaw());
        Vec3 to = eyeAbs.add(dir.scale(48.0));
        BlockHitResult hit = e.level().clip(new ClipContext(eyeAbs, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        Vec3 endAbs = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
        float len = (float) endAbs.subtract(eyeAbs).length();
        float ex = (float) eyeRel.x, ey = (float) eyeRel.y, ez = (float) eyeRel.z;
        float dx = (float) dir.x, dy = (float) dir.y, dz = (float) dir.z;
        if (at < 42F) {
            float k = (at - 10F) / 32F;
            RenderUtil.prism(vc, m, ex, ey, ez, dx, dy, dz, len, 0.035F, 0.035F, 0.2F + 0.5F * k, 0.2F + 0.5F * k, 4, 0F, k2[0], k2[1], k2[2]);
            RenderUtil.flare(vc, m, ex + dx * 1.3F, ey + dy * 1.3F, ez + dz * 1.3F, 0.2F + 1.8F * k, 0.9F, time * 0.4F, k1[0], k1[1], k1[2]);
            RenderUtil.flare(vc, m, ex + dx * 1.3F, ey + dy * 1.3F, ez + dz * 1.3F, 0.1F + 0.9F * k, 0.9F, -time * 0.3F, 1F, 1F, 1F);
            return;
        }
        float fade = at < 46F ? (at - 42F) / 4F : (at > 100F ? (112F - at) / 12F : 1F);
        float w = 1.0F + 0.12F * Mth.sin(time * 1.7F);
        float spin = time * 0.15F;
        RenderUtil.prism(vc, m, ex, ey, ez, dx, dy, dz, len, 0.9F * w, 1.1F * w, 0.28F * fade, 0.18F * fade, 10, spin, k1[0], k1[1], k1[2]);
        RenderUtil.prism(vc, m, ex, ey, ez, dx, dy, dz, len, 0.55F * w, 0.65F * w, 0.5F * fade, 0.4F * fade, 8, -spin * 1.3F, k2[0], k2[1], k2[2]);
        RenderUtil.prism(vc, m, ex, ey, ez, dx, dy, dz, len, 0.26F * w, 0.3F * w, 1.0F * fade, 0.9F * fade, 6, spin * 2F, 1F, 1F, 1F);
        for (float s = 1.5F + (time * 2.2F) % 4.0F; s < len; s += 4.0F) {
            RenderUtil.prism(vc, m, ex + dx * s, ey + dy * s, ez + dz * s, dx, dy, dz, 0.5F, 1.5F * w, 1.5F * w, 0.0F, 0.4F * fade, 10, spin, k1[0], k1[1], k1[2]);
        }
        float px = ex + dx * len, py = ey + dy * len, pz = ez + dz * len;
        RenderUtil.flare(vc, m, ex + dx * 1.2F, ey + dy * 1.2F, ez + dz * 1.2F, 1.8F * fade, 1.0F, time * 0.5F, 1F, 1F, 1F);
        RenderUtil.flare(vc, m, px, py, pz, 2.6F * fade, 1.0F, time * 0.4F, k1[0], k1[1], k1[2]);
        RenderUtil.flare(vc, m, px, py, pz, 1.4F * fade, 1.0F, -time * 0.4F, 1F, 1F, 1F);
        if (hit.getType() != HitResult.Type.MISS) {
            RenderUtil.disc(vc, m, px, py + 0.04F, pz, 0.0F, 2.2F * fade, 0.7F * fade, 0.0F, 24, spin, k1[0], k1[1], k1[2]);
        }
    }
}
