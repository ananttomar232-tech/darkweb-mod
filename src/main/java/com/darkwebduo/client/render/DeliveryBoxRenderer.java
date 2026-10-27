package com.darkwebduo.client.render;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.block.DeliveryBoxBlock;
import com.darkwebduo.block.entity.DeliveryBoxBlockEntity;
import com.darkwebduo.item.KeycardItem;
import com.darkwebduo.util.CardFlight;
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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws everything that moves in the delivery-box cinematic: the springy lid, the charge glow, the light column,
 * the shockwave disc, the flying keycard (spinning, with a glow trail) and orbiting sparkles.
 */
public class DeliveryBoxRenderer implements BlockEntityRenderer<DeliveryBoxBlockEntity> {
    private static final float PX = 1.0F / 16.0F;

    public DeliveryBoxRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(new ResourceLocation(DarkWebDuoMod.MODID, path));
    }

    @Override
    public void render(DeliveryBoxBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof DeliveryBoxBlock)) return;
        Level level = be.getLevel();
        if (level == null) return;

        int tier = ((DeliveryBoxBlock) state.getBlock()).getTier();
        boolean open = state.getValue(DeliveryBoxBlock.OPEN);
        float[] c1 = tier == 1 ? new float[]{0.10F, 0.90F, 1.00F} : new float[]{1.00F, 0.77F, 0.23F};
        float[] c2 = tier == 1 ? new float[]{1.00F, 0.17F, 0.84F} : new float[]{1.00F, 0.16F, 0.24F};
        float time = (level.getGameTime() % 100000L) + partialTick;
        float facingDeg = state.getValue(DeliveryBoxBlock.FACING).toYRot();

        // ---------------- idle: a faint pulsing halo so closed boxes read as "live" packages
        if (!open) {
            ps.pushPose();
            ps.translate(0.5, 0.0, 0.5);
            VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
            Matrix4f m = ps.last().pose();
            float pulse = 0.5F + 0.5F * (float) Math.sin(time * 0.12F);
            RenderUtil.disc(glow, m, 0, 0.02F, 0, 0.30F, 0.62F + pulse * 0.08F, 0.30F + 0.18F * pulse, 0.0F, 24, time * 0.01F, c1[0], c1[1], c1[2]);
            RenderUtil.flare(glow, m, 0, 0.78F + 0.04F * (float) Math.sin(time * 0.1F), 0, 0.16F + 0.04F * pulse, 0.55F, time * 0.05F, c1[0], c1[1], c1[2]);
            ps.popPose();
            return;
        }

        float t = be.animTime(partialTick);
        boolean running = be.isOpening();

        // ---------------- lid (static model has no lid in the OPEN state)
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-facingDeg));

        float charge = running ? CardFlight.chargeIntensity(t) : 0F;
        if (charge > 0F && t < CardFlight.T_LID_START + 4) {
            float sh = 0.012F * charge;
            ps.translate(Math.sin(time * 2.9F) * sh, 0.0, Math.cos(time * 3.3F) * sh);
        }

        float angle = running ? CardFlight.lidAngle(t) : CardFlight.LID_OPEN_DEGREES;
        TextureAtlasSprite top = sprite("block/delivery_box_lid_top_" + tier);
        TextureAtlasSprite side = sprite("block/delivery_box_lid_side_" + tier);
        VertexConsumer solid = buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));

        ps.pushPose();
        ps.translate(0.0F, 9.0F * PX, -6.5F * PX);
        ps.mulPose(Axis.XP.rotationDegrees(-angle));
        ps.translate(0.0F, -9.0F * PX, 6.5F * PX);
        RenderUtil.Uv uvTop = RenderUtil.Uv.of(top, 0F, 0F, 1F, 1F);
        RenderUtil.Uv uvSide = RenderUtil.Uv.of(side, 0F, 0F, 1F, 0.125F);
        RenderUtil.Uv uvUnder = RenderUtil.Uv.of(side, 0F, 0F, 1F, 1F);
        RenderUtil.cuboid(solid, ps.last(),
                -6.5F * PX, 9.0F * PX, -6.5F * PX, 6.5F * PX, 11.0F * PX, 6.5F * PX,
                uvUnder, uvTop, uvSide, uvSide, uvSide, uvSide,
                light, overlay, 1F, 1F, 1F, 1F);
        ps.popPose();

        if (running) {
            // ---------------- glow, column, shockwave
            VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
            Matrix4f m = ps.last().pose();

            if (charge > 0F) {
                float k = charge;
                RenderUtil.disc(glow, m, 0, 0.03F, 0, 0.1F, 0.4F + 0.7F * k, 0.5F * k, 0.0F, 28, time * 0.2F, c1[0], c1[1], c1[2]);
                RenderUtil.flare(glow, m, 0, 0.62F, 0, 0.25F + 0.45F * k, 0.6F * k, time * 0.3F, c2[0], c2[1], c2[2]);
                // light leaking through the seam between lid and body
                RenderUtil.glowQuad(glow, m,
                        -6.0F * PX, 9.0F * PX, 6.6F * PX, 0.9F * k,
                        6.0F * PX, 9.0F * PX, 6.6F * PX, 0.9F * k,
                        6.0F * PX, 9.6F * PX, 6.6F * PX, 0.0F,
                        -6.0F * PX, 9.6F * PX, 6.6F * PX, 0.0F,
                        c1[0], c1[1], c1[2]);
            }

            float bI = CardFlight.beamIntensity(t);
            if (bI > 0.001F) {
                float flick = 0.9F + 0.1F * (float) Math.sin(time * 1.7F);
                float widen = 1.0F + 0.35F * (float) Math.exp(-(t - CardFlight.T_BURST) * 0.18F);
                // core column
                RenderUtil.beam(glow, m, 0, 0.62F, 0, 5.0F, 0.26F * widen, 0.10F, 0.85F * bI * flick, 0.0F, 10, time * 0.05F, 1.0F, 1.0F, 1.0F);
                // coloured halo
                RenderUtil.beam(glow, m, 0, 0.62F, 0, 4.4F, 0.52F * widen, 0.22F, 0.40F * bI * flick, 0.0F, 12, -time * 0.03F, c1[0], c1[1], c1[2]);
                RenderUtil.beam(glow, m, 0, 0.62F, 0, 3.6F, 0.85F * widen, 0.35F, 0.16F * bI, 0.0F, 14, time * 0.02F, c2[0], c2[1], c2[2]);
                // thin rays circling the column
                for (int i = 0; i < 6; i++) {
                    float a = time * 0.06F + i * (float) (Math.PI / 3);
                    float rr = 0.55F + 0.12F * (float) Math.sin(time * 0.1F + i);
                    RenderUtil.beam(glow, m, (float) Math.cos(a) * rr, 0.62F, (float) Math.sin(a) * rr, 3.2F + i * 0.2F,
                            0.025F, 0.01F, 0.55F * bI, 0.0F, 4, a, c1[0], c1[1], c1[2]);
                }
                // shockwave rings spreading along the floor
                float since = Math.max(0F, t - CardFlight.T_BURST);
                for (int k = 0; k < 3; k++) {
                    float u = (since * 0.045F + k * 0.33F) % 1.0F;
                    float rad = 0.4F + u * 3.2F;
                    RenderUtil.disc(glow, m, 0, 0.04F, 0, rad - 0.06F, rad + 0.06F, 0.55F * bI * (1F - u), 0.55F * bI * (1F - u), 40, 0F, c2[0], c2[1], c2[2]);
                }
                // burst flare at the moment the lid blows
                float burstK = CardFlight.clamp01(1F - (t - CardFlight.T_BURST) / 14F);
                if (burstK > 0F) {
                    RenderUtil.flare(glow, m, 0, 0.9F, 0, 1.6F * burstK + 0.2F, 0.9F * burstK, time * 0.2F, 1F, 1F, 1F);
                    RenderUtil.flare(glow, m, 0, 0.9F, 0, 2.4F * burstK + 0.1F, 0.5F * burstK, -time * 0.13F, c1[0], c1[1], c1[2]);
                }
                // orbiting sparkles
                for (int i = 0; i < 10; i++) {
                    float a = time * (0.05F + 0.01F * (i % 3)) + i * 0.628F;
                    float y = 0.9F + ((time * 0.02F + i * 0.19F) % 1.0F) * 3.2F;
                    float rr = 0.4F + 0.25F * (float) Math.sin(i * 1.7F + time * 0.04F);
                    RenderUtil.flare(glow, m, (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, 0.07F, 0.9F * bI, time * 0.2F + i, 1F, 1F, 1F);
                }
            }
        }
        ps.popPose();

        // ---------------- the flying keycard (world space, so the pose is not rotated with the block)
        if (running && CardFlight.cardVisible(t)) {
            Vec3 base = new Vec3(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY(), be.getBlockPos().getZ() + 0.5);
            Vec3 target = base.add(0, 2.2, 0);
            if (be.getOpener() != null) {
                Player pl = level.getPlayerByUUID(be.getOpener());
                if (pl != null) {
                    target = pl.getPosition(partialTick).add(0, pl.getBbHeight() * 0.62, 0);
                }
            }
            Vec3 p = CardFlight.cardPos(t, base, target);
            float s = CardFlight.cardScale(t);

            // glow trail: the card's last few positions as fading flares
            VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
            for (int k = 1; k <= 6; k++) {
                float tt = t - k * 1.6F;
                if (!CardFlight.cardVisible(tt)) break;
                Vec3 q = CardFlight.cardPos(tt, base, target);
                ps.pushPose();
                ps.translate(q.x - be.getBlockPos().getX(), q.y - be.getBlockPos().getY(), q.z - be.getBlockPos().getZ());
                float fade = 1F - k / 7F;
                RenderUtil.flare(glow, ps.last().pose(), 0, 0, 0, 0.32F * fade * s, 0.55F * fade, time * 0.3F + k, c1[0], c1[1], c1[2]);
                ps.popPose();
            }

            ps.pushPose();
            ps.translate(p.x - be.getBlockPos().getX(), p.y - be.getBlockPos().getY(), p.z - be.getBlockPos().getZ());
            RenderUtil.flare(glow, ps.last().pose(), 0, 0, 0, 0.6F * s, 0.7F, time * 0.2F, 1F, 1F, 1F);
            ps.mulPose(Axis.YP.rotationDegrees(CardFlight.cardSpin(t)));
            ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * 0.2F) * 12F));
            float sc = 1.9F * s;
            ps.scale(sc, sc, sc);
            ItemStack stack = new ItemStack(KeycardItem.itemFor(tier));
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buffer, level, 0);
            ps.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(DeliveryBoxBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
