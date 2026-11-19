package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import com.sugar.temple.TempleStory;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Puerta con cerradura: una puerta de evento que abre el grupo con sus llaves. KEYS son los bits de las
 * llaves que pide (1-4 pequeñas, 16 la del jefe; una cerradura doble pide dos). Al abrirla se abren todas
 * las puertas de su canal. También se abre con /event door, como cualquier puerta.
 */
public class LockedDoorBlock extends EventDoorBlock {
    public static final IntegerProperty KEYS = IntegerProperty.create("keys", 1, 31);

    public LockedDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(CHANNEL, 1).setValue(KEYS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KEYS);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isCreative() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (state.getValue(OPEN)) return InteractionResult.PASS;
        var held = player.getItemInHand(hand);
        if (player.isCreative() && (held.is(com.sugar.registry.ModItems.SMALL_KEY.get()) || held.is(com.sugar.registry.ModItems.BOSS_KEY.get()))) {
            TempleSavedData.get(((ServerLevel) level).getServer()).findAt(level.dimension(), pos)
                    .ifPresent(d -> TempleService.setDoors(((ServerLevel) level).getServer(), d, state.getValue(CHANNEL), true));
            return InteractionResult.CONSUME;
        }
        ServerLevel sl = (ServerLevel) level;
        Optional<TempleData> found = TempleSavedData.get(sl.getServer()).findAt(level.dimension(), pos);
        if (found.isEmpty() || !found.get().active) return InteractionResult.CONSUME;
        if (!tryUnlock(sl, pos, state, found.get())) {
            boolean boss = (state.getValue(KEYS) & TempleData.BOSS_KEY) != 0;
            player.displayClientMessage(Component.literal(boss ? "Un cerrojo enorme. Hace falta la Llave del Jefe."
                    : Integer.bitCount(state.getValue(KEYS)) > 1 ? "Dos cerraduras. Hacen falta dos llaves." : "Está cerrada con llave.")
                    .withStyle(ChatFormatting.GRAY), true);
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        return InteractionResult.CONSUME;
    }

    /** Si el grupo tiene las llaves, abre todas las puertas del canal. */
    public static boolean tryUnlock(ServerLevel level, BlockPos pos, BlockState state, TempleData data) {
        int need = state.getValue(KEYS);
        if ((data.keys & need) != need) return false;
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.5F, 0.6F);
        TempleService.setDoors(level.getServer(), data, state.getValue(CHANNEL), true);
        TempleStory.narrate(level, data, (need & TempleData.BOSS_KEY) != 0 ? TempleStory.bossDoor(data.type) : TempleStory.unlocked(data.type));
        return true;
    }
}
