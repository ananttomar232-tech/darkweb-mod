package com.darkwebduo.client;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.client.cutscene.CutsceneHud;
import com.darkwebduo.client.render.CipherWardenModel;
import com.darkwebduo.client.render.CipherWardenRenderer;
import com.darkwebduo.client.render.DeliveryBoxRenderer;
import com.darkwebduo.client.render.GiantMonitorRenderer;
import com.darkwebduo.client.render.IPhoneDuoRenderer;
import com.darkwebduo.client.render.OmegaRootModel;
import com.darkwebduo.client.render.OmegaRootRenderer;
import com.darkwebduo.client.render.OmegaTerminalRenderer;
import com.darkwebduo.registry.ModBlockEntities;
import com.darkwebduo.registry.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus client registrations: renderers, model layers and the cutscene overlay. */
@Mod.EventBusSubscriber(modid = DarkWebDuoMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CIPHER_WARDEN.get(), CipherWardenRenderer::new);
        event.registerEntityRenderer(ModEntities.OMEGA_ROOT.get(), OmegaRootRenderer::new);
        event.registerEntityRenderer(ModEntities.CUTSCENE_CAMERA.get(), NoopRenderer::new);

        event.registerBlockEntityRenderer(ModBlockEntities.DELIVERY_BOX.get(), DeliveryBoxRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GIANT_MONITOR.get(), GiantMonitorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.OMEGA_TERMINAL.get(), OmegaTerminalRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.IPHONE_DUO.get(), IPhoneDuoRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CipherWardenModel.LAYER_LOCATION, CipherWardenModel::createBodyLayer);
        event.registerLayerDefinition(OmegaRootModel.LAYER_LOCATION, OmegaRootModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("cutscene", (gui, graphics, partialTick, width, height) ->
                CutsceneHud.render(graphics, partialTick, width, height));
    }
}
