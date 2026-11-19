package com.sugar.block;

import com.sugar.registry.ModItems;
import com.sugar.temple.TempleSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Muro agrietado: un martillazo con el Martillo de Obsidiana lo derrumba entero (todos los bloques
 * agrietados que se tocan). Con /event reset vuelve a estar en pie.
 */
public class CrackedWallBlock extends TempleBlock {
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    private static final int MAX = 400;

    public CrackedWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BROKEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BROKEN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(BROKEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BROKEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        hit((ServerLevel) level, pos, player);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl) hit(sl, pos, player);
    }

    private static void hit(ServerLevel level, BlockPos pos, Player player) {
        if (!player.getMainHandItem().is(ModItems.OBSIDIAN_HAMMER.get())) {
            player.displayClientMessage(Component.literal("La pared está agrietada. Con algo pesado cedería.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean active = TempleSavedData.get(level.getServer()).findAt(level.dimension(), pos).map(d -> d.active).orElse(false);
        if (!active && !player.isCreative()) return;
        smash(level, pos);
    }

    /** Derrumba todo el muro conectado. Devuelve cuántos bloques ha roto. */
    public static int smash(ServerLevel level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        int broken = 0;
        while (!queue.isEmpty() && seen.size() < MAX) {
            BlockPos p = queue.poll();
            if (!seen.add(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof CrackedWallBlock) || s.getValue(BROKEN)) continue;
            level.setBlock(p, s.setValue(BROKEN, true), Block.UPDATE_ALL);
            broken++;
            if (broken % 3 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()),
                    p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.1);
            for (Direction d : Direction.values()) queue.add(p.relative(d));
        }
        if (broken > 0) {
            level.playSound(null, start, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.5F, 0.7F);
            level.playSound(null, start, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
        }
        return broken;
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(BROKEN)) level.setBlock(pos, state.setValue(BROKEN, false), Block.UPDATE_ALL);
    }
}
