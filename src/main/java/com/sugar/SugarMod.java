package com.sugar;

import com.mojang.logging.LogUtils;
import com.sugar.registry.ModBlocks;
import com.sugar.registry.ModCreativeTab;
import com.sugar.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SugarMod.MODID)
public class SugarMod {
    public static final String MODID = "sugar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SugarMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTab.TABS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SugarConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
