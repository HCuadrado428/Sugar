package com.sugar.temple.gen;

import com.sugar.block.EventDoorBlock;
import com.sugar.block.GroupPlateBlock;
import com.sugar.block.WaveAltarBlock;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleObjectives;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/** Salas de "modo evento" que comparten los tres templos. Coordenadas relativas, eje principal +X. */
public final class EventRooms {

    /** Puerta de 3x3 en la pared x (de z a z+2). */
    public static void doorsX(StructureBuilder b, int x, int y, int z, int channel) {
        BlockState door = ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel);
        b.fill(x, y, z, x, y + 2, z + 2, door);
    }

    /** Puerta de 3x3 en la pared z (de x a x+2). */
    public static void doorsZ(StructureBuilder b, int x, int y, int z, int channel) {
        BlockState door = ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel);
        b.fill(x, y, z, x + 2, y + 2, z, door);
    }

    /**
     * Sala de oleadas: altar en el centro, cuatro columnas para cubrirse, entrada abierta en x1 y puerta
     * del canal en x2. Las columnas tienen un escalón para subir a una de ellas (ahí puede ir una reliquia).
     */
    public static int[] waveRoom(StructureBuilder b, int x1, int x2, int z1, int z2, int h, Supplier<BlockState> wall,
                                 Supplier<BlockState> floor, BlockState pillar, BlockState light, int channel) {
        int zm = (z1 + z2) / 2, xm = (x1 + x2) / 2;
        b.room(x1, 0, z1, x2, h, z2, wall, floor);
        b.air(x1, 1, zm - 1, x1, 3, zm + 1);
        b.set(xm, 0, zm, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(xm, 1, zm, ModBlocks.WAVE_ALTAR.get().defaultBlockState().setValue(WaveAltarBlock.CHANNEL, channel));
        int qx = (x2 - x1) / 4, qz = (z2 - z1) / 4;
        for (int[] c : new int[][]{{xm - qx, zm - qz}, {xm + qx, zm - qz}, {xm - qx, zm + qz}, {xm + qx, zm + qz}}) {
            b.fill(c[0], 1, c[1], c[0] + 1, 3, c[1] + 1, pillar);
        }
        b.set(xm + qx - 2, 1, zm + qz, pillar);                       // escalera de dos peldaños a la columna del fondo
        b.fill(xm + qx - 1, 1, zm + qz, xm + qx - 1, 2, zm + qz, pillar);
        b.set(x1 + 2, 0, zm, ModBlocks.CHECKPOINT_STONE.get());
        for (int[] c : new int[][]{{xm - qx, zm}, {xm + qx, zm}, {xm, zm - qz}, {xm, zm + qz}}) b.set(c[0], h, c[1], light);
        doorsX(b, x2, 1, zm - 1, channel);
        return new int[]{xm + qx, 4, zm + qz};                         // encima de esa columna
    }

    /**
     * Sala de las placas: un foso con cuatro islotes; en cada uno una Placa de Grupo. Hace falta alguien
     * en cada placa a la vez para abrir la puerta del canal en x2. Se llega con piedras de salto.
     * stone: bloque de las piedras (puede ser invisible, como la plataforma del eco).
     */
    public static void plateRoom(StructureBuilder b, int x1, int x2, int z1, int z2, int h, int depth, Supplier<BlockState> wall,
                                 BlockState pitFill, int fillLayers, BlockState island, BlockState stone, BlockState light, int channel) {
        int zm = (z1 + z2) / 2;
        b.room(x1, -depth, z1, x2, h, z2, wall, () -> island);
        b.fill(x1 + 1, -depth + 1, z1 + 1, x2 - 1, -depth + fillLayers, z2 - 1, pitFill);
        b.air(x1, 1, zm - 1, x1, 3, zm + 1);
        // Rellanos de entrada y salida
        b.fill(x1 + 1, -depth + 1, zm - 1, x1 + 2, 0, zm + 1, island);
        b.fill(x2 - 2, -depth + 1, zm - 1, x2 - 1, 0, zm + 1, island);
        b.set(x1 + 1, 0, zm, ModBlocks.CHECKPOINT_STONE.get());
        // Cuatro islotes con placa
        int ax = x1 + 5, bx = x2 - 5, az = z1 + 3, bz = z2 - 3;
        int[][] isl = {{ax, az}, {ax, bz}, {bx, az}, {bx, bz}};
        for (int[] c : isl) {
            b.fill(c[0] - 1, -depth + 1, c[1] - 1, c[0] + 1, 0, c[1] + 1, island);
            b.set(c[0], 0, c[1], ModBlocks.GROUP_PLATE.get().defaultBlockState().setValue(GroupPlateBlock.CHANNEL, channel));
        }
        // Piedras de salto: rellano -> islotes de la izquierda -> islotes de la derecha -> rellano de salida
        stones(b, x1 + 2, zm, ax, az, stone);
        stones(b, x1 + 2, zm, ax, bz, stone);
        stones(b, ax, az, bx, az, stone);
        stones(b, ax, bz, bx, bz, stone);
        stones(b, bx, az, x2 - 2, zm, stone);
        stones(b, bx, bz, x2 - 2, zm, stone);
        for (int[] c : isl) b.set(c[0], h, c[1], light);
        doorsX(b, x2, 1, zm - 1, channel);
    }

    /** Piedras sueltas cada 2 bloques entre dos puntos (a la altura del suelo, y=0). */
    private static void stones(StructureBuilder b, int xa, int za, int xb, int zb, BlockState stone) {
        double len = Math.hypot(xb - xa, zb - za);
        int n = (int) Math.floor(len / 2.0);
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            int x = (int) Math.round(xa + (xb - xa) * t), z = (int) Math.round(za + (zb - za) * t);
            if (Math.abs(x - xa) <= 1 && Math.abs(z - za) <= 1 || Math.abs(x - xb) <= 1 && Math.abs(z - zb) <= 1) continue;
            b.set(x, 0, z, stone);
        }
    }

    /** Cámara del tesoro pegada a una pared z (la pared es z0), hacia -z. Puerta del canal 8. */
    public static void vault(StructureBuilder b, int x1, int y, int z0, Supplier<BlockState> wall, Supplier<BlockState> floor, BlockState light) {
        b.room(x1, y, z0 - 7, x1 + 8, y + 5, z0, wall, floor);
        for (int dx : new int[]{2, 4, 6}) b.set(x1 + dx, y + 1, z0 - 5, ModBlocks.TREASURE_CRATE.get());
        b.set(x1 + 4, y + 5, z0 - 3, light);
        b.set(x1 + 1, y + 1, z0 - 6, Blocks.GOLD_BLOCK);
        b.set(x1 + 7, y + 1, z0 - 6, Blocks.GOLD_BLOCK);
        doorsZ(b, x1 + 3, y + 1, z0, TempleObjectives.VAULT);
    }

    public static void relic(StructureBuilder b, int x, int y, int z) {
        b.set(x, y, z, ModBlocks.RELIC.get());
    }

    private EventRooms() {}
}
