package com.sugar.temple.gen;

import com.sugar.block.EventDoorBlock;
import com.sugar.block.EventSwitchBlock;
import com.sugar.block.entity.MagmaRiserBlockEntity;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Corazón del Volcán (templo por defecto). Se construye hacia +X desde la posición del admin:
 *
 *  1. Vestíbulo (x 0-10)            punto de reunión, puerta canal 0
 *  2. Pasarela de lava (x 10-42)    dos carriles de basalto inestable sobre magma, rocas del techo
 *  3. Sala de sellos (x 42-56)      3 sellos a distintas alturas, puerta canal 1
 *  4. Pozo de ascenso (x 57-67)     espiral de plataformas mientras sube el magma
 *  5. Antesala (x 67-77, arriba)    checkpoint, cofres de suministros, puerta canal 2
 *  6. Cráter (centro x 99)          arena circular de radio 20 con 8 respiraderos y el altar
 */
public class VolcanoTempleGenerator implements TempleGenerator {
    private static final int CX = 99, CZ = 5;      // centro del cráter
    private static final int ARENA_Y = 23;          // suelo del cráter y de la antesala
    private static final int ARENA_R = 20;

    @Override
    public Result generate(ServerLevel level, BlockPos feet) {
        StructureBuilder b = new StructureBuilder(level, feet.offset(-5, -1, -5));

        Supplier<BlockState> wall = b.mix(6, Blocks.POLISHED_BLACKSTONE_BRICKS, 2, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, 1, Blocks.BLACKSTONE);
        Supplier<BlockState> floor = b.mix(5, Blocks.POLISHED_BASALT, 3, Blocks.BLACKSTONE, 1, Blocks.POLISHED_BLACKSTONE);
        Supplier<BlockState> rock = b.mix(6, Blocks.BASALT, 3, Blocks.BLACKSTONE, 1, Blocks.SMOOTH_BASALT);

        cone(b, rock);
        lobby(b, wall, floor);
        lavaWalk(b, wall, floor);
        sealRoom(b, wall, floor);
        b.fill(67, 0, 0, 77, ARENA_Y - 1, 10, rock);   // base de la antesala
        shaft(b, wall, floor);
        antechamber(b, wall, floor);
        arena(b, floor);

        return new Result(b.bounds(), feet);
    }

    // ---------- 6. Volcán y cráter ----------

    private void cone(StructureBuilder b, Supplier<BlockState> rock) {
        for (int y = -1; y <= 40; y++) {
            double r = 30.0 - (y + 1) * 7.0 / 41.0;
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r) continue;
                    if (y <= ARENA_Y || d > ARENA_R) {
                        boolean surface = d > r - 1.2;
                        b.set(CX + dx, y, CZ + dz, surface && b.random.nextInt(12) == 0
                                ? Blocks.MAGMA_BLOCK.defaultBlockState() : rock.get());
                    } else {
                        b.set(CX + dx, y, CZ + dz, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    private void arena(StructureBuilder b, Supplier<BlockState> floor) {
        // Suelo
        for (int dx = -ARENA_R; dx <= ARENA_R; dx++) {
            for (int dz = -ARENA_R; dz <= ARENA_R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > ARENA_R) continue;
                BlockState s = d < 3.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : ((int) d) % 6 == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : floor.get();
                b.set(CX + dx, ARENA_Y, CZ + dz, s);
            }
        }
        // Altar en el centro y 8 respiraderos en anillo
        b.set(CX, ARENA_Y, CZ, ModBlocks.BOSS_ALTAR.get());
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int vx = CX + (int) Math.round(Math.cos(a) * 11);
            int vz = CZ + (int) Math.round(Math.sin(a) * 11);
            b.set(vx, ARENA_Y, vz, ModBlocks.MAGMA_VENT.get());
            b.set(vx, ARENA_Y - 1, vz, Blocks.MAGMA_BLOCK);
        }
        // Luces en la pared
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            b.set(CX + (int) Math.round(Math.cos(a) * (ARENA_R + 1)), ARENA_Y + 4, CZ + (int) Math.round(Math.sin(a) * (ARENA_R + 1)), Blocks.SHROOMLIGHT);
        }
        // Pasillo desde la antesala
        b.air(78, ARENA_Y + 1, 4, 79, ARENA_Y + 3, 6);
        b.fill(78, ARENA_Y, 4, 79, ARENA_Y, 6, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
    }

    // ---------- 1. Vestíbulo ----------

    private void lobby(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(0, 0, 0, 10, 6, 10, wall, floor);
        b.set(5, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{2, 2}, {8, 2}, {2, 8}, {8, 8}}) b.set(c[0], 6, c[1], Blocks.SHROOMLIGHT);
        b.air(0, 1, 5, 0, 2, 5);  // entrada a pie desde fuera
    }

