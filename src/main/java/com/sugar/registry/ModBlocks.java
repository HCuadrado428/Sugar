package com.sugar.registry;

import com.sugar.SugarMod;
import com.sugar.block.AshStatueBlock;
import com.sugar.block.BombBushBlock;
import com.sugar.block.BossAltarBlock;
import com.sugar.block.FireGeyserBlock;
import com.sugar.block.CheckpointBlock;
import com.sugar.block.CrumblingBlock;
import com.sugar.block.EchoDrumBlock;
import com.sugar.block.EchoGuideBlock;
import com.sugar.block.EchoPlatformBlock;
import com.sugar.block.LensPedestalBlock;
import com.sugar.block.EventDoorBlock;
import com.sugar.block.EventSwitchBlock;
import com.sugar.block.MagmaRiserBlock;
import com.sugar.block.MagmaVentBlock;
import com.sugar.block.RisingMagmaBlock;
import com.sugar.block.RockfallBlock;
import com.sugar.block.SupplyCrateBlock;
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

    // ---------- Corazón del Volcán ----------

    public static final RegistryObject<Block> CRUMBLING_BASALT = BLOCKS.register("crumbling_basalt",
            () -> new CrumblingBlock(eventBlock(MapColor.COLOR_BLACK).sound(SoundType.BASALT).noOcclusion()));

    public static final RegistryObject<Block> RISING_MAGMA = BLOCKS.register("rising_magma",
            () -> new RisingMagmaBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noCollission()
                    .lightLevel(s -> 13)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, e) -> false)));

    public static final RegistryObject<Block> MAGMA_RISER = BLOCKS.register("magma_riser",
            () -> new MagmaRiserBlock(eventBlock(MapColor.NETHER).sound(SoundType.STONE).lightLevel(s -> 9)));

    public static final RegistryObject<Block> MAGMA_VENT = BLOCKS.register("magma_vent",
            () -> new MagmaVentBlock(eventBlock(MapColor.FIRE).sound(SoundType.STONE).lightLevel(s -> 12)));

    public static final RegistryObject<Block> ROCKFALL = BLOCKS.register("rockfall",
            () -> new RockfallBlock(eventBlock(MapColor.COLOR_BLACK).sound(SoundType.BASALT)));

    public static final RegistryObject<Block> EVENT_SWITCH = BLOCKS.register("event_switch",
            () -> new EventSwitchBlock(eventBlock(MapColor.COLOR_PURPLE).sound(SoundType.STONE)
                    .lightLevel(s -> s.getValue(EventSwitchBlock.ACTIVE) ? 12 : 3)));

    public static final RegistryObject<Block> SUPPLY_CRATE = BLOCKS.register("supply_crate",
            () -> new SupplyCrateBlock(eventBlock(MapColor.WOOD).sound(SoundType.WOOD)));

    // ---------- Las Fauces de Ceniza ----------

    public static final RegistryObject<Block> BOMB_BUSH = BLOCKS.register("bomb_bush",
            () -> new BombBushBlock(eventBlock(MapColor.COLOR_ORANGE).sound(SoundType.AZALEA).noOcclusion().noCollission()
                    .lightLevel(s -> s.getValue(BombBushBlock.GROWN) ? 6 : 0)));

    public static final RegistryObject<Block> ASH_STATUE = BLOCKS.register("ash_statue",
            () -> new AshStatueBlock(eventBlock(MapColor.COLOR_GRAY).sound(SoundType.DEEPSLATE_BRICKS).noOcclusion()));

    public static final RegistryObject<Block> FIRE_GEYSER = BLOCKS.register("fire_geyser",
            () -> new FireGeyserBlock(eventBlock(MapColor.COLOR_BLACK).sound(SoundType.BASALT).lightLevel(s -> 7)));

    // ---------- Santuario del Eco ----------

    public static final RegistryObject<Block> ECHO_DRUM = BLOCKS.register("echo_drum",
            () -> new EchoDrumBlock(eventBlock(MapColor.COLOR_BROWN).sound(SoundType.WOOD)));

    public static final RegistryObject<Block> ECHO_GONG = BLOCKS.register("echo_gong",
            () -> new EchoGuideBlock(eventBlock(MapColor.GOLD).sound(SoundType.METAL).lightLevel(s -> 6)));

    public static final RegistryObject<Block> LENS_PEDESTAL = BLOCKS.register("lens_pedestal",
            () -> new LensPedestalBlock(eventBlock(MapColor.COLOR_CYAN).sound(SoundType.AMETHYST).noOcclusion()
                    .lightLevel(s -> s.getValue(LensPedestalBlock.HAS_LENS) ? 10 : 2)));

    public static final RegistryObject<Block> ECHO_PLATFORM = BLOCKS.register("echo_platform",
            () -> new EchoPlatformBlock(eventBlock(MapColor.NONE).sound(SoundType.AMETHYST).noOcclusion()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    /** Solo para dibujar la plataforma oculta cuando se lleva la lente (no tiene objeto). */
    public static final RegistryObject<Block> ECHO_PLATFORM_SHOWN = BLOCKS.register("echo_platform_shown",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().noLootTable().strength(-1.0F, 3600000.0F)));

    // ---------- Modo evento (todas las mazmorras) ----------

    public static final RegistryObject<Block> GROUP_PLATE = BLOCKS.register("group_plate",
            () -> new com.sugar.block.GroupPlateBlock(eventBlock(MapColor.COLOR_LIGHT_GREEN).sound(SoundType.STONE)
                    .lightLevel(s -> s.getValue(com.sugar.block.GroupPlateBlock.LIT) ? 12 : 3)));

    public static final RegistryObject<Block> WAVE_ALTAR = BLOCKS.register("wave_altar",
            () -> new com.sugar.block.WaveAltarBlock(eventBlock(MapColor.COLOR_RED).sound(SoundType.STONE)
                    .lightLevel(s -> s.getValue(com.sugar.block.WaveAltarBlock.STATE) == 1 ? 15 : 6)));

    public static final RegistryObject<Block> BOSS_BELL = BLOCKS.register("boss_bell",
            () -> new com.sugar.block.BossBellBlock(eventBlock(MapColor.GOLD).sound(SoundType.ANVIL).lightLevel(s -> 8)));

    public static final RegistryObject<Block> RELIC = BLOCKS.register("relic",
            () -> new com.sugar.block.RelicBlock(eventBlock(MapColor.COLOR_PURPLE).sound(SoundType.AMETHYST).noOcclusion()
                    .lightLevel(s -> s.getValue(com.sugar.block.RelicBlock.FOUND) ? 2 : 11)));

    public static final RegistryObject<Block> TREASURE_CRATE = BLOCKS.register("treasure_crate",
            () -> new com.sugar.block.TreasureCrateBlock(eventBlock(MapColor.GOLD).sound(SoundType.WOOD).lightLevel(s -> 7)));

    // ---------- Templo al estilo Ocarina (llaves, cerraduras, martillo) ----------

    public static final RegistryObject<Block> KEY_CHEST = BLOCKS.register("key_chest",
            () -> new com.sugar.block.KeyChestBlock(eventBlock(MapColor.WOOD).sound(SoundType.WOOD).noOcclusion()));

    public static final RegistryObject<Block> BIG_CHEST = BLOCKS.register("big_chest",
            () -> new com.sugar.block.BigChestBlock(eventBlock(MapColor.GOLD).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> 6)));

    public static final RegistryObject<Block> LOCKED_DOOR = BLOCKS.register("locked_door",
            () -> new com.sugar.block.LockedDoorBlock(eventBlock(MapColor.METAL).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> BOSS_DOOR = BLOCKS.register("boss_door",
            () -> new com.sugar.block.LockedDoorBlock(eventBlock(MapColor.COLOR_RED).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 4)));

    public static final RegistryObject<Block> CRACKED_WALL = BLOCKS.register("cracked_wall",
            () -> new com.sugar.block.CrackedWallBlock(eventBlock(MapColor.COLOR_BLACK).sound(SoundType.DEEPSLATE_BRICKS).noOcclusion()));

    public static final RegistryObject<Block> HAMMER_SWITCH = BLOCKS.register("hammer_switch",
            () -> new com.sugar.block.HammerSwitchBlock(eventBlock(MapColor.COLOR_PURPLE).sound(SoundType.STONE)
                    .lightLevel(s -> s.getValue(EventSwitchBlock.ACTIVE) ? 13 : 4)));

    public static final RegistryObject<Block> MAGMA_LAKE = BLOCKS.register("magma_lake",
            () -> new com.sugar.block.DrainableMagmaBlock(eventBlock(MapColor.FIRE).noOcclusion().noCollission()
                    .lightLevel(s -> s.getValue(com.sugar.block.EventDoorBlock.OPEN) ? 0 : 15)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    public static final RegistryObject<Block> GUARDIAN_NEST = BLOCKS.register("guardian_nest",
            () -> new com.sugar.block.GuardianNestBlock(eventBlock(MapColor.NONE).noCollission().noOcclusion()));

    private ModBlocks() {}
}
