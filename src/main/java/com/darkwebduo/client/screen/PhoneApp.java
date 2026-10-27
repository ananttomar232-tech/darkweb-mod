package com.darkwebduo.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * One app running in a 240x360 logical pane. All coordinates handed in/out are logical pane coordinates; the screen
 * has already translated and scaled the GuiGraphics pose, so apps simply draw in 0..240 x 0..360.
 */
public abstract class PhoneApp {
    public static final int W = 240;
    public static final int H = 360;

    protected final PhoneHost host;

    protected PhoneApp(PhoneHost host) {
        this.host = host;
    }

    public abstract String id();

    public abstract void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt);

    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        return false;
    }

    public boolean mouseReleased(PhonePane pane, double x, double y, int button) {
        return false;
    }

    public boolean mouseDragged(PhonePane pane, double x, double y, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(PhonePane pane, double x, double y, double delta) {
        return false;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        return false;
    }

    public boolean charTyped(char c, int mods) {
        return false;
    }

    public void tick() {}

    public void onOpen() {}

    public void onClose() {}

    /** true when the app paints a light background so the status bar should be dark */
    public boolean lightStatusBar() {
        return false;
    }

    /** true when the app wants the status bar hidden (camera) */
    public boolean hideStatusBar() {
        return false;
    }

    /** true when the app draws no background so the live world shows through the glass (camera viewfinder) */
    public boolean seeThrough() {
        return false;
    }

    /** true when the app handles the Escape key itself (e.g. to leave a text field); otherwise Esc closes the phone */
    public boolean wantsEscape() {
        return false;
    }
}
