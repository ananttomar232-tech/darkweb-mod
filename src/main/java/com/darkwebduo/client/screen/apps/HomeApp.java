package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.render.PhoneInfo;
import com.darkwebduo.client.render.PhoneUv;
import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/** The home screen of one pane: time/weather widget, app grid and dock (layout shared with the 3D screen texture). */
public class HomeApp extends PhoneApp {
    private static final String[] GRID = {"clock", "calc", "notes", "maps", "weather"};
    private static final String[] DOCK = {"photos", "camera", "music", "settings"};
    private int pressed = -1;

    public HomeApp(PhoneHost host) {
        super(host);
    }

    @Override
    public String id() {
        return "home";
    }

    private static float gridX(int slot) {
        return PhoneUv.GRID_X0 + (slot % 4) * (PhoneUv.ICON_SIZE + PhoneUv.GRID_GAP);
    }

    private static float gridY(int slot) {
        return PhoneUv.GRID_Y0 + (slot / 4) * PhoneUv.GRID_ROW;
    }

    private static float dockX(int slot) {
        return 24F + slot * ((PhoneUv.PANE_W - 28F - 20F - PhoneUv.ICON_SIZE) / 3F);
    }

    private static float dockY() {
        return PhoneUv.DOCK_Y + 10F;
    }

    /** centre of an app icon on the home screen (logical pane coordinates), or the pane centre when unknown */
    public static float[] center(String id) {
        float sz = PhoneUv.ICON_SIZE;
        for (int i = 0; i < GRID.length; i++) {
            if (GRID[i].equals(id)) return new float[] {gridX(i) + sz * 0.5F, gridY(i) + sz * 0.5F};
        }
        for (int i = 0; i < DOCK.length; i++) {
            if (DOCK[i].equals(id)) return new float[] {dockX(i) + sz * 0.5F, dockY() + sz * 0.5F};
        }
        return new float[] {120F, 180F};
    }

    /** slot index under the point: 0..4 grid, 10..13 dock, -1 none */
    private int hit(double x, double y) {
        float sz = PhoneUv.ICON_SIZE;
        for (int i = 0; i < GRID.length; i++) {
            if (PhoneUi.inside(x, y, gridX(i) - 4F, gridY(i) - 3F, gridX(i) + sz + 4F, gridY(i) + sz + 17F)) return i;
        }
        for (int i = 0; i < DOCK.length; i++) {
            if (PhoneUi.inside(x, y, dockX(i), dockY(), dockX(i) + sz, dockY() + sz)) return 10 + i;
        }
        return -1;
    }

    private void icon(GuiGraphics g, String id, float x, float y, boolean down) {
        float sz = PhoneUv.ICON_SIZE;
        PhoneUi.rr(g, x, y + 3F, x + sz, y + sz + 3F, sz * 0.225F, 0x30000000);
        PhoneUi.image(g, PhoneUi.gui("icon_" + id), x, y, sz, sz, 128, 128);
        if (down) PhoneUi.rr(g, x, y, x + sz, y + sz, sz * 0.225F, 0x55000000);
    }

    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Font f = host.font();
        // widget card
        PhoneUi.rr(g, 14, 34, 226, 112, 20, 0x4CFFFFFF);
        PhoneUi.rrStroke(g, 14, 34, 226, 112, 20, 0.7F, 0x4AFFFFFF);
        PhoneUi.text(g, f, PhoneInfo.clock(), 28, 42, 3.6F, 0xF8FFFFFF);
        PhoneUi.text(g, f, PhoneInfo.date(), 28, 84, 1.0F, 0xE8FFFFFF);
        PhoneUi.text(g, f, PhoneInfo.info(), 28, 97, 0.85F, 0xB4FFFFFF);
        PhoneUi.image(g, PhoneUi.gui("icon_weather"), 176, 46, 36, 36, 128, 128);

        float sz = PhoneUv.ICON_SIZE;
        for (int i = 0; i < GRID.length; i++) {
            float x = gridX(i), y = gridY(i);
            icon(g, GRID[i], x, y, pressed == i);
            String name = I18n.get("phone.darkweb_duo.app." + GRID[i]);
            PhoneUi.textShadow(g, f, name, x + sz * 0.5F - PhoneUi.width(f, name, 0.85F) * 0.5F, y + sz + 5F, 0.85F, 0xFFFFFFFF);
        }
        // page dots
        for (int i = 0; i < 3; i++) {
            PhoneUi.disc(g, 120F + (i - 1) * 9F, PhoneUv.DOCK_Y - 9F, 2F, i == 0 ? 0xEBFFFFFF : 0x6EFFFFFF);
        }
        // dock
        PhoneUi.rr(g, 10, PhoneUv.DOCK_Y, 230, PhoneUv.DOCK_Y + 66, 22, 0x46FFFFFF);
        PhoneUi.rrStroke(g, 10, PhoneUv.DOCK_Y, 230, PhoneUv.DOCK_Y + 66, 22, 0.7F, 0x3CFFFFFF);
        for (int i = 0; i < DOCK.length; i++) {
            icon(g, DOCK[i], dockX(i), dockY(), pressed == 10 + i);
        }
    }

    @Override
    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        int h = hit(x, y);
        pressed = h;
        return h >= 0;
    }

    @Override
    public boolean mouseReleased(PhonePane pane, double x, double y, int button) {
        int was = pressed;
        pressed = -1;
        if (was < 0) return false;
        if (hit(x, y) == was) {
            host.tap();
            host.launch(was >= 10 ? DOCK[was - 10] : GRID[was]);
        }
        return true;
    }
}
