package com.sugar.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Martillo de Obsidiana: golpear con él la cabeza de la Sierpe la aturde. No sirve como arma. */
public class ObsidianHammerItem extends Item {

    public ObsidianHammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sugar.obsidian_hammer.desc").withStyle(ChatFormatting.GRAY));
    }
}
