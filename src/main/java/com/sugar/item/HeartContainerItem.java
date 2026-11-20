package com.sugar.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Contenedor de corazón: lo suelta cada jefe al caer, uno por jugador. Al usarlo da un corazón
 * más de vida máxima, para siempre (también tras morir). Como mucho uno por templo: tres en total.
 */
public class HeartContainerItem extends Item {
    public static final String TAG = "sugar_hearts";
    public static final int MAX = 3;
    private static final UUID MODIFIER = UUID.fromString("5b0e0f7c-2a4d-4b8e-9d51-3f6c1a7e9b21");

    public HeartContainerItem(Properties properties) {
        super(properties);
    }

    public static int hearts(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(TAG);
    }

    /** Aplica los corazones guardados (al usar uno, al entrar y al reaparecer). */
    public static void apply(Player player) {
        AttributeInstance hp = player.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        hp.removeModifier(MODIFIER);
        int n = hearts(player);
        if (n > 0) hp.addPermanentModifier(new AttributeModifier(MODIFIER, "Sugar heart containers", 2.0 * n, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        int n = hearts(player);
        if (n >= MAX) {
            player.displayClientMessage(Component.literal("Ya tienes todos los corazones de los templos.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putInt(TAG, n + 1);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        apply(player);
        player.heal(2.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.literal("❤ ¡Tu vida máxima aumenta!").withStyle(ChatFormatting.RED), true);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Úsalo para ganar un corazón más de vida (máx. " + MAX + ").").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }

    /** Lo reparte a cada jugador del templo: al inventario o, si está lleno, a sus pies. */
    public static void reward(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }
}
