package com.darkwebduo;

import com.darkwebduo.entity.CipherWardenEntity;
import com.darkwebduo.entity.OmegaRootEntity;
import com.darkwebduo.net.ModNetwork;
import com.darkwebduo.registry.ModBlockEntities;
import com.darkwebduo.registry.ModBlocks;
import com.darkwebduo.registry.ModEntities;
import com.darkwebduo.registry.ModItems;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.registry.ModTabs;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(DarkWebDuoMod.MODID)
public class DarkWebDuoMod {
    public static final String MODID = "darkweb_duo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DarkWebDuoMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModTabs.TABS.register(bus);

        bus.addListener(this::commonSetup);
        bus.addListener(this::registerAttributes);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    private void registerAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntities.CIPHER_WARDEN.get(), CipherWardenEntity.createAttributes().build());
        event.put(ModEntities.OMEGA_ROOT.get(), OmegaRootEntity.createAttributes().build());
    }
}
