package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bloque especial de templo. Al colocarse dentro de la zona de un templo se registra solo,
 * y el comando de reset lo restaura con {@link #resetBlock}.
 */
public abstract class TempleBlock extends Block {

    protected TempleBlock(Properties properties) {
        super(properties);
    }

    /** Se llama cuando el bloque queda registrado en un templo. */
    public void onRegistered(TempleData data, BlockPos pos) {}

    /** Devuelve el bloque a su estado inicial al hacer /event reset. */
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {}

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !oldState.is(state.getBlock())) {
            TempleService.registerBlock(serverLevel, pos, state);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) {
            TempleService.unregisterBlock(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
