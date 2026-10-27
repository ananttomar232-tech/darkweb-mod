package com.darkwebduo.client.render;

import com.darkwebduo.block.IPhoneDuoBlock;
import com.darkwebduo.block.entity.IPhoneDuoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the iPhone Duo hovering over its stand: an idle showcase (closed = slowly turning, open = turns to face you) and the
 * boss-reward arrival show (scan-line materialisation, spiralling descent, landing shockwave, unfold, boot).
 */
public class IPhoneDuoRenderer implements BlockEntityRenderer<IPhoneDuoBlockEntity> {
    private final Font font;
    private final PhoneRig.State st = new PhoneRig.State();

    public IPhoneDuoRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public boolean shouldRenderOffScreen(IPhoneDuoBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    @Override
    public void render(IPhoneDuoBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof IPhoneDuoBlock)) return;
        BlockPos pos = be.getBlockPos();

        float gt = (float) (level.getGameTime() % 1000000L) + partial;
        float appear = be.appearTime(partial);
        boolean appearing = appear >= 0F;

        float fold;
        float height;
        float spin = 0F;
        float alpha = 1F;
        int screen = PhoneRig.SCREEN_ON;
        float screenLevel = 1F;
        float bob = 0.012F * (float) Math.sin(gt * 0.07F);

        if (appearing) {
            fold = IPhoneDuoBlockEntity.appearFold(appear);
            height = IPhoneDuoBlockEntity.heightAt(appear) + bob * smooth((appear - IPhoneDuoBlockEntity.T_LAND) / 20F);
            float k = Mth.clamp(appear / (float) IPhoneDuoBlockEntity.T_LAND, 0F, 1F);
            float e = 1F - (1F - k) * (1F - k) * (1F - k);
            spin = 1080F * (1F - e);
            alpha = smooth(appear / (float) IPhoneDuoBlockEntity.T_SOLID);
            if (appear < IPhoneDuoBlockEntity.T_BOOT) {
                screen = PhoneRig.SCREEN_OFF;
            } else if (appear < IPhoneDuoBlockEntity.T_BOOT_END) {
                screen = PhoneRig.SCREEN_BOOT;
                screenLevel = Math.min(1F, (appear - IPhoneDuoBlockEntity.T_BOOT) / 10F)
                        * Math.min(1F, (IPhoneDuoBlockEntity.T_BOOT_END - appear) / 8F);
            } else {
                screenLevel = Math.min(1F, (appear - IPhoneDuoBlockEntity.T_BOOT_END) / 10F);
            }
        } else {
            fold = be.foldFraction(partial);
            height = IPhoneDuoBlockEntity.HOVER + bob;
        }

        // ---- orientation: closed = slow showcase turn, open = turns towards the viewer (limited to the front half-space)
        float baseYaw = -state.getValue(IPhoneDuoBlock.FACING).toYRot();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - (pos.getX() + 0.5);
        double dz = cam.z - (pos.getZ() + 0.5);
        float camYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float toCam = Mth.clamp(Mth.wrapDegrees(camYaw - baseYaw), -62F, 62F);
        float idle = Mth.wrapDegrees(gt * 0.6F);
        float openness = smooth(1F - fold);
        float yaw = baseYaw + Mth.lerp(openness, idle, toCam) + spin;

        st.fold = fold;
        st.colorway = be.getColorway();
        st.screen = screen;
        st.screenLevel = screenLevel;
        st.bodyAlpha = alpha;
        st.clock = PhoneInfo.clock();
        st.date = PhoneInfo.date();
        st.info = PhoneInfo.info();

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(0.0, height, 0.0);
        PhoneRig.draw(ps, buf, font, light, st);
        ps.popPose();

