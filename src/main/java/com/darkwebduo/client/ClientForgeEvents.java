package com.darkwebduo.client;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.client.cutscene.CameraFx;
import com.darkwebduo.client.cutscene.CutsceneClient;
import com.darkwebduo.client.screen.CameraView;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client hooks for cutscenes, camera shake and the flash overlay. */
@Mod.EventBusSubscriber(modid = DarkWebDuoMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CutsceneClient.clientTick();
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            CutsceneClient.renderTick(event.renderTickTime);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float partial = (float) event.getPartialTick();
        float shake = CameraFx.shakeAmount(partial);
        float roll = CutsceneClient.isActive() ? CutsceneClient.roll() : 0F;
        if (shake > 0F && !CutsceneClient.isActive()) {
            float t = (System.nanoTime() / 1.0E7F) * 0.02F;
            event.setYaw(event.getYaw() + CameraFx.noise(t, 4F) * shake * 1.6F);
            event.setPitch(event.getPitch() + CameraFx.noise(t, 5F) * shake * 1.6F);
            roll += CameraFx.noise(t, 6F) * shake * 1.2F;
        } else if (shake > 0F) {
            float t = (System.nanoTime() / 1.0E7F) * 0.02F;
            roll += CameraFx.noise(t, 6F) * shake * 0.8F;
        }
        if (roll != 0F) {
            event.setRoll(event.getRoll() + roll);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (CutsceneClient.isActive()) {
            event.setFOV(CutsceneClient.fov());
        } else if (Math.abs(CameraView.zoom - 1.0F) > 0.001F) {
            event.setFOV(event.getFOV() / CameraView.zoom);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (CutsceneClient.isActive()) {
            Input in = event.getInput();
            in.forwardImpulse = 0F;
            in.leftImpulse = 0F;
            in.up = false;
            in.down = false;
            in.left = false;
            in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (CutsceneClient.isActive()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (CutsceneClient.isActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (CutsceneClient.isActive() && "minecraft".equals(event.getOverlay().id().getNamespace())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CutsceneClient.stop();
        CameraFx.clear();
    }
}
