package com.darkwebduo.client.cutscene;

import com.darkwebduo.entity.CutsceneCameraEntity;
import com.darkwebduo.registry.ModEntities;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client-side cutscene controller: takes over the camera, tracks time, exposes values to the event hooks and the HUD. */
public final class CutsceneClient {
    private static boolean active;
    private static int bossId = -1;
    private static int arg;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 lastFocus = Vec3.ZERO;
    private static int ticks;
    private static CutsceneScripts.Script script;
    private static CutsceneCameraEntity cam;
    private static Entity previousCamera;
    private static CameraType previousCameraType;

    // values computed each frame by renderTick()
    private static float curFov = 70F;
    private static float curRoll;
    private static float curShake;

    private CutsceneClient() {}

    public static boolean isActive() {
        return active;
    }

    public static CutsceneScripts.Script script() {
        return script;
    }

    public static int arg() {
        return arg;
    }

    public static void start(int id, int a, int boss, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        CutsceneScripts.Script s = CutsceneScripts.get(id, a);
        if (s == null) return;
        if (active) restoreCamera(mc);
        script = s;
        arg = a;
        bossId = boss;
        origin = new Vec3(x, y, z);
        lastFocus = origin;
        ticks = 0;
        previousCamera = mc.getCameraEntity();
        previousCameraType = mc.options.getCameraType();
        cam = new CutsceneCameraEntity(ModEntities.CUTSCENE_CAMERA.get(), mc.level);
        cam.setPos(x, y + 10, z);
        mc.setCameraEntity(cam);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        active = true;
        renderTick(0F);
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (active) restoreCamera(mc);
        active = false;
        script = null;
        cam = null;
        previousCamera = null;
    }

    private static void restoreCamera(Minecraft mc) {
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        } else if (previousCamera != null) {
            mc.setCameraEntity(previousCamera);
        }
        if (previousCameraType != null) {
            mc.options.setCameraType(previousCameraType);
        }
        curRoll = 0F;
        curShake = 0F;
    }

    /** Called every client tick (END phase). */
    public static void clientTick() {
        CameraFx.tick();
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || script == null) {
            stop();
            return;
        }
        ticks++;
        // trigger the scripted flashes exactly once on their tick
        for (CutsceneScripts.Flash f : script.flashes) {
            if (f.at == ticks) CameraFx.flash(f.argb, f.dur);
        }
        if (ticks >= script.duration + 2) {
            stop();
        }
    }

    /** Called at the start of every rendered frame; places the camera entity. */
    public static void renderTick(float partial) {
        if (!active || script == null || cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float t = ticks + partial;
        float[] v = CutsceneScripts.sample(script.keys, t);
        float radius = v[0], height = v[1], angle = v[2], look = v[3];
        curFov = v[4];
        curRoll = v[5];
        curShake = v[6];

        Vec3 focus = focusPoint(mc, partial, t);
        double ang = Math.toRadians(angle);
        double cx = focus.x + Math.cos(ang) * radius;
        double cy = focus.y + height;
        double cz = focus.z + Math.sin(ang) * radius;

        float shake = curShake + CameraFx.shakeAmount(partial);
        if (shake > 0F) {
            float tt = (ticks + partial) * 0.9F;
            cx += CameraFx.noise(tt, 1F) * shake * 0.12F;
            cy += CameraFx.noise(tt, 2F) * shake * 0.12F;
            cz += CameraFx.noise(tt, 3F) * shake * 0.12F;
        }

        double lx = focus.x, ly = focus.y + look, lz = focus.z;
        double dx = lx - cx, dy = ly - cy, dz = lz - cz;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));

        double ey = cy - 1.62;
        cam.setPos(cx, ey, cz);
        cam.xo = cx; cam.yo = ey; cam.zo = cz;
        cam.xOld = cx; cam.yOld = ey; cam.zOld = cz;
        cam.setYRot(yaw);
        cam.yRotO = yaw;
        cam.setXRot(pitch);
        cam.xRotO = pitch;
        if (mc.getCameraEntity() != cam) {
            mc.setCameraEntity(cam);
        }
    }

    private static Vec3 focusPoint(Minecraft mc, float partial, float t) {
        if (script.originFrom >= 0 && t >= script.originFrom) {
            return origin;
        }
        if (script.focusBoss && bossId >= 0 && mc.level != null) {
            Entity boss = mc.level.getEntity(bossId);
            if (boss != null && !boss.isRemoved()) {
                lastFocus = boss.getPosition(partial);
                return lastFocus;
            }
            return lastFocus;
        }
        return origin;
    }

    // ---- values for event hooks / HUD
    public static float fov() {
        return curFov;
    }

    public static float roll() {
        return curRoll;
    }

    public static float time(float partial) {
        return ticks + partial;
    }

    /** 0..1 letterbox amount (eases in/out). */
    public static float letterbox(float partial) {
        if (!active || script == null) return 0F;
        float t = ticks + partial;
        float in = Math.min(1F, t / 10F);
        float out = Math.min(1F, (script.duration - t) / 10F);
        float k = Math.max(0F, Math.min(in, out));
        return k * k * (3F - 2F * k);
    }

    /** current glitch amount 0..1 from the script windows */
    public static float glitch(float partial) {
        if (!active || script == null) return 0F;
        float t = ticks + partial;
        float g = 0F;
        for (CutsceneScripts.Glitch gl : script.glitches) {
            if (t >= gl.from && t <= gl.to) g = Math.max(g, gl.amount);
        }
        return g;
    }
}
