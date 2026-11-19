package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.WaveRoom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Altar de Oleadas: al tocarlo empiezan las oleadas de la sala; al superarlas se abren las puertas
 * de su canal. STATE: 0 apagado, 1 en marcha, 2 superado.
 */
public class WaveAltarBlock extends TempleBlock {
    public static final IntegerProperty CHANNEL = EventDoorBlock.CHANNEL;
    public static final IntegerProperty STATE = IntegerProperty.create("state", 0, 2);

    public WaveAltarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHANNEL, 2).setValue(STATE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHANNEL, STATE);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        if (player.isCreative() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            int next = (state.getValue(CHANNEL) + 1) % 16;
            level.setBlock(pos, state.setValue(CHANNEL, next), Block.UPDATE_ALL);
            player.displayClientMessage(Component.literal("Canal del altar: " + next).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        if (state.getValue(STATE) == 2) {
            player.displayClientMessage(Component.literal("Esta sala ya está despejada.").withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        }
        if (state.getValue(STATE) == 1 || WaveRoom.isRunning(pos)) return InteractionResult.CONSUME;
        Optional<TempleData> data = TempleSavedData.get(sl.getServer()).findAt(level.dimension(), pos);
        if (data.isEmpty() || !data.get().active) {
            player.displayClientMessage(Component.literal("El altar está frío... (el evento no ha empezado)").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        activate(sl, pos, state, data.get());
        return InteractionResult.CONSUME;
    }

    public static void activate(ServerLevel level, BlockPos pos, BlockState state, TempleData data) {
        level.setBlock(pos, state.setValue(STATE, 1), Block.UPDATE_ALL);
        WaveRoom.start(level, pos, data, state.getValue(CHANNEL));
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        WaveRoom.reset(level, pos);
        if (state.getValue(STATE) != 0) level.setBlock(pos, state.setValue(STATE, 0), Block.UPDATE_ALL);
    }
}
