package com.darkwebduo.client.render;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.cutscene.CutsceneIds;
import com.darkwebduo.entity.BossBase;
import com.darkwebduo.entity.OmegaRootEntity;
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

/** Omega Root renderer: scaled generated model + emissive layer (5 phase colourways) + additive VFX (beams, sigil, novas, cutscene light). */
public class OmegaRootRenderer extends MobRenderer<OmegaRootEntity, OmegaRootModel> {
    private static final ResourceLocation TEX = new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/omega_root.png");
    private static final ResourceLocation[] GLOW = {
            new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/omega_root_glow_1.png"),
            new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/omega_root_glow_2.png"),
            new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/omega_root_glow_3.png"),
            new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/omega_root_glow_4.png"),
            new ResourceLocation(DarkWebDuoMod.MODID, "textures/entity/omega_root_glow_5.png")};
    private static final float[][] C1 = {{1.00F, 0.85F, 0.48F}, {0.44F, 0.91F, 1.00F}, {1.00F, 0.31F, 0.85F}, {1.00F, 0.31F, 0.19F}, {1.00F, 1.00F, 1.00F}};
    private static final float[][] C2 = {{0.54F, 0.36F, 1.00F}, {1.00F, 1.00F, 1.00F}, {1.00F, 0.85F, 0.48F}, {1.00F, 0.69F, 0.13F}, {0.54F, 0.94F, 1.00F}};
    /** model-space heights (blocks above the feet) of the chest core and of the mask */
    private static final float CORE_Y = 5.45F;
    private static final float EYE_Y = 7.4F;

    // smoothed aim (the server updates the aim once per tick; interpolate it so beams sweep smoothly)
    private static int aimEntity = -1;
    private static long aimTick = Long.MIN_VALUE;
    private static float aimYawPrev, aimYawCur, aimPitchPrev, aimPitchCur;

