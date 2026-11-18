package com.sugar.registry;

import com.sugar.SugarMod;
import com.sugar.block.entity.EchoPlatformBlockEntity;
import com.sugar.block.entity.MagmaRiserBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SugarMod.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MagmaRiserBlockEntity>> MAGMA_RISER = BLOCK_ENTITIES.register("magma_riser",
            () -> BlockEntityType.Builder.of(MagmaRiserBlockEntity::new, ModBlocks.MAGMA_RISER.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<EchoPlatformBlockEntity>> ECHO_PLATFORM = BLOCK_ENTITIES.register("echo_platform",
            () -> BlockEntityType.Builder.of(EchoPlatformBlockEntity::new, ModBlocks.ECHO_PLATFORM.get()).build(null));

    private ModBlockEntities() {}
}
