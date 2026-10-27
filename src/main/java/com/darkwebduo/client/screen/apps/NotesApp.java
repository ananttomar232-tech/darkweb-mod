package com.darkwebduo.client.screen.apps;

import com.darkwebduo.client.screen.PhoneApp;
import com.darkwebduo.client.screen.PhoneData;
import com.darkwebduo.client.screen.PhoneHost;
import com.darkwebduo.client.screen.PhonePane;
import com.darkwebduo.client.screen.PhoneUi;
import com.darkwebduo.registry.ModSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** A paper notepad: word-wrapped editor with cursor, clipboard paste and automatic saving. */
public class NotesApp extends PhoneApp {
    private static final float LEFT = 18F;
    private static final float MAXW = 202F;
    private static final float TOP = 74F;
    private static final float BOTTOM = 326F;
    private static final float LH = 11F;
    private static final int MAXLEN = 6000;
    private static final int PAPER = 0xFFFFFBEC;
    private static final int INK = 0xFF1F1B12;
    private static final int GOLD = 0xFFD99A00;

    private final List<int[]> lines = new ArrayList<int[]>();
    private int cursor;
    private float scroll;
    private boolean wrapDirty = true;
    private boolean dirty;
    private int ticks;
    private int saveTimer;

    public NotesApp(PhoneHost host) {
        super(host);
        cursor = PhoneData.notes.length();
    }

    @Override
    public String id() {
        return "notes";
    }

    @Override
    public boolean lightStatusBar() {
        return true;
    }

    // ------------------------------------------------------------------ text model
    private void rewrap() {
        wrapDirty = false;
        lines.clear();
        Font f = host.font();
        String t = PhoneData.notes;
        int n = t.length();
        int start = 0;
        while (true) {
            int nl = t.indexOf('\n', start);
            int endPara = nl < 0 ? n : nl;
            int s = start;
            if (s >= endPara) {
                lines.add(new int[] {s, s});
            }
            while (s < endPara) {
                int e = s;
                int lastSpace = -1;
                float w = 0F;
                while (e < endPara) {
                    char c = t.charAt(e);
                    float cw = f.width(String.valueOf(c));
                    if (w + cw > MAXW && e > s) break;
                    w += cw;
                    if (c == ' ') lastSpace = e;
                    e++;
                }
                if (e < endPara && lastSpace > s) e = lastSpace + 1;
                lines.add(new int[] {s, e});
                s = e;
            }
            if (nl < 0) break;
            start = nl + 1;
            if (start >= n) {
                lines.add(new int[] {n, n});
                break;
            }
        }
    }

    private int lineOf(int idx) {
        int found = 0;
        for (int i = 0; i < lines.size(); i++) {
            int[] l = lines.get(i);
            if (l[0] <= idx && idx <= l[1]) found = i;
        }
        return found;
    }

    private String sub(int a, int b) {
        String t = PhoneData.notes;
        return t.substring(Math.max(0, Math.min(a, t.length())), Math.max(0, Math.min(b, t.length())));
    }

    private float cursorX(int li) {
        int[] l = lines.get(li);
        int c = Math.max(l[0], Math.min(cursor, l[1]));
        return LEFT + host.font().width(sub(l[0], c));
    }

