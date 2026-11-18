package com.sugar.block;

import com.sugar.temple.EchoPuzzle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Gong del Eco: al tocarlo, los tambores tocan una secuencia que el grupo debe repetir. */
public class EchoGuideBlock extends TempleBlock {

    public EchoGuideBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) EchoPuzzle.start((ServerLevel) level, pos, this);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        EchoPuzzle.playbackTick(level, pos, this);
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        EchoPuzzle.reset(level, pos);
    }
}