        // ---- additive effects around the phone (not rotated)
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        effects(ps, buf, gt, appear, height, fold, screen != PhoneRig.SCREEN_OFF);
        ps.popPose();
    }

    private void effects(PoseStack ps, MultiBufferSource buf, float gt, float appear, float height, float fold, boolean screenOn) {
        VertexConsumer glow = buf.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        final float cy = height + PhoneRig.PH * 0.5F;

        // levitation field under the phone (always on)
        float breath = 0.5F + 0.5F * (float) Math.sin(gt * 0.09F);
        RenderUtil.disc(glow, m, 0F, 0.075F, 0F, 0.10F, 0.40F, 0.30F + 0.10F * breath, 0.0F, 40, gt * 0.02F, 0.55F, 0.78F, 1.0F);
        RenderUtil.disc(glow, m, 0F, 0.078F, 0F, 0.35F, 0.37F, 0.55F, 0.55F, 40, -gt * 0.03F, 0.80F, 0.93F, 1.0F);
        RenderUtil.beam(glow, m, 0F, 0.08F, 0F, height + 0.05F, 0.20F, 0.30F, 0.16F + 0.06F * breath, 0.0F, 18, gt * 0.03F, 0.60F, 0.82F, 1.0F);

        if (appear >= 0F) {
            float t = appear;
            // scan-line of light that "prints" the phone into existence
            if (t < IPhoneDuoBlockEntity.T_SOLID + 6) {
                float k = Mth.clamp(t / (float) IPhoneDuoBlockEntity.T_SOLID, 0F, 1F);
                float sy = height + k * PhoneRig.PH;
                float a = 0.9F * (1F - smooth((t - IPhoneDuoBlockEntity.T_SOLID) / 6F));
                RenderUtil.glowQuad(glow, m, -0.55F, sy, -0.55F, 0F, 0.55F, sy, -0.55F, a, 0.55F, sy, 0.55F, 0F, -0.55F, sy, 0.55F, a * 0.5F,
                        0.7F, 0.9F, 1F);
                RenderUtil.disc(glow, m, 0F, sy, 0F, 0.05F, 0.55F, 0.8F * a, 0F, 36, gt * 0.2F, 0.7F, 0.9F, 1F);
            }
            // descent column
            if (t < IPhoneDuoBlockEntity.T_LAND + 20) {
                float col = 1F - smooth((t - IPhoneDuoBlockEntity.T_LAND) / 20F);
                float grow = smooth(t / 20F);
                RenderUtil.beam(glow, m, 0F, 0.08F, 0F, 5.5F, 0.46F * grow, 0.08F, 0.45F * col * grow, 0.0F, 24, gt * 0.1F, 0.62F, 0.85F, 1F);
                RenderUtil.beam(glow, m, 0F, 0.08F, 0F, 5.5F, 0.18F * grow, 0.03F, 0.75F * col * grow, 0.1F * col, 12, -gt * 0.17F, 1F, 1F, 1F);
            }
            // orbiting sparks
            if (t < IPhoneDuoBlockEntity.T_LAND) {
                for (int i = 0; i < 6; i++) {
                    float a = gt * 0.35F + i * 1.0472F;
                    float r = 0.42F + 0.08F * (float) Math.sin(gt * 0.2F + i);
                    float yy = cy + 0.22F * (float) Math.sin(gt * 0.15F + i * 2F);
                    RenderUtil.flare(glow, m, (float) Math.cos(a) * r, yy, (float) Math.sin(a) * r, 0.07F, 0.9F, gt * 0.3F, 0.8F, 0.95F, 1F);
                }
            }
            // landing shockwave
            float lt = t - IPhoneDuoBlockEntity.T_LAND;
            if (lt >= 0F && lt < 30F) {
                float k = lt / 30F;
                float r = 0.2F + 2.6F * (1F - (1F - k) * (1F - k));
                float a = 0.9F * (1F - k);
                RenderUtil.disc(glow, m, 0F, 0.09F, 0F, r - 0.10F, r + 0.10F, a, a * 0.1F, 56, 0F, 0.75F, 0.92F, 1F);
                RenderUtil.disc(glow, m, 0F, 0.09F, 0F, 0.0F, r * 0.6F, a * 0.5F, 0F, 40, 0F, 1F, 1F, 1F);
                RenderUtil.flare(glow, m, 0F, 0.35F, 0F, 0.9F * (1F - k), 0.9F * (1F - k), gt * 0.05F, 0.8F, 0.92F, 1F);
            }
            // unfold flash along the hinge
            float ut = t - IPhoneDuoBlockEntity.T_UNFOLD;
            if (ut >= 0F && ut < 30F) {
                float a = 0.8F * (1F - ut / 30F);
                RenderUtil.flare(glow, m, 0F, cy, 0F, 0.55F * (1F - ut / 40F), a, gt * 0.02F, 0.8F, 0.92F, 1F);
            }
            // boot glow
            if (t >= IPhoneDuoBlockEntity.T_BOOT && t < IPhoneDuoBlockEntity.T_BOOT_END + 10) {
                float a = 0.18F * (float) Math.sin(Math.PI * Mth.clamp((t - IPhoneDuoBlockEntity.T_BOOT) / 50F, 0F, 1F));
                RenderUtil.disc(glow, m, 0F, cy - 0.34F, 0.02F, 0.0F, 0.62F, a, 0F, 30, 0F, 0.8F, 0.9F, 1F);
            }
        } else if (screenOn && fold < 0.9F) {
            // faint screen-spill light on the stand when open
            float a = 0.07F * (1F - fold);
            RenderUtil.disc(glow, m, 0F, 0.082F, 0F, 0.0F, 0.55F, a, 0F, 30, 0F, 0.7F, 0.85F, 1F);
        }
    }
}
