package com.darkwebduo.registry;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.item.IPhoneDuoItem;
import com.darkwebduo.item.KeycardItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DarkWebDuoMod.MODID);

    public static final RegistryObject<Item> GIANT_MONITOR = ITEMS.register("giant_monitor",
            () -> new BlockItem(ModBlocks.GIANT_MONITOR.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> GIANT_KEYBOARD = ITEMS.register("giant_keyboard",
            () -> new BlockItem(ModBlocks.GIANT_KEYBOARD.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> GIANT_MOUSE = ITEMS.register("giant_mouse",
            () -> new BlockItem(ModBlocks.GIANT_MOUSE.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> DELIVERY_BOX = ITEMS.register("delivery_box",
            () -> new BlockItem(ModBlocks.DELIVERY_BOX.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> OMEGA_DELIVERY_BOX = ITEMS.register("omega_delivery_box",
            () -> new BlockItem(ModBlocks.OMEGA_DELIVERY_BOX.get(), new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> KEYCARD_DOOR = ITEMS.register("keycard_door",
            () -> new DoubleHighBlockItem(ModBlocks.KEYCARD_DOOR.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> OMEGA_TERMINAL = ITEMS.register("omega_terminal",
            () -> new BlockItem(ModBlocks.OMEGA_TERMINAL.get(), new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> DARK_KEYCARD = ITEMS.register("dark_keycard",
            () -> new KeycardItem(1, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> OMEGA_KEYCARD = ITEMS.register("omega_keycard",
            () -> new KeycardItem(2, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** Placeable as a display block AND usable in hand (opens the phone UI). Class is owned by the iPhone module. */
    public static final RegistryObject<Item> IPHONE_DUO = ITEMS.register("iphone_duo",
            () -> new IPhoneDuoItem(ModBlocks.IPHONE_DUO.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private ModItems() {}
}
