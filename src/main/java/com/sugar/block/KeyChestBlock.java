package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleStory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * Cofre con llave: guarda una llave pequeña del grupo (KEY 1-4). La llave no va al inventario:
 * es de todos, y cualquiera puede gastarla en la cerradura que le corresponde.
 * En creativo: shift + clic derecho con la mano vacía para cambiar de llave.
 */
public class KeyChestBlock extends TempleBlock {
    public static final IntegerProperty KEY = IntegerProperty.create("key", 1, 4);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public KeyChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KEY, 1).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KEY, OPEN);
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
            int next = state.getValue(KEY) % 4 + 1;
            level.setBlock(pos, state.setValue(KEY, next), Block.UPDATE_ALL);
            player.displayClientMessage(Component.literal("Llave del cofre: " + next).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        if (state.getValue(OPEN)) {
            player.displayClientMessage(Component.literal("Está vacío.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        ServerLevel sl = (ServerLevel) level;
        Optional<TempleData> data = TempleSavedData.get(sl.getServer()).findAt(level.dimension(), pos);
        if (data.isEmpty() || !data.get().active) {
            player.displayClientMessage(Component.literal("Está cerrado... (el evento no ha empezado)").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        open(sl, pos, state, data.get());
        return InteractionResult.CONSUME;
    }

    /** Abre el cofre y el grupo gana su llave. */
    public static void open(ServerLevel level, BlockPos pos, BlockState state, TempleData data) {
        level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
        data.keys |= 1 << (state.getValue(KEY) - 1);
        TempleSavedData.get(level.getServer()).setDirty();
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.9F);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.5F);
        level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 14, 0.3, 0.3, 0.3, 0.5);
        TempleStory.narrate(level, data, TempleStory.smallKey(data.type, Integer.bitCount(data.keys & 0xF)));
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(OPEN)) level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_ALL);
    }
}
