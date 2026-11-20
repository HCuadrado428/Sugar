package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Bloque empujable: clic derecho para empujarlo un bloque en la dirección en la que lo miras. Solo se mueve si
 * delante hay hueco y suelo. Si queda encima de una Baldosa Destino, cuenta para el puzle. El reset lo devuelve
 * a su sitio.
 */
public class PushBlock extends TempleBlock {

    public PushBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        Optional<TempleData> data = TempleSavedData.get(sl.getServer()).findAt(level.dimension(), pos);
        if (data.isEmpty() || !data.get().active && !player.isCreative()) return InteractionResult.CONSUME;
        double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
        Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        if (!push(sl, pos, dir, data.get())) {
            level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        return InteractionResult.CONSUME;
    }

    /** Mueve el bloque un paso. Devuelve false si no cabe. */
    public static boolean push(ServerLevel level, BlockPos pos, Direction dir, TempleData data) {
        BlockPos to = pos.relative(dir);
        if (!level.getBlockState(to).canBeReplaced() || level.getBlockState(to.below()).isAir()) return false;
        BlockState st = level.getBlockState(pos);
        long home = data.pushHome.getOrDefault(pos.asLong(), pos.asLong());
        data.pushHome.remove(pos.asLong());
        level.setBlock(to, st, Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (to.asLong() != home) data.pushHome.put(to.asLong(), home);
        TempleSavedData.get(level.getServer()).setDirty();
        level.playSound(null, to, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.8F, 0.5F);
        level.playSound(null, to, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
        PushTargetBlock.check(level, data);
        return true;
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        TempleSavedData.get(level.getServer()).findAt(level.dimension(), pos).ifPresent(data -> {
            Long home = data.pushHome.remove(pos.asLong());
            if (home == null || home == pos.asLong()) return;
            BlockPos h = BlockPos.of(home);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(h, state, Block.UPDATE_ALL);
        });
    }
}
