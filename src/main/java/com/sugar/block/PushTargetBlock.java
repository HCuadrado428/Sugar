package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baldosa Destino: se enciende cuando tiene un Bloque Empujable encima. Con todas las de un canal encendidas se
 * abren las puertas de ese canal.
 */
public class PushTargetBlock extends TempleBlock {
    public static final IntegerProperty CHANNEL = EventDoorBlock.CHANNEL;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public PushTargetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHANNEL, 1).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHANNEL, LIT);
    }

    /** Actualiza las baldosas y abre los canales completos. */
    public static void check(ServerLevel level, TempleData data) {
        Map<Integer, int[]> count = new HashMap<>();
        List<BlockPos> targets = TempleService.positionsOf(level, data, PushTargetBlock.class);
        for (BlockPos p : targets) {
            BlockState st = level.getBlockState(p);
            boolean on = level.getBlockState(p.above()).getBlock() instanceof PushBlock;
            if (st.getValue(LIT) != on) {
                level.setBlock(p, st.setValue(LIT, on), Block.UPDATE_ALL);
                if (on) level.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 0.8F);
            }
            int[] c = count.computeIfAbsent(st.getValue(CHANNEL), k -> new int[2]);
            c[0]++;
            if (on) c[1]++;
        }
        for (Map.Entry<Integer, int[]> e : count.entrySet()) {
            if (e.getValue()[0] == e.getValue()[1] && !data.isDone(e.getKey())) {
                TempleService.setDoors(level.getServer(), data, e.getKey(), true);
                data.markDone(e.getKey());
            }
        }
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
    }
}
