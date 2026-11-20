package com.sugar.temple.gen;

import com.sugar.block.BigChestBlock;
import com.sugar.block.EventSwitchBlock;
import com.sugar.block.FireGeyserBlock;
import com.sugar.block.GroupPlateBlock;
import com.sugar.block.WaveAltarBlock;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Supplier;

import static com.sugar.temple.TempleObjectives.*;

/**
 * Las Fauces de Ceniza, al estilo Ocarina of Time (25-50 jugadores). El admin está en el campamento minero;
 * 100 bloques al este está la montaña con su boca de piedra.
 *
 * Dentro: la Gran Garganta (pozo circular, dos pisos y un lago de lava al fondo) y alrededor:
 *   Piso 2 (y 0):   Vestíbulo · Galería de las estatuas (muros que se vuelan con bombas → Llave 1, reliquia 1)
 *                   · Nido de crías (oleadas → Gran Cofre: bombas para todos) · Sala de los sellos (🔒1 → Llave 2)
 *   Piso 1 (y -10): Mina abandonada (muro de estatuas → Llave 3, brújula, reliquia 2) · Sala de los Géiseres (🔒2+3,
 *                   8 placas: vacía el lago y da la Llave 4) · Polvorín (🔒4, 6 interruptores que se encienden con
 *                   bombas → Llave del Jefe) · Cámara oculta (muro de estatuas, reliquia 3, tesoro canal 8)
 *   Fondo (y -20):  Puerta del Jefe → túnel → caverna de la Gran Bestia (suelo y -24).
 */
public class AshTempleGenerator implements TempleGenerator {
    private static final int CX = 100, CZ = 5, ARENA_R = 20, DOME_H = 17;
    /** Distancia del campamento base a la entrada del templo. */
    public static final int APPROACH = 100;
    private static final int HX = 26, HZ = 5, F2 = 0, F1 = -10, BOTTOM = -20, LAIR_Y = -24;

    @Override
    public Result generate(ServerLevel level, BlockPos feet) {
        // El admin está en el campamento base; el templo empieza 100 bloques al este
        StructureBuilder b = new StructureBuilder(level, feet.offset(APPROACH, -1, -5));
        Supplier<BlockState> wall = b.mix(6, Blocks.DEEPSLATE_BRICKS, 2, Blocks.CRACKED_DEEPSLATE_BRICKS, 2, Blocks.COBBLED_DEEPSLATE);
        Supplier<BlockState> floor = b.mix(5, Blocks.POLISHED_DEEPSLATE, 3, Blocks.DEEPSLATE_TILES, 1, Blocks.BLACKSTONE);
        Supplier<BlockState> rock = b.mix(5, Blocks.DEEPSLATE, 3, Blocks.BASALT, 2, Blocks.BLACKSTONE, 1, Blocks.TUFF);
        OotKit k = new OotKit(b, HX, HZ, BOTTOM, 8, new int[]{F2, F1}, wall, floor);

        b.oy = LAIR_Y;
        cavern(b, rock, floor);
        b.oy = 0;
        lobby(b, wall, floor);
        hub(b, k);
        gallery(b, k);
        nest(b, k);
        seals(b, k);
        mine(b, k, rock);
        geysers(b, k);
        powder(b, k);
        hidden(b, k);
        tunnel(b, wall, floor);
        OotKit.nests(b, new int[][]{{26, F2 + 4, -12, 1}, {20, F2 + 1, -18, 3}, {HX, F2 - 4, HZ, 1}, {51, F2 + 1, 0, 3},
                {30, F1 + 4, -25, 1}, {20, F1 + 1, -13, 3}, {54, F1 + 4, 5, 1}, {26, F1 + 1, 25, 3}});

        Supplier<BlockState> ash = b.mix(3, Blocks.TUFF, 3, Blocks.LIGHT_GRAY_CONCRETE_POWDER, 2, Blocks.GRAVEL, 2, Blocks.BASALT, 1, Blocks.COARSE_DIRT);
        Approach.drape(b, b.bounds(), 10, rock, ash);
        outside(b, rock, ash, floor);
        return new Result(b.bounds(), feet);
    }

    private static BlockState geyser(int phase) {
        return ModBlocks.FIRE_GEYSER.get().defaultBlockState().setValue(FireGeyserBlock.PHASE, phase % 4);
    }

