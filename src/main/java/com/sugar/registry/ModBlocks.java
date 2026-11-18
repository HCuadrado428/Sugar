package com.sugar.registry;

import com.sugar.SugarMod;
import com.sugar.block.BossAltarBlock;
import com.sugar.block.CheckpointBlock;
import com.sugar.block.EventDoorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SugarMod.MODID);

    /** Bloques de evento: irrompibles en supervivencia, solo los admins en creativo pueden quitarlos. */
    private static BlockBehaviour.Properties eventBlock(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((s, l, p, e) -> false);
    }

    public static final RegistryObject<Block> CHECKPOINT_STONE = BLOCKS.register("checkpoint_stone",
            () -> new CheckpointBlock(eventBlock(MapColor.STONE).sound(SoundType.STONE).lightLevel(s -> 7)));

    public static final RegistryObject<Block> EVENT_DOOR = BLOCKS.register("event_door",
            () -> new EventDoorBlock(eventBlock(MapColor.METAL).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> BOSS_ALTAR = BLOCKS.register("boss_altar",
            () -> new BossAltarBlock(eventBlock(MapColor.COLOR_BLACK).sound(SoundType.STONE).lightLevel(s -> 10)));

    private ModBlocks() {}
}
