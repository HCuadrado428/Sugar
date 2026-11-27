package com.sugar.block;

import com.sugar.boss.SugarBoss;
import com.sugar.registry.ModItems;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tótem del jefe. Durante la pelea el jefe carga unos cuantos y se alimenta de ellos: hay que
 * romperlos (golpes, clic derecho, flechas o bombas; el Martillo de Obsidiana cuenta doble) antes
 * de que termine la carga, o el jefe se cura y se enfurece. Cada tótem roto le quita vida.
 * KIND 0-2: Volcán, Ceniza, Eco (solo cambia el aspecto).
 */
public class BossTotemBlock extends TempleBlock {
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 2);
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
    public static final IntegerProperty HITS = IntegerProperty.create("hits", 0, 15);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();

    public BossTotemBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, 0).setValue(CHARGED, false).setValue(HITS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, CHARGED, HITS);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Golpes necesarios para romper un tótem: más cuantos más jugadores hay dentro. */
    public static int needed(ServerLevel level, TempleData data) {
        return Math.min(15, 3 + TempleService.playersInside(level, data).size() / 10);
    }

    public static void charge(ServerLevel level, BlockPos pos, boolean charged) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof BossTotemBlock)) return;
        level.setBlock(pos, s.setValue(CHARGED, charged).setValue(HITS, 0), 3);
        if (charged) {
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 0.6F);
            level.sendParticles(particle(s.getValue(KIND)), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.05);
        }
    }

    public static ParticleOptions particle(int kind) {
        return switch (kind) {
            case 1 -> ParticleTypes.SMALL_FLAME;
            case 2 -> ParticleTypes.SCULK_SOUL;
            default -> ParticleTypes.FLAME;
        };
    }

    /** Un golpe (o varios) a un tótem cargado. Devuelve true si se ha roto. */
    public static boolean hit(ServerLevel level, BlockPos pos, TempleData data, int strength, @Nullable Player who) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof BossTotemBlock) || !s.getValue(CHARGED)) return false;
        if (who != null) {
            long now = level.getGameTime();
            Long last = LAST_HIT.get(who.getUUID());
            if (last != null && now - last < 8) return false;
            LAST_HIT.put(who.getUUID(), now);
        }
        int need = needed(level, data);
        int hits = Math.min(15, s.getValue(HITS) + strength);
        if (hits < need) {
            level.setBlock(pos, s.setValue(HITS, hits), 3);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.2F, 0.6F + hits * 0.08F);
            if (who != null) who.displayClientMessage(Component.literal("Tótem: " + hits + "/" + need).withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        level.setBlock(pos, s.setValue(CHARGED, false).setValue(HITS, 0), 3);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.6F, 1.4F);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
        SugarBoss boss = TempleService.findBoss(level, data);
        if (boss != null) boss.onTotemBroken(level);
        return true;
    }

    private static Optional<TempleData> dataAt(ServerLevel level, BlockPos pos) {
        return TempleSavedData.get(level.getServer()).findAt(level.dimension(), pos).filter(d -> d.active);
    }

    private static int strengthOf(Player player) {
        return player.getMainHandItem().is(ModItems.OBSIDIAN_HAMMER.get()) ? 2 : 1;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel sl && state.getValue(CHARGED))
            dataAt(sl, pos).ifPresent(d -> hit(sl, pos, d, strengthOf(player), player));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isCreative() && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            if (!level.isClientSide) level.setBlock(pos, state.cycle(KIND), 3);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!state.getValue(CHARGED)) {
            if (!level.isClientSide) player.displayClientMessage(Component.literal("Un tótem dormido. El jefe lo despertará para alimentarse de él.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel sl) dataAt(sl, pos).ifPresent(d -> hit(sl, pos, d, strengthOf(player), player));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel sl && state.getValue(CHARGED))
            dataAt(sl, hit.getBlockPos()).ifPresent(d -> hit(sl, hit.getBlockPos(), d, 1, null));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(CHARGED)) return;
        for (int i = 0; i < 2; i++)
            level.addParticle(particle(state.getValue(KIND)), pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.0,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.05, 0);
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(CHARGED, false).setValue(HITS, 0), 3);
    }
}
