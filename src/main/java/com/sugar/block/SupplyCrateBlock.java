package com.sugar.block;

import com.sugar.SugarMod;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Cofre de Suministros: cada jugador recoge un lote por evento.
 * El contenido sale de data/sugar/loot_tables/supplies/<templo>.json
 */
public class SupplyCrateBlock extends TempleBlock {

    public SupplyCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;
        ServerLevel serverLevel = (ServerLevel) level;
        TempleSavedData saved = TempleSavedData.get(serverLevel.getServer());
        Optional<TempleData> found = saved.findAt(level.dimension(), pos);

        if (found.isEmpty() || !found.get().active) {
            sp.displayClientMessage(Component.literal("Está cerrado hasta que empiece el evento.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        TempleData data = found.get();
        if (!data.claimedSupplies.add(sp.getUUID())) {
            sp.displayClientMessage(Component.literal("Ya has recogido tus suministros.").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        saved.setDirty();

        LootTable table = serverLevel.getServer().getLootData().getLootTable(SugarMod.id("supplies/" + data.type.id()));
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, sp)
                .withParameter(LootContextParams.ORIGIN, sp.position())
                .create(LootContextParamSets.GIFT);
        for (ItemStack stack : table.getRandomItems(params)) {
            if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        }
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
        sp.displayClientMessage(Component.literal("Suministros recogidos.").withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }
}
