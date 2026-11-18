package com.sugar.temple.gen;

import com.sugar.block.EventDoorBlock;
import com.sugar.block.FireGeyserBlock;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Las Fauces de Ceniza (templo por defecto). Se construye hacia +X desde la posición del admin:
 *
 *  1. Vestíbulo (x 0-10)               punto de reunión, puerta canal 0
 *  2. Paso de los géiseres (x 10-46)    pasarela sobre magma con filas de géiseres que se alternan
 *                                       y un tramo de basalto que se derrumba
 *  3. Galería de estatuas (x 46-70)     tres muros de estatuas que hay que volar con bombas
 *  4. Antesala (x 70-80)                checkpoint, cofres, puerta canal 2
 *  5. Caverna (centro x 102)            arena con cúpula, foso de lava, matas de pólvora y grietas
 */
public class AshTempleGenerator implements TempleGenerator {
    private static final int CX = 102, CZ = 5, ARENA_R = 20, DOME_H = 17;

    @Override
    public Result generate(ServerLevel level, BlockPos feet) {
        StructureBuilder b = new StructureBuilder(level, feet.offset(-5, -1, -5));

        Supplier<BlockState> wall = b.mix(6, Blocks.DEEPSLATE_BRICKS, 2, Blocks.CRACKED_DEEPSLATE_BRICKS, 2, Blocks.COBBLED_DEEPSLATE);
        Supplier<BlockState> floor = b.mix(5, Blocks.POLISHED_DEEPSLATE, 3, Blocks.DEEPSLATE_TILES, 1, Blocks.BLACKSTONE);
        Supplier<BlockState> rock = b.mix(5, Blocks.DEEPSLATE, 3, Blocks.BASALT, 2, Blocks.BLACKSTONE, 1, Blocks.TUFF);

        cavern(b, rock, floor);
        lobby(b, wall, floor);
        geyserWalk(b, wall, floor);
        gallery(b, wall, floor);
        antechamber(b, wall, floor);

        return new Result(b.bounds(), feet);
    }

    // ---------- 1. Vestíbulo ----------

    private void lobby(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(0, 0, 0, 10, 6, 10, wall, floor);
        b.set(5, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{2, 2}, {8, 2}, {2, 8}, {8, 8}}) b.set(c[0], 6, c[1], Blocks.SHROOMLIGHT);
        b.air(0, 1, 5, 0, 2, 5);
        doors(b, 10, 1, 4, 0);
    }

    // ---------- 2. Paso de los géiseres ----------

    private void geyserWalk(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(10, -4, 0, 46, 10, 10, wall, floor);
        doors(b, 10, 1, 4, 0);
        b.fill(11, -3, 1, 45, 0, 9, ModBlocks.RISING_MAGMA.get().defaultBlockState());
        // Pasarela de 3 de ancho con filas de géiseres que se alternan
        b.fill(11, 0, 4, 24, 0, 6, floor);
        b.fill(33, 0, 4, 45, 0, 6, floor);
        int phase = 0;
        for (int x : new int[]{15, 19, 23, 36, 40, 44}) {
            BlockState g = ModBlocks.FIRE_GEYSER.get().defaultBlockState().setValue(FireGeyserBlock.PHASE, phase % 4);
            b.fill(x, 0, 4, x, 0, 6, g);
            phase++;
        }
        // Tramo de basalto inestable
        BlockState crumbling = ModBlocks.CRUMBLING_BASALT.get().defaultBlockState();
        int[] zs = {3, 6, 4, 7};
        for (int i = 0; i < 4; i++) b.fill(25 + 2 * i, 0, zs[i] - 1, 25 + 2 * i, 0, zs[i], crumbling);
        b.fill(25, 0, 4, 25, 0, 6, crumbling);
        b.fill(32, 0, 4, 32, 0, 6, crumbling);

        b.set(12, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        b.set(34, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        for (int x : new int[]{16, 24, 32, 40}) b.set(x, 10, 5, Blocks.SHROOMLIGHT);
        for (int x : new int[]{21, 29, 38}) b.set(x, 10, 2, ModBlocks.ROCKFALL.get());
    }

    // ---------- 3. Galería de estatuas ----------

    private void gallery(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(46, 0, -1, 70, 9, 11, wall, floor);
        b.air(46, 1, 4, 46, 3, 6);
        BlockState statue = ModBlocks.ASH_STATUE.get().defaultBlockState();
        for (int x : new int[]{52, 58, 64}) b.fill(x, 1, 0, x, 8, 10, statue);
        // Matas de pólvora en cada tramo
        for (int x : new int[]{48, 54, 60, 66}) {
            b.set(x, 1, 0, ModBlocks.BOMB_BUSH.get());
            b.set(x, 1, 10, ModBlocks.BOMB_BUSH.get());
        }
        // Géiseres entre los muros
        int phase = 0;
        for (int x : new int[]{55, 61}) {
            for (int z = 3; z <= 7; z += 2) {
                b.set(x, 0, z, ModBlocks.FIRE_GEYSER.get().defaultBlockState().setValue(FireGeyserBlock.PHASE, phase % 4));
                phase++;
            }
        }
        b.set(47, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        b.set(68, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        for (int x : new int[]{49, 55, 61, 67}) b.set(x, 9, 5, Blocks.SHROOMLIGHT);
    }

    // ---------- 4. Antesala ----------

    private void antechamber(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(70, 0, 0, 80, 7, 10, wall, floor);
        b.air(70, 1, 4, 70, 3, 6);
        b.set(72, 0, 5, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{73, 1}, {77, 1}, {73, 9}, {77, 9}}) b.set(c[0], 1, c[1], ModBlocks.SUPPLY_CRATE.get());
        for (int[] c : new int[][]{{73, 3}, {77, 3}, {73, 7}, {77, 7}}) b.set(c[0], 7, c[1], Blocks.SHROOMLIGHT);
        doors(b, 80, 1, 4, 2);
    }

    // ---------- 5. Caverna ----------

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
        b.fill(81, 0, 4, CX - ARENA_R + 4, 0, 6, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());   // puente
        b.air(81, 1, 4, CX - ARENA_R + 1, 3, 6);
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

    private void doors(StructureBuilder b, int x, int y, int z, int channel) {
        BlockState door = ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel);
        b.fill(x, y, z, x, y + 2, z + 2, door);
    }
}
