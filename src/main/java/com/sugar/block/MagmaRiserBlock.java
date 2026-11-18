package com.sugar.block;

import com.sugar.block.entity.MagmaRiserBlockEntity;
import com.sugar.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Fuente de Magma: mientras el evento está activo, el magma sube por encima de este bloque
 * capa a capa (9x9 por defecto), se mantiene un momento y vuelve a bajar.
 */
public class MagmaRiserBlock extends TempleBlock implements EntityBlock {

    public MagmaRiserBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MagmaRiserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.MAGMA_RISER.get()) return null;
        return (lvl, pos, st, be) -> ((MagmaRiserBlockEntity) be).serverTick((ServerLevel) lvl, pos);
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof MagmaRiserBlockEntity riser) riser.drainAll(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MagmaRiserBlockEntity riser) {
            riser.drainAll(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
