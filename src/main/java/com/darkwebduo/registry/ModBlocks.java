package com.darkwebduo.registry;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.block.DeliveryBoxBlock;
import com.darkwebduo.block.GiantKeyboardBlock;
import com.darkwebduo.block.GiantMonitorBlock;
import com.darkwebduo.block.GiantMouseBlock;
import com.darkwebduo.block.IPhoneDuoBlock;
import com.darkwebduo.block.KeycardDoorBlock;
import com.darkwebduo.block.OmegaTerminalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DarkWebDuoMod.MODID);

    public static final RegistryObject<Block> GIANT_MONITOR = BLOCKS.register("giant_monitor",
            () -> new GiantMonitorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(3.0F, 8.0F).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> 6)));

    public static final RegistryObject<Block> GIANT_KEYBOARD = BLOCKS.register("giant_keyboard",
            () -> new GiantKeyboardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).strength(1.5F, 4.0F).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> 4)));

    public static final RegistryObject<Block> GIANT_MOUSE = BLOCKS.register("giant_mouse",
            () -> new GiantMouseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).strength(1.5F, 4.0F).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> 3)));

    /** Tier 1 box: contains the Dark Web Keycard. */
    public static final RegistryObject<Block> DELIVERY_BOX = BLOCKS.register("delivery_box",
            () -> new DeliveryBoxBlock(1, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN).strength(1.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()
                    .lightLevel(s -> s.getValue(DeliveryBoxBlock.OPEN) ? 10 : 2)));

    /** Tier 2 box: contains the Omega Keycard. */
    public static final RegistryObject<Block> OMEGA_DELIVERY_BOX = BLOCKS.register("omega_delivery_box",
            () -> new DeliveryBoxBlock(2, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(1.0F, 3.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion()
                    .lightLevel(s -> s.getValue(DeliveryBoxBlock.OPEN) ? 12 : 4)));

    public static final RegistryObject<Block> KEYCARD_DOOR = BLOCKS.register("keycard_door",
            () -> new KeycardDoorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(8.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion()
                    .requiresCorrectToolForDrops().lightLevel(s -> 5)));

    public static final RegistryObject<Block> OMEGA_TERMINAL = BLOCKS.register("omega_terminal",
            () -> new OmegaTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD).strength(30.0F, 3600000.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion()
                    .lightLevel(s -> 12)));

    public static final RegistryObject<Block> IPHONE_DUO = BLOCKS.register("iphone_duo",
            () -> new IPhoneDuoBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> 9)));

    private ModBlocks() {}
}
