package com.sugar.block;

import com.sugar.registry.ModItems;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Atril de la Lente: guarda UNA Lente del Eco por evento. Quien la coge guía al resto. */
public class LensPedestalBlock extends TempleBlock {
    public static final BooleanProperty HAS_LENS = BooleanProperty.create("has_lens");

    public LensPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HAS_LENS, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_LENS);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean active = TempleSavedData.get(((ServerLevel) level).getServer()).findAt(level.dimension(), pos).map(d -> d.active).orElse(false);
        if (!active) {
            player.displayClientMessage(Component.literal("La lente no responde hasta que empiece el evento.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        if (!state.getValue(HAS_LENS)) {
            player.displayClientMessage(Component.literal("Alguien ya se ha llevado esta lente.").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        ItemStack lens = new ItemStack(ModItems.ECHO_LENS.get());
        if (!player.getInventory().add(lens)) player.drop(lens, false);
        level.setBlock(pos, state.setValue(HAS_LENS, false), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 0.8F);
        player.displayClientMessage(Component.literal("Llévala en una mano para ver lo oculto. ¡Guía a los demás!").withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(HAS_LENS)) level.setBlock(pos, state.setValue(HAS_LENS, true), Block.UPDATE_ALL);
    }
}
