package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Sello de Obsidiana: puzle cooperativo. Al activarlo se queda encendido 10 segundos.
 * Si todos los sellos del mismo canal están encendidos a la vez, se abren las puertas de ese canal.
 * En creativo: shift + clic derecho con la mano vacía para cambiar de canal.
 */
public class EventSwitchBlock extends TempleBlock {
    public static final IntegerProperty CHANNEL = EventDoorBlock.CHANNEL;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");
    private static final int ACTIVE_TICKS = 200;

    public EventSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHANNEL, 1).setValue(ACTIVE, false).setValue(LOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHANNEL, ACTIVE, LOCKED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel serverLevel = (ServerLevel) level;

        if (player.isCreative() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            int next = (state.getValue(CHANNEL) + 1) % 16;
            level.setBlock(pos, state.setValue(CHANNEL, next), Block.UPDATE_ALL);
            player.displayClientMessage(Component.literal("Canal del sello: " + next).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        if (state.getValue(LOCKED) || state.getValue(ACTIVE)) return InteractionResult.CONSUME;

        Optional<TempleData> data = TempleSavedData.get(serverLevel.getServer()).findAt(level.dimension(), pos);
        if (data.isEmpty() || !data.get().active) {
            player.displayClientMessage(Component.literal("El sello no responde...").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }

        activate(serverLevel, pos, state, data.get());
        return InteractionResult.CONSUME;
    }

    /** Cuánto tiempo se queda encendido (en ticks). */
    protected int activeTicks() {
        return ACTIVE_TICKS;
    }

    public void activate(ServerLevel level, BlockPos pos, BlockState state, TempleData data) {
        if (state.getValue(LOCKED) || state.getValue(ACTIVE)) return;
        level.setBlock(pos, state.setValue(ACTIVE, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, activeTicks());
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
        checkSolved(level, data, state.getValue(CHANNEL));
    }

    protected static void checkSolved(ServerLevel level, TempleData data, int channel) {
        List<BlockPos> switches = TempleService.positionsOf(level, data, EventSwitchBlock.class).stream()
                .filter(p -> level.getBlockState(p).getValue(CHANNEL) == channel)
                .toList();
        int active = 0;
        for (BlockPos p : switches) {
            if (level.getBlockState(p).getValue(ACTIVE)) active++;
        }
        if (active < switches.size()) {
            return;
        }
        for (BlockPos p : switches) {
            BlockState s = level.getBlockState(p);
            level.setBlock(p, s.setValue(LOCKED, true).setValue(ACTIVE, true), Block.UPDATE_ALL);
        }
        TempleService.setDoors(level.getServer(), data, channel, true);
        data.markDone(channel);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE) && !state.getValue(LOCKED)) {
            level.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(ACTIVE) || state.getValue(LOCKED)) {
            level.setBlock(pos, state.setValue(ACTIVE, false).setValue(LOCKED, false), Block.UPDATE_ALL);
        }
    }
}
