package com.sugar.registry;

import com.sugar.SugarMod;
import com.sugar.entity.FallingRock;
import com.sugar.entity.MagmaBolt;
import com.sugar.entity.MagmaDecoy;
import com.sugar.entity.MagmaSerpent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SugarMod.MODID);

    public static final RegistryObject<EntityType<MagmaSerpent>> MAGMA_SERPENT = ENTITIES.register("magma_serpent",
            () -> EntityType.Builder.of(MagmaSerpent::new, MobCategory.MONSTER)
                    .sized(2.5F, 5.5F).fireImmune().clientTrackingRange(12).updateInterval(1)
                    .build(SugarMod.id("magma_serpent").toString()));

    public static final RegistryObject<EntityType<MagmaDecoy>> MAGMA_DECOY = ENTITIES.register("magma_decoy",
            () -> EntityType.Builder.of(MagmaDecoy::new, MobCategory.MISC)
                    .sized(2.5F, 5.5F).fireImmune().clientTrackingRange(10).updateInterval(1)
                    .build(SugarMod.id("magma_decoy").toString()));

    public static final RegistryObject<EntityType<MagmaBolt>> MAGMA_BOLT = ENTITIES.register("magma_bolt",
            () -> EntityType.Builder.<MagmaBolt>of(MagmaBolt::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(2)
                    .build(SugarMod.id("magma_bolt").toString()));

    public static final RegistryObject<EntityType<FallingRock>> FALLING_ROCK = ENTITIES.register("falling_rock",
            () -> EntityType.Builder.<FallingRock>of(FallingRock::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F).clientTrackingRange(8).updateInterval(1)
                    .build(SugarMod.id("falling_rock").toString()));

    private ModEntities() {}
}
