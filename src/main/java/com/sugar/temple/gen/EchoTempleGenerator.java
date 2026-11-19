package com.sugar.temple.gen;

import com.sugar.block.EchoDrumBlock;
import com.sugar.block.EventDoorBlock;
import com.sugar.entity.EchoDrummer;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleObjectives;
import com.sugar.temple.TempleGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Supplier;

/**
 * Santuario del Eco (templo por defecto). Se construye hacia +X desde la posición del admin:
 *
 *  1. Vestíbulo (x 0-12)             punto de reunión, atriles con las Lentes del Eco, puerta canal 0
 *  2. Puente invisible (x 12-52)     un abismo con un camino que solo se ve con la lente; quien la
 *                                    lleve guía a los demás. Si caes, hay agua abajo y una escalera
 *  3. Sala de los Tambores (x 52-74) "Simón dice": tocad el gong y repetid el ritmo; abre el canal 1
 *  4. Cripta del Eco (x 75-97)       sala de oleadas: zombis, esqueletos, arañas; puerta canal 2
 *  5. Placas del abismo (x 98-114)   cuatro placas de grupo en islotes; las piedras de salto son
 *                                    plataformas ocultas (hace falta la lente); puerta canal 3
 *  6. Antesala (x 115-125)           checkpoint, cofres, dos atriles, Campana del Jefe (canal 9) y tesoro (canal 8)
 *  7. Gran Tambor (centro x 151)     arena circular flotando sobre el abismo
 *  Reliquias: al final de un ramal oculto del puente invisible, en un rincón de la sala de los tambores
 *  y en un islote apartado de las placas (solo se llega con la lente).
 */
public class EchoTempleGenerator implements TempleGenerator {
    public static final int CX = 110, CZ = 7, CAVE_R = 24, CAVE_H = 22;
    private static final int SHIFT = 41;
    private static final int PIT = -16;
    /** Distancia del campamento a la entrada y cuánto se baja por el barranco. */
    public static final int APPROACH = 100, DROP = 20;

    /** Camino del puente invisible: puntos por los que pasa (x, z). */
    static final int[][] PATH = {{16, 7}, {20, 7}, {20, 3}, {27, 3}, {27, 11}, {33, 11}, {37, 11}, {42, 11}, {42, 4}, {46, 4}, {46, 9}, {51, 9}, {51, 7}};

    @Override
    public Result generate(ServerLevel level, BlockPos feet) {
        // El admin está en el campamento, arriba; el santuario está 100 bloques al este y 20 más abajo
        StructureBuilder b = new StructureBuilder(level, origin(feet));

        Supplier<BlockState> wall = b.mix(6, Blocks.DEEPSLATE_BRICKS, 2, Blocks.DEEPSLATE_TILES, 1, Blocks.CRACKED_DEEPSLATE_BRICKS, 1, Blocks.SCULK);
        Supplier<BlockState> floor = b.mix(5, Blocks.POLISHED_DEEPSLATE, 3, Blocks.DEEPSLATE_TILES, 1, Blocks.SCULK);
        Supplier<BlockState> rock = b.mix(6, Blocks.DEEPSLATE, 2, Blocks.COBBLED_DEEPSLATE, 1, Blocks.TUFF, 1, Blocks.SCULK);

        b.ox = SHIFT;
        arena(b, rock);
        b.ox = 0;
        lobby(b, wall, floor);
        abyss(b, wall);
        drumRoom(b, wall, floor);
        crypt(b, wall, floor);
        abyssPlates(b, wall);
        b.ox = SHIFT;
        antechamber(b, wall, floor);
        b.ox = 0;

        Supplier<BlockState> moss = b.mix(4, Blocks.MOSS_BLOCK, 3, Blocks.PODZOL, 2, Blocks.COARSE_DIRT, 1, Blocks.SCULK);
        Approach.drape(b, b.bounds(), 10, rock, moss);
        outside(b, rock, moss, floor);

        return new Result(b.bounds(), feet);
    }

    /** Origen de la construcción a partir de donde está el admin (centro del campamento). */
    public static BlockPos origin(BlockPos feet) {
        return feet.offset(APPROACH, -DROP - 1, -7);
    }

    // ---------- 1. Vestíbulo ----------

