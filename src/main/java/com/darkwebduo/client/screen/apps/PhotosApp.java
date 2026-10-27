package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The gallery: shows the game's screenshots (including photos taken with the Camera app) as a grid with a full viewer. */
public class PhotosApp extends PhoneApp {
    private static final int MAX_PHOTOS = 120;
    private static final int THUMB = 96;
    private static final float CELL = 72F;
    private static final float GAP = 2.5F;
    private static final float GRID_TOP = 62F;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "darkweb-phone-photos");
        t.setDaemon(true);
        return t;
    });
    private static int counter;

    private static final class Img {
        volatile NativeImage pending;
        volatile boolean failed;
        boolean started;
        DynamicTexture tex;
        ResourceLocation loc;
        int w;
        int h;
    }

    private final List<File> files = new ArrayList<File>();
    private final List<Img> thumbs = new ArrayList<Img>();
    private Img full;
    private int viewing = -1;
    private float scroll;
    private float dragAccX;
    private double downX;
    private double downY;
    private boolean moved;

    public PhotosApp(PhoneHost host) {
        super(host);
        scan();
    }

    @Override
    public String id() {
        return "photos";
    }

    private void scan() {
        files.clear();
        thumbs.clear();
        File dir = new File(host.mc().gameDirectory, "screenshots");
        File[] list = dir.listFiles();
        if (list == null) return;
        List<File> all = new ArrayList<File>();
        for (File f : list) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) all.add(f);
        }
        File[] arr = all.toArray(new File[0]);
        Arrays.sort(arr, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (int i = 0; i < arr.length && i < MAX_PHOTOS; i++) {
            files.add(arr[i]);
            thumbs.add(new Img());
        }
    }

    // ------------------------------------------------------------------ background loading
    private static int avg(int c0, int c1, int c2, int c3, int shift) {
        return (((c0 >> shift) & 255) + ((c1 >> shift) & 255) + ((c2 >> shift) & 255) + ((c3 >> shift) & 255)) / 4;
    }

    private void loadThumb(final File file, final Img img) {
        img.started = true;
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                InputStream in = null;
                try {
                    in = new FileInputStream(file);
                    NativeImage src = NativeImage.read(in);
                    int side = Math.min(src.getWidth(), src.getHeight());
                    int ox = (src.getWidth() - side) / 2;
                    int oy = (src.getHeight() - side) / 2;
                    NativeImage out = new NativeImage(THUMB, THUMB, false);
                    for (int y = 0; y < THUMB; y++) {
                        for (int x = 0; x < THUMB; x++) {
                            int x0 = ox + x * side / THUMB;
                            int x1 = Math.min(src.getWidth() - 1, ox + (x + 1) * side / THUMB - 1);
                            int y0 = oy + y * side / THUMB;
                            int y1 = Math.min(src.getHeight() - 1, oy + (y + 1) * side / THUMB - 1);
                            int c0 = src.getPixelRGBA(x0, y0);
                            int c1 = src.getPixelRGBA(Math.max(x0, x1), y0);
                            int c2 = src.getPixelRGBA(x0, Math.max(y0, y1));
                            int c3 = src.getPixelRGBA(Math.max(x0, x1), Math.max(y0, y1));
                            int r = avg(c0, c1, c2, c3, 0);
                            int g = avg(c0, c1, c2, c3, 8);
                            int b = avg(c0, c1, c2, c3, 16);
                            out.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
                        }
                    }
                    img.w = THUMB;
                    img.h = THUMB;
                    src.close();
                    img.pending = out;
                } catch (Exception e) {
                    img.failed = true;
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Exception ignored) {
                            // nothing to do
                        }
                    }
                }
            }
        });
    }

    private void loadFull(final File file, final Img img) {
        img.started = true;
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                InputStream in = null;
                try {
                    in = new FileInputStream(file);
                    NativeImage src = NativeImage.read(in);
                    img.w = src.getWidth();
                    img.h = src.getHeight();
                    img.pending = src;
                } catch (Exception e) {
                    img.failed = true;
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Exception ignored) {
                            // nothing to do
                        }
                    }
                }
            }
        });
    }

    /** moves a finished background image into a GPU texture (render thread only) */
    private void upload(Img img) {
        NativeImage p = img.pending;
        if (p == null || img.tex != null) return;
        img.pending = null;
        img.tex = new DynamicTexture(p);
        img.tex.setFilter(true, false);
        img.loc = new ResourceLocation("darkweb_duo", "phone_photo/" + (counter++));
        host.mc().getTextureManager().register(img.loc, img.tex);
    }

    private void release(Img img) {
        if (img == null) return;
        if (img.tex != null && img.loc != null) {
            host.mc().getTextureManager().release(img.loc);
            img.tex = null;
            img.loc = null;
        }
        NativeImage p = img.pending;
        if (p != null) {
            img.pending = null;
            p.close();
        }
    }

    @Override
    public void onClose() {
        for (int i = 0; i < thumbs.size(); i++) release(thumbs.get(i));
        release(full);
        full = null;
    }

    // ------------------------------------------------------------------ render
    private float contentHeight() {
        int rows = (files.size() + 2) / 3;
        return GRID_TOP + rows * (CELL + GAP) + 24F;
    }

    private void clampScroll() {
        scroll = Math.max(0F, Math.min(Math.max(0F, contentHeight() - (H - 8F)), scroll));
    }

    private void open(int i) {
        if (i < 0 || i >= files.size()) return;
        release(full);
        viewing = i;
        full = new Img();
        loadFull(files.get(i), full);
    }

    private void closeViewer() {
        release(full);
        full = null;
        viewing = -1;
    }

    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Font f = host.font();
        g.fill(0, 0, W, H, 0xFF000000);
        if (viewing >= 0) {
            renderViewer(g, f, pane);
            return;
        }
        pane.scissor(g, 0, 26F, W, H);
        PhoneUi.text(g, f, "Photos", 18F, 34F - scroll, 1.7F, 0xFFFFFFFF);
        PhoneUi.text(g, f, files.size() + (files.size() == 1 ? " Photo" : " Photos"), 18F, 52F - scroll, 0.8F, 0xFF8E8E93);
        if (files.isEmpty()) {
            PhoneUi.image(g, PhoneUi.gui("icon_photos"), 92F, 120F, 56F, 56F, 128, 128);
            PhoneUi.textC(g, f, "No Photos", 120F, 192F, 1.3F, 0xFFFFFFFF);
            PhoneUi.textC(g, f, "Take one with the Camera,", 120F, 210F, 0.82F, 0xFF8E8E93);
            PhoneUi.textC(g, f, "or press F2 in the world.", 120F, 222F, 0.82F, 0xFF8E8E93);
        }
        int loadsThisFrame = 0;
        for (int i = 0; i < files.size(); i++) {
            int col = i % 3;
            int row = i / 3;
            float x = 1.5F + col * (CELL + GAP);
            float y = GRID_TOP + row * (CELL + GAP) - scroll;
            if (y > H || y + CELL < 26F) continue;
            Img t = thumbs.get(i);
            if (!t.started && !t.failed && loadsThisFrame < 2) {
                loadThumb(files.get(i), t);
                loadsThisFrame++;
            }
            upload(t);
            if (t.tex != null) {
                PhoneUi.image(g, t.loc, x, y, CELL, CELL, THUMB, THUMB);
            } else {
                PhoneUi.rr(g, x, y, x + CELL, y + CELL, 0F, 0xFF1C1C1E);
                if (t.failed) PhoneUi.textC(g, f, "?", x + CELL * 0.5F, y + CELL * 0.5F - 4F, 1.2F, 0xFF48484A);
            }
        }
        pane.unscissor(g);
    }

    private void renderViewer(GuiGraphics g, Font f, PhonePane pane) {
        upload(full);
        if (full.tex != null) {
            float maxW = 232F;
            float maxH = 250F;
            float k = Math.min(maxW / full.w, maxH / full.h);
            float w = full.w * k;
            float h = full.h * k;
            float x = 120F - w * 0.5F + dragAccX * 0.4F;
            float y = 180F - h * 0.5F;
            PhoneUi.image(g, full.loc, x, y, w, h, full.w, full.h);
        } else if (full.failed) {
            PhoneUi.textC(g, f, "Could not open this photo", 120F, 170F, 1.0F, 0xFF8E8E93);
        } else {
            PhoneUi.textC(g, f, "Loading...", 120F, 170F, 1.0F, 0xFF8E8E93);
        }
        g.fill(0, 0, W, 54, 0xCC000000);
        PhoneUi.text(g, f, "‹", 14F, 28F, 2.0F, 0xFF0A84FF);
        PhoneUi.text(g, f, "Photos", 26F, 33F, 1.0F, 0xFF0A84FF);
        File file = files.get(viewing);
        PhoneUi.textC(g, f, new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(new Date(file.lastModified())), 120F, 28F, 0.9F, 0xFFFFFFFF);
        PhoneUi.textC(g, f, new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(new Date(file.lastModified())), 120F, 40F, 0.72F, 0xFF8E8E93);
        g.fill(0, 316, W, H, 0xCC000000);
        PhoneUi.textC(g, f, (viewing + 1) + " of " + files.size(), 120F, 328F, 0.85F, 0xFF8E8E93);
        if (viewing > 0) PhoneUi.text(g, f, "‹ Newer", 18F, 328F, 0.9F, 0xFF0A84FF);
        if (viewing < files.size() - 1) PhoneUi.textR(g, f, "Older ›", 222F, 328F, 0.9F, 0xFF0A84FF);
        PhoneUi.textC(g, f, "Open folder", 120F, 340F, 0.72F, 0xFF0A84FF);
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        downX = x;
        downY = y;
        moved = false;
        dragAccX = 0F;
        return true;
    }

    @Override
    public boolean mouseDragged(PhonePane pane, double x, double y, int button, double dx, double dy) {
        if (Math.abs(x - downX) > 3D || Math.abs(y - downY) > 3D) moved = true;
        if (viewing >= 0) {
            dragAccX += (float) dx;
        } else {
            scroll -= (float) dy;
            clampScroll();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        float acc = dragAccX;
        dragAccX = 0F;
        if (viewing >= 0) {
            if (moved && Math.abs(acc) > 30F) {
                open(viewing + (acc < 0F ? 1 : -1));
                return true;
            }
            if (moved) return true;
            host.tap();
            if (y < 54D && x < 90D) {
                closeViewer();
            } else if (y >= 322D && y < 336D && x < 80D) {
                open(viewing - 1);
            } else if (y >= 322D && y < 336D && x > 160D) {
                open(viewing + 1);
            } else if (y >= 336D) {
                Util.getPlatform().openFile(new File(host.mc().gameDirectory, "screenshots"));
            }
            return true;
        }
        if (moved) return true;
        if (y >= GRID_TOP - 4F + 0F) {
            int col = (int) Math.floor((x - 1.5D) / (CELL + GAP));
            int row = (int) Math.floor((y - GRID_TOP + scroll) / (CELL + GAP));
            if (col >= 0 && col < 3 && row >= 0) {
                int i = row * 3 + col;
                if (i < files.size()) {
                    host.tap();
                    open(i);
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(PhonePane pane, double x, double y, double delta) {
        if (viewing >= 0) {
            open(viewing - (int) Math.signum(delta));
            return true;
        }
        scroll -= (float) delta * 40F;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (viewing >= 0) {
            if (key == GLFW.GLFW_KEY_LEFT) {
                open(viewing - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                open(viewing + 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_ESCAPE) {
                closeViewer();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean wantsEscape() {
        return viewing >= 0;
    }
}
