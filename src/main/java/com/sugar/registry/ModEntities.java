package com.sugar.registry;

import com.sugar.SugarMod;
import com.sugar.entity.AshBeast;
import com.sugar.entity.AshBomb;
import com.sugar.entity.AshHatchling;
import com.sugar.entity.DrummerHand;
import com.sugar.entity.EchoDrummer;
import com.sugar.entity.FallingRock;
import com.sugar.entity.ForgeForeman;
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

    public static final RegistryObject<EntityType<AshBeast>> ASH_BEAST = ENTITIES.register("ash_beast",
            () -> EntityType.Builder.of(AshBeast::new, MobCategory.MONSTER)
                    .sized(3.4F, 3.0F).fireImmune().clientTrackingRange(12).updateInterval(1)
                    .build(SugarMod.id("ash_beast").toString()));

    public static final RegistryObject<EntityType<AshHatchling>> ASH_HATCHLING = ENTITIES.register("ash_hatchling",
            () -> EntityType.Builder.of(AshHatchling::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.8F).fireImmune().clientTrackingRange(8)
                    .build(SugarMod.id("ash_hatchling").toString()));

    public static final RegistryObject<EntityType<AshBomb>> ASH_BOMB = ENTITIES.register("ash_bomb",
            () -> EntityType.Builder.<AshBomb>of(AshBomb::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(2)
                    .build(SugarMod.id("ash_bomb").toString()));

    public static final RegistryObject<EntityType<EchoDrummer>> ECHO_DRUMMER = ENTITIES.register("echo_drummer",
            () -> EntityType.Builder.of(EchoDrummer::new, MobCategory.MONSTER)
                    .sized(3.0F, 4.4F).fireImmune().clientTrackingRange(12).updateInterval(1)
                    .build(SugarMod.id("echo_drummer").toString()));

    public static final RegistryObject<EntityType<DrummerHand>> DRUMMER_HAND = ENTITIES.register("drummer_hand",
            () -> EntityType.Builder.of(DrummerHand::new, MobCategory.MISC)
                    .sized(2.6F, 1.3F).fireImmune().clientTrackingRange(12).updateInterval(1)
                    .build(SugarMod.id("drummer_hand").toString()));

    public static final RegistryObject<EntityType<ForgeForeman>> FORGE_FOREMAN = ENTITIES.register("forge_foreman",
            () -> EntityType.Builder.of(ForgeForeman::new, MobCategory.MONSTER)
                    .sized(1.9F, 2.9F).fireImmune().clientTrackingRange(10)
                    .build(SugarMod.id("forge_foreman").toString()));

    private ModEntities() {}
}
