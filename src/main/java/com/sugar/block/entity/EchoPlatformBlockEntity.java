package com.sugar.block.entity;

import com.sugar.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Solo existe para que el cliente pueda dibujar la plataforma cuando se lleva la lente. */
public class EchoPlatformBlockEntity extends BlockEntity {
    public EchoPlatformBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ECHO_PLATFORM.get(), pos, state);
    }
}
