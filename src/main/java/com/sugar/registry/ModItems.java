package com.sugar.registry;

import com.sugar.SugarMod;
import com.sugar.item.ObsidianHammerItem;
import com.sugar.item.TrophyItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SugarMod.MODID);

    public static final RegistryObject<Item> CHECKPOINT_STONE = blockItem("checkpoint_stone", ModBlocks.CHECKPOINT_STONE);
    public static final RegistryObject<Item> EVENT_DOOR = blockItem("event_door", ModBlocks.EVENT_DOOR);
    public static final RegistryObject<Item> BOSS_ALTAR = blockItem("boss_altar", ModBlocks.BOSS_ALTAR);

    public static final RegistryObject<Item> CRUMBLING_BASALT = blockItem("crumbling_basalt", ModBlocks.CRUMBLING_BASALT);
    public static final RegistryObject<Item> MAGMA_RISER = blockItem("magma_riser", ModBlocks.MAGMA_RISER);
    public static final RegistryObject<Item> MAGMA_VENT = blockItem("magma_vent", ModBlocks.MAGMA_VENT);
    public static final RegistryObject<Item> ROCKFALL = blockItem("rockfall", ModBlocks.ROCKFALL);
    public static final RegistryObject<Item> EVENT_SWITCH = blockItem("event_switch", ModBlocks.EVENT_SWITCH);
    public static final RegistryObject<Item> SUPPLY_CRATE = blockItem("supply_crate", ModBlocks.SUPPLY_CRATE);

    public static final RegistryObject<Item> OBSIDIAN_HAMMER = ITEMS.register("obsidian_hammer",
            () -> new ObsidianHammerItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> SERPENT_SCALE = ITEMS.register("serpent_scale",
            () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), "item.sugar.serpent_scale.desc"));

    private static RegistryObject<Item> blockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().rarity(Rarity.EPIC)));
    }

    private ModItems() {}
}
