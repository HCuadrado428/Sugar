package com.sugar.block;

import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/** Ojo de piedra: solo se activa con un disparo (flecha, tridente, bola de nieve...). Se cierra a los 10 s. */
public class EyeSwitchBlock extends EventSwitchBlock {

    public EyeSwitchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected int activeTicks() {
        return 200;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isCreative() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) return super.use(state, level, pos, player, hand, hit);
        if (!level.isClientSide)
            player.displayClientMessage(Component.literal("El ojo te sigue con la mirada... quizá un disparo lo despierte.").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!(level instanceof ServerLevel sl)) return;
        Optional<TempleData> data = TempleSavedData.get(sl.getServer()).findAt(level.dimension(), hit.getBlockPos());
        data.filter(d -> d.active).ifPresent(d -> activate(sl, hit.getBlockPos(), state, d));
    }
}
