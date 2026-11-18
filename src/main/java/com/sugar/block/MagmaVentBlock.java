package com.sugar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;

/**
 * Respiradero de Magma: agujero por donde asoma la Sierpe.
 * Pisarlo quema. Cuando entra en erupción lanza una columna de fuego que hace daño.
 */
public class MagmaVentBlock extends TempleBlock {
    public static final BooleanProperty ERUPTING = BooleanProperty.create("erupting");
    private static final int ERUPT_INTERVAL = 10;

    public MagmaVentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ERUPTING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ERUPTING);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity living && !entity.fireImmune()) {
            living.setSecondsOnFire(2);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ERUPTING)) return;
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5, 25, 0.3, 1.5, 0.3, 0.02);
        level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
        if (random.nextInt(4) == 0) {
            level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.8F, 0.5F);
        }
        AABB column = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 5, pos.getZ() + 1).inflate(0.3, 0, 0.3);
        for (Player p : level.getEntitiesOfClass(Player.class, column)) {
            p.hurt(level.damageSources().inFire(), 4.0F);
            p.setSecondsOnFire(4);
        }
        level.scheduleTick(pos, this, ERUPT_INTERVAL);
    }

    public static void setErupting(ServerLevel level, BlockPos pos, boolean erupting) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MagmaVentBlock vent) || state.getValue(ERUPTING) == erupting) return;
        level.setBlock(pos, state.setValue(ERUPTING, erupting), Block.UPDATE_CLIENTS);
        if (erupting) {
            level.scheduleTick(pos, vent, ERUPT_INTERVAL);
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
    }

    public static boolean isErupting(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof MagmaVentBlock && state.getValue(ERUPTING);
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        setErupting(level, pos, false);
    }
}
