package com.darkwebduo.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Real-world clock / date and in-game weather lines shown on the phone screens (cached, refreshed once a second). */
public final class PhoneInfo {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);
    private static long lastRefresh;
    private static String clock = "9:41";
    private static String date = "";
    private static String info = "";

    private PhoneInfo() {}

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < 500L && !date.isEmpty()) return;
        lastRefresh = now;
        LocalTime t = LocalTime.now();
        int h = t.getHour() % 12;
        if (h == 0) h = 12;
        int m = t.getMinute();
        clock = h + ":" + (m < 10 ? "0" : "") + m;
        date = LocalDate.now().format(DATE);
        info = weather();
    }

    public static String clock() {
        refresh();
        return clock;
    }

    public static String date() {
        refresh();
        return date;
    }

    public static String info() {
        refresh();
        return info;
    }

    /** "24-hour" clock with seconds for the Clock app */
    public static String clockFull() {
        LocalTime t = LocalTime.now();
        return two(t.getHour()) + ":" + two(t.getMinute()) + ":" + two(t.getSecond());
    }

    public static String two(int v) {
        return v < 10 ? "0" + v : Integer.toString(v);
    }

    /** in-game conditions at the player's position */
    public static String weather() {
        Level level = Minecraft.getInstance().level;
        if (level == null) return "";
        String w;
        if (level.isThundering()) w = "Thunderstorm";
        else if (level.isRaining()) w = "Rain";
        else w = "Clear";
        long dayTime = level.getDayTime() % 24000L;
        String phase = dayTime < 12000L ? "Day" : "Night";
        return w + "  -  " + phase + " " + (level.getDayTime() / 24000L + 1L);
    }
}
