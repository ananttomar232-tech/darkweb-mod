package com.darkwebduo.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

/** Client entry point used by the iPhone Duo block and item. */
public final class PhoneScreens {
    private PhoneScreens() {}

    /** pos is the phone block being used (null when the phone is held in hand) */
    public static void open(@Nullable BlockPos pos, int colorway) {
        Minecraft mc = Minecraft.getInstance();
        PhoneData.load();
        mc.setScreen(new IPhoneDuoScreen(pos, colorway));
    }
}
