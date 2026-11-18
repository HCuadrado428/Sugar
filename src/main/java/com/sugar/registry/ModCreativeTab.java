package com.sugar.registry;

import com.sugar.SugarMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SugarMod.MODID);

    public static final RegistryObject<CreativeModeTab> SUGAR = TABS.register("sugar", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.sugar"))
            .icon(() -> new ItemStack(ModItems.BOSS_ALTAR.get()))
            .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private ModCreativeTab() {}
}