    private int indexAt(int li, float x) {
        int[] l = lines.get(li);
        Font f = host.font();
        int best = l[0];
        float bestD = Float.MAX_VALUE;
        for (int k = l[0]; k <= l[1]; k++) {
            float cx = LEFT + f.width(sub(l[0], k));
            float d = Math.abs(cx - x);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        // a soft-wrapped line ends where the next begins: never park the cursor after the trailing space of a wrap
        return best;
    }

    private void changed() {
        wrapDirty = true;
        dirty = true;
        saveTimer = 100;
    }

    private void insert(String s) {
        String t = PhoneData.notes;
        if (t.length() + s.length() > MAXLEN) return;
        PhoneData.notes = t.substring(0, cursor) + s + t.substring(cursor);
        cursor += s.length();
        changed();
        if (PhoneData.tapSounds) host.sound(ModSounds.PHONE_TYPE.get(), 0.9F + (cursor % 5) * 0.04F);
    }

    private void backspace() {
        String t = PhoneData.notes;
        if (cursor <= 0) return;
        PhoneData.notes = t.substring(0, cursor - 1) + t.substring(cursor);
        cursor--;
        changed();
        if (PhoneData.tapSounds) host.sound(ModSounds.PHONE_TYPE.get(), 0.8F);
    }

    private void delete() {
        String t = PhoneData.notes;
        if (cursor >= t.length()) return;
        PhoneData.notes = t.substring(0, cursor) + t.substring(cursor + 1);
        changed();
    }

    private void keepCursorVisible() {
        if (wrapDirty) rewrap();
        float y = lineOf(cursor) * LH;
        float view = BOTTOM - TOP;
        if (y < scroll) scroll = y;
        if (y + LH > scroll + view) scroll = y + LH - view;
        clampScroll();
    }

    private void clampScroll() {
        float max = Math.max(0F, lines.size() * LH - (BOTTOM - TOP));
        scroll = Math.max(0F, Math.min(max, scroll));
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, PhonePane pane, int mx, int my, float pt) {
        Font f = host.font();
        if (wrapDirty) rewrap();
        g.fill(0, 0, W, H, PAPER);
        // header
        g.fill(0, 0, W, 64, 0xFFFFF3C4);
        g.fill(0, 64, W, 65, 0x22000000);
        PhoneUi.text(g, f, "‹", 14, 33, 2.0F, GOLD);
        PhoneUi.text(g, f, "Notes", 26, 38, 1.15F, GOLD);
        PhoneUi.textC(g, f, "Note", 120, 38, 1.2F, INK);
        int words = 0;
        String t = PhoneData.notes.trim();
        if (!t.isEmpty()) words = t.split("\\s+").length;
        PhoneUi.textR(g, f, words + (words == 1 ? " word" : " words"), 226, 40, 0.8F, 0xFF8A7A3A);

        pane.scissor(g, 0, TOP - 4F, W, BOTTOM);
        for (int i = 0; i < lines.size(); i++) {
            float y = TOP + i * LH - scroll;
            if (y < TOP - LH || y > BOTTOM) continue;
            int[] l = lines.get(i);
            String s = sub(l[0], l[1]);
            if (s.endsWith("\n")) s = s.substring(0, s.length() - 1);
            PhoneUi.text(g, f, s, LEFT, y, 1.0F, INK);
        }
        if (((ticks / 10) & 1) == 0) {
            int li = lineOf(cursor);
            float cx = cursorX(li);
            float cy = TOP + li * LH - scroll;
            g.fill(Math.round(cx), Math.round(cy - 1F), Math.round(cx) + 1, Math.round(cy + 9F), GOLD);
        }
        pane.unscissor(g);

        // scrollbar
        float total = lines.size() * LH;
        float view = BOTTOM - TOP;
        if (total > view) {
            float th = Math.max(18F, view * view / total);
            float ty = TOP + (view - th) * (scroll / Math.max(1F, total - view));
            PhoneUi.rr(g, 232F, ty, 234F, ty + th, 1F, 0x55000000);
        }
        // footer
        g.fill(0, 328, W, 342, 0xFFFFF3C4);
        PhoneUi.textC(g, f, "Click to place the cursor · Ctrl+V pastes", 120, 331, 0.75F, 0xFF8A7A3A);
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(PhonePane pane, double x, double y, int button) {
        if (button != 0) return false;
        if (y >= 8D && y < 60D && x < 70D && x > 6D) {
            host.tap();
            host.home();
            return true;
        }
        if (y >= TOP - 4F && y < BOTTOM) {
            if (wrapDirty) rewrap();
            int li = (int) Math.floor((y - TOP + scroll) / LH);
            li = Math.max(0, Math.min(lines.size() - 1, li));
            cursor = indexAt(li, (float) x);
            ticks = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(PhonePane pane, double x, double y, int button, double dx, double dy) {
        if (y >= TOP - 4F && y < BOTTOM && Math.abs(dy) > 0.01D) {
            scroll -= (float) dy;
            clampScroll();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(PhonePane pane, double x, double y, double delta) {
        scroll -= (float) delta * 24F;
        clampScroll();
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (c >= 32 && c != 127) {
            insert(String.valueOf(c));
            keepCursorVisible();
            ticks = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (wrapDirty) rewrap();
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = host.mc().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < clip.length(); i++) {
                    char c = clip.charAt(i);
                    if (c == '\r') continue;
                    if (c == '\n' || c >= 32) sb.append(c);
                }
                insert(sb.toString());
            }
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            insert("\n");
        } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
            backspace();
        } else if (key == GLFW.GLFW_KEY_DELETE) {
            delete();
        } else if (key == GLFW.GLFW_KEY_LEFT) {
            cursor = Math.max(0, cursor - 1);
        } else if (key == GLFW.GLFW_KEY_RIGHT) {
            cursor = Math.min(PhoneData.notes.length(), cursor + 1);
        } else if (key == GLFW.GLFW_KEY_HOME) {
            cursor = lines.get(lineOf(cursor))[0];
        } else if (key == GLFW.GLFW_KEY_END) {
            cursor = lines.get(lineOf(cursor))[1];
        } else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            int li = lineOf(cursor);
            float x = cursorX(li);
            int to = li + (key == GLFW.GLFW_KEY_UP ? -1 : 1);
            if (to >= 0 && to < lines.size()) cursor = indexAt(to, x);
        } else {
            return false;
        }
        ticks = 0;
        keepCursorVisible();
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        if (dirty && --saveTimer <= 0) {
            PhoneData.save();
            dirty = false;
        }
    }

    @Override
    public void onClose() {
        if (dirty) {
            PhoneData.save();
            dirty = false;
        }
    }
}
