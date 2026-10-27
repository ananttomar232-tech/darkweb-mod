package com.darkwebduo.client.screen;

import com.darkwebduo.client.screen.apps.CalcApp;
import com.darkwebduo.client.screen.apps.CameraApp;
import com.darkwebduo.client.screen.apps.ClockApp;
import com.darkwebduo.client.screen.apps.HomeApp;
import com.darkwebduo.client.screen.apps.MapsApp;
import com.darkwebduo.client.screen.apps.MusicApp;
import com.darkwebduo.client.screen.apps.NotesApp;
import com.darkwebduo.client.screen.apps.PhotosApp;
import com.darkwebduo.client.screen.apps.SettingsApp;
import com.darkwebduo.client.screen.apps.WeatherApp;

/** Creates the app for an id ("home", "clock", ...). */
public final class PhoneApps {
    private PhoneApps() {}

    public static PhoneApp create(String id, PhoneHost host) {
        if ("clock".equals(id)) return new ClockApp(host);
        if ("calc".equals(id)) return new CalcApp(host);
        if ("notes".equals(id)) return new NotesApp(host);
        if ("camera".equals(id)) return new CameraApp(host);
        if ("maps".equals(id)) return new MapsApp(host);
        if ("music".equals(id)) return new MusicApp(host);
        if ("weather".equals(id)) return new WeatherApp(host);
        if ("settings".equals(id)) return new SettingsApp(host);
        if ("photos".equals(id)) return new PhotosApp(host);
        return new HomeApp(host);
    }
}
