package com.darkwebduo.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.sounds.SoundEvent;

/** What an app can ask of the pane / phone it runs in. */
public interface PhoneHost {
    Font font();

    Minecraft mc();

    /** opens an app (by id) in the pane that owns this host */
    void launch(String appId);

    /** back to the home screen of this pane */
    void home();

    /** UI tick sound */
    void tap();

    void sound(SoundEvent event, float pitch);

    /** the camera app: hides the phone, takes a screenshot of the world, then reopens the phone */
    void capturePhoto();

    /** true when the phone is unfolded and this pane is one of two */
    boolean isDual();
}