    private void lobby(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(0, 0, 0, 12, 7, 14, wall, floor);
        b.set(6, 0, 7, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{3, 3}, {9, 3}, {3, 11}, {9, 11}}) b.set(c[0], 6, c[1], Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        for (int[] c : new int[][]{{3, 1}, {6, 1}, {9, 1}, {3, 13}, {6, 13}, {9, 13}}) b.set(c[0], 1, c[1], ModBlocks.LENS_PEDESTAL.get());
        b.air(0, 1, 6, 0, 3, 8);              // se entra desde el barranco (la entrada de fuera está en el campamento)
        doors(b, 12, 1, 6, 0);
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

    // ---------- 2. Puente invisible ----------

    private void abyss(StructureBuilder b, Supplier<BlockState> wall) {
        b.room(12, PIT, 0, 52, 10, 14, wall, () -> Blocks.DEEPSLATE.defaultBlockState());
        doors(b, 12, 1, 6, 0);
        b.fill(13, PIT + 1, 1, 51, PIT + 3, 13, Blocks.WATER.defaultBlockState());
        // Rellano de salida (visible) y escalera para volver si caes
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = PIT + 1; y < 0; y++) b.set(15, y, 1, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        for (int y = PIT + 1; y <= 0; y++) b.set(16, y, 1, ladder);
        b.fill(13, 0, 1, 15, 0, 13, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        // El camino oculto
        BlockState hidden = ModBlocks.ECHO_PLATFORM.get().defaultBlockState();
        for (int i = 0; i + 1 < PATH.length; i++) {
            int[] a = PATH[i], c = PATH[i + 1];
            b.fill(a[0], 0, a[1], c[0], 0, c[1], hidden);
        }
        // Ramal oculto sin salida con una reliquia al final
        b.fill(37, 0, 12, 37, 0, 13, hidden);
        EventRooms.relic(b, 37, 1, 13);
        // Descanso a mitad de camino (visible) con checkpoint
        b.fill(34, 0, 10, 36, 0, 12, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        b.fill(34, PIT + 1, 10, 36, -1, 12, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        b.set(35, 0, 11, ModBlocks.CHECKPOINT_STONE.get());
        b.set(35, 1, 12, Blocks.SOUL_LANTERN);
        // Salida hacia la sala de los tambores
        b.fill(51, 0, 6, 51, 0, 8, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        for (int x : new int[]{18, 26, 34, 42, 50}) {
            for (int z : new int[]{2, 12}) {
                b.set(x, 9, z, Blocks.CHAIN);
                b.set(x, 8, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            }
        }
        for (int x = 14; x < 52; x += 3) b.set(x, PIT + 4, x % 2 == 0 ? 1 : 13, Blocks.SEA_LANTERN);    // luz en el fondo
    }

    // ---------- 3. Sala de los Tambores ----------

    private void drumRoom(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(52, 0, -2, 74, 9, 16, wall, floor);
        b.air(52, 1, 6, 52, 3, 8);
        int gx = 63, gz = 7;
        b.set(gx, 0, gz, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(gx, 1, gz, ModBlocks.ECHO_GONG.get());
        // Ocho tambores en círculo; cada nota se repite en lados opuestos para que llegue todo el grupo
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int x = gx + (int) Math.round(Math.cos(a) * 6), z = gz + (int) Math.round(Math.sin(a) * 6);
            b.set(x, 0, z, Blocks.STRIPPED_DARK_OAK_LOG);
            b.set(x, 1, z, ModBlocks.ECHO_DRUM.get().defaultBlockState().setValue(EchoDrumBlock.NOTE, i % 4));
        }
        b.set(54, 0, 7, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{57, 2}, {69, 2}, {57, 12}, {69, 12}, {63, 7}}) b.set(c[0], 9, c[1], Blocks.SHROOMLIGHT);
        EventRooms.relic(b, 53, 1, 0);
        doors(b, 74, 1, 6, 1);
    }

    // ---------- 4. Cripta del Eco (oleadas) ----------

    private void crypt(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        EventRooms.waveRoom(b, 75, 97, 0, 14, 9, wall, floor, Blocks.DEEPSLATE_TILES.defaultBlockState(),
                Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        for (int x = 78; x <= 94; x += 4) { b.set(x, 1, 1, Blocks.SKELETON_SKULL); b.set(x, 1, 13, Blocks.SKELETON_SKULL); }
        for (int x = 80; x <= 92; x += 6) { b.set(x, 0, 3, Blocks.SCULK); b.set(x, 0, 11, Blocks.SCULK); }
    }

    // ---------- 5. Placas del abismo ----------

    private void abyssPlates(StructureBuilder b, Supplier<BlockState> wall) {
        BlockState hidden = ModBlocks.ECHO_PLATFORM.get().defaultBlockState();
        EventRooms.plateRoom(b, 98, 114, 0, 14, 10, 16, wall, Blocks.WATER.defaultBlockState(), 3,
                Blocks.POLISHED_DEEPSLATE.defaultBlockState(), hidden, Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        // Escalera para salir del agua y un islote apartado con reliquia (solo se llega por plataformas ocultas)
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = -15; y <= 0; y++) { b.set(99, y, 3, Blocks.DEEPSLATE_BRICKS.defaultBlockState()); b.set(100, y, 3, ladder); }
        b.fill(99, 0, 4, 100, 0, 5, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        b.fill(106, -15, 7, 106, 0, 7, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        b.fill(101, 0, 7, 105, 0, 7, hidden);
        EventRooms.relic(b, 106, 1, 7);
    }

    // ---------- 6. Antesala ----------

    private void antechamber(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(74, 0, 1, 84, 7, 13, wall, floor);
        b.air(74, 1, 6, 74, 3, 8);
        b.set(76, 0, 7, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{77, 2}, {81, 2}, {77, 12}, {81, 12}}) b.set(c[0], 1, c[1], ModBlocks.SUPPLY_CRATE.get());
        b.set(83, 1, 3, ModBlocks.LENS_PEDESTAL.get());
        b.set(79, 1, 12, ModBlocks.LENS_PEDESTAL.get());
        for (int[] c : new int[][]{{77, 4}, {81, 4}, {77, 10}, {81, 10}}) b.set(c[0], 7, c[1], Blocks.SHROOMLIGHT);
        b.set(82, 1, 10, ModBlocks.BOSS_BELL.get());
        EventRooms.vault(b, 75, 0, 1, wall, floor, Blocks.SHROOMLIGHT.defaultBlockState());
        doors(b, 84, 1, 6, TempleObjectives.BELL);
    }

    // ---------- 5. Gran Tambor ----------

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
        b.fill(85, 0, 6, CX - R, 0, 8, Blocks.DEEPSLATE_TILES.defaultBlockState());
        b.air(85, 1, 6, CX - R + 1, 3, 8);
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

    private void doors(StructureBuilder b, int x, int y, int z, int channel) {
        BlockState door = ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel);
        b.fill(x, y, z, x, y + 2, z + 2, door);
    }
}
