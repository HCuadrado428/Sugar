package com.sugar;

import com.mojang.logging.LogUtils;
import com.sugar.registry.ModBlockEntities;
import com.sugar.registry.ModBlocks;
import com.sugar.registry.ModCreativeTab;
import com.sugar.registry.ModEntities;
import com.sugar.registry.ModItems;
import com.sugar.temple.TempleRegistry;
import com.sugar.temple.TempleType;
import com.sugar.temple.gen.AshTempleGenerator;
import com.sugar.temple.gen.VolcanoTempleGenerator;
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
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModCreativeTab.TABS.register(modBus);

        TempleRegistry.registerBoss(TempleType.VOLCAN, ModEntities.MAGMA_SERPENT);
        TempleRegistry.registerGenerator(TempleType.VOLCAN, new VolcanoTempleGenerator());
        TempleRegistry.registerBoss(TempleType.CENIZA, ModEntities.ASH_BEAST);
        TempleRegistry.registerGenerator(TempleType.CENIZA, new AshTempleGenerator());

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SugarConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
