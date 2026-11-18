package com.sugar.block;

import com.sugar.temple.EchoPuzzle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Tambor del Eco: cada uno suena con una nota (0-3). Se toca con clic izquierdo o derecho.
 * Forma parte del puzle de la Sala de los Tambores (ver {@link EchoPuzzle}).
 * En creativo: shift + clic derecho con la mano vacía para cambiar de nota.
 */
public class EchoDrumBlock extends TempleBlock {
    public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, 3);

    public EchoDrumBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NOTE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NOTE);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isCreative() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            level.setBlock(pos, state.setValue(NOTE, (state.getValue(NOTE) + 1) % 4), Block.UPDATE_ALL);
            EchoPuzzle.playNote((ServerLevel) level, pos, (state.getValue(NOTE) + 1) % 4);
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer sp) EchoPuzzle.drumHit((ServerLevel) level, pos, state.getValue(NOTE), sp);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) EchoPuzzle.drumHit((ServerLevel) level, pos, state.getValue(NOTE), sp);
    }
}
