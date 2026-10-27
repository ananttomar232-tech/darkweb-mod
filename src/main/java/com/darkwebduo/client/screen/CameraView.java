package com.darkwebduo.client.screen;

import com.darkwebduo.DarkWebDuoMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * State shared by the Camera app, the phone screen and the render hooks: the optical zoom (applied through the FOV event)
 * and the "grab the next finished frame" request used to save the photo.
 */
@Mod.EventBusSubscriber(modid = DarkWebDuoMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CameraView {
    /** current smoothed zoom factor (0.5 = ultra wide, 1 = main) */
    public static float zoom = 1.0F;
    /** zoom the user asked for */
    public static float zoomTarget = 1.0F;
    /** set by the phone screen on the one frame it draws nothing; the render-tick hook then saves that frame */
    public static volatile boolean armed;
    /** true once a frame has been handed to the screenshot writer */
    public static volatile boolean grabbed;
    /** text of the last "saved" message (null while nothing was saved yet) */
    public static volatile String lastSaved;
    public static volatile long lastSavedAt;

    private CameraView() {}

    public static void resetZoom() {
        zoom = 1.0F;
        zoomTarget = 1.0F;
    }

    public static void tickZoom() {
        zoom += (zoomTarget - zoom) * 0.28F;
        if (Math.abs(zoomTarget - zoom) < 0.002F) zoom = zoomTarget;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !armed) return;
        armed = false;
        Minecraft mc = Minecraft.getInstance();
        try {
            Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), msg -> {
                lastSaved = msg.getString();
                lastSavedAt = System.currentTimeMillis();
            });
        } catch (Exception e) {
            lastSaved = "Could not save the photo";
            lastSavedAt = System.currentTimeMillis();
        }
        grabbed = true;
    }
}
