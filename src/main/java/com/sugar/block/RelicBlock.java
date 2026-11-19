package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleObjectives;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import com.sugar.temple.TempleStory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Optional;

/**
 * Reliquia escondida. Cada una cuenta un trozo de la historia del jefe a quien la encuentra;
 * con todas se abre la cámara del tesoro (canal 8).
 */
public class RelicBlock extends TempleBlock {
    public static final BooleanProperty FOUND = BooleanProperty.create("found");
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 10, 12);

    public RelicBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FOUND, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FOUND);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        if (state.getValue(FOUND)) {
            player.displayClientMessage(Component.literal("Esta reliquia ya la ha encontrado alguien.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        Optional<TempleData> found = TempleSavedData.get(sl.getServer()).findAt(level.dimension(), pos);
        if (found.isEmpty() || !found.get().active) {
            player.displayClientMessage(Component.literal("Brilla débilmente... (el evento no ha empezado)").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        collect(sl, pos, state, found.get(), player.getName().getString());
        return InteractionResult.CONSUME;
    }

    public static void collect(ServerLevel level, BlockPos pos, BlockState state, TempleData data, String who) {
        List<BlockPos> relics = TempleService.positionsOf(level, data, RelicBlock.class);
        long before = relics.stream().filter(p -> level.getBlockState(p).getValue(FOUND)).count();
        level.setBlock(pos, state.setValue(FOUND, true), Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.2, 0.3, 0.2, 0.03);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 1.2F);
        // Cada reliquia cuenta un trozo de la historia del jefe, en orden
        net.minecraft.server.level.ServerPlayer finder = level.getServer().getPlayerList().getPlayerByName(who);
        String text = TempleStory.relic(data.type, (int) before);
        if (finder != null) TempleStory.narrate(finder, text, ChatFormatting.LIGHT_PURPLE);
        if (before + 1 >= relics.size()) {
            TempleService.setDoors(level.getServer(), data, TempleObjectives.VAULT, true);
            TempleService.rumble(level, data, TempleObjectives.VAULT);
            TempleStory.narrate(level, data, TempleStory.vault(data.type));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FOUND) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.8, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(FOUND)) level.setBlock(pos, state.setValue(FOUND, false), Block.UPDATE_ALL);
    }
}
