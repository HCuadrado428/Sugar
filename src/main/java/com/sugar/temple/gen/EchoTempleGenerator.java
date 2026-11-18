package com.sugar.temple.gen;

import com.sugar.block.EchoDrumBlock;
import com.sugar.block.EventDoorBlock;
import com.sugar.entity.EchoDrummer;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Santuario del Eco (templo por defecto). Se construye hacia +X desde la posición del admin:
 *
 *  1. Vestíbulo (x 0-12)             punto de reunión, atriles con las Lentes del Eco, puerta canal 0
 *  2. Puente invisible (x 12-52)     un abismo con un camino que solo se ve con la lente; quien la
 *                                    lleve guía a los demás. Si caes, hay agua abajo y una escalera
 *  3. Sala de los Tambores (x 52-74) "Simón dice": tocad el gong y repetid el ritmo; abre el canal 1
 *  4. Antesala (x 74-84)             checkpoint, cofres, dos atriles más, puerta canal 2
 *  5. Gran Tambor (centro x 110)     arena circular flotando sobre el abismo
 */
public class EchoTempleGenerator implements TempleGenerator {
    public static final int CX = 110, CZ = 7, CAVE_R = 24, CAVE_H = 22;
    private static final int PIT = -16;

    /** Camino del puente invisible: puntos por los que pasa (x, z). */
    static final int[][] PATH = {{16, 7}, {20, 7}, {20, 3}, {27, 3}, {27, 11}, {33, 11}, {37, 11}, {42, 11}, {42, 4}, {46, 4}, {46, 9}, {51, 9}, {51, 7}};

    @Override
    public Result generate(ServerLevel level, BlockPos feet) {
        StructureBuilder b = new StructureBuilder(level, feet.offset(-6, -1, -7));

        Supplier<BlockState> wall = b.mix(6, Blocks.DEEPSLATE_BRICKS, 2, Blocks.DEEPSLATE_TILES, 1, Blocks.CRACKED_DEEPSLATE_BRICKS, 1, Blocks.SCULK);
        Supplier<BlockState> floor = b.mix(5, Blocks.POLISHED_DEEPSLATE, 3, Blocks.DEEPSLATE_TILES, 1, Blocks.SCULK);
        Supplier<BlockState> rock = b.mix(6, Blocks.DEEPSLATE, 2, Blocks.COBBLED_DEEPSLATE, 1, Blocks.TUFF, 1, Blocks.SCULK);

        arena(b, rock);
        lobby(b, wall, floor);
        abyss(b, wall);
        drumRoom(b, wall, floor);
        antechamber(b, wall, floor);

        return new Result(b.bounds(), feet);
    }

    // ---------- 1. Vestíbulo ----------

    private void lobby(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(0, 0, 0, 12, 7, 14, wall, floor);
        b.set(6, 0, 7, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{3, 3}, {9, 3}, {3, 11}, {9, 11}}) b.set(c[0], 6, c[1], Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        for (int[] c : new int[][]{{3, 1}, {6, 1}, {9, 1}, {3, 13}, {6, 13}, {9, 13}}) b.set(c[0], 1, c[1], ModBlocks.LENS_PEDESTAL.get());
        b.air(0, 1, 7, 0, 2, 7);
        doors(b, 12, 1, 6, 0);
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
        doors(b, 74, 1, 6, 1);
    }

    // ---------- 4. Antesala ----------

    private void antechamber(StructureBuilder b, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        b.room(74, 0, 1, 84, 7, 13, wall, floor);
        doors(b, 74, 1, 6, 1);
        b.set(76, 0, 7, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{77, 2}, {81, 2}, {77, 12}, {81, 12}}) b.set(c[0], 1, c[1], ModBlocks.SUPPLY_CRATE.get());
        b.set(79, 1, 2, ModBlocks.LENS_PEDESTAL.get());
        b.set(79, 1, 12, ModBlocks.LENS_PEDESTAL.get());
        for (int[] c : new int[][]{{77, 4}, {81, 4}, {77, 10}, {81, 10}}) b.set(c[0], 7, c[1], Blocks.SHROOMLIGHT);
        doors(b, 84, 1, 6, 2);
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
        b.fill(85, 0, 6, CX - R, 0, 8, Blocks.POLISHED_DEEPSLATE_BRICKS.defaultBlockState());
        b.air(85, 1, 6, CX - R + 1, 3, 8);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = PIT + 1; y <= 0; y++) {
            b.set(CX - CAVE_R + 1, y, 10, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            b.set(CX - CAVE_R + 2, y, 10, ladder);
        }
        b.fill(CX - CAVE_R + 1, 0, 9, CX - CAVE_R + 3, 0, 9, Blocks.POLISHED_DEEPSLATE_BRICKS.defaultBlockState());
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
