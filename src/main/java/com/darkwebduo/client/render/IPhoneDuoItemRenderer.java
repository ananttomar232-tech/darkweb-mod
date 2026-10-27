package com.darkwebduo.client.render;

import com.darkwebduo.item.IPhoneDuoItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the real 3D phone for the iPhone Duo item (inventory, hand, item frame, dropped). */
public class IPhoneDuoItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final PhoneRig.State st = new PhoneRig.State();

    public IPhoneDuoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        st.colorway = IPhoneDuoItem.colorOf(stack);
        st.fold = 1F;
        st.screen = PhoneRig.SCREEN_ON;
        st.screenLevel = 1F;
        st.bodyAlpha = 1F;
        st.clock = PhoneInfo.clock();
        st.date = PhoneInfo.date();
        st.info = PhoneInfo.info();
        ps.pushPose();
        ps.translate(0.5F, 0.5F, 0.5F);
        ps.scale(1.22F, 1.22F, 1.22F);
        ps.translate(0F, -PhoneRig.PH * 0.5F, 0F);
        PhoneRig.draw(ps, buf, Minecraft.getInstance().font, light, st);
        ps.popPose();
    }
}
