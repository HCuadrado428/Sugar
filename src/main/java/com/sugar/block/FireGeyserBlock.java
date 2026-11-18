package com.sugar.block;

import com.sugar.temple.TempleSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Géiser de Fuego: durante el evento lanza una columna de fuego con ritmo fijo
 * (cada 4 s, dura 1,25 s, avisa con humo 0,75 s antes). Su FASE (0-3) retrasa el ritmo 1 s,
 * para hacer filas que se alternan. En creativo: shift + clic derecho para cambiar de fase.
 */
public class FireGeyserBlock extends TempleBlock {
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 3);
    private static final int PERIOD = 80, ERUPT = 25, WARN = 15, STEP = 4;

    public FireGeyserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(state.getBlock())) level.scheduleTick(pos, this, STEP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.isCreative() || !player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            int next = (state.getValue(PHASE) + 1) % 4;
            level.setBlock(pos, state.setValue(PHASE, next), Block.UPDATE_ALL);
            player.displayClientMessage(Component.literal("Fase del géiser: " + next).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, STEP);
        boolean active = TempleSavedData.get(level.getServer()).findAt(level.dimension(), pos).map(d -> d.active).orElse(false);
        if (!active) return;

        long cycle = (level.getGameTime() + state.getValue(PHASE) * 20L) % PERIOD;
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        if (cycle < ERUPT) {
            for (int i = 0; i < 6; i++) {
                level.sendParticles(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5,
                        0, (random.nextDouble() - 0.5) * 0.1, 1.0, (random.nextDouble() - 0.5) * 0.1, 0.35 + random.nextDouble() * 0.2);
            }
            level.sendParticles(ParticleTypes.LAVA, x, y + 0.2, z, 1, 0.2, 0.1, 0.2, 0.0);
            if (cycle < STEP) level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.2F, 0.5F);
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(pos.above()).expandTowards(0, 3, 0).inflate(0.2, 0, 0.2))) {
                p.hurt(level.damageSources().inFire(), 4.0F);
                p.setSecondsOnFire(4);
            }
        } else if (cycle >= PERIOD - WARN) {
            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.1, z, 3, 0.2, 0.05, 0.2, 0.01);
            if (cycle >= PERIOD - STEP) level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
    }
}