    public OmegaRootRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new OmegaRootModel(ctx.bakeLayer(OmegaRootModel.LAYER_LOCATION)), 3.6F);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(OmegaRootEntity e) {
        return TEX;
    }

    @Override
    protected void scale(OmegaRootEntity e, PoseStack ps, float partial) {
        float s = OmegaRootModel.MODEL_SCALE;
        ps.scale(s, s, s);
    }

    @Override
    protected boolean shouldShowName(OmegaRootEntity e) {
        return false;
    }

    private static float rnd(long seed) {
        long x = seed * 0x9E3779B97F4A7C15L;
        x ^= (x >>> 29);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 32);
        return ((x & 0xFFFFL) / 65535.0F) * 2F - 1F;
    }

    private static float pulse(float at, float t0, float dur) {
        if (at < t0 || at > t0 + dur) return 0F;
        return 1F - (at - t0) / dur;
    }

    private static float ramp(float v, float a, float b) {
        return Mth.clamp((v - a) / (b - a), 0F, 1F);
    }

    private static float glitch(OmegaRootEntity e, float partial) {
        int mode = e.getMode();
        float mt = e.modeTime(partial);
        if (mode == BossBase.MODE_DYING) return Math.min(1F, mt / 200F) * 0.4F;
        if (mode == BossBase.MODE_CUTSCENE && e.getCutsceneId() == CutsceneIds.OMEGA_PHASE && mt < 26F) return 0.22F;
        if (mode == BossBase.MODE_FIGHT && e.getAttack() == OmegaRootEntity.A_SKIP) {
            float at = e.attackTime(partial);
            for (int b = 10; b <= 70; b += 30) if (at > b - 4F && at < b + 3F) return 0.3F;
        }
        return 0F;
    }

    @Override
    public void render(OmegaRootEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        this.model.partial = partial;
        int mode = e.getMode();
        boolean hidden = mode == BossBase.MODE_DYING && e.modeTime(partial) >= 202F;
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
    static float glowAlpha(OmegaRootEntity e, float partial) {
        int mode = e.getMode();
        float time = e.tickCount + partial;
        float mt = e.modeTime(partial);
        if (mode == BossBase.MODE_CUTSCENE && e.getCutsceneId() == CutsceneIds.OMEGA_SUMMON) {
            return 0.65F + 0.35F * Mth.sin(time * 0.35F);
        }
        if (mode == BossBase.MODE_DYING) {
            float flick = 0.5F + 0.5F * rnd((long) (mt * 1.7F) + 77);
            return mt < 200F ? 0.45F + 0.55F * flick : 1F;
        }
        if (mode == BossBase.MODE_CUTSCENE) return 0.55F + 0.45F * Math.abs(Mth.sin(mt * 0.9F));
        return 0.9F + 0.1F * Mth.sin(time * 0.2F);
    }

    private static final class GlowLayer extends RenderLayer<OmegaRootEntity, OmegaRootModel> {
        GlowLayer(RenderLayerParent<OmegaRootEntity, OmegaRootModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, OmegaRootEntity e, float limbSwing, float limbSwingAmount,
                           float partial, float age, float netHeadYaw, float headPitch) {
            float a = glowAlpha(e, partial);
            if (a <= 0.01F) return;
            int ph = Mth.clamp(e.getPhase(), 1, 5);
            int idx = e.getMode() == BossBase.MODE_CUTSCENE && e.getCutsceneId() == CutsceneIds.OMEGA_PHASE && e.modeTime(partial) > 25F
                    ? Math.min(4, ph) : ph - 1;
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(GLOW[idx]));
            this.getParentModel().renderToBuffer(ps, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, a);
        }
    }

    // ------------------------------------------------------------------ helpers
    private static Vec3 entityBase(OmegaRootEntity e, float partial) {
        return new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
    }

    /** vertical offset (<= 0) from the entity's feet down to the floor under it */
    private static float floorOffset(OmegaRootEntity e, Vec3 base) {
        Vec3 from = base.add(0.0, 1.0, 0.0);
        Vec3 to = base.add(0.0, -45.0, 0.0);
        BlockHitResult r = e.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        if (r.getType() == HitResult.Type.MISS) return 0F;
        return (float) Math.min(0.0, r.getLocation().y - base.y);
    }

    private static void updateAim(OmegaRootEntity e) {
        long gt = e.level().getGameTime();
        if (aimEntity != e.getId()) {
            aimEntity = e.getId();
            aimTick = gt;
            aimYawPrev = aimYawCur = e.getAimYaw();
            aimPitchPrev = aimPitchCur = e.getAimPitch();
            return;
        }
        if (gt != aimTick) {
            aimTick = gt;
            aimYawPrev = aimYawCur;
            aimPitchPrev = aimPitchCur;
            aimYawCur = e.getAimYaw();
            aimPitchCur = e.getAimPitch();
            // keep the interpolation on the short way round
            aimYawPrev = aimYawCur + Mth.wrapDegrees(aimYawPrev - aimYawCur);
        }
    }

    private static float smoothYaw(float partial) {
        return Mth.lerp(partial, aimYawPrev, aimYawCur);
    }

    private static float smoothPitch(float partial) {
        return Mth.lerp(partial, aimPitchPrev, aimPitchCur);
    }

    /** one giant layered beam from an entity-relative origin; returns nothing, draws impact flare and scorch ring. */
    private void drawBeam(OmegaRootEntity e, float partial, Matrix4f m, VertexConsumer vc, Vec3 orig, float yaw, float pitch,
                          float w, float fade, float time, float[] k1, float[] k2) {
        if (fade <= 0.01F) return;
        Vec3 base = entityBase(e, partial);
        Vec3 from = base.add(orig);
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 to = from.add(dir.scale(72.0));
        BlockHitResult hit = e.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        Vec3 endAbs = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
        float len = (float) endAbs.subtract(from).length();
        float ex = (float) orig.x, ey = (float) orig.y, ez = (float) orig.z;
        float dx = (float) dir.x, dy = (float) dir.y, dz = (float) dir.z;
        float pw = 1.0F + 0.1F * Mth.sin(time * 1.9F);
        float spin = time * 0.17F;
        RenderUtil.prism(vc, m, ex, ey, ez, dx, dy, dz, len, 0.95F * w * pw, 1.15F * w * pw, 0.26F * fade, 0.16F * fade, 12, spin, k1[0], k1[1], k1[2]);
        RenderUtil.prism(vc, m, ex, ey, ez, dx, dy, dz, len, 0.62F * w * pw, 0.72F * w * pw, 0.5F * fade, 0.4F * fade, 10, -spin * 1.3F, k2[0], k2[1], k2[2]);
        RenderUtil.prism(vc, m, ex, ey, ez, dx, dy, dz, len, 0.3F * w * pw, 0.36F * w * pw, 1.0F * fade, 0.9F * fade, 8, spin * 2F, 1F, 1F, 1F);
        for (float s = 1.5F + (time * 2.6F) % 4.5F; s < len; s += 4.5F) {
            RenderUtil.prism(vc, m, ex + dx * s, ey + dy * s, ez + dz * s, dx, dy, dz, 0.55F, 1.6F * w, 1.6F * w, 0.0F, 0.45F * fade, 10, spin, k1[0], k1[1], k1[2]);
        }
        float px = ex + dx * len, py = ey + dy * len, pz = ez + dz * len;
        RenderUtil.flare(vc, m, ex + dx * 1.4F, ey + dy * 1.4F, ez + dz * 1.4F, 2.2F * w * fade, 1.0F, time * 0.5F, 1F, 1F, 1F);
        RenderUtil.flare(vc, m, ex + dx * 1.4F, ey + dy * 1.4F, ez + dz * 1.4F, 3.4F * w * fade, 0.7F, -time * 0.3F, k1[0], k1[1], k1[2]);
        RenderUtil.flare(vc, m, px, py, pz, 2.8F * w * fade, 1.0F, time * 0.4F, k1[0], k1[1], k1[2]);
        RenderUtil.flare(vc, m, px, py, pz, 1.5F * w * fade, 1.0F, -time * 0.4F, 1F, 1F, 1F);
        if (hit.getType() != HitResult.Type.MISS) {
            RenderUtil.disc(vc, m, px, py + 0.05F, pz, 0.0F, 2.6F * w * fade, 0.7F * fade, 0.0F, 28, spin, k1[0], k1[1], k1[2]);
            RenderUtil.disc(vc, m, px, py + 0.06F, pz, 2.0F * w * fade, 2.4F * w * fade, 0.8F * fade, 0.8F * fade, 28, -spin, k2[0], k2[1], k2[2]);
        }
    }

    private void charge(VertexConsumer vc, Matrix4f m, float x, float y, float z, float k, float time, float[] k1, float[] k2) {
        if (k <= 0F) return;
        RenderUtil.flare(vc, m, x, y, z, 0.4F + 2.6F * k, 0.9F, time * 0.4F, k1[0], k1[1], k1[2]);
        RenderUtil.flare(vc, m, x, y, z, 0.2F + 1.3F * k, 0.95F, -time * 0.3F, 1F, 1F, 1F);
        RenderUtil.shell(vc, m, x, y, z, 0.6F + 1.8F * k, 0.2F, 8, 16, time * 0.1F, k2[0], k2[1], k2[2]);
    }

    // ------------------------------------------------------------------ additive VFX
    private void renderVfx(OmegaRootEntity e, float partial, PoseStack ps, MultiBufferSource buf) {
        int mode = e.getMode();
        updateAim(e);
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        ps.pushPose();
        Matrix4f m = ps.last().pose();
        float time = e.tickCount + partial;
        float mt = e.modeTime(partial);
        int ph = Mth.clamp(e.getPhase(), 1, 5);
        float[] k1 = C1[ph - 1];
        float[] k2 = C2[ph - 1];
        float yawBody = Mth.rotLerp(partial, e.yRotO, e.getYRot());
        Vec3 fwd = Vec3.directionFromRotation(0.0F, yawBody);
        Vec3 rgt = new Vec3(-fwd.z, 0.0, fwd.x);
        float ga = glowAlpha(e, partial);
        Vec3 base = entityBase(e, partial);
        float fy = floorOffset(e, base);
        int cid = e.getCutsceneId();
        boolean summon = mode == BossBase.MODE_CUTSCENE && cid == CutsceneIds.OMEGA_SUMMON;
        float st = 160F + mt; // summon timeline

        // ---------- ground sigil
        float sig = 0F;
        if (mode == BossBase.MODE_FIGHT) sig = e.getAttack() != 0 ? 0.55F : 0.32F;
        else if (summon) sig = st < 210F ? ramp(st, 160F, 208F) * 0.4F : 0.7F * (1F - ramp(st, 250F, 280F) * 0.55F);
        else if (mode == BossBase.MODE_CUTSCENE) sig = 0.85F;
        else if (mode == BossBase.MODE_DYING) sig = Math.max(0F, 1F - mt / 200F) * 0.8F;
        if (sig > 0.01F) {
            float sy = fy + 0.06F;
            float spin = time * 0.018F;
            RenderUtil.disc(vc, m, 0F, sy, 0F, 0F, 13.5F, sig * 0.05F, sig * 0.12F, 56, spin, k1[0], k1[1], k1[2]);
            RenderUtil.disc(vc, m, 0F, sy, 0F, 13.0F, 13.6F, sig * 0.85F, sig * 0.85F, 72, spin, k1[0], k1[1], k1[2]);
            RenderUtil.disc(vc, m, 0F, sy, 0F, 9.2F, 9.5F, sig * 0.75F, sig * 0.75F, 64, -spin * 1.5F, k2[0], k2[1], k2[2]);
            RenderUtil.disc(vc, m, 0F, sy, 0F, 5.6F, 5.85F, sig * 0.7F, sig * 0.7F, 48, spin * 2.1F, k1[0], k1[1], k1[2]);
            RenderUtil.disc(vc, m, 0F, sy, 0F, 2.7F, 2.9F, sig * 0.6F, sig * 0.6F, 32, -spin * 3F, k2[0], k2[1], k2[2]);
            for (int i = 0; i < 8; i++) {
                float a0 = spin * 3F + i * (float) (Math.PI / 4);
                float c = Mth.cos(a0), s = Mth.sin(a0);
                float px = -s * 0.1F, pz = c * 0.1F;
                RenderUtil.glowQuad(vc, m,
                        c * 2.9F - px, sy + 0.01F, s * 2.9F - pz, sig * 0.7F,
                        c * 13.0F - px, sy + 0.01F, s * 13.0F - pz, sig * 0.15F,
                        c * 13.0F + px, sy + 0.01F, s * 13.0F + pz, sig * 0.15F,
                        c * 2.9F + px, sy + 0.01F, s * 2.9F + pz, sig * 0.7F,
                        k2[0], k2[1], k2[2]);
            }
        }

        // ---------- chest core heart-beat
        if (mode == BossBase.MODE_FIGHT || mode == BossBase.MODE_CUTSCENE) {
            RenderUtil.flare(vc, m, (float) fwd.x * 1.05F, CORE_Y, (float) fwd.z * 1.05F, 0.75F + 0.2F * Mth.sin(time * 0.25F), 0.6F * ga, time * 0.1F, k1[0], k1[1], k1[2]);
        }

        // ---------- attack-specific effects
        if (mode == BossBase.MODE_FIGHT) {
            int atk = e.getAttack();
            float at = e.attackTime(partial);
            fightFx(e, partial, m, vc, atk, at, time, k1, k2, fwd, rgt, fy, ga);
        }

        // ---------- cutscenes
        if (summon) {
            if (st >= 160F && st < 232F) {
                float a = ramp(st, 160F, 176F) * (1F - ramp(st, 205F, 232F));
                float h = 70F;
                RenderUtil.beam(vc, m, 0F, fy, 0F, h, 4.2F, 1.6F, 0.34F * a, 0.0F, 14, time * 0.05F, k1[0], k1[1], k1[2]);
                RenderUtil.beam(vc, m, 0F, fy, 0F, h, 2.0F, 0.7F, 0.5F * a, 0.0F, 12, -time * 0.09F, k2[0], k2[1], k2[2]);
                RenderUtil.beam(vc, m, 0F, fy, 0F, h, 0.9F, 0.3F, 0.7F * a, 0.0F, 8, time * 0.15F, 1F, 1F, 1F);
            }
            if (st >= 160F && st < 212F) {
                float a = 0.8F * (1F - ramp(st, 200F, 212F));
                RenderUtil.shell(vc, m, 0F, CORE_Y, 0F, 4.2F + 0.8F * Mth.sin(time * 0.3F), 0.2F * a, 10, 24, time * 0.07F, k1[0], k1[1], k1[2]);
                RenderUtil.flare(vc, m, 0F, CORE_Y, 0F, 3.5F, 0.8F * a, time * 0.2F, 1F, 1F, 1F);
            }
            if (st >= 210F && st < 262F) {
                float k = (st - 210F) / 50F;
                float a = 1F - k;
                RenderUtil.shell(vc, m, 0F, fy + 0.5F, 0F, 1.5F + k * 42F, 0.3F * a, 8, 28, 0F, k1[0], k1[1], k1[2]);
                RenderUtil.disc(vc, m, 0F, fy + 0.08F, 0F, Math.max(0F, k * 38F - 2.5F), k * 38F, 0.9F * a, 0.9F * a, 64, 0F, k1[0], k1[1], k1[2]);
                RenderUtil.disc(vc, m, 0F, fy + 0.09F, 0F, Math.max(0F, k * 28F - 1.8F), k * 28F, 0.8F * a, 0.8F * a, 56, 0F, k2[0], k2[1], k2[2]);
                if (st < 224F) RenderUtil.flare(vc, m, 0F, fy + 1.2F, 0F, 9F * (1F - (st - 210F) / 14F), 1.0F, time * 0.3F, 1F, 1F, 1F);
            }
            if (st >= 235F && st < 262F) {
                float a = 1F - ramp(st, 235F, 262F);
                float hx = (float) fwd.x * 1.6F, hz = (float) fwd.z * 1.6F;
                RenderUtil.flare(vc, m, hx, EYE_Y, hz, 2.0F + (st - 235F) * 0.35F, 0.9F * a, time * 0.3F, k1[0], k1[1], k1[2]);
                RenderUtil.shell(vc, m, hx, EYE_Y, hz, (st - 235F) * 0.9F, 0.25F * a, 8, 20, 0F, k2[0], k2[1], k2[2]);
            }
        }
        if (mode == BossBase.MODE_CUTSCENE && cid == CutsceneIds.OMEGA_PHASE && mt > 22F && mt < 62F) {
            float a = ramp(mt, 22F, 27F) * (mt > 50F ? (62F - mt) / 12F : 1F);
            RenderUtil.beam(vc, m, 0F, fy, 0F, 70F, 4.6F, 1.8F, 0.5F * a, 0.0F, 14, time * 0.08F, k1[0], k1[1], k1[2]);
            RenderUtil.beam(vc, m, 0F, fy, 0F, 70F, 2.0F, 0.8F, 0.65F * a, 0.0F, 12, -time * 0.12F, k2[0], k2[1], k2[2]);
            RenderUtil.shell(vc, m, 0F, CORE_Y, 0F, (mt - 22F) * 0.6F, 0.22F * a, 10, 24, time * 0.1F, k1[0], k1[1], k1[2]);
            RenderUtil.flare(vc, m, 0F, CORE_Y, 0F, 4.5F * a, 0.9F * a, time * 0.3F, 1F, 1F, 1F);
        }
        if (mode == BossBase.MODE_DYING) {
            deathFx(e, m, vc, mt, time, k1, k2, fy);
        }
        ps.popPose();
    }

    private void fightFx(OmegaRootEntity e, float partial, Matrix4f m, VertexConsumer vc, int atk, float at, float time,
                         float[] k1, float[] k2, Vec3 fwd, Vec3 rgt, float fy, float ga) {
        float fx = (float) fwd.x, fz = (float) fwd.z, rx = (float) rgt.x, rz = (float) rgt.z;
        switch (atk) {
            case OmegaRootEntity.A_REND: {
                for (int t0 : new int[]{16, 32, 48}) {
                    float p = pulse(at, t0, 7F);
                    if (p > 0F) RenderUtil.flare(vc, m, fx * 5.5F, 3.6F, fz * 5.5F, 4.5F * p + 1F, 0.9F * p, time * 0.4F, k1[0], k1[1], k1[2]);
                }
                float p = pulse(at, 60F, 12F);
                if (p > 0F) {
                    RenderUtil.flare(vc, m, fx * 4.2F, fy + 1F, fz * 4.2F, 7F * (1F - p) + 2F, 0.9F * p, time * 0.3F, 1F, 1F, 1F);
                    RenderUtil.shell(vc, m, fx * 4.2F, fy + 0.5F, fz * 4.2F, (1F - p) * 9F, 0.3F * p, 8, 20, 0F, k2[0], k2[1], k2[2]);
                }
                break;
            }
            case OmegaRootEntity.A_FISSURE: {
                if (at < 29F) {
                    float k = ramp(at, 2F, 26F);
                    for (int s = -1; s <= 1; s += 2) charge(vc, m, rx * 2.6F * s, 8.6F, rz * 2.6F * s, k, time, k1, k2);
                }
                float p = pulse(at, 28F, 14F);
                if (p > 0F) {
                    RenderUtil.flare(vc, m, 0F, fy + 1.2F, 0F, 10F * p + 2F, 1.0F * p, time * 0.3F, 1F, 1F, 1F);
                    RenderUtil.disc(vc, m, 0F, fy + 0.1F, 0F, Math.max(0F, (1F - p) * 28F - 2.5F), (1F - p) * 28F, 0.9F * p, 0.9F * p, 64, 0F, k1[0], k1[1], k1[2]);
                }
                break;
            }
            case OmegaRootEntity.A_SEEKER: {
                for (int s = -1; s <= 1; s += 2) {
                    float ph = ((at - 14F) % 8F + 8F) % 8F / 8F;
                    float size = at >= 14F && at <= 66F ? 0.7F + 1.1F * (1F - ph) : 0.6F * ramp(at, 2F, 14F);
                    if (size > 0.05F) RenderUtil.flare(vc, m, rx * 4.4F * s, 6.6F, rz * 4.4F * s, size, 0.85F, time * 0.4F, k1[0], k1[1], k1[2]);
                }
                break;
            }
            case OmegaRootEntity.A_BEAM: {
                Vec3 orig = new Vec3(fx * 1.5F, CORE_Y, fz * 1.5F);
                if (at < 52F) {
                    float k = ramp(at, 2F, 50F);
                    charge(vc, m, (float) orig.x, CORE_Y, (float) orig.z, k * 1.6F, time, k1, k2);
                    Vec3 d = Vec3.directionFromRotation(smoothPitch(partial), smoothYaw(partial));
                    if (at > 8F) RenderUtil.prism(vc, m, (float) orig.x, CORE_Y, (float) orig.z, (float) d.x, (float) d.y, (float) d.z, 40F, 0.04F, 0.04F, 0.15F + 0.6F * k, 0.05F, 4, 0F, k2[0], k2[1], k2[2]);
                } else if (at < 134F) {
                    float fade = at < 56F ? (at - 52F) / 4F : (at > 124F ? (134F - at) / 10F : 1F);
                    drawBeam(e, partial, m, vc, orig, smoothYaw(partial), smoothPitch(partial), 2.0F, fade, time, k1, k2);
                }
                break;
            }
            case OmegaRootEntity.A_TWIN: {
                Vec3 orig = new Vec3(fx * 1.0F, EYE_Y, fz * 1.0F);
                if (at < 42F) {
                    charge(vc, m, (float) orig.x, EYE_Y, (float) orig.z, ramp(at, 2F, 40F) * 1.3F, time, k2, k1);
                } else if (at < 112F) {
                    float fade = at < 46F ? (at - 42F) / 4F : (at > 102F ? (112F - at) / 10F : 1F);
                    float spread = OmegaRootEntity.twinSpread(Math.max(0F, at - 40F));
                    float yaw = smoothYaw(partial);
                    drawBeam(e, partial, m, vc, orig, yaw + spread, 11F, 1.5F, fade, time, k1, k2);
                    drawBeam(e, partial, m, vc, orig, yaw - spread, 11F, 1.5F, fade, time, k2, k1);
                }
                break;
            }
            case OmegaRootEntity.A_SKIP: {
                for (int b = 10; b <= 70; b += 30) {
                    float p = pulse(at, b - 2F, 7F);
                    if (p > 0F) {
                        RenderUtil.flare(vc, m, 0F, 4.2F, 0F, 7F * p + 1F, 0.9F * p, time * 0.5F, k1[0], k1[1], k1[2]);
                        RenderUtil.shell(vc, m, 0F, 4.2F, 0F, (1F - p) * 6F, 0.3F * p, 8, 20, time * 0.2F, k2[0], k2[1], k2[2]);
                    }
                    float s = pulse(at, b + 12F, 6F);
                    if (s > 0F) RenderUtil.flare(vc, m, fx * 5.5F, 3.6F, fz * 5.5F, 4F * s + 1F, 0.9F * s, time * 0.4F, k1[0], k1[1], k1[2]);
                }
                break;
            }
            case OmegaRootEntity.A_METEORS:
            case OmegaRootEntity.A_JUDGMENT: {
                float a = ramp(at, 10F, 22F) * (1F - ramp(at, atk == OmegaRootEntity.A_METEORS ? 90F : 100F, atk == OmegaRootEntity.A_METEORS ? 108F : 118F));
                if (a > 0.01F) {
                    RenderUtil.beam(vc, m, 0F, EYE_Y, 0F, EYE_Y + 60F, 1.2F, 0.5F, 0.55F * a, 0.0F, 10, time * 0.12F, k1[0], k1[1], k1[2]);
                    RenderUtil.flare(vc, m, 0F, 9.6F, 0F, 2.2F + 0.5F * Mth.sin(time * 0.4F), 0.8F * a, time * 0.3F, 1F, 1F, 1F);
                }
                break;
            }
            case OmegaRootEntity.A_GRID: {
                float p = pulse(at, 24F, 12F);
                float p2 = pulse(at, 58F, 12F);
                float pp = Math.max(p, p2);
                if (pp > 0F) {
                    RenderUtil.flare(vc, m, 0F, fy + 1.5F, 0F, 9F * pp + 2F, pp, time * 0.3F, 1F, 1F, 1F);
                    RenderUtil.shell(vc, m, 0F, fy + 0.5F, 0F, (1F - pp) * 18F, 0.3F * pp, 8, 24, 0F, k1[0], k1[1], k1[2]);
                }
                break;
            }
            case OmegaRootEntity.A_FLOOR: {
                float a = ramp(at, 4F, 12F) * (1F - ramp(at, 70F, 90F));
                if (a > 0.01F) {
                    float r = ((at - 12F) * 0.9F) % 30F;
                    if (r < 0F) r += 30F;
                    RenderUtil.disc(vc, m, 0F, fy + 0.12F, 0F, Math.max(0F, r - 0.7F), r, 0.8F * a, 0.8F * a, 72, 0F, k2[0], k2[1], k2[2]);
                    RenderUtil.flare(vc, m, 0F, 8.5F, 0F, 2.0F, 0.7F * a, time * 0.3F, k1[0], k1[1], k1[2]);
                }
                break;
            }
            case OmegaRootEntity.A_SINGULARITY: {
                if (at > 14F && at < 118F) {
                    float grow = ramp(at, 16F, 30F) * (1F - ramp(at, 104F, 118F));
                    float cx = fx * 13F, cz = fz * 13F, cy = fy + 2.2F;
                    float spin = time * 0.12F;
                    RenderUtil.shell(vc, m, cx, cy, cz, 3.0F * grow, 0.3F, 10, 24, spin, k1[0], k1[1], k1[2]);
                    RenderUtil.flare(vc, m, cx, cy, cz, 5.5F * grow, 0.8F, spin * 2F, k2[0], k2[1], k2[2]);
                    for (int i = 0; i < 4; i++) {
                        float rr = 4.0F + i * 2.6F;
                        RenderUtil.disc(vc, m, cx, cy, cz, rr * grow, (rr + 0.4F) * grow, 0.7F, 0.7F, 56, (i % 2 == 0 ? spin : -spin) * (1.6F - i * 0.25F), i % 2 == 0 ? k1[0] : k2[0], i % 2 == 0 ? k1[1] : k2[1], i % 2 == 0 ? k1[2] : k2[2]);
                    }
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2 + 0.7;
                        float sx = cx + (float) Math.cos(a) * 16F, sz = cz + (float) Math.sin(a) * 16F;
                        RenderUtil.flare(vc, m, sx, cy, sz, 2.2F * grow, 0.7F, -spin * 2F, k2[0], k2[1], k2[2]);
                        RenderUtil.disc(vc, m, sx, cy, sz, 2.4F * grow, 2.7F * grow, 0.6F, 0.6F, 32, spin * 2F, k1[0], k1[1], k1[2]);
                    }
                    charge(vc, m, rx * -3.6F + fx * 3F, 6.8F, rz * -3.6F + fz * 3F, ramp(at, 4F, 18F) * (1F - ramp(at, 100F, 112F)), time, k1, k2);
                }
                break;
            }
            case OmegaRootEntity.A_LEGION: {
                float k = ramp(at, 2F, 18F);
                float p = pulse(at, 18F, 10F);
                charge(vc, m, 0F, 9.0F, 0F, k * 1.4F, time, k1, k2);
                if (p > 0F) RenderUtil.shell(vc, m, 0F, 9.0F, 0F, (1F - p) * 10F, 0.3F * p, 8, 20, 0F, k2[0], k2[1], k2[2]);
                break;
            }
            case OmegaRootEntity.A_CLOCK: {
                float a = ramp(at, 6F, 40F) * (1F - ramp(at, 120F, 130F));
                if (a > 0.01F) {
                    RenderUtil.disc(vc, m, 0F, fy + 0.12F, 0F, 1.4F, 1.9F, 0.9F * a, 0.9F * a, 32, time * 0.2F, k1[0], k1[1], k1[2]);
                    RenderUtil.flare(vc, m, 0F, fy + 0.8F, 0F, 3F, 0.8F * a, time * 0.3F, k2[0], k2[1], k2[2]);
                }
                break;
            }
            case OmegaRootEntity.A_COMETS: {
                float a = ramp(at, 6F, 16F) * (1F - ramp(at, 70F, 84F));
                if (a > 0.01F) {
                    for (int s = -1; s <= 1; s += 2) charge(vc, m, rx * 3.4F * s, 9.0F, rz * 3.4F * s, a * 0.9F, time + s * 3F, k1, k2);
                }
                break;
            }
            case OmegaRootEntity.A_CHARGE: {
                if (at < 20F) {
                    charge(vc, m, 0F, CORE_Y, 0F, ramp(at, 2F, 19F) * 1.4F, time, k1, k2);
                } else if (at < 46F) {
                    float len = Math.min(34F, 6F + (at - 20F) * 2.2F);
                    float a = at < 40F ? 1F : (46F - at) / 6F;
                    RenderUtil.prism(vc, m, 0F, 3.2F, 0F, -fx, 0F, -fz, len, 2.8F, 0.2F, 0.5F * a, 0.0F, 12, time * 0.2F, k1[0], k1[1], k1[2]);
                    RenderUtil.prism(vc, m, 0F, 3.2F, 0F, -fx, 0F, -fz, len * 0.8F, 1.4F, 0.1F, 0.8F * a, 0.0F, 8, -time * 0.3F, 1F, 1F, 1F);
                    RenderUtil.flare(vc, m, fx * 1.5F, 3.6F, fz * 1.5F, 4.5F * a, 0.9F * a, time * 0.5F, k2[0], k2[1], k2[2]);
                }
                float p = pulse(at, 40F, 10F);
                if (p > 0F) RenderUtil.shell(vc, m, 0F, fy + 0.5F, 0F, (1F - p) * 14F, 0.3F * p, 8, 24, 0F, k1[0], k1[1], k1[2]);
                break;
            }
            case OmegaRootEntity.A_HANDS: {
                for (int t0 : new int[]{8, 30}) {
                    float k = ramp(at, t0, t0 + 6F) * (1F - ramp(at, t0 + 6F, t0 + 10F));
                    if (k > 0.01F) {
                        for (int s = -1; s <= 1; s += 2) charge(vc, m, rx * 3.0F * s, 10.8F, rz * 3.0F * s, k * 1.2F, time, k1, k2);
                    }
                }
                break;
            }
            case OmegaRootEntity.A_PRISM: {
                if (at > 10F && at < 94F) {
                    float a = ramp(at, 10F, 20F) * (1F - ramp(at, 84F, 94F));
                    for (int i = 0; i < 8; i++) {
                        double ang = time * 0.25 + i * Math.PI / 4;
                        RenderUtil.flare(vc, m, (float) Math.cos(ang) * 2.8F, fy + 1.2F + 0.0F, (float) Math.sin(ang) * 2.8F, 0.9F, 0.8F * a, (float) ang, i % 2 == 0 ? k1[0] : k2[0], i % 2 == 0 ? k1[1] : k2[1], i % 2 == 0 ? k1[2] : k2[2]);
                    }
                    RenderUtil.disc(vc, m, 0F, fy + 0.12F, 0F, 2.5F, 3.1F, 0.9F * a, 0.9F * a, 40, time * 0.3F, k1[0], k1[1], k1[2]);
                }
                break;
            }
            case OmegaRootEntity.A_NOVA: {
                if (at > 6F && at < 140F) {
                    float k = ramp(at, 6F, 120F);
                    float fade = at > 132F ? (140F - at) / 8F : 1F;
                    float cy = CORE_Y;
                    RenderUtil.shell(vc, m, 0F, cy, 0F, 2.0F + 12F * k, 0.14F * fade, 12, 28, time * 0.05F, k1[0], k1[1], k1[2]);
                    RenderUtil.shell(vc, m, 0F, cy, 0F, 1.4F + 7F * k, 0.2F * fade, 10, 24, -time * 0.08F, k2[0], k2[1], k2[2]);
                    RenderUtil.flare(vc, m, 0F, cy, 0F, 1.5F + 5F * k, 0.95F * fade, time * 0.2F, 1F, 1F, 1F);
                    RenderUtil.beam(vc, m, 0F, fy, 0F, 0.6F, 2.6F, 1.6F, 0.3F * fade, 0.0F, 12, time * 0.1F, k1[0], k1[1], k1[2]);
                    for (int i = 0; i < 3; i++) {
                        double a = time * (0.07 + i * 0.03) + i * 2.1;
                        float rad = 9F + 5F * i;
                        RenderUtil.flare(vc, m, (float) Math.cos(a) * rad * k, cy + (i - 1) * 2F, (float) Math.sin(a) * rad * k, 1.4F, 0.8F * fade, (float) a, k2[0], k2[1], k2[2]);
                    }
                }
                float p = pulse(at, 136F, 16F);
                if (p > 0F) {
                    RenderUtil.flare(vc, m, 0F, fy + 1.5F, 0F, 16F * p + 3F, p, time * 0.3F, 1F, 1F, 1F);
                    RenderUtil.shell(vc, m, 0F, fy + 0.6F, 0F, (1F - p) * 40F, 0.4F * p, 10, 32, 0F, k1[0], k1[1], k1[2]);
                    RenderUtil.disc(vc, m, 0F, fy + 0.12F, 0F, Math.max(0F, (1F - p) * 40F - 3F), (1F - p) * 40F, 0.9F * p, 0.9F * p, 72, 0F, k1[0], k1[1], k1[2]);
                }
                break;
            }
            default:
                break;
        }
    }

    private void deathFx(OmegaRootEntity e, Matrix4f m, VertexConsumer vc, float mt, float time, float[] k1, float[] k2, float fy) {
        float cy = CORE_Y;
        if (mt > 90F && mt < 202F) {
            float build = ramp(mt, 90F, 200F);
            float size = 1.5F + 9F * build * build;
            RenderUtil.flare(vc, m, 0F, cy, 0F, size, 0.9F, time * 0.3F, 1F, 1F, 1F);
            RenderUtil.flare(vc, m, 0F, cy, 0F, size * 0.7F, 0.7F, -time * 0.2F, k1[0], k1[1], k1[2]);
            RenderUtil.shell(vc, m, 0F, cy, 0F, size * 0.55F, 0.3F, 10, 24, time * 0.1F, k2[0], k2[1], k2[2]);
        }
        if (mt >= 120F && mt < 202F) {
            long seg = (long) (mt / 3F);
            float fadeIn = ramp(mt, 120F, 135F);
            for (int i = 0; i < 6; i++) {
                float dx = rnd(seg * 13 + i * 5L + 1), dy = rnd(seg * 13 + i * 5L + 2), dz = rnd(seg * 13 + i * 5L + 3);
                float l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                if (l < 0.05F) continue;
                dx /= l;
                dy = dy / l * 0.7F;
                dz /= l;
                float len = 24F + 18F * Math.abs(rnd(seg * 13 + i * 5L + 4));
                float ph = (mt / 3F) - seg;
                float a = fadeIn * (1F - ph * 0.5F);
                RenderUtil.prism(vc, m, 0F, cy, 0F, dx, dy, dz, len, 0.45F, 0.12F, 0.8F * a, 0.0F, 6, time * 0.3F, i % 2 == 0 ? k1[0] : 1F, i % 2 == 0 ? k1[1] : 1F, i % 2 == 0 ? k1[2] : 1F);
                RenderUtil.prism(vc, m, 0F, cy, 0F, dx, dy, dz, len, 1.1F, 0.25F, 0.25F * a, 0.0F, 8, -time * 0.2F, k2[0], k2[1], k2[2]);
            }
        }
        if (mt >= 200F && mt < 262F) {
            float k = (mt - 200F) / 62F;
            float a = 1F - k;
            RenderUtil.flare(vc, m, 0F, cy, 0F, 12F + (mt - 200F) * 3.2F, a, time * 0.3F, 1F, 1F, 1F);
            RenderUtil.flare(vc, m, 0F, cy, 0F, 8F + (mt - 200F) * 2.4F, 0.9F * a, -time * 0.2F, k1[0], k1[1], k1[2]);
            RenderUtil.shell(vc, m, 0F, cy, 0F, (mt - 200F) * 2.6F, 0.4F * a, 12, 32, 0F, k1[0], k1[1], k1[2]);
            RenderUtil.shell(vc, m, 0F, cy, 0F, (mt - 200F) * 1.7F, 0.45F * a, 10, 28, time * 0.05F, k2[0], k2[1], k2[2]);
            RenderUtil.beam(vc, m, 0F, fy, 0F, 90F, 7F, 2.2F, 0.7F * a, 0.0F, 16, time * 0.2F, 1F, 1F, 1F);
            RenderUtil.beam(vc, m, 0F, fy, 0F, 90F, 12F, 4F, 0.3F * a, 0.0F, 16, -time * 0.1F, k1[0], k1[1], k1[2]);
            RenderUtil.disc(vc, m, 0F, fy + 0.1F, 0F, Math.max(0F, (mt - 200F) * 1.4F - 3F), (mt - 200F) * 1.4F, 0.9F * a, 0.9F * a, 72, 0F, k1[0], k1[1], k1[2]);
        }
    }
}
