package com.sugar.block;

import com.sugar.registry.ModItems;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import com.sugar.temple.TempleStory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Gran Cofre. KIND 0: Martillo de Obsidiana (uno para cada jugador), 1: Brújula del templo (una para
 * cada jugador, apunta al Gran Cofre de la Llave del Jefe), 2: Llave del Jefe (del grupo),
 * 3: bolsa de bombas (8 por jugador), 4: Lente del Eco (una por jugador).
 * La primera vez que se abre hay escena: sonido, luz y el narrador.
 * En creativo: shift + clic derecho con la mano vacía para cambiar el contenido.
 */
public class BigChestBlock extends TempleBlock {
    public static final int HAMMER = 0, COMPASS = 1, BOSS_KEY = 2, BOMBS = 3, LENS = 4;
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 4);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    public BigChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, 0).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, OPEN);
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
        if (player.isCreative() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            int next = (state.getValue(KIND) + 1) % 5;
            level.setBlock(pos, state.setValue(KIND, next), Block.UPDATE_ALL);
            player.displayClientMessage(Component.literal("Gran Cofre: " + new String[]{"martillo", "brújula", "Llave del Jefe", "bombas", "lente"}[next])
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        ServerLevel sl = (ServerLevel) level;
        Optional<TempleData> found = TempleSavedData.get(sl.getServer()).findAt(level.dimension(), pos);
        if (found.isEmpty() || !found.get().active) {
            player.displayClientMessage(Component.literal("Está cerrado... (el evento no ha empezado)").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        open(sl, pos, state, found.get(), player);
        return InteractionResult.CONSUME;
    }

    /** Lo abre (con escena la primera vez) y da su objeto a quien lo abre, si le toca. */
    public static void open(ServerLevel level, BlockPos pos, BlockState state, TempleData data, Player player) {
        int kind = state.getValue(KIND);
        if (!state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.5F, 0.6F);
            level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.08);
            level.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
            if (kind == BOSS_KEY) data.keys |= TempleData.BOSS_KEY;
            TempleSavedData.get(level.getServer()).setDirty();
            TempleStory.narrate(level, data, TempleStory.bigChest(data.type, kind, player == null ? "Alguien" : player.getName().getString()));
        }
        if (kind == BOSS_KEY || player == null) {
            if (player != null && state.getValue(OPEN)) player.displayClientMessage(Component.literal("Está vacío.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        var claims = data.chestClaims.computeIfAbsent(pos.asLong(), k -> new HashSet<>());
        if (!claims.add(player.getUUID())) {
            player.displayClientMessage(Component.literal("Ya has cogido el tuyo.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        TempleSavedData.get(level.getServer()).setDirty();
        ItemStack stack = switch (kind) {
            case HAMMER -> new ItemStack(ModItems.OBSIDIAN_HAMMER.get());
            case BOMBS -> new ItemStack(ModItems.ASH_BOMB.get(), 8);
            case LENS -> new ItemStack(ModItems.ECHO_LENS.get());
            default -> compass(level, data);
        };
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.displayClientMessage(Component.literal("Has conseguido: ").withStyle(ChatFormatting.GOLD).append(stack.getHoverName()), true);
    }

    /** Brújula que apunta al Gran Cofre de la Llave del Jefe. */
    private static ItemStack compass(ServerLevel level, TempleData data) {
        ItemStack stack = new ItemStack(Items.COMPASS);
        List<BlockPos> targets = TempleService.positionsOf(level, data, BigChestBlock.class).stream()
                .filter(p -> level.getBlockState(p).getValue(KIND) == BOSS_KEY).toList();
        CompoundTag tag = stack.getOrCreateTag();
        if (!targets.isEmpty()) {
            tag.put("LodestonePos", NbtUtils.writeBlockPos(targets.get(0)));
            tag.putString("LodestoneDimension", level.dimension().location().toString());
            tag.putBoolean("LodestoneTracked", false);
        }
        stack.setHoverName(Component.literal("Brújula del Templo").withStyle(ChatFormatting.AQUA));
        return stack;
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(OPEN)) level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_ALL);
    }
}
