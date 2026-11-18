package com.sugar.block;

import com.sugar.entity.FallingRock;
import com.sugar.temple.TempleSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Grieta del Techo: se coloca en el techo. Durante el evento deja caer rocas sobre
 * los jugadores que pasan por debajo (radio de 7 bloques). La sombra de la roca avisa.
 */
public class RockfallBlock extends TempleBlock {
    private static final int RADIUS = 7;
    private static final int DEPTH = 24;

    public RockfallBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            level.scheduleTick(pos, this, 40);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean active = TempleSavedData.get(level.getServer()).findAt(level.dimension(), pos)
                .map(d -> d.active).orElse(false);
        if (active) {
            AABB area = new AABB(pos.getX() - RADIUS, pos.getY() - DEPTH, pos.getZ() - RADIUS,
                    pos.getX() + RADIUS + 1, pos.getY(), pos.getZ() + RADIUS + 1);
            List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, area,
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
            if (!players.isEmpty()) {
                ServerPlayer target = players.get(random.nextInt(players.size()));
                FallingRock.spawn(level, target.getX(), pos.getY() - 1.0, target.getZ(), 7.0F);
            }
        }
        level.scheduleTick(pos, this, 50 + random.nextInt(70));
    }
}
