package com.sugar.item;

import com.sugar.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Lente del Eco: mientras la llevas en una mano ves lo que está oculto (puentes y el Tamborilero). */
public class EchoLensItem extends Item {

    public EchoLensItem(Properties properties) {
        super(properties);
    }

    public static boolean isHolding(@Nullable Player player) {
        if (player == null) return false;
        return player.getMainHandItem().is(ModItems.ECHO_LENS.get()) || player.getOffhandItem().is(ModItems.ECHO_LENS.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sugar.echo_lens.desc").withStyle(ChatFormatting.AQUA));
    }
}
