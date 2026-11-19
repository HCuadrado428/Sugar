package com.sugar.block;

import com.sugar.registry.ModItems;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Interruptor de obsidiana: como un sello, pero solo se enciende de un martillazo (golpe o clic derecho con
 * el Martillo de Obsidiana) y dura poco: hay que golpearlos todos casi a la vez.
 */
public class HammerSwitchBlock extends EventSwitchBlock {

    public HammerSwitchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected int activeTicks() {
        return 100;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isCreative() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        hammer(state, (ServerLevel) level, pos, player);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) hammer(state, sl, pos, player);
    }

    private void hammer(BlockState state, ServerLevel level, BlockPos pos, Player player) {
        if (!player.getMainHandItem().is(ModItems.OBSIDIAN_HAMMER.get())) {
            player.displayClientMessage(Component.literal("Ni se mueve. Haría falta un buen golpe: un martillo... o una explosión.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Optional<TempleData> data = TempleSavedData.get(level.getServer()).findAt(level.dimension(), pos);
        if (data.isEmpty() || !data.get().active) return;
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8F, 0.6F);
        activate(level, pos, state, data.get());
    }
}
