package com.sugar.block;

import com.sugar.entity.Keese;
import com.sugar.registry.ModEntities;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleService;
import com.sugar.temple.TempleType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Nido de guardianes (invisible): cuando un jugador se acerca por primera vez durante el evento, hace salir a los
 * enemigos de la sala. KIND 0-2: Keese de fuego, ceniza o eco; 3: los guardianes propios del templo (cubos de
 * magma, crías de ceniza o esqueletos). /event reset los quita y lo deja listo otra vez.
 * En creativo: shift + clic derecho con la mano vacía cambia el tipo.
 */
public class GuardianNestBlock extends TempleBlock {
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 3);
    public static final BooleanProperty TRIGGERED = BooleanProperty.create("triggered");
    public static final String TAG = "sugar_guardian";
    private static final VoxelShape SELECT = Block.box(4, 4, 4, 12, 12, 12);

    public GuardianNestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, 0).setValue(TRIGGERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, TRIGGERED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext e && e.getEntity() instanceof Player p && p.isCreative() ? SELECT : Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isCreative() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            int next = (state.getValue(KIND) + 1) % 4;
            level.setBlock(pos, state.setValue(KIND, next), Block.UPDATE_ALL);
            player.displayClientMessage(Component.literal("Nido: " + new String[]{"Keese de fuego", "Keese de ceniza", "Keese de eco", "guardianes del templo"}[next])
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Hace salir a los guardianes (una sola vez por evento). */
    public static void trigger(ServerLevel level, BlockPos pos, BlockState state, TempleData data) {
        level.setBlock(pos, state.setValue(TRIGGERED, true), Block.UPDATE_CLIENTS);
        int kind = state.getValue(KIND);
        int extra = TempleService.playersInside(level, data).size() / 15;
        int n = (kind == 3 ? 2 : 2) + extra;
        for (int i = 0; i < n; i++) {
            Mob mob = create(level, kind, data.type);
            if (mob == null) continue;
            mob.moveTo(pos.getX() + 0.5 + level.random.nextGaussian() * 0.8, pos.getY() + 0.1, pos.getZ() + 0.5 + level.random.nextGaussian() * 0.8,
                    level.random.nextFloat() * 360, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
            if (mob instanceof Keese k) k.setVariant(kind);
            mob.addTag(TAG);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
        }
    }

    private static Mob create(ServerLevel level, int kind, TempleType type) {
        if (kind < 3) return ModEntities.KEESE.get().create(level);
        EntityType<? extends Mob> t = switch (type) {
            case VOLCAN -> EntityType.MAGMA_CUBE;
            case CENIZA -> ModEntities.ASH_HATCHLING.get();
            default -> EntityType.SKELETON;
        };
        Mob m = t.create(level);
        if (m instanceof net.minecraft.world.entity.monster.MagmaCube cube) cube.setSize(2, true);
        return m;
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(24), e -> e.getTags().contains(TAG))) m.discard();
        if (state.getValue(TRIGGERED)) level.setBlock(pos, state.setValue(TRIGGERED, false), Block.UPDATE_CLIENTS);
    }
}
