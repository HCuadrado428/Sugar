package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleObjectives;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Campana del Jefe: cuando la mayoría del grupo está en la antesala, tocarla abre la puerta de la
 * arena (canal 9) y el jefe despierta tras una cuenta atrás de 5 segundos.
 */
public class BossBellBlock extends TempleBlock {
    public static final BooleanProperty RUNG = BooleanProperty.create("rung");
    public static final double GATHER_RADIUS = 16.0;
    public static final float QUORUM = 0.6F;

    public BossBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNG, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNG);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        Optional<TempleData> found = TempleSavedData.get(sl.getServer()).findAt(level.dimension(), pos);
        if (found.isEmpty() || !found.get().active) {
            player.displayClientMessage(Component.literal("La campana está muda... (el evento no ha empezado)").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        TempleData data = found.get();
        if (state.getValue(RUNG) || TempleService.findBoss(sl, data) != null || data.summonCountdown > 0) {
            player.displayClientMessage(Component.literal("El jefe ya está despierto.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        List<ServerPlayer> inside = TempleService.playersInside(sl, data);
        long near = inside.stream().filter(p -> p.blockPosition().closerThan(pos, GATHER_RADIUS)).count();
        int needed = Math.max(1, (int) Math.ceil(inside.size() * QUORUM));
        if (near < needed) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
            player.displayClientMessage(Component.literal("Reunid al grupo en la antesala: " + near + "/" + needed + " listos")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        ring(sl, pos, state, data);
        return InteractionResult.CONSUME;
    }

    public static void ring(ServerLevel level, BlockPos pos, BlockState state, TempleData data) {
        level.setBlock(pos, state.setValue(RUNG, true), Block.UPDATE_ALL);
        for (int i = 0; i < 3; i++) level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 0.5F + i * 0.05F);
        level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3.0F, 0.6F);
        TempleService.setDoors(level.getServer(), data, TempleObjectives.BELL, true);
        data.markDone(TempleObjectives.BELL);
        data.summonCountdown = 100;
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(RUNG)) level.setBlock(pos, state.setValue(RUNG, false), Block.UPDATE_ALL);
    }
}
