package com.sugar.temple.gen;

import com.sugar.block.BigChestBlock;
import com.sugar.block.EchoDrumBlock;
import com.sugar.block.EventSwitchBlock;
import com.sugar.block.GroupPlateBlock;
import com.sugar.block.WaveAltarBlock;
import com.sugar.entity.EchoDrummer;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Supplier;

import static com.sugar.temple.TempleObjectives.*;

/**
 * Santuario del Eco, al estilo Ocarina of Time (25-50 jugadores). El admin está en el campamento, arriba; un
 * barranco baja 20 bloques hasta la fachada del santuario, enterrado 100 bloques al este.
 *
 * Dentro: el Pozo de los Ecos (sala central de dos pisos sobre un abismo con agua al fondo) y alrededor:
 *   Piso 2 (y 0):   Vestíbulo · Sala de los Tambores (ritmo → Llave 1, reliquia 1) · Cripta (oleadas →
 *                   Gran Cofre: una Lente del Eco para cada uno) · Puente invisible (🔒1, con la lente → Llave 2,
 *                   reliquia 2)
 *   Piso 1 (y -10): Osario (foso de fuego con puente oculto → Llave 3, brújula) · Placas del abismo (🔒2+3, piedras
 *                   ocultas: abren la reja del fondo y la Llave 4) · Sala de los Ecos (🔒4, laberinto oscuro con
 *                   6 sellos a la vez → Llave del Jefe) · Cámara oculta (solo se llega con la lente, reliquia 3,
 *                   tesoro canal 8)
 *   Fondo (y -24):  repisa, camino oculto sobre el abismo, reja (canal 5) y Puerta del Jefe → túnel → Gran Tambor.
 */
public class EchoTempleGenerator implements TempleGenerator {
    public static final int CX = 105, CZ = 7, CAVE_R = 24, CAVE_H = 22;
    private static final int PIT = -16;
    /** Distancia del campamento a la entrada y cuánto se baja por el barranco. */
    public static final int APPROACH = 100, DROP = 20;
    private static final int HX = 28, HZ = 7, F2 = 0, F1 = -10, LEDGE = -24, ABYSS = -36, LAIR_Y = -28;

    @Override
    public Result generate(ServerLevel level, BlockPos feet) {
        // El admin está en el campamento, arriba; el santuario está 100 bloques al este y 20 más abajo
        StructureBuilder b = new StructureBuilder(level, origin(feet));
        Supplier<BlockState> wall = b.mix(6, Blocks.DEEPSLATE_BRICKS, 2, Blocks.DEEPSLATE_TILES, 1, Blocks.CRACKED_DEEPSLATE_BRICKS, 1, Blocks.SCULK);
        Supplier<BlockState> floor = b.mix(5, Blocks.POLISHED_DEEPSLATE, 3, Blocks.DEEPSLATE_TILES, 1, Blocks.SCULK);
        Supplier<BlockState> rock = b.mix(6, Blocks.DEEPSLATE, 2, Blocks.COBBLED_DEEPSLATE, 1, Blocks.TUFF, 1, Blocks.SCULK);
        OotKit k = new OotKit(b, HX, HZ, ABYSS, 8, new int[]{F2, F1}, wall, floor);

        b.oy = LAIR_Y;
        arena(b, rock);
        b.oy = 0;
        lobby(b, wall, floor);
        hub(b, k);
        drums(b, k);
        crypt(b, k);
        bridge(b, k);
        ossuary(b, k, rock);
        plates(b, k);
        echoes(b, k);
        hidden(b, k);
        tunnel(b, wall, floor);

        Supplier<BlockState> moss = b.mix(4, Blocks.MOSS_BLOCK, 3, Blocks.PODZOL, 2, Blocks.COARSE_DIRT, 1, Blocks.SCULK);
        Approach.drape(b, b.bounds(), 10, rock, moss);
        outside(b, rock, moss, floor);
        return new Result(b.bounds(), feet);
    }

