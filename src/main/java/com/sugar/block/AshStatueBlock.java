package com.sugar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Estatua Agrietada: bloquea el paso y solo la rompen las Bombas de Ceniza. Se restaura con el reset. */
public class AshStatueBlock extends TempleBlock {
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    public AshStatueBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BROKEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BROKEN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(BROKEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BROKEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BROKEN) ? Shapes.empty() : Shapes.block();
    }

    /** Rompe las estatuas en un radio alrededor de una explosión. Devuelve cuántas ha roto. */
    public static int breakAround(ServerLevel level, Vec3 center, double radius) {
        int broken = 0;
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(center);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (p.distToCenterSqr(center) > radius * radius) continue;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof AshStatueBlock && !s.getValue(BROKEN)) {
                level.setBlock(p, s.setValue(BROKEN, true), Block.UPDATE_CLIENTS);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.POLISHED_BLACKSTONE.defaultBlockState()),
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
                broken++;
            }
        }
        if (broken > 0) level.playSound(null, c, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
        return broken;
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(BROKEN)) level.setBlock(pos, state.setValue(BROKEN, false), Block.UPDATE_CLIENTS);
    }
}