    // ---------- Vestíbulo y Gran Garganta ----------

    private void lobby(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(0, 0, 0, 10, 6, 10, wall, floor);
        b.set(5, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{2, 2}, {8, 2}, {2, 8}, {8, 8}}) b.set(c[0], 6, c[1], Blocks.SHROOMLIGHT);
        b.set(2, 1, 9, ModBlocks.SUPPLY_CRATE.get());
        b.set(8, 1, 9, ModBlocks.SUPPLY_CRATE.get());
        b.air(0, 1, 4, 0, 3, 6);              // se entra por las Fauces (la entrada de fuera está en el campamento)
        b.fill(10, 1, 4, 10, 3, 6, OotKit.door(0));
    }

    private void hub(StructureBuilder b, OotKit k) {
        k.hub(Blocks.SHROOMLIGHT.defaultBlockState(), BOTTOM);
        k.ramp(195, 260, F2, F1);
        k.ramp(14, 80, F1, BOTTOM);
        k.corridor(10, 4, 14, 6, F2);
        // Lago de lava del fondo (se vacía con las placas de la Sala de los Géiseres)
        BlockState lake = ModBlocks.MAGMA_LAKE.get().defaultBlockState().setValue(com.sugar.block.EventDoorBlock.CHANNEL, V_BRIDGE);
        for (int x = HX - 12; x <= HX + 12; x++)
            for (int z = HZ - 12; z <= HZ + 12; z++)
                if (k.dist(x, z) <= OotKit.IN)
                    for (int y = BOTTOM + 1; y <= BOTTOM + 3; y++)
                        if (b.level.getBlockState(b.abs(x, y, z)).isAir()) b.set(x, y, z, lake);
        b.fill(HX + 12, BOTTOM + 1, HZ - 1, HX + 12, BOTTOM + 4, HZ + 1, OotKit.bossDoor());
        b.fill(HX + 12, BOTTOM + 5, HZ - 2, HX + 12, BOTTOM + 5, HZ + 2, Blocks.GOLD_BLOCK.defaultBlockState());
        // Matas de pólvora en los balcones
        for (double a : new double[]{40, 140, 300}) {
            double r = Math.toRadians(a);
            b.set(HX + (int) Math.round(Math.cos(r) * 10), F2 + 1, HZ + (int) Math.round(Math.sin(r) * 10), ModBlocks.BOMB_BUSH.get());
            b.set(HX + (int) Math.round(Math.cos(r + 0.5) * 10), F1 + 1, HZ + (int) Math.round(Math.sin(r + 0.5) * 10), ModBlocks.BOMB_BUSH.get());
        }
    }

    // ---------- Piso 2 ----------

    private void gallery(StructureBuilder b, OotKit k) {
        b.room(14, F2, -30, 38, F2 + 8, -9, k.wall, k.floor);
        k.corridor(25, -10, 27, -7, F2);
        BlockState statue = ModBlocks.ASH_STATUE.get().defaultBlockState();
        for (int z : new int[]{-15, -22}) b.fill(15, F2 + 1, z, 37, F2 + 7, z, statue);
        for (int z : new int[]{-11, -18, -25}) { b.set(16, F2 + 1, z, ModBlocks.BOMB_BUSH.get()); b.set(36, F2 + 1, z, ModBlocks.BOMB_BUSH.get()); }
        int ph = 0;
        for (int x = 18; x <= 34; x += 4) b.set(x, F2, -19, geyser(ph++));
        b.set(26, F2 + 1, -28, OotKit.keyChest(1));
        EventRooms.relic(b, 16, F2 + 1, -28);
        for (int[] l : new int[][]{{20, -12}, {32, -12}, {26, -19}, {20, -26}, {32, -26}}) b.set(l[0], F2 + 8, l[1], Blocks.SHROOMLIGHT);
    }

    private void nest(StructureBuilder b, OotKit k) {
        b.room(14, F2, 19, 38, F2 + 9, 40, k.wall, k.floor);
        k.corridor(25, 17, 27, 20, F2);
        k.doorZ(25, F2 + 1, 19, OotKit.door(V_FORGE_GATE));
        b.set(26, F2, 30, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(26, F2 + 1, 30, ModBlocks.WAVE_ALTAR.get().defaultBlockState().setValue(WaveAltarBlock.CHANNEL, V_FORGE));
        for (int[] c : new int[][]{{19, 24}, {32, 24}, {19, 35}, {32, 35}}) b.fill(c[0], F2 + 1, c[1], c[0] + 1, F2 + 3, c[1] + 1, Blocks.POLISHED_BASALT.defaultBlockState());
        for (int[] e : new int[][]{{16, 21}, {36, 21}, {16, 38}, {36, 38}}) b.set(e[0], F2 + 1, e[1], Blocks.SNIFFER_EGG);
        b.set(26, F2 + 1, 38, OotKit.bigChest(BigChestBlock.BOMBS));
        k.cage(26, F2 + 1, 38, V_FORGE);
        for (int[] l : new int[][]{{20, 30}, {32, 30}, {26, 23}, {26, 36}}) b.set(l[0], F2 + 9, l[1], Blocks.SHROOMLIGHT);
    }

    private void seals(StructureBuilder b, OotKit k) {
        b.room(40, F2, -6, 62, F2 + 9, 16, k.wall, k.floor);
        k.corridor(38, 4, 40, 6, F2);
        k.doorX(40, F2 + 1, 4, OotKit.lock(V_LOCK_SEALS, 1));
        BlockState step = Blocks.POLISHED_BASALT.defaultBlockState();
        b.fill(42, F2 + 1, -5, 42, F2 + 1, -4, step);
        b.fill(43, F2 + 1, -5, 57, F2 + 2, -4, step);
        for (int x = 42; x <= 57; x++) b.fill(x, F2 + 1, 14, x, Math.min(F2 + 5, F2 + 1 + (x - 42)), 15, step);
        BlockState seal = ModBlocks.EVENT_SWITCH.get().defaultBlockState().setValue(EventSwitchBlock.CHANNEL, V_SEALS);
        b.set(56, F2 + 3, -5, seal);
        b.set(56, F2 + 6, 15, seal);
        b.set(61, F2 + 1, 5, seal);
        for (int z : new int[]{-2, 12}) b.fill(48, F2 + 1, z, 54, F2 + 1, z, Blocks.RAIL.defaultBlockState());
        b.set(51, F2 + 1, 5, OotKit.keyChest(2));
        k.cage(51, F2 + 1, 5, V_SEALS);
        for (int[] l : new int[][]{{46, 5}, {57, 5}, {51, -1}, {51, 11}}) b.set(l[0], F2 + 9, l[1], Blocks.SHROOMLIGHT);
    }

    // ---------- Piso 1 ----------

    private void mine(StructureBuilder b, OotKit k, Supplier<BlockState> rock) {
        b.room(14, F1 - 2, -30, 38, F1 + 9, -9, k.wall, k.floor);
        b.fill(15, F1 - 1, -29, 37, F1, -10, k.floor);
        k.corridor(25, -10, 27, -7, F1);
        b.fill(25, F1 + 1, -9, 27, F1 + 3, -9, ModBlocks.ASH_STATUE.get().defaultBlockState());   // muro que hay que volar
        // Foso de lava de lado a lado con un puente que se hunde
        b.fill(15, F1 - 1, -22, 37, F1, -17, ModBlocks.RISING_MAGMA.get().defaultBlockState());
        b.fill(25, F1, -22, 27, F1, -17, ModBlocks.CRUMBLING_BASALT.get().defaultBlockState());
        for (int x = 16; x <= 36; x++) b.set(x, F1 + 1, -12, Blocks.RAIL);
        for (int z = -16; z >= -28; z--) b.set(16, F1 + 1, z, Blocks.RAIL);
        for (int x : new int[]{20, 32}) { b.set(x, F1 + 9, -20, ModBlocks.ROCKFALL.get()); b.fill(x, F1 + 1, -14, x, F1 + 8, -14, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()); }
        b.set(26, F1 + 1, -28, OotKit.keyChest(3));
        b.set(20, F1 + 1, -28, OotKit.bigChest(BigChestBlock.COMPASS));
        EventRooms.relic(b, 36, F1 + 1, -28);
        b.set(26, F1, -11, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] l : new int[][]{{20, -11}, {32, -11}, {26, -25}}) b.set(l[0], F1 + 9, l[1], Blocks.LANTERN);
    }

    private void geysers(StructureBuilder b, OotKit k) {
        int pit = F1 - 4;
        b.room(40, pit, -6, 62, F1 + 9, 16, k.wall, k.floor);
        b.fill(41, pit + 1, -5, 61, pit + 2, 15, ModBlocks.RISING_MAGMA.get().defaultBlockState());
        k.corridor(38, 4, 40, 6, F1);
        k.doorX(40, F1 + 1, 4, OotKit.lock(V_LOCK_BRIDGE, 2 | 4));
        b.fill(41, pit + 1, -5, 43, F1, 15, k.floor);      // rellano de entrada
        b.fill(58, pit + 1, -5, 61, F1, 15, k.floor);      // rellano del fondo
        b.set(42, F1, 5, ModBlocks.CHECKPOINT_STONE.get());
        BlockState plate = ModBlocks.GROUP_PLATE.get().defaultBlockState().setValue(GroupPlateBlock.CHANNEL, V_BRIDGE);
        int ph = 0;
        for (int cx : new int[]{47, 54})
            for (int cz : new int[]{-3, 2, 8, 13}) {
                b.fill(cx - 1, pit + 1, cz - 1, cx + 1, F1, cz + 1, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                b.set(cx, F1, cz, plate);
                b.set(cx - 1, F1, cz - 1, geyser(ph++));
                b.set(cx + 1, F1, cz + 1, geyser(ph + 1));
            }
        for (int cz : new int[]{-3, 2, 8, 13})
            for (int sx : new int[]{50, 52, 57}) b.fill(sx, pit + 1, cz, sx, F1, cz, Blocks.POLISHED_BASALT.defaultBlockState());
        b.set(60, F1 + 1, 5, OotKit.keyChest(4));
        k.cage(60, F1 + 1, 5, V_BRIDGE);
        for (int[] l : new int[][]{{47, 5}, {54, 5}, {44, -3}, {44, 13}}) b.set(l[0], F1 + 9, l[1], Blocks.SHROOMLIGHT);
    }

    private void powder(StructureBuilder b, OotKit k) {
        b.room(14, F1, 19, 38, F1 + 9, 40, k.wall, k.floor);
        k.corridor(25, 17, 27, 20, F1);
        k.doorZ(25, F1 + 1, 19, OotKit.lock(V_LOCK_HAMMER, 8));
        BlockState sw = ModBlocks.HAMMER_SWITCH.get().defaultBlockState().setValue(EventSwitchBlock.CHANNEL, V_SWITCHES);
        for (int[] s : new int[][]{{15, 30}, {37, 30}, {20, 21}, {32, 21}, {20, 39}, {32, 39}}) b.set(s[0], F1 + 1, s[1], sw);
        for (int[] s : new int[][]{{17, 25}, {35, 25}, {17, 35}, {35, 35}, {22, 30}, {30, 30}, {26, 23}, {26, 37}}) b.set(s[0], F1 + 1, s[1], ModBlocks.BOMB_BUSH.get());
        int ph = 0;
        for (int[] g : new int[][]{{20, 27}, {32, 27}, {20, 33}, {32, 33}}) b.set(g[0], F1, g[1], geyser(ph++));
        for (int[] c : new int[][]{{15, 21}, {37, 21}, {15, 39}, {37, 39}}) b.set(c[0], F1 + 1, c[1], Blocks.BARREL);
        b.set(26, F1 + 1, 30, OotKit.bigChest(BigChestBlock.BOSS_KEY));
        k.cage(26, F1 + 1, 30, V_SWITCHES);
        for (int[] l : new int[][]{{20, 24}, {32, 24}, {20, 36}, {32, 36}}) b.set(l[0], F1 + 9, l[1], Blocks.LANTERN);
    }

    private void hidden(StructureBuilder b, OotKit k) {
        b.room(-13, F1, -1, -1, F1 + 6, 11, k.wall, k.floor);          // tesoro
        for (int z : new int[]{2, 5, 8}) b.set(-11, F1 + 1, z, ModBlocks.TREASURE_CRATE.get());
        b.set(-12, F1 + 1, 0, Blocks.GOLD_BLOCK); b.set(-12, F1 + 1, 10, Blocks.GOLD_BLOCK);
        b.set(-7, F1 + 6, 5, Blocks.SHROOMLIGHT);
        b.room(-1, F1, -1, 11, F1 + 6, 11, k.wall, k.floor);           // cámara oculta, bajo el vestíbulo
        k.doorX(-1, F1 + 1, 4, OotKit.door(VAULT));
        k.corridor(11, 4, 14, 6, F1);
        b.fill(11, F1 + 1, 4, 11, F1 + 3, 6, ModBlocks.ASH_STATUE.get().defaultBlockState());
        EventRooms.relic(b, 0, F1 + 1, 0);
        b.set(5, F1 + 6, 5, Blocks.SHROOMLIGHT);
    }

    // ---------- Túnel y caverna de la Bestia ----------

    private void tunnel(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        for (int x = HX + 13; x <= CX - ARENA_R + 1; x++) {
            int y = Math.max(LAIR_Y, BOTTOM - (x - (HX + 13)));
            b.fill(x, y - 1, HZ - 3, x, y + 5, HZ + 3, wall);
            b.fill(x, y, HZ - 2, x, y, HZ + 2, floor);
            b.air(x, y + 1, HZ - 2, x, y + 4, HZ + 2);
            if (x % 8 == 0) { b.set(x, y + 4, HZ - 3, Blocks.SHROOMLIGHT); b.set(x, y + 4, HZ + 3, Blocks.SHROOMLIGHT); }
        }
        b.set(HX + 22, LAIR_Y, HZ, ModBlocks.CHECKPOINT_STONE.get());
    }

    private void cavern(StructureBuilder b, Supplier<BlockState> rock, Supplier<BlockState> floor) {
        int outer = ARENA_R + 3;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > outer) continue;
                for (int y = -3; y <= DOME_H + 2; y++) {
                    double dome = (d * d) / (double) (ARENA_R * ARENA_R) + (y * y) / (double) (DOME_H * DOME_H);
                    boolean inside = y >= 1 && dome < 1.0;
                    b.set(CX + dx, y, CZ + dz, inside ? Blocks.AIR.defaultBlockState() : rock.get());
                }
            }
        }
        // Suelo, foso de lava junto a la pared y puente de entrada
        BlockState lava = ModBlocks.RISING_MAGMA.get().defaultBlockState();
        for (int dx = -ARENA_R; dx <= ARENA_R; dx++) {
            for (int dz = -ARENA_R; dz <= ARENA_R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > ARENA_R) continue;
                if (d >= ARENA_R - 2.5) {
                    b.set(CX + dx, 0, CZ + dz, lava);
                    b.set(CX + dx, -1, CZ + dz, lava);
                } else {
                    b.set(CX + dx, 0, CZ + dz, d < 3.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                            : ((int) d) % 5 == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : floor.get());
                }
            }
        }
        b.fill(CX - ARENA_R - 3, 0, CZ - 2, CX - ARENA_R + 4, 0, CZ + 2, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());   // puente
        b.air(CX - ARENA_R - 3, 1, CZ - 2, CX - ARENA_R + 4, 3, CZ + 2);
        b.set(CX, 0, CZ, ModBlocks.BOSS_ALTAR.get());
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + Math.PI / 6;
            b.set(CX + (int) Math.round(Math.cos(a) * 14), 1, CZ + (int) Math.round(Math.sin(a) * 14), ModBlocks.BOMB_BUSH.get());
            b.set(CX + (int) Math.round(Math.cos(a - Math.PI / 6) * 9), 0, CZ + (int) Math.round(Math.sin(a - Math.PI / 6) * 9), ModBlocks.MAGMA_VENT.get());
        }
        // Luces en la cúpula
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            int y = 8;
            double r = ARENA_R * Math.sqrt(1 - (y * y) / (double) (DOME_H * DOME_H)) + 0.6;
            b.set(CX + (int) Math.round(Math.cos(a) * r), y, CZ + (int) Math.round(Math.sin(a) * r), Blocks.SHROOMLIGHT);
        }
        b.set(CX, DOME_H, CZ, Blocks.SHROOMLIGHT);
    }

    // ---------- 0. Fuera: el Desfiladero de Ceniza ----------

    /**
     * Campamento minero (donde está el admin) → llanura de ceniza con géiseres y matas de pólvora → muro de roca
     * con un desfiladero cerrado por estatuas (hay que volarlas con bombas) → grieta de lava con puente que se
     * hunde → las Fauces: la boca de piedra de la montaña, que lleva al vestíbulo.
     */
    private void outside(StructureBuilder b, Supplier<BlockState> rock, Supplier<BlockState> ash, Supplier<BlockState> floor) {
        int zc = 5, x0 = -APPROACH - 12, x1 = -9;
        // Valle: suelo de ceniza y paredes a los lados
        for (int x = x0; x <= x1; x++)
            for (int z = zc - 30; z <= zc + 30; z++) {
                for (int y = -3; y < 0; y++) b.set(x, y, z, rock.get());
                b.set(x, 0, z, ash.get());
                for (int y = 1; y <= 12; y++) if (!b.level.getBlockState(b.abs(x, y, z)).isAir()) b.set(x, y, z, Blocks.AIR.defaultBlockState());
                int edge = Math.min(z - (zc - 30), (zc + 30) - z);
                if (edge < 6) {
                    int h = 8 + (5 - edge) + (int) Math.round(Math.sin(x * 0.4 + z) * 1.5);
                    b.fill(x, 1, z, x, h, z, rock);
                    b.set(x, h + 1, z, ash.get());
                }
            }
        // Árboles quemados y matojos
        for (int k = 0; k < 26; k++) {
            int x = x0 + 14 + b.random.nextInt(x1 - x0 - 16), z = zc + (b.random.nextBoolean() ? 1 : -1) * (8 + b.random.nextInt(14));
            if (b.random.nextInt(3) == 0) { b.set(x, 1, z, Blocks.DEAD_BUSH); continue; }
            int h = 3 + b.random.nextInt(4);
            b.fill(x, 1, z, x, h, z, Blocks.DARK_OAK_LOG.defaultBlockState());
            b.set(x + 1, h - 1, z, Blocks.DARK_OAK_LOG); b.set(x, h, z - 1, Blocks.DARK_OAK_LOG);
        }
        Approach.camp(b, -APPROACH, 0, zc, 12, 12, () -> Blocks.SPRUCE_LOG.defaultBlockState(),
                b.mix(5, Blocks.COARSE_DIRT, 3, Blocks.GRAVEL, 2, Blocks.PODZOL),
                Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), Blocks.ORANGE_WOOL.defaultBlockState(), Blocks.CAMPFIRE.defaultBlockState(), Blocks.LANTERN.defaultBlockState());
        for (int x = -APPROACH - 10; x <= -APPROACH + 10; x++) b.set(x, 1, zc + 10, Blocks.RAIL);        // vía de la mina
        for (int[] p : new int[][]{{-APPROACH - 9, zc - 9}, {-APPROACH - 8, zc - 9}, {-APPROACH, zc - 10}}) b.set(p[0], 1, p[1], Blocks.BARREL);

        List<int[]> path = Approach.path(-APPROACH + 13, 0, x1, 0, zc, 9);
        Supplier<BlockState> trail = b.mix(4, Blocks.COARSE_DIRT, 2, Blocks.GRAVEL, 1, Blocks.TUFF);
        Approach.pave(b, path, 2, trail, rock, 5, 0);

        // 1. Llanura de los géiseres: filas que se alternan, y matas de pólvora para más adelante
        int k = 0;
        for (double s : new double[]{0.12, 0.17, 0.22}) {
            int[] c = Approach.at(path, s);
            for (int w = -2; w <= 2; w++)
                b.set(c[0], 0, c[2] + w, ModBlocks.FIRE_GEYSER.get().defaultBlockState().setValue(FireGeyserBlock.PHASE, (k * 2 + (w & 1)) % 4));
            k++;
        }
        for (double s : new double[]{0.27, 0.31, 0.35}) {
            int[] c = Approach.at(path, s);
            b.set(c[0], 1, c[2] - 3, ModBlocks.BOMB_BUSH.get());
            b.set(c[0], 1, c[2] + 3, ModBlocks.BOMB_BUSH.get());
        }
        int[] cp1 = Approach.at(path, 0.29);
        b.set(cp1[0], 0, cp1[2], ModBlocks.CHECKPOINT_STONE.get());

        // 2. Muro de roca de lado a lado del valle con un desfiladero; dentro, las estatuas cierran el paso
        int mx1 = Approach.at(path, 0.40)[0], mx2 = Approach.at(path, 0.62)[0];
        for (int x = mx1; x <= mx2; x++)
            for (int z = zc - 25; z <= zc + 25; z++) {
                int h = 10 + (int) Math.round(Math.sin(x * 0.3) * Math.cos(z * 0.2) * 2);
                b.fill(x, 1, z, x, h, z, rock);
                b.set(x, h + 1, z, ash.get());
            }
        for (int[] c : path) {
            if (c[0] < mx1 - 1 || c[0] > mx2 + 1) continue;
            for (int w = -2; w <= 2; w++) {
                b.set(c[0], 0, c[2] + w, trail.get());
                b.air(c[0], 1, c[2] + w, c[0], 13, c[2] + w);
            }
        }
        for (double s : new double[]{0.44, 0.48}) {
            int[] c = Approach.at(path, s);
            b.set(c[0], 1, c[2] - 2, ModBlocks.BOMB_BUSH.get());
            b.set(c[0], 1, c[2] + 2, ModBlocks.BOMB_BUSH.get());
        }
        int[] sw = Approach.at(path, 0.55);
        BlockState statue = ModBlocks.ASH_STATUE.get().defaultBlockState();
        for (int dx = 0; dx <= 1; dx++)
            for (int w = -3; w <= 3; w++) b.fill(sw[0] + dx, 1, sw[2] + w, sw[0] + dx, 5, sw[2] + w, statue);
        for (int w = -2; w <= 2; w += 2) b.set(sw[0] - 4, 12, sw[2] + w, Blocks.SHROOMLIGHT);

        // 3. Grieta de lava de lado a lado con un puente de basalto que se hunde
        int[] gc = Approach.at(path, 0.72);
        for (int dx = -1; dx <= 2; dx++)
            for (int z = zc - 24; z <= zc + 24; z++) {
                b.set(gc[0] + dx, -2, z, rock.get());
                b.fill(gc[0] + dx, -1, z, gc[0] + dx, 0, z, ModBlocks.RISING_MAGMA.get().defaultBlockState());
                b.air(gc[0] + dx, 1, z, gc[0] + dx, 3, z);
            }
        for (int dx = -1; dx <= 2; dx++)
            for (int w = -1; w <= 1; w++) b.set(gc[0] + dx, 0, gc[2] + w, ModBlocks.CRUMBLING_BASALT.get());
        int[] cp2 = Approach.at(path, 0.78);
        b.set(cp2[0], 0, cp2[2], ModBlocks.CHECKPOINT_STONE.get());

        // 4. Las Fauces: una cara de piedra en la ladera con la boca abierta hacia el vestíbulo
        int fx = -11;
        for (int z = zc - 11; z <= zc + 11; z++) Approach.fillAir(b, fx + 2, 0, z, -1, 15 - Math.abs(z - zc) / 2, z, rock);
        for (int z = zc - 11; z <= zc + 11; z++) {
            int h = 15 - Math.abs(z - zc) / 2;
            b.fill(fx, 0, z, fx + 1, h, z, Blocks.BLACKSTONE.defaultBlockState());
            b.set(fx - 1, h, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        b.fill(fx, 0, zc - 2, 0, 0, zc + 2, floor);
        b.air(fx - 1, 1, zc - 2, 0, 6, zc + 2);
        for (int z = zc - 3; z <= zc + 3; z++) { b.set(fx - 1, 7, z, Blocks.POLISHED_BLACKSTONE_BRICKS); b.set(fx - 1, 0, z, Blocks.POLISHED_BLACKSTONE_BRICKS); }
        for (int z = zc - 2; z <= zc + 2; z += 2) { b.set(fx - 1, 6, z, Blocks.BONE_BLOCK); b.set(fx - 1, 5, z, Blocks.BONE_BLOCK); }   // colmillos de arriba
        for (int z : new int[]{zc - 2, zc + 2}) b.set(fx - 1, 1, z, Blocks.BONE_BLOCK);                                                  // colmillos de abajo
        for (int dz : new int[]{-5, 4}) {                                                                                                // ojos
            b.fill(fx - 1, 10, zc + dz, fx - 1, 11, zc + dz + 1, Blocks.MAGMA_BLOCK.defaultBlockState());
            b.set(fx - 1, 10, zc + dz + (dz < 0 ? 1 : 0), Blocks.SHROOMLIGHT);
            b.fill(fx - 1, 12, zc + dz - 1, fx - 1, 12, zc + dz + 2, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        }
        b.set(fx + 1, 6, zc, Blocks.SHROOMLIGHT);
    }
}