    // ---------- 2. Pasarela sobre magma ----------

    private void lavaWalk(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(10, -4, 0, 42, 9, 10, wall, floor);
        b.fill(11, -3, 1, 41, 0, 9, ModBlocks.RISING_MAGMA.get().defaultBlockState());
        b.fill(11, 0, 1, 12, 0, 9, floor);    // salida
        b.fill(38, 0, 1, 41, 0, 9, floor);    // llegada

        BlockState crumbling = ModBlocks.CRUMBLING_BASALT.get().defaultBlockState();
        int[] laneA = {1, 3, 2, 1, 3, 2};
        int[] laneB = {6, 8, 7, 6, 8, 7};
        for (int i = 0; i < 6; i++) {
            int x = 14 + 4 * i;
            b.fill(x, 0, laneA[i], x + 1, 0, laneA[i] + 1, crumbling);
            b.fill(x, 0, laneB[i], x + 1, 0, laneB[i] + 1, crumbling);
        }

        for (int x : new int[]{20, 28, 34}) {
            b.set(x, 9, 2, ModBlocks.ROCKFALL.get());
            b.set(x, 9, 8, ModBlocks.ROCKFALL.get());
        }
        for (int x : new int[]{16, 24, 32, 40}) b.set(x, 9, 5, Blocks.SHROOMLIGHT);

        b.set(12, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        b.set(40, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        doors(b, 10, 1, 4, 0);                 // puerta del vestíbulo (canal 0)
    }

    // ---------- 3. Sala de sellos ----------

    private void sealRoom(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(42, 0, -2, 56, 18, 12, wall, floor);
        b.air(42, 1, 4, 42, 3, 6);

        List<int[]> path = path(ring(43, -1, 55, 11), 43, 7);
        BlockState step = Blocks.POLISHED_BASALT.defaultBlockState();
        for (int k = 0; k <= 12; k++) {
            int[] c = path.get(2 * k);
            b.set(c[0], k + 1, c[1], step);
        }
        BlockState seal = ModBlocks.EVENT_SWITCH.get().defaultBlockState().setValue(EventSwitchBlock.CHANNEL, 1);
        b.set(49, 2, -2, seal);                          // sello del suelo
        int[] mid = outward(path.get(12), 43, -1, 55, 11);
        b.set(mid[0], 8, mid[1], seal);                  // sello a media altura
        int[] top = outward(path.get(24), 43, -1, 55, 11);
        b.set(top[0], 14, top[1], seal);                 // sello alto

        for (int[] c : new int[][]{{46, 2}, {52, 2}, {46, 8}, {52, 8}}) b.set(c[0], 18, c[1], Blocks.SHROOMLIGHT);
        b.set(44, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        b.set(54, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        doors(b, 56, 1, 4, 1);
    }

    // ---------- 4. Pozo de ascenso ----------

    private void shaft(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(57, 0, 0, 67, 30, 10, wall, floor);
        b.air(57, 1, 4, 57, 3, 6);

        BlockPos riserPos = b.abs(62, 0, 5);
        b.set(62, 0, 5, ModBlocks.MAGMA_RISER.get());
        if (b.level.getBlockEntity(riserPos) instanceof MagmaRiserBlockEntity riser) {
            riser.configure(4, 20, 30);
        }

        List<int[]> path = path(ring(58, 1, 66, 9), 58, 7);
        int n = path.size();
        BlockState step = Blocks.POLISHED_BASALT.defaultBlockState();
        for (int k = 0; k <= 21; k++) {
            int[] c = path.get((2 * k) % n);
            b.set(c[0], k + 1, c[1], step);
        }
        // Balcón superior (sin taparle el hueco a las últimas plataformas)
        Set<Integer> skip = new HashSet<>();
        for (int k = 18; k <= 21; k++) {
            skip.add((2 * k) % n);
            skip.add((2 * k + 1) % n);
        }
        BlockState balcony = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int i = 0; i < n; i++) {
            if (skip.contains(i)) continue;
            int[] c = path.get(i);
            b.set(c[0], ARENA_Y, c[1], balcony);
        }
        for (int y = 6; y <= 26; y += 10) {
            b.set(57, y, 2, Blocks.SHROOMLIGHT);
            b.set(67, y, 8, Blocks.SHROOMLIGHT);
            b.set(62, y, 0, Blocks.SHROOMLIGHT);
            b.set(62, y, 10, Blocks.SHROOMLIGHT);
        }
    }

    // ---------- 5. Antesala ----------

    private void antechamber(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(67, ARENA_Y, 0, 77, ARENA_Y + 7, 10, wall, floor);
        b.air(67, ARENA_Y + 1, 4, 67, ARENA_Y + 3, 6);
        b.set(69, ARENA_Y, 5, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{70, 1}, {74, 1}, {70, 9}, {74, 9}}) {
            b.set(c[0], ARENA_Y + 1, c[1], ModBlocks.SUPPLY_CRATE.get());
        }
        for (int[] c : new int[][]{{70, 3}, {74, 3}, {70, 7}, {74, 7}}) b.set(c[0], ARENA_Y + 7, c[1], Blocks.SHROOMLIGHT);
        doors(b, 77, ARENA_Y + 1, 4, 2);
    }

    // ---------- Utilidades ----------

    /** Puerta de 3x3 en la pared x, desde (y, z) hacia arriba y +z. */
    private void doors(StructureBuilder b, int x, int y, int z, int channel) {
        BlockState door = ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel);
        b.fill(x, y, z, x, y + 2, z + 2, door);
    }

    /** Celdas del borde de un rectángulo, en orden horario empezando por (x1, z1). */
    private static List<int[]> ring(int x1, int z1, int x2, int z2) {
        List<int[]> l = new ArrayList<>();
        for (int x = x1; x <= x2; x++) l.add(new int[]{x, z1});
        for (int z = z1 + 1; z <= z2; z++) l.add(new int[]{x2, z});
        for (int x = x2 - 1; x >= x1; x--) l.add(new int[]{x, z2});
        for (int z = z2 - 1; z > z1; z--) l.add(new int[]{x1, z});
        return l;
    }

    /** Recorre el anillo en sentido antihorario empezando en (sx, sz). */
    private static List<int[]> path(List<int[]> ring, int sx, int sz) {
        List<int[]> r = new ArrayList<>(ring);
        Collections.reverse(r);
        int start = 0;
        for (int i = 0; i < r.size(); i++) {
            if (r.get(i)[0] == sx && r.get(i)[1] == sz) start = i;
        }
        List<int[]> out = new ArrayList<>(r.subList(start, r.size()));
        out.addAll(r.subList(0, start));
        return out;
    }

    /** Bloque de pared que hay justo detrás de una celda del borde. */
    private static int[] outward(int[] c, int x1, int z1, int x2, int z2) {
        if (c[0] == x1) return new int[]{x1 - 1, c[1]};
        if (c[0] == x2) return new int[]{x2 + 1, c[1]};
        if (c[1] == z1) return new int[]{c[0], z1 - 1};
        return new int[]{c[0], z2 + 1};
    }
}
