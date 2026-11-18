package com.sugar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Basalto Inestable: al pisarlo tiembla, se derrumba a los 0,6 s y se regenera a los 5 s.
 * STAGE: 0 = entero, 1 = temblando, 2 = derrumbado.
 */
public class CrumblingBlock extends TempleBlock {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
    private static final int CRUMBLE_DELAY = 12;
    private static final int REGEN_DELAY = 100;

    public CrumblingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof Player && state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.setValue(STAGE, 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, CRUMBLE_DELAY);
            level.playSound(null, pos, SoundEvents.BASALT_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (stage == 1) {
            level.setBlock(pos, state.setValue(STAGE, 2), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.BASALT_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
            level.scheduleTick(pos, this, REGEN_DELAY);
        } else if (stage == 2) {
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
                level.scheduleTick(pos, this, 20);
                return;
            }
            level.setBlock(pos, state.setValue(STAGE, 0), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.BASALT_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(STAGE) == 2 ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STAGE) == 2 ? Shapes.empty() : Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STAGE) == 2 ? Shapes.empty() : Shapes.block();
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(STAGE) != 0) level.setBlock(pos, state.setValue(STAGE, 0), Block.UPDATE_CLIENTS);
    }
}
