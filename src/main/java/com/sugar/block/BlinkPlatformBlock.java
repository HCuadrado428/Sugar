package com.sugar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Plataforma intermitente: aparece y desaparece cada 3 s mientras el templo está activo. Las de fase 0 y fase 1
 * se turnan, así que hay que saltar al ritmo. En creativo: shift + clic derecho para cambiar la fase.
 */
public class BlinkPlatformBlock extends TempleBlock {
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 1);
    public static final BooleanProperty SOLID = BooleanProperty.create("solid");
    public static final int PERIOD = 60;

    public BlinkPlatformBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, 0).setValue(SOLID, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE, SOLID);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(SOLID) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SOLID) ? Shapes.block() : Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide || !player.isCreative() || !player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty())
            return net.minecraft.world.InteractionResult.PASS;
        level.setBlock(pos, state.setValue(PHASE, 1 - state.getValue(PHASE)), Block.UPDATE_ALL);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** Pone la plataforma en su estado para este instante. */
    public static void update(ServerLevel level, BlockPos pos, BlockState state, long time) {
        boolean solid = (time / PERIOD) % 2 == state.getValue(PHASE);
        if (state.getValue(SOLID) != solid) {
            level.setBlock(pos, state.setValue(SOLID, solid), Block.UPDATE_ALL);
            if (solid) level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(SOLID)) level.setBlock(pos, state.setValue(SOLID, true), Block.UPDATE_ALL);
    }
}
