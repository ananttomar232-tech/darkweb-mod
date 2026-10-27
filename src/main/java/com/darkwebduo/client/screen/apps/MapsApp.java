package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import com.darkwebduo.entity.BossBase;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Locale;

/** A live top-down map of the loaded world around the player, drawn from the terrain heightmap and block map colours. */
public class MapsApp extends PhoneApp {
    private static final int N = 64;
    private static final int PX = 192;
    private static final int BG = 0xFF0B0D12;
    private static int counter;

    private DynamicTexture tex;
    private ResourceLocation loc;
    private int zoom = 1; // 0 near (1 block/px), 1 mid (2), 2 far (4)
    private int ticks;
    private int lastX = Integer.MIN_VALUE;
    private int lastZ;
    private int lastZoom = -1;
    private int sinceBuild = 999;

    public MapsApp(PhoneHost host) {
        super(host);
    }

    @Override
    public String id() {
        return "maps";
    }

    private void ensureTexture() {
        if (tex != null) return;
        tex = new DynamicTexture(N, N, false);
        loc = new ResourceLocation("darkweb_duo", "phone_map/" + (counter++));
        host.mc().getTextureManager().register(loc, tex);
        NativeImage img = tex.getPixels();
        if (img != null) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) img.setPixelRGBA(x, y, 0xFF181410);
            }
            tex.upload();
        }
    }

    @Override
    public void onClose() {
        if (tex != null) {
            host.mc().getTextureManager().release(loc);
            tex = null;
            loc = null;
        }
    }

    @Override
    public void tick() {
        ticks++;
        sinceBuild++;
        Minecraft mc = host.mc();
        Player p = mc.player;
        if (p == null || mc.level == null) return;
        int dx = Math.abs(p.getBlockX() - lastX);
        int dz = Math.abs(p.getBlockZ() - lastZ);
        if (tex != null && (lastZoom != zoom || sinceBuild >= 20 || (sinceBuild >= 4 && (dx > 1 || dz > 1)))) {
            rebuild(mc.level, p);
        }
    }

    private static int pack(int r, int g, int b) {
        r = Mth.clamp(r, 0, 255);
        g = Mth.clamp(g, 0, 255);
        b = Mth.clamp(b, 0, 255);
        return 0xFF000000 | (b << 16) | (g << 8) | r; // NativeImage pixels are ABGR
    }

    /** reads the top solid block of a column (handles roofed dimensions by scanning down from near the player) */
    private int surfaceY(Level level, int x, int z, int playerY, boolean ceiling) {
        if (!ceiling) return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int y = Math.min(level.getMaxBuildHeight() - 2, playerY + 10);
        for (int i = 0; i < 40 && y > level.getMinBuildHeight(); i++, y--) {
            mp.set(x, y, z);
            if (!level.getBlockState(mp).isAir()) {
                mp.set(x, y + 1, z);
                if (level.getBlockState(mp).isAir()) return y;
            }
        }
        return playerY - 20;
    }

    private void rebuild(Level level, Player p) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        sinceBuild = 0;
        lastZoom = zoom;
        int step = 1 << zoom;
        int cx = p.getBlockX();
        int cz = p.getBlockZ();
        lastX = cx;
        lastZ = cz;
        boolean ceiling = level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int pz = 0; pz < N; pz++) {
            for (int px = 0; px < N; px++) {
                int wx = cx + (px - N / 2) * step;
                int wz = cz + (pz - N / 2) * step;
                mp.set(wx, 64, wz);
                if (!level.hasChunkAt(mp)) {
                    img.setPixelRGBA(px, pz, (px + pz) % 2 == 0 ? 0xFF1B1812 : 0xFF181510);
                    continue;
                }
                int y = surfaceY(level, wx, wz, p.getBlockY(), ceiling);
                mp.set(wx, y, wz);
                BlockState bs = level.getBlockState(mp);
                if (bs.isAir()) {
                    img.setPixelRGBA(px, pz, 0xFF0C0A08);
                    continue;
                }
                int abgr = bs.getMapColor(level, mp).calculateRGBColor(MapColor.Brightness.HIGH);
                int r = abgr & 255;
                int gr = (abgr >> 8) & 255;
                int b = (abgr >> 16) & 255;
                if ((abgr >>> 24) == 0) {
                    r = 12;
                    gr = 10;
                    b = 8;
                }
                float shade;
                if (bs.getFluidState().is(FluidTags.WATER)) {
                    int depth = 0;
                    BlockPos.MutableBlockPos dp = new BlockPos.MutableBlockPos(wx, y, wz);
                    while (depth < 12 && level.getBlockState(dp).getFluidState().is(FluidTags.WATER)) {
                        depth++;
                        dp.setY(y - depth);
                    }
                    shade = 1F - Math.min(0.55F, depth * 0.05F);
                    r = (int) (r * 0.8F);
                    gr = (int) (gr * 0.9F);
                } else {
                    int yn = surfaceY(level, wx + step, wz - step, p.getBlockY(), ceiling);
                    shade = 1F + Mth.clamp((y - yn) * 0.07F, -0.28F, 0.24F);
                }
                img.setPixelRGBA(px, pz, pack((int) (r * shade), (int) (gr * shade), (int) (b * shade)));
            }
        }
        tex.upload();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Font f = host.font();
        Minecraft mc = host.mc();
        Player p = mc.player;
        ClientLevel level = mc.level;
        g.fill(0, 0, W, H, BG);
        PhoneUi.text(g, f, "Maps", 18F, 32F, 1.6F, 0xFFFFFFFF);
        if (p == null || level == null) {
            PhoneUi.textC(g, f, "No location", 120F, 170F, 1.1F, 0xFF8E8E93);
            return;
        }
        ensureTexture();
        PhoneUi.textR(g, f, WeatherApp.biomeName(level, p.blockPosition()), 224F, 38F, 0.9F, 0xFF8EC5FF);

        float mx0 = 24F;
        float my0 = 62F;
        PhoneUi.image(g, loc, mx0, my0, PX, PX, N, N);
        PhoneUi.cornerMask(g, mx0, my0, mx0 + PX, my0 + PX, 16F, BG);
        PhoneUi.rrStroke(g, mx0, my0, mx0 + PX, my0 + PX, 16F, 0.8F, 0x55FFFFFF);
        float cx = mx0 + PX * 0.5F;
        float cy = my0 + PX * 0.5F;
        float ppx = PX / (float) N; // screen px per map px
        int step = 1 << zoom;

        // distance rings
        PhoneUi.ring(g, cx, cy, 32F, 0.35F, 0x33FFFFFF);
        PhoneUi.ring(g, cx, cy, 64F, 0.35F, 0x22FFFFFF);

        // markers
        double px = p.getX();
        double pz = p.getZ();
        float half = PX * 0.5F - 8F;
        for (Entity e : level.entitiesForRendering()) {
            if (e == p || !(e instanceof LivingEntity)) continue;
            float ex = (float) ((e.getX() - px) / step * ppx);
            float ez = (float) ((e.getZ() - pz) / step * ppx);
            boolean boss = e instanceof BossBase;
            boolean hostile = e instanceof Enemy;
            boolean player = e instanceof Player;
            if (!boss && !hostile && !player) continue;
            if (Math.abs(ex) > half || Math.abs(ez) > half) {
                if (!boss) continue;
                float k = half / Math.max(Math.abs(ex), Math.abs(ez));
                ex *= k;
                ez *= k;
            }
            if (boss) {
                float pulse = 0.5F + 0.5F * (float) Math.sin(ticks * 0.25F);
                PhoneUi.disc(g, cx + ex, cy + ez, 6F + 3F * pulse, 0x44FF2A2A);
                PhoneUi.disc(g, cx + ex, cy + ez, 3.4F, 0xFFFF3030);
                PhoneUi.ring(g, cx + ex, cy + ez, 5.4F, 0.7F, 0xFFFFFFFF);
            } else if (player) {
                PhoneUi.disc(g, cx + ex, cy + ez, 2.8F, 0xFF4FC3FF);
            } else {
                PhoneUi.disc(g, cx + ex, cy + ez, 1.8F, 0xFFFF9F0A);
            }
        }

        // player arrow (screen up = north = -z; yaw 0 faces +z)
        double yaw = Math.toRadians(p.getYRot());
        float dx = (float) -Math.sin(yaw);
        float dz = (float) Math.cos(yaw);
        float ox = -dz;
        float oz = dx;
        PhoneUi.tri(g, cx + dx * 10F, cy + dz * 10F, cx - dx * 6.5F + ox * 6F, cy - dz * 6.5F + oz * 6F,
                cx - dx * 6.5F - ox * 6F, cy - dz * 6.5F - oz * 6F, 0xCC000000);
        PhoneUi.tri(g, cx + dx * 8.2F, cy + dz * 8.2F, cx - dx * 5F + ox * 4.4F, cy - dz * 5F + oz * 4.4F,
                cx - dx * 5F - ox * 4.4F, cy - dz * 5F - oz * 4.4F, 0xFF0A84FF);

        // compass
        PhoneUi.disc(g, cx, my0 + 9F, 6.5F, 0xCC000000);
        PhoneUi.textC(g, f, "N", cx, my0 + 5.4F, 0.9F, 0xFFFF453A);
        PhoneUi.textC(g, f, "S", cx, my0 + PX - 14F, 0.8F, 0xCCFFFFFF);
        PhoneUi.textC(g, f, "W", mx0 + 9F, cy - 3.5F, 0.8F, 0xCCFFFFFF);
        PhoneUi.textC(g, f, "E", mx0 + PX - 9F, cy - 3.5F, 0.8F, 0xCCFFFFFF);

        // readout
        BlockPos bp = p.blockPosition();
        PhoneUi.text(g, f, String.format(Locale.ENGLISH, "X %d   Y %d   Z %d", bp.getX(), bp.getY(), bp.getZ()), 24F, 262F, 1.0F, 0xFFFFFFFF);
        PhoneUi.text(g, f, "Facing " + cap(p.getDirection().getName()), 24F, 276F, 0.85F, 0xFF8E8E93);

        // nearest boss signal
        BossBase near = null;
        double best = Double.MAX_VALUE;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof BossBase && e.isAlive()) {
                double d = e.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    near = (BossBase) e;
                }
            }
        }
        if (near != null) {
            PhoneUi.rr(g, 24F, 292F, 216F, 316F, 10F, 0x33FF2A2A);
            PhoneUi.disc(g, 38F, 304F, 3.4F, 0xFFFF3030);
            PhoneUi.text(g, f, near.getDisplayName().getString(), 48F, 297F, 0.85F, 0xFFFFFFFF);
            PhoneUi.textR(g, f, Math.round(Math.sqrt(best)) + " m", 208F, 297F, 0.85F, 0xFFFF8A80);
        } else {
            PhoneUi.rr(g, 24F, 292F, 216F, 316F, 10F, 0x22FFFFFF);
            PhoneUi.text(g, f, "No boss signals nearby", 36F, 297F, 0.85F, 0xFF8E8E93);
        }

        // zoom control
        String[] names = {"Near", "Mid", "Far"};
        for (int i = 0; i < 3; i++) {
            float x0 = 24F + i * 64F;
            boolean on = zoom == i;
            PhoneUi.rr(g, x0, 324F, x0 + 60F, 342F, 9F, on ? 0xFF0A84FF : 0xFF1C1C1E);
            PhoneUi.textC(g, f, names[i], x0 + 30F, 329F, 0.85F, on ? 0xFFFFFFFF : 0xFF8E8E93);
        }
    }

    @Override
    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        if (y >= 324D && y <= 342D) {
            int i = (int) Math.floor((x - 24D) / 64D);
            if (i >= 0 && i < 3 && x - 24D - i * 64D <= 60D) {
                if (zoom != i) {
                    zoom = i;
                    host.tap();
                }
                return true;
            }
        }
        return false;
    }
}
