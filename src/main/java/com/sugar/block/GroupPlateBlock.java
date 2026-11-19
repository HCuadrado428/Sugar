package com.sugar.block;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Placa de Grupo: se enciende mientras alguien está encima. Cuando TODAS las placas del mismo canal
 * están ocupadas a la vez se abren las puertas de ese canal (lo comprueba TempleEvents).
 * En creativo: shift + clic derecho con la mano vacía para cambiar de canal.
 */
public class GroupPlateBlock extends TempleBlock {
    public static final IntegerProperty CHANNEL = EventDoorBlock.CHANNEL;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public GroupPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHANNEL, 3).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHANNEL, LIT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isCreative() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            int next = (state.getValue(CHANNEL) + 1) % 16;
            level.setBlock(pos, state.setValue(CHANNEL, next), Block.UPDATE_ALL);
            player.displayClientMessage(Component.literal("Canal de la placa: " + next).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.literal("Hace falta alguien encima de cada placa a la vez.").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
    }
}
