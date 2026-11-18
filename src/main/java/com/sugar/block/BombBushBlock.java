package com.sugar.block;

import com.sugar.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Mata de Pólvora: clic derecho para coger una Bomba de Ceniza. Vuelve a dar fruto a los 10 s. */
public class BombBushBlock extends TempleBlock {
    public static final BooleanProperty GROWN = BooleanProperty.create("grown");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);
    private static final int REGROW = 200;

    public BombBushBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GROWN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GROWN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(GROWN)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack bomb = new ItemStack(ModItems.ASH_BOMB.get());
            if (!player.getInventory().add(bomb)) player.drop(bomb, false);
            level.setBlock(pos, state.setValue(GROWN, false), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, REGROW);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(GROWN)) {
            level.setBlock(pos, state.setValue(GROWN, true), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
        }
    }

    @Override
    public void resetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(GROWN)) level.setBlock(pos, state.setValue(GROWN, true), Block.UPDATE_CLIENTS);
    }
}
