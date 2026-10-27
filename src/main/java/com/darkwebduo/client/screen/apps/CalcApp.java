package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** A fully working four-function calculator with percent and sign keys; also accepts the keyboard. */
public class CalcApp extends PhoneApp {
    private static final String[][] KEYS = {
            {"AC", "+/-", "%", "/"},
            {"7", "8", "9", "x"},
            {"4", "5", "6", "-"},
            {"1", "2", "3", "+"},
            {"0", "0", ".", "="}};
    private static final float BX = 8F, BY = 96F, BW = 50F, BH = 44F, GAP = 8F;

    private String display = "0";
    private double acc;
    private char op;
    private boolean fresh = true;
    private char activeOp;
    private boolean error;

    public CalcApp(PhoneHost host) {
        super(host);
    }

    @Override
    public String id() {
        return "calc";
    }

    // ------------------------------------------------------------------ logic
    private double value() {
        try {
            return Double.parseDouble(display);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Error";
        if (Math.abs(v) >= 1.0E15 || (Math.abs(v) < 1.0E-9 && v != 0.0)) {
            return String.format(java.util.Locale.ENGLISH, "%.6e", v);
        }
        String s = String.format(java.util.Locale.ENGLISH, "%.10f", v);
        if (s.indexOf('.') >= 0) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') end--;
            if (end > 0 && s.charAt(end - 1) == '.') end--;
            s = s.substring(0, end);
        }
        if (s.equals("-0")) s = "0";
        return s;
    }

    private void setDisplay(double v) {
        display = format(v);
        error = display.equals("Error");
    }

    private void apply() {
        if (op == 0) return;
        double b = value();
        double r = acc;
        switch (op) {
            case '+':
                r = acc + b;
                break;
            case '-':
                r = acc - b;
                break;
            case 'x':
                r = acc * b;
                break;
            case '/':
                r = b == 0.0 ? Double.NaN : acc / b;
                break;
            default:
                break;
        }
        setDisplay(r);
        acc = r;
    }

    private void press(String k) {
        if (error && !k.equals("AC")) return;
        char c = k.charAt(0);
        if (k.length() == 1 && c >= '0' && c <= '9') {
            if (fresh) {
                display = String.valueOf(c);
                fresh = false;
            } else if (display.length() < 12) {
                display = display.equals("0") ? String.valueOf(c) : display + c;
            }
            activeOp = 0;
        } else if (k.equals(".")) {
            if (fresh) {
                display = "0.";
                fresh = false;
            } else if (display.indexOf('.') < 0 && display.length() < 12) {
                display += ".";
            }
            activeOp = 0;
        } else if (k.equals("AC")) {
            display = "0";
            acc = 0.0;
            op = 0;
            fresh = true;
            error = false;
            activeOp = 0;
        } else if (k.equals("+/-")) {
            if (!display.equals("0")) display = display.startsWith("-") ? display.substring(1) : "-" + display;
        } else if (k.equals("%")) {
            setDisplay(value() / 100.0);
            fresh = true;
        } else if (k.equals("=")) {
            if (op != 0) {
                apply();
                op = 0;
                fresh = true;
                activeOp = 0;
            }
        } else if (k.equals("+") || k.equals("-") || k.equals("x") || k.equals("/")) {
            if (op != 0 && !fresh) apply();
            else acc = value();
            op = c;
            activeOp = c;
            fresh = true;
        }
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Font f = host.font();
        g.fill(0, 0, W, H, 0xFF000000);
        float sc = display.length() <= 6 ? 3.6F : display.length() <= 8 ? 2.8F : display.length() <= 10 ? 2.3F : 1.9F;
        PhoneUi.textR(g, f, display, 226, 80F - 8F * sc, sc, 0xFFFFFFFF);
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 4; c++) {
                if (r == 4 && c == 1) continue;
                String k = KEYS[r][c];
                float x0 = BX + c * (BW + GAP);
                float y0 = BY + r * (BH + GAP);
                float x1 = x0 + BW;
                if (r == 4 && c == 0) x1 = x0 + BW * 2F + GAP;
                boolean op = c == 3;
                boolean fn = r == 0 && c < 3;
                boolean active = op && activeOp != 0 && k.charAt(0) == activeOp && !k.equals("=");
                int bg = fn ? 0xFFA5A5A5 : (op ? (active ? 0xFFFFFFFF : 0xFFFF9F0A) : 0xFF333333);
                int fg = fn ? 0xFF000000 : (op ? (active ? 0xFFFF9F0A : 0xFFFFFFFF) : 0xFFFFFFFF);
                boolean hover = PhoneUi.inside(mx, my, x0, y0, x1, y0 + BH);
                if (hover) bg = PhoneUi.mix(bg, 0xFFFFFFFF, 0.16F);
                PhoneUi.rr(g, x0, y0, x1, y0 + BH, BH * 0.5F, bg);
                String label = k.equals("/") ? "÷" : (k.equals("x") ? "×" : (k.equals("-") ? "−" : k));
                float tx = r == 4 && c == 0 ? x0 + 20F : (x0 + x1) * 0.5F;
                if (r == 4 && c == 0) PhoneUi.text(g, f, label, tx - 3F, y0 + BH * 0.5F - 7F, 1.7F, fg);
                else PhoneUi.textC(g, f, label, tx, y0 + BH * 0.5F - 7F, 1.7F, fg);
            }
        }
    }

    @Override
    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 4; c++) {
                if (r == 4 && c == 1) continue;
                float x0 = BX + c * (BW + GAP);
                float y0 = BY + r * (BH + GAP);
                float x1 = x0 + BW;
                if (r == 4 && c == 0) x1 = x0 + BW * 2F + GAP;
                if (PhoneUi.inside(x, y, x0, y0, x1, y0 + BH)) {
                    host.tap();
                    press(KEYS[r][c]);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if ((c >= '0' && c <= '9') || c == '.' || c == '+' || c == '-' || c == '%' || c == '/') {
            press(String.valueOf(c));
            return true;
        }
        if (c == '*' || c == 'x' || c == 'X') {
            press("x");
            return true;
        }
        if (c == '=') {
            press("=");
            return true;
        }
        if (c == 'c' || c == 'C') {
            press("AC");
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            press("=");
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!fresh && display.length() > 1) display = display.substring(0, display.length() - 1);
            else if (!fresh) display = "0";
            if (display.equals("-")) display = "0";
            return true;
        }
        return false;
    }
}
