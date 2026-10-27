package com.darkwebduo.client.screen;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** Client-side phone settings and notes, kept in <gamedir>/darkweb_duo_phone.properties. */
public final class PhoneData {
    public static int wallpaper = 0;
    public static float brightness = 1.0F;
    public static float volume = 0.8F;
    public static boolean tapSounds = true;
    public static boolean twentyFour = false;
    public static String notes = "Welcome to your iPhone Duo.\n\nTwo full apps, side by side: unfold the phone and open a second app in the right-hand pane.";
    private static boolean loaded;

    private PhoneData() {}

    private static File file() {
        return new File(Minecraft.getInstance().gameDirectory, "darkweb_duo_phone.properties");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (!f.isFile()) return;
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            Properties p = new Properties();
            p.load(in);
            wallpaper = Math.max(0, Math.min(2, Integer.parseInt(p.getProperty("wallpaper", "0"))));
            brightness = Math.max(0.35F, Math.min(1.0F, Float.parseFloat(p.getProperty("brightness", "1.0"))));
            volume = Math.max(0F, Math.min(1.0F, Float.parseFloat(p.getProperty("volume", "0.8"))));
            tapSounds = Boolean.parseBoolean(p.getProperty("tapSounds", "true"));
            twentyFour = Boolean.parseBoolean(p.getProperty("twentyFour", "false"));
            notes = p.getProperty("notes", notes);
        } catch (Exception e) {
            // keep defaults
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

    public static void save() {
        OutputStream out = null;
        try {
            Properties p = new Properties();
            p.setProperty("wallpaper", Integer.toString(wallpaper));
            p.setProperty("brightness", Float.toString(brightness));
            p.setProperty("volume", Float.toString(volume));
            p.setProperty("tapSounds", Boolean.toString(tapSounds));
            p.setProperty("twentyFour", Boolean.toString(twentyFour));
            p.setProperty("notes", notes);
            out = new FileOutputStream(file());
            p.store(out, "iPhone Duo (Dark Web & iPhone Duo mod)");
        } catch (Exception e) {
            // best effort
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
    }
}
