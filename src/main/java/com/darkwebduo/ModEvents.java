package com.darkwebduo;

import com.darkwebduo.command.BossFightCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus (game) events shared by client and server. */
@Mod.EventBusSubscriber(modid = DarkWebDuoMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BossFightCommand.register(event.getDispatcher());
    }
}