    /** Origen de la construcción a partir de donde está el admin (centro del campamento). */
    public static BlockPos origin(BlockPos feet) {
        return feet.offset(APPROACH, -DROP - 1, -7);
    }

    private static BlockState hiddenPlatform() {
        return ModBlocks.ECHO_PLATFORM.get().defaultBlockState();
    }

    private static BlockState lantern() {
        return Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
    }

    /** Camino oculto de 2 de ancho entre dos puntos (solo se ve con la lente). */
    private static void hiddenPath(StructureBuilder b, int x1, int z1, int x2, int z2, int y) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1)) * 2;
        for (int i = 0; i <= n; i++) {
            int x = (int) Math.round(x1 + (x2 - x1) * i / (double) n), z = (int) Math.round(z1 + (z2 - z1) * i / (double) n);
            b.set(x, y, z, hiddenPlatform());
            b.set(x, y, z + 1, hiddenPlatform());
        }
    }

    // ---------- Vestíbulo y Pozo de los Ecos ----------

    private void lobby(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(0, 0, 0, 12, 7, 14, wall, floor);
        b.set(6, 0, 7, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{3, 3}, {9, 3}, {3, 11}, {9, 11}}) b.set(c[0], 6, c[1], lantern());
        b.set(2, 1, 1, ModBlocks.SUPPLY_CRATE.get());
        b.set(2, 1, 13, ModBlocks.SUPPLY_CRATE.get());
        b.air(0, 1, 6, 0, 3, 8);              // se entra desde el barranco (la entrada de fuera está en el campamento)
        b.fill(12, 1, 6, 12, 3, 8, OotKit.door(0));
    }

    private void hub(StructureBuilder b, OotKit k) {
        k.hub(Blocks.SEA_LANTERN.defaultBlockState(), ABYSS);
        b.fill(HX - 12, ABYSS + 1, HZ - 12, HX + 12, ABYSS + 3, HZ + 12, Blocks.WATER.defaultBlockState());
        for (int x = HX - 13; x <= HX + 13; x++)                         // el agua solo dentro del pozo
            for (int z = HZ - 13; z <= HZ + 13; z++)
                if (k.dist(x, z) > OotKit.IN) for (int y = ABYSS + 1; y <= ABYSS + 3; y++) b.set(x, y, z, k.wall.get());
        k.ramp(200, 262, F2, F1);
        k.ramp(100, 185, F1, LEDGE);
        // Repisa del fondo al oeste y escalera desde el agua
        for (double a = 165; a <= 215; a += 0.5) {
            double r0 = Math.toRadians(a);
            for (double r = OotKit.BALCONY + 0.1; r <= OotKit.IN - 0.1; r += 0.35)
                b.set(HX + (int) Math.round(Math.cos(r0) * r), LEDGE, HZ + (int) Math.round(Math.sin(r0) * r), k.floor.get());
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = ABYSS + 1; y <= LEDGE; y++) b.set(HX - 11, y, HZ + 2, ladder);
        // Camino oculto hasta la reja y la Puerta del Jefe, al este
        hiddenPath(b, HX - 8, HZ - 1, HX + 11, HZ - 1, LEDGE);
        b.fill(HX + 11, LEDGE + 1, HZ - 1, HX + 11, LEDGE + 4, HZ + 1, OotKit.door(V_BRIDGE));
        b.fill(HX + 12, LEDGE + 1, HZ - 1, HX + 12, LEDGE + 4, HZ + 1, OotKit.bossDoor());
        b.set(HX + 12, LEDGE + 5, HZ, Blocks.SCULK_CATALYST);
        // Hueco en el balcón del piso 1 al oeste: solo se cruza con la lente
        for (double a = 160; a <= 200; a += 0.5) {
            double r0 = Math.toRadians(a);
            for (double r = OotKit.BALCONY + 0.1; r <= OotKit.IN - 0.1; r += 0.35)
                b.set(HX + (int) Math.round(Math.cos(r0) * r), F1, HZ + (int) Math.round(Math.sin(r0) * r), hiddenPlatform());
        }
        k.corridor(12, 6, 16, 8, F2);
    }

    // ---------- Piso 2 ----------

    private void drums(StructureBuilder b, OotKit k) {
        b.room(16, F2, -28, 40, F2 + 9, -7, k.wall, k.floor);
        k.corridor(27, -8, 29, -5, F2);
        int gx = 28, gz = -17;
        b.set(gx, F2, gz, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(gx, F2 + 1, gz, ModBlocks.ECHO_GONG.get());
        for (int i = 0; i < 8; i++) {                                   // cada nota en lados opuestos, para que llegue todo el grupo
            double a = Math.PI * 2 * i / 8;
            int x = gx + (int) Math.round(Math.cos(a) * 6), z = gz + (int) Math.round(Math.sin(a) * 6);
            b.set(x, F2, z, Blocks.STRIPPED_DARK_OAK_LOG);
            b.set(x, F2 + 1, z, ModBlocks.ECHO_DRUM.get().defaultBlockState().setValue(EchoDrumBlock.NOTE, i % 4));
        }
        b.set(37, F2 + 1, -17, OotKit.keyChest(1));
        k.cage(37, F2 + 1, -17, V_SEALS);
        EventRooms.relic(b, 17, F2 + 1, -27);
        b.set(28, F2, -9, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{21, -22}, {35, -22}, {21, -12}, {35, -12}}) b.set(c[0], F2 + 9, c[1], Blocks.SHROOMLIGHT);
    }

    private void crypt(StructureBuilder b, OotKit k) {
        b.room(16, F2, 21, 40, F2 + 9, 42, k.wall, k.floor);
        k.corridor(27, 19, 29, 22, F2);
        k.doorZ(27, F2 + 1, 21, OotKit.door(V_FORGE_GATE));
        b.set(28, F2, 31, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(28, F2 + 1, 31, ModBlocks.WAVE_ALTAR.get().defaultBlockState().setValue(WaveAltarBlock.CHANNEL, V_FORGE));
        for (int x = 18; x <= 38; x += 4) for (int z : new int[]{24, 38}) {               // tumbas
            b.fill(x, F2 + 1, z, x + 1, F2 + 1, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            b.set(x, F2 + 2, z, Blocks.COBBLED_DEEPSLATE_WALL);
        }
        b.set(28, F2 + 1, 40, OotKit.bigChest(BigChestBlock.LENS));
        k.cage(28, F2 + 1, 40, V_FORGE);
        for (int[] c : new int[][]{{22, 31}, {34, 31}, {28, 25}, {28, 36}}) b.set(c[0], F2 + 8, c[1], lantern());
    }

    private void bridge(StructureBuilder b, OotKit k) {
        int pit = F2 - 3;
        b.room(42, pit, -8, 72, F2 + 9, 22, k.wall, k.floor);
        b.fill(43, pit + 1, -7, 71, pit + 2, 21, Blocks.WATER.defaultBlockState());
        k.corridor(40, 6, 42, 8, F2);
        k.doorX(42, F2 + 1, 6, OotKit.lock(V_LOCK_SEALS, 1));
        b.fill(43, pit + 1, -7, 45, F2, 21, k.floor);                  // rellano de entrada
        b.fill(68, pit + 1, -7, 71, F2, 21, k.floor);                  // rellano del fondo
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = pit + 1; y <= F2; y++) b.set(46, y, 0, ladder);
        b.set(44, F2, 7, ModBlocks.CHECKPOINT_STONE.get());
        hiddenPath(b, 46, 7, 52, 1, F2);
        hiddenPath(b, 52, 1, 58, 13, F2);
        hiddenPath(b, 58, 13, 64, 4, F2);
        hiddenPath(b, 64, 4, 67, 7, F2);
        hiddenPath(b, 58, 13, 62, 18, F2);                             // ramal a la reliquia
        b.fill(62, pit + 1, 18, 64, F2, 20, k.floor);
        EventRooms.relic(b, 63, F2 + 1, 19);
        b.set(70, F2 + 1, 7, OotKit.keyChest(2));
        for (int[] c : new int[][]{{50, 7}, {58, 7}, {64, 7}}) b.set(c[0], F2 + 9, c[1], lantern());
    }

    // ---------- Piso 1 ----------

    private void ossuary(StructureBuilder b, OotKit k, Supplier<BlockState> rock) {
        b.room(16, F1 - 7, -28, 40, F1 + 9, -7, k.wall, k.floor);
        b.fill(17, F1 - 6, -27, 39, F1, -8, rock);
        k.corridor(27, -8, 29, -5, F1);
        b.air(17, F1 - 5, -22, 39, F1, -15);                           // foso de fuego de alma
        b.fill(17, F1 - 6, -22, 39, F1 - 6, -15, Blocks.SOUL_SAND.defaultBlockState());
        b.fill(17, F1 - 5, -22, 39, F1 - 5, -15, Blocks.SOUL_FIRE.defaultBlockState());
        hiddenPath(b, 27, -15, 27, -22, F1);
        b.set(28, F1, -14, Blocks.CHISELED_DEEPSLATE);
        for (int x = 18; x <= 38; x += 5) { b.set(x, F1 + 1, -9, Blocks.BONE_BLOCK); b.set(x, F1 + 1, -26, Blocks.BONE_BLOCK); b.set(x, F1 + 2, -26, Blocks.SKELETON_SKULL); }
        for (int x : new int[]{22, 34}) b.set(x, F1 + 9, -18, ModBlocks.ROCKFALL.get());
        b.set(28, F1 + 1, -26, OotKit.keyChest(3));
        b.set(20, F1 + 1, -26, OotKit.bigChest(BigChestBlock.COMPASS));
        b.set(28, F1, -10, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{22, -11}, {34, -11}, {28, -25}}) b.set(c[0], F1 + 8, c[1], lantern());
    }

    private void plates(StructureBuilder b, OotKit k) {
        int pit = F1 - 10;
        b.room(42, pit, -8, 72, F1 + 6, 22, k.wall, k.floor);
        b.fill(43, pit + 1, -7, 71, pit + 3, 21, Blocks.WATER.defaultBlockState());
        k.corridor(40, 6, 42, 8, F1);
        k.doorX(42, F1 + 1, 6, OotKit.lock(V_LOCK_BRIDGE, 2 | 4));
        b.fill(43, pit + 1, -7, 45, F1, 21, k.floor);
        b.fill(68, pit + 1, -7, 71, F1, 21, k.floor);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = pit + 1; y <= F1; y++) b.set(46, y, 0, ladder);
        b.set(44, F1, 7, ModBlocks.CHECKPOINT_STONE.get());
        BlockState plate = ModBlocks.GROUP_PLATE.get().defaultBlockState().setValue(GroupPlateBlock.CHANNEL, V_BRIDGE);
        for (int cz : new int[]{-4, 3, 11, 18}) {
            for (int cx : new int[]{50, 58}) {
                b.fill(cx - 1, pit + 1, cz - 1, cx + 1, F1, cz + 1, Blocks.DEEPSLATE_TILES.defaultBlockState());
                b.set(cx, F1, cz, plate);
                b.set(cx, F1 + 5, cz, lantern());
            }
            for (int x = 46; x <= 48; x++) b.set(x, F1, cz, hiddenPlatform());
            for (int x = 52; x <= 56; x++) b.set(x, F1, cz, hiddenPlatform());
            for (int x = 60; x <= 67; x++) b.set(x, F1, cz, hiddenPlatform());
        }
        b.set(70, F1 + 1, 7, OotKit.keyChest(4));
        k.cage(70, F1 + 1, 7, V_BRIDGE);
    }

    private void echoes(StructureBuilder b, OotKit k) {
        b.room(16, F1, 21, 40, F1 + 7, 42, k.wall, k.floor);
        k.corridor(27, 19, 29, 22, F1);
        k.doorZ(27, F1 + 1, 21, OotKit.lock(V_LOCK_HAMMER, 8));
        // Laberinto: tres muros con huecos alternos
        BlockState w = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        b.fill(17, F1 + 1, 26, 35, F1 + 6, 26, w);
        b.fill(21, F1 + 1, 32, 39, F1 + 6, 32, w);
        b.fill(17, F1 + 1, 37, 25, F1 + 6, 37, w);
        b.fill(31, F1 + 1, 37, 39, F1 + 6, 37, w);
        BlockState seal = ModBlocks.EVENT_SWITCH.get().defaultBlockState().setValue(EventSwitchBlock.CHANNEL, V_SWITCHES);
        for (int[] s : new int[][]{{17, 23}, {39, 28}, {17, 30}, {39, 35}, {17, 41}, {39, 41}}) b.set(s[0], F1 + 1, s[1], seal);
        b.set(28, F1 + 1, 40, OotKit.bigChest(BigChestBlock.BOSS_KEY));
        k.cage(28, F1 + 1, 40, V_SWITCHES);
        for (int[] c : new int[][]{{28, 23}, {28, 29}, {28, 35}}) b.set(c[0], F1 + 6, c[1], lantern());
    }

    private void hidden(StructureBuilder b, OotKit k) {
        b.room(-13, F1, 0, -1, F1 + 6, 14, k.wall, k.floor);           // tesoro
        for (int z : new int[]{3, 7, 11}) b.set(-11, F1 + 1, z, ModBlocks.TREASURE_CRATE.get());
        b.set(-7, F1 + 6, 7, Blocks.SHROOMLIGHT);
        b.room(-1, F1, 0, 13, F1 + 6, 14, k.wall, k.floor);            // cámara oculta, bajo el vestíbulo
        k.doorX(-1, F1 + 1, 6, OotKit.door(VAULT));
        k.corridor(13, 6, 16, 8, F1);
        EventRooms.relic(b, 1, F1 + 1, 1);
        b.set(6, F1 + 6, 7, lantern());
    }

    // ---------- Túnel y Gran Tambor ----------

    private void tunnel(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        for (int x = HX + 13; x <= CX - CAVE_R + 4; x++) {
            int y = Math.max(LAIR_Y, LEDGE - (x - (HX + 13)));
            b.fill(x, y - 1, HZ - 3, x, y + 5, HZ + 3, wall);
            b.fill(x, y, HZ - 2, x, y, HZ + 2, floor);
            b.air(x, y + 1, HZ - 2, x, y + 4, HZ + 2);
            if (x % 8 == 0) { b.set(x, y + 4, HZ - 3, Blocks.SOUL_LANTERN); b.set(x, y + 4, HZ + 3, Blocks.SOUL_LANTERN); }
        }
        b.fill(CX - CAVE_R - 1, LAIR_Y, HZ - 1, CX - EchoDrummer.ARENA_R, LAIR_Y, HZ + 1, Blocks.DEEPSLATE_TILES.defaultBlockState());
        b.set(HX + 24, LAIR_Y, HZ, ModBlocks.CHECKPOINT_STONE.get());
    }

    private void arena(StructureBuilder b, Supplier<BlockState> rock) {
        int outer = CAVE_R + 2;
        int R = EchoDrummer.ARENA_R;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > outer) continue;
                for (int y = PIT; y <= CAVE_H + 2; y++) {
                    boolean inside;
                    if (y <= 0) inside = d < CAVE_R && y > PIT;
                    else inside = (d * d) / (double) (CAVE_R * CAVE_R) + (y * y) / (double) (CAVE_H * CAVE_H) < 1.0;
                    b.set(CX + dx, y, CZ + dz, inside ? Blocks.AIR.defaultBlockState() : rock.get());
                }
                if (d < CAVE_R) b.fill(CX + dx, PIT + 1, CZ + dz, CX + dx, PIT + 3, CZ + dz, Blocks.WATER.defaultBlockState());
            }
        }
        // El tambor: piel con anillos y la runa del ojo, caja de madera y aro
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 0.5) continue;
                int x = CX + dx, z = CZ + dz;
                BlockState skin;
                if (d > R - 0.5) skin = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
                else if (Math.abs(d - (R - 2.5)) < 0.5 || Math.abs(d - 7) < 0.5) skin = Blocks.BROWN_TERRACOTTA.defaultBlockState();
                else if (d < 2.5) skin = Blocks.CYAN_TERRACOTTA.defaultBlockState();
                else if (d < 3.5) skin = Blocks.BLACK_TERRACOTTA.defaultBlockState();
                else skin = Blocks.WHITE_TERRACOTTA.defaultBlockState();
                b.set(x, 0, z, skin);
                for (int y = -4; y < 0; y++) {
                    boolean shell = d > R - 1.5;
                    BlockState body = shell ? ((y == -2 && ((dx + dz) & 1) == 0) ? Blocks.BONE_BLOCK.defaultBlockState()
                            : Blocks.DARK_OAK_PLANKS.defaultBlockState()) : Blocks.SPRUCE_PLANKS.defaultBlockState();
                    b.set(x, y, z, y == -4 && !shell ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : body);
                }
                if (d > R - 0.5) b.set(x, 1, z, ((int) Math.round(Math.atan2(dz, dx) * 12) & 1) == 0
                        ? Blocks.DARK_OAK_FENCE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }
        b.set(CX, 0, CZ, ModBlocks.BOSS_ALTAR.get());
        // Puente de entrada desde la antesala, con escalera desde el agua
        b.fill(CX - CAVE_R, 0, CZ - 1, CX - R, 0, CZ + 1, Blocks.DEEPSLATE_TILES.defaultBlockState());
        b.air(CX - CAVE_R, 1, CZ - 1, CX - R + 1, 3, CZ + 1);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = PIT + 1; y <= 0; y++) {
            b.set(CX - CAVE_R + 1, y, 10, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            b.set(CX - CAVE_R + 2, y, 10, ladder);
        }
        b.fill(CX - CAVE_R + 1, 0, 9, CX - CAVE_R + 3, 0, 9, Blocks.DEEPSLATE_TILES.defaultBlockState());
        // Luz: faroles de alma en la pared y cristales de sculk en la cúpula
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            int y = 6;
            double r = CAVE_R * Math.sqrt(1 - (y * y) / (double) (CAVE_H * CAVE_H)) + 0.6;
            b.set(CX + (int) Math.round(Math.cos(a) * r), y, CZ + (int) Math.round(Math.sin(a) * r), Blocks.SHROOMLIGHT);
            b.set(CX + (int) Math.round(Math.cos(a) * (CAVE_R - 0.4)), PIT + 4, CZ + (int) Math.round(Math.sin(a) * (CAVE_R - 0.4)), Blocks.SEA_LANTERN);
        }
        b.set(CX, CAVE_H, CZ, Blocks.SHROOMLIGHT);
    }

    // ---------- 0. Fuera: el Barranco de los Ecos ----------

    /**
     * Campamento en lo alto (donde está el admin) → un barranco que baja 20 bloques haciendo eses: sima con
     * puente que se hunde, un cementerio con checkpoint, la Puerta de los Ecos (cuatro placas a la vez),
     * arcos de roca que sueltan piedras y, al fondo, la fachada del Santuario.
     */
    private void outside(StructureBuilder b, Supplier<BlockState> rock, Supplier<BlockState> moss, Supplier<BlockState> floor) {
        int zc = 7, top = DROP, x0 = -APPROACH - 12, x1 = -11;
        // Meseta: roca solo donde hay aire (en un mundo normal es el propio terreno) y suelo musgoso arriba
        Approach.fillAir(b, x0, 0, zc - 30, x1 + 1, top - 1, zc + 30, rock);
        for (int x = x0; x <= x1 + 1; x++)
            for (int z = zc - 30; z <= zc + 30; z++)
                if (b.level.getBlockState(b.abs(x, top, z)).isAir()) b.set(x, top, z, moss.get());
        Approach.camp(b, -APPROACH, top, zc, 12, 12, () -> Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                b.mix(4, Blocks.PODZOL, 3, Blocks.MOSS_BLOCK, 2, Blocks.COARSE_DIRT),
                Blocks.GRAY_WOOL.defaultBlockState(), Blocks.CYAN_WOOL.defaultBlockState(), Blocks.SOUL_CAMPFIRE.defaultBlockState(),
                Blocks.SOUL_LANTERN.defaultBlockState());
        // Abetos oscuros alrededor del campamento
        for (int k = 0; k < 14; k++) {
            int x = -APPROACH - 10 + b.random.nextInt(30), z = zc + (b.random.nextBoolean() ? 1 : -1) * (14 + b.random.nextInt(12));
            int h = 5 + b.random.nextInt(4);
            b.fill(x, top + 1, z, x, top + h, z, Blocks.DARK_OAK_LOG.defaultBlockState());
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.set(x + dx, top + h, z + dz, Blocks.DARK_OAK_LEAVES);
            b.set(x, top + h + 1, z, Blocks.DARK_OAK_LEAVES);
        }

        List<int[]> path = Approach.path(-APPROACH + 13, top, x1, 0, zc, 10);
        Supplier<BlockState> trail = b.mix(4, Blocks.COBBLED_DEEPSLATE, 3, Blocks.MOSSY_COBBLESTONE, 2, Blocks.TUFF);
        Approach.pave(b, path, 3, trail, rock, 4, top + 2);
        for (int i = 0; i < path.size(); i += 24) {                   // faroles de alma a los lados
            int[] c = path.get(i);
            b.set(c[0], c[1] + 1, c[2] + 3, Blocks.COBBLED_DEEPSLATE_WALL);
            b.set(c[0], c[1] + 2, c[2] + 3, Blocks.SOUL_LANTERN);
        }

        // 1. Sima con un puente que se hunde
        for (int i = (int) (path.size() * 0.12); i <= (int) (path.size() * 0.17); i++) {
            int[] c = path.get(i);
            for (int w = -3; w <= 3; w++) {
                b.air(c[0], c[1] - 6, c[2] + w, c[0], c[1], c[2] + w);
                b.set(c[0], c[1] - 7, c[2] + w, ModBlocks.RISING_MAGMA.get());
                if (Math.abs(w) <= 1) b.set(c[0], c[1], c[2] + w, ModBlocks.CRUMBLING_BASALT.get());
            }
        }
        // 2. Cementerio: ensanche con tumbas y checkpoint
        clearing(b, path, 0.30, 0.36, floor);
        int[] g = Approach.at(path, 0.33);
        b.set(g[0], g[1], g[2], ModBlocks.CHECKPOINT_STONE.get());
        for (int k = -2; k <= 2; k++) {
            for (int w : new int[]{-6, 6}) {
                int[] c = Approach.at(path, 0.33 + k * 0.012);
                b.set(c[0], c[1] + 1, c[2] + w, Blocks.COBBLED_DEEPSLATE_WALL);
                b.set(c[0], c[1] + 2, c[2] + w, k == 0 ? Blocks.SOUL_LANTERN : Blocks.CHISELED_DEEPSLATE);
            }
        }
        b.set(g[0] + 2, g[1] + 1, g[2] - 5, ModBlocks.SUPPLY_CRATE.get());
        b.set(g[0] - 2, g[1] + 1, g[2] + 5, ModBlocks.SUPPLY_CRATE.get());
        // 3. La Puerta de los Ecos: una reja (canal 4) que se abre con cuatro placas a la vez
        clearing(b, path, 0.48, 0.55, floor);
        BlockState plate = ModBlocks.GROUP_PLATE.get().defaultBlockState().setValue(com.sugar.block.GroupPlateBlock.CHANNEL, 4);
        for (double s : new double[]{0.49, 0.54}) {
            int[] c = Approach.at(path, s);
            b.set(c[0], c[1], c[2] - 6, plate);
            b.set(c[0], c[1], c[2] + 6, plate);
        }
        int[] gate = Approach.at(path, 0.57);
        b.fill(gate[0], gate[1] + 1, gate[2] - 3, gate[0], gate[1] + 6, gate[2] + 3, Approach.door(4));
        b.fill(gate[0], gate[1] + 7, gate[2] - 4, gate[0], gate[1] + 7, gate[2] + 4, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        for (int w : new int[]{-4, 4}) b.fill(gate[0], gate[1] + 1, gate[2] + w, gate[0], gate[1] + 6, gate[2] + w, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        b.set(gate[0] - 1, gate[1] + 7, gate[2], Blocks.SOUL_LANTERN);
        // 4. Arcos de roca que sueltan piedras
        for (double s : new double[]{0.66, 0.72, 0.78}) {
            int[] c = Approach.at(path, s);
            b.fill(c[0], c[1] + 6, c[2] - 4, c[0] + 1, c[1] + 7, c[2] + 4, rock);
            b.set(c[0], c[1] + 6, c[2], ModBlocks.ROCKFALL.get());
        }
        int[] cp = Approach.at(path, 0.84);
        b.set(cp[0], cp[1], cp[2], ModBlocks.CHECKPOINT_STONE.get());
        // 5. Fachada del Santuario al fondo del barranco
        int fx = x1;
        for (int z = zc - 9; z <= zc + 9; z++) {
            int h = 14 - Math.abs(z - zc) / 3;
            b.fill(fx, 0, z, fx + 1, h, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            Approach.fillAir(b, fx + 2, 0, z, -1, h, z, rock);
        }
        for (int w : new int[]{-6, -3, 3, 6}) b.fill(fx - 1, 1, zc + w, fx - 1, 11, zc + w, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        b.fill(fx - 1, 12, zc - 7, fx - 1, 12, zc + 7, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        b.fill(fx - 1, 8, zc - 1, fx - 1, 10, zc + 1, Blocks.SCULK.defaultBlockState());
        b.set(fx - 1, 9, zc, Blocks.SCULK_CATALYST);
        for (int w : new int[]{-5, 5}) b.set(fx - 1, 7, zc + w, Blocks.SOUL_LANTERN);
        b.fill(fx - 1, 0, zc - 1, 0, 0, zc + 1, floor);
        b.air(fx - 1, 1, zc - 1, 0, 4, zc + 1);
    }

    /** Ensancha el barranco (de s1 a s2 del camino) para tener sitio. */
    private void clearing(StructureBuilder b, List<int[]> path, double s1, double s2, Supplier<BlockState> floor) {
        for (int i = (int) (path.size() * s1); i <= (int) (path.size() * s2); i++) {
            int[] c = path.get(i);
            for (int w = -7; w <= 7; w++) {
                b.set(c[0], c[1], c[2] + w, floor.get());
                b.air(c[0], c[1] + 1, c[2] + w, c[0], DROP + 2, c[2] + w);
            }
        }
    }
}
