package com.darkwebduo.registry;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.entity.CipherWardenEntity;
import com.darkwebduo.entity.CutsceneCameraEntity;
import com.darkwebduo.entity.OmegaRootEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DarkWebDuoMod.MODID);

    /** Boss 1. Hitbox 2.6 x 6.4. */
    public static final RegistryObject<EntityType<CipherWardenEntity>> CIPHER_WARDEN = ENTITIES.register("cipher_warden",
            () -> EntityType.Builder.of(CipherWardenEntity::new, MobCategory.MONSTER)
                    .sized(2.6F, 6.4F).fireImmune().clientTrackingRange(20).updateInterval(2)
                    .build("cipher_warden"));

    /** Boss 2. Hitbox 3.4 x 8.6. */
    public static final RegistryObject<EntityType<OmegaRootEntity>> OMEGA_ROOT = ENTITIES.register("omega_root",
            () -> EntityType.Builder.of(OmegaRootEntity::new, MobCategory.MONSTER)
                    .sized(3.4F, 8.6F).fireImmune().clientTrackingRange(24).updateInterval(2)
                    .build("omega_root"));

    /** Client-only camera used by cutscenes; never saved, never sent. */
    public static final RegistryObject<EntityType<CutsceneCameraEntity>> CUTSCENE_CAMERA = ENTITIES.register("cutscene_camera",
            () -> EntityType.Builder.<CutsceneCameraEntity>of(CutsceneCameraEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F).noSave().noSummon().clientTrackingRange(0)
                    .build("cutscene_camera"));

    private ModEntities() {}
}
