package com.sugar.registry;

import com.sugar.SugarMod;
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

    private static RegistryObject<Item> blockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().rarity(Rarity.EPIC)));
    }

    private ModItems() {}
}
