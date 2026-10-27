package com.darkwebduo.registry;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.item.IPhoneDuoItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DarkWebDuoMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.darkweb_duo.main"))
                    .icon(() -> new ItemStack(ModItems.DARK_KEYCARD.get()))
                    .displayItems((params, out) -> {
                        out.accept(ModItems.GIANT_MONITOR.get());
                        out.accept(ModItems.GIANT_KEYBOARD.get());
                        out.accept(ModItems.GIANT_MOUSE.get());
                        out.accept(ModItems.DELIVERY_BOX.get());
                        out.accept(ModItems.OMEGA_DELIVERY_BOX.get());
                        out.accept(ModItems.DARK_KEYCARD.get());
                        out.accept(ModItems.OMEGA_KEYCARD.get());
                        out.accept(ModItems.KEYCARD_DOOR.get());
                        out.accept(ModItems.OMEGA_TERMINAL.get());
                        out.accept(ModItems.IPHONE_DUO.get());
                        out.accept(IPhoneDuoItem.make(1));
                    })
                    .build());

    private ModTabs() {}
}
