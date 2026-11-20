package com.sugar.registry;

import com.sugar.SugarMod;
import com.sugar.item.AshBombItem;
import com.sugar.item.EchoLensItem;
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

    public static final RegistryObject<Item> BOMB_BUSH = blockItem("bomb_bush", ModBlocks.BOMB_BUSH);
    public static final RegistryObject<Item> ASH_STATUE = blockItem("ash_statue", ModBlocks.ASH_STATUE);
    public static final RegistryObject<Item> FIRE_GEYSER = blockItem("fire_geyser", ModBlocks.FIRE_GEYSER);

    public static final RegistryObject<Item> ASH_BOMB = ITEMS.register("ash_bomb",
            () -> new AshBombItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ASH_FANG = ITEMS.register("ash_fang",
            () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), "item.sugar.ash_fang.desc"));

    public static final RegistryObject<Item> ECHO_DRUM = blockItem("echo_drum", ModBlocks.ECHO_DRUM);
    public static final RegistryObject<Item> ECHO_GONG = blockItem("echo_gong", ModBlocks.ECHO_GONG);
    public static final RegistryObject<Item> LENS_PEDESTAL = blockItem("lens_pedestal", ModBlocks.LENS_PEDESTAL);
    public static final RegistryObject<Item> ECHO_PLATFORM = blockItem("echo_platform", ModBlocks.ECHO_PLATFORM);

    public static final RegistryObject<Item> ECHO_LENS = ITEMS.register("echo_lens",
            () -> new EchoLensItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ECHO_EYE = ITEMS.register("echo_eye",
            () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), "item.sugar.echo_eye.desc"));

    public static final RegistryObject<Item> GROUP_PLATE = blockItem("group_plate", ModBlocks.GROUP_PLATE);
    public static final RegistryObject<Item> WAVE_ALTAR = blockItem("wave_altar", ModBlocks.WAVE_ALTAR);
    public static final RegistryObject<Item> BOSS_BELL = blockItem("boss_bell", ModBlocks.BOSS_BELL);
    public static final RegistryObject<Item> RELIC = blockItem("relic", ModBlocks.RELIC);
    public static final RegistryObject<Item> TREASURE_CRATE = blockItem("treasure_crate", ModBlocks.TREASURE_CRATE);

    public static final RegistryObject<Item> KEY_CHEST = blockItem("key_chest", ModBlocks.KEY_CHEST);
    public static final RegistryObject<Item> BIG_CHEST = blockItem("big_chest", ModBlocks.BIG_CHEST);
    public static final RegistryObject<Item> LOCKED_DOOR = blockItem("locked_door", ModBlocks.LOCKED_DOOR);
    public static final RegistryObject<Item> BOSS_DOOR = blockItem("boss_door", ModBlocks.BOSS_DOOR);
    public static final RegistryObject<Item> CRACKED_WALL = blockItem("cracked_wall", ModBlocks.CRACKED_WALL);
    public static final RegistryObject<Item> HAMMER_SWITCH = blockItem("hammer_switch", ModBlocks.HAMMER_SWITCH);
    public static final RegistryObject<Item> MAGMA_LAKE = blockItem("magma_lake", ModBlocks.MAGMA_LAKE);
    public static final RegistryObject<Item> GUARDIAN_NEST = blockItem("guardian_nest", ModBlocks.GUARDIAN_NEST);
    public static final RegistryObject<Item> PUSH_BLOCK = blockItem("push_block", ModBlocks.PUSH_BLOCK);
    public static final RegistryObject<Item> PUSH_TARGET = blockItem("push_target", ModBlocks.PUSH_TARGET);
    public static final RegistryObject<Item> BRAZIER = blockItem("brazier", ModBlocks.BRAZIER);
    public static final RegistryObject<Item> EYE_SWITCH = blockItem("eye_switch", ModBlocks.EYE_SWITCH);
    public static final RegistryObject<Item> BLINK_PLATFORM = blockItem("blink_platform", ModBlocks.BLINK_PLATFORM);
    /** Llaves para admins: en creativo, clic derecho en una cerradura para abrirla sin el grupo. */
    public static final RegistryObject<Item> HEART_CONTAINER = ITEMS.register("heart_container",
            () -> new com.sugar.item.HeartContainerItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> SMALL_KEY = ITEMS.register("small_key",
            () -> new TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "item.sugar.small_key.desc"));
    public static final RegistryObject<Item> BOSS_KEY = ITEMS.register("boss_key",
            () -> new TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "item.sugar.boss_key.desc"));

    private static RegistryObject<Item> blockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().rarity(Rarity.EPIC)));
    }

    private ModItems() {}
}
