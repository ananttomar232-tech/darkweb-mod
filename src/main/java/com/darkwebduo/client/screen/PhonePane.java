package com.darkwebduo.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/** Screen-space placement of one logical 240x360 pane (so apps can scissor in logical coordinates). */
public final class PhonePane {
    public float ox;
    public float oy;
    public float s = 1F;
    /** extra horizontal squash while the right pane folds in/out */
    public float sx = 1F;

    public void scissor(GuiGraphics g, float x0, float y0, float x1, float y1) {
        g.enableScissor(Math.round(ox + x0 * s * sx), Math.round(oy + y0 * s), Math.round(ox + x1 * s * sx), Math.round(oy + y1 * s));
    }

    public void unscissor(GuiGraphics g) {
        g.disableScissor();
    }
}
