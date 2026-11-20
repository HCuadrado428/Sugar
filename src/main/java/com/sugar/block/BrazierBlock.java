package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Optional;

/**
 * Brasero del templo: se enciende con una antorcha, un mechero, una carga ígnea, una flecha en llamas o una
 * explosión de bomba. Se apaga a los 20 s, salvo que ya estén todos los de su canal encendidos: entonces se
 * abren las puertas del canal y se quedan ardiendo.
 */
public class BrazierBlock extends TempleBlock {
    public static final IntegerProperty CHANNEL = EventDoorBlock.CHANNEL;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 11, 14);
    private static final int BURN = 400;

    public BrazierBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHANNEL, 1).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHANNEL, LIT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    private static boolean isFire(ItemStack s) {
        return s.is(Items.TORCH) || s.is(Items.SOUL_TORCH) || s.is(Items.FLINT_AND_STEEL) || s.is(Items.FIRE_CHARGE) || s.is(Items.BLAZE_ROD);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (state.getValue(LIT)) return InteractionResult.PASS;
        if (!isFire(player.getItemInHand(hand))) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Está frío. Hace falta fuego: una antorcha, un mechero o una flecha en llamas.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        light((ServerLevel) level, pos, state);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel sl && projectile.isOnFire() && !state.getValue(LIT)) light(sl, hit.getBlockPos(), state);
    }

    public static void light(ServerLevel level, BlockPos pos, BlockState state) {
        Optional<TempleData> data = TempleSavedData.get(level.getServer()).findAt(level.dimension(), pos);
        if (data.isEmpty() || !data.get().active) return;
        level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.2, 0.3, 0.2, 0.03);
        level.scheduleTick(pos, state.getBlock(), BURN);
        int ch = state.getValue(CHANNEL);
        List<BlockPos> all = TempleService.positionsOf(level, data.get(), BrazierBlock.class).stream()
                .filter(p -> level.getBlockState(p).getValue(CHANNEL) == ch).toList();
        if (!data.get().isDone(ch) && all.stream().allMatch(p -> level.getBlockState(p).getValue(LIT))) {
            TempleService.setDoors(level.getServer(), data.get(), ch, true);
            data.get().markDone(ch);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        boolean solved = TempleSavedData.get(level.getServer()).findAt(level.dimension(), pos).map(d -> d.isDone(state.getValue(CHANNEL))).orElse(false);
        if (solved) return;
        level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.8, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.03, 0);
        if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0, 0.05, 0);
        if (random.nextInt(20) == 0) level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F, false);
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
    }
}
