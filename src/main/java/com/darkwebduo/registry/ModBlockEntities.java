package com.darkwebduo.registry;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.block.entity.DeliveryBoxBlockEntity;
import com.darkwebduo.block.entity.GiantMonitorBlockEntity;
import com.darkwebduo.block.entity.IPhoneDuoBlockEntity;
import com.darkwebduo.block.entity.OmegaTerminalBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DarkWebDuoMod.MODID);

    public static final RegistryObject<BlockEntityType<DeliveryBoxBlockEntity>> DELIVERY_BOX = BLOCK_ENTITIES.register("delivery_box",
            () -> BlockEntityType.Builder.of(DeliveryBoxBlockEntity::new,
                    ModBlocks.DELIVERY_BOX.get(), ModBlocks.OMEGA_DELIVERY_BOX.get()).build(null));

    public static final RegistryObject<BlockEntityType<GiantMonitorBlockEntity>> GIANT_MONITOR = BLOCK_ENTITIES.register("giant_monitor",
            () -> BlockEntityType.Builder.of(GiantMonitorBlockEntity::new, ModBlocks.GIANT_MONITOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<OmegaTerminalBlockEntity>> OMEGA_TERMINAL = BLOCK_ENTITIES.register("omega_terminal",
            () -> BlockEntityType.Builder.of(OmegaTerminalBlockEntity::new, ModBlocks.OMEGA_TERMINAL.get()).build(null));

    public static final RegistryObject<BlockEntityType<IPhoneDuoBlockEntity>> IPHONE_DUO = BLOCK_ENTITIES.register("iphone_duo",
            () -> BlockEntityType.Builder.of(IPhoneDuoBlockEntity::new, ModBlocks.IPHONE_DUO.get()).build(null));

    private ModBlockEntities() {}
}
