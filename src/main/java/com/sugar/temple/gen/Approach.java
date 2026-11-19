package com.sugar.temple.gen;

import com.sugar.block.EventDoorBlock;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleObjectives;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Exterior de un templo (la "llegada", como la subida al volcán): campamento base, camino con retos y un
 * monte que cubre el templo por fuera. Coordenadas locales del {@link StructureBuilder}, con ox = 0.
 */
final class Approach {

    /** Centro del camino cada medio bloque: {x, y, z}. Va de (x0, y0, zc) a (x1, y1, zc) haciendo eses. */
    static List<int[]> path(int x0, int y0, int x1, int y1, int zc, double amp) {
        List<int[]> out = new ArrayList<>();
        int n = Math.abs(x1 - x0) * 2;
        for (int i = 0; i <= n; i++) {
            double s = i / (double) n;
            double x = x0 + (x1 - x0) * s;
            double z = zc + amp * Math.sin(Math.PI * 1.5 * s) * Math.sin(Math.PI * s);
            double y = y0 + (y1 - y0) * s;
            out.add(new int[]{(int) Math.round(x), (int) Math.round(y), (int) Math.round(z)});
        }
        return out;
    }

    static int[] at(List<int[]> path, double s) {
        return path.get(Math.max(0, Math.min(path.size() - 1, (int) Math.round(s * (path.size() - 1)))));
    }

    /** Pavimenta el camino (ancho 2*hw+1), con relleno debajo y aire encima hasta clearTop (o clearH bloques). */
    static void pave(StructureBuilder b, List<int[]> path, int hw, Supplier<BlockState> floor, Supplier<BlockState> rock, int clearH, int clearTop) {
        for (int[] c : path) {
            for (int w = -hw; w <= hw; w++) {
                int x = c[0], y = c[1], z = c[2] + w;
                for (int yy = y - 3; yy < y; yy++) b.set(x, yy, z, rock.get());
                b.set(x, y, z, floor.get());
                int top = Math.max(y + clearH, clearTop);
                for (int yy = y + 1; yy <= top; yy++) b.set(x, yy, z, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Rellena con roca solo donde hay aire (para no romper el terreno de verdad). */
    static void fillAir(StructureBuilder b, int x1, int y1, int z1, int x2, int y2, int z2, Supplier<BlockState> s) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y1; y <= y2; y++)
                    if (b.level.getBlockState(b.abs(x, y, z)).isAir()) b.set(x, y, z, s.get());
    }

    /**
     * Monte que tapa el templo: sobre cada columna busca el techo de lo construido y apila roca encima hasta una
     * loma suave (más alta en el centro). Solo rellena aire, así que no toca el interior.
     */
    static void drape(StructureBuilder b, BoundingBox world, int margin, Supplier<BlockState> rock, Supplier<BlockState> top) {
        BlockPos o = b.origin;
        int x0 = world.minX() - o.getX() - margin, x1 = world.maxX() - o.getX() + margin;
        int z0 = world.minZ() - o.getZ() - margin, z1 = world.maxZ() - o.getZ() + margin;
        int yLo = world.minY() - o.getY(), yHi = world.maxY() - o.getY();
        int wx = x1 - x0 + 1, wz = z1 - z0 + 1;
        int[][] roof = new int[wx][wz];
        int maxRoof = 0;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int r = Integer.MIN_VALUE;
                for (int y = yHi; y >= Math.max(0, yLo); y--)
                    if (!b.level.getBlockState(b.abs(x, y, z)).isAir()) { r = y; break; }
                roof[x - x0][z - z0] = r;
                maxRoof = Math.max(maxRoof, r);
            }
        double cx = (x0 + x1) / 2.0, cz = (z0 + z1) / 2.0, rx = wx / 2.0, rz = wz / 2.0;
        int H = maxRoof + 7;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                double e = Math.pow((x - cx) / rx, 2) + Math.pow((z - cz) / rz, 2);
                if (e >= 1) continue;
                int r = roof[x - x0][z - z0];
                double noise = Math.sin(x * 0.31) * Math.cos(z * 0.27) * 2.0;
                int s = (int) Math.round(H * (1 - e) + noise);
                if (r != Integer.MIN_VALUE) s = Math.max(s, r + 3);
                int from = r == Integer.MIN_VALUE ? 0 : r + 1;
                for (int y = from; y < s; y++)
                    if (b.level.getBlockState(b.abs(x, y, z)).isAir()) b.set(x, y, z, rock.get());
                if (s >= from && b.level.getBlockState(b.abs(x, s, z)).isAir()) b.set(x, s, z, top.get());
            }
    }

    /** Campamento base con muro y dos puertas: oeste (canal 10, /event open) y este (canal 0, /event start). */
    static void camp(StructureBuilder b, int cx, int y, int cz, int hx, int hz, Supplier<BlockState> fence, Supplier<BlockState> ground,
                     BlockState tent, BlockState tentTop, BlockState fire, BlockState lamp) {
        int x1 = cx - hx, x2 = cx + hx, z1 = cz - hz, z2 = cz + hz;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                for (int yy = y - 3; yy < y; yy++) b.set(x, yy, z, Blocks.DIRT.defaultBlockState());
                b.set(x, y, z, ground.get());
                for (int yy = y + 1; yy <= y + 8; yy++) b.set(x, yy, z, Blocks.AIR.defaultBlockState());
            }
        for (int x = x1; x <= x2; x++) { b.fill(x, y + 1, z1, x, y + 3, z1, fence); b.fill(x, y + 1, z2, x, y + 3, z2, fence); }
        for (int z = z1; z <= z2; z++) { b.fill(x1, y + 1, z, x1, y + 3, z, fence); b.fill(x2, y + 1, z, x2, y + 3, z, fence); }
        b.fill(x1, y + 1, cz - 1, x1, y + 3, cz + 1, door(TempleObjectives.ENTRANCE));
        b.fill(x2, y + 1, cz - 1, x2, y + 3, cz + 1, door(0));
        for (int dz : new int[]{-2, 2}) { b.set(x1, y + 4, cz + dz, lamp); b.set(x2, y + 4, cz + dz, lamp); }
        for (int[] t : new int[][]{{x1 + 3, z1 + 3}, {x1 + 3, z2 - 4}, {x2 - 5, z1 + 3}, {x2 - 5, z2 - 4}}) {
            b.fill(t[0], y + 1, t[1], t[0] + 2, y + 2, t[1] + 1, tent);
            b.air(t[0] + 1, y + 1, t[1], t[0] + 1, y + 1, t[1]);
            b.fill(t[0] + 1, y + 3, t[1], t[0] + 1, y + 3, t[1] + 1, tentTop);
        }
        b.set(cx, y + 1, cz + 4, fire);
        b.set(cx - 4, y + 1, cz, ModBlocks.SUPPLY_CRATE.get());
        b.set(cx + 4, y + 1, cz, ModBlocks.SUPPLY_CRATE.get());
        b.set(cx, y, cz - 1, ModBlocks.CHECKPOINT_STONE.get());
    }

    static BlockState door(int channel) {
        return ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel);
    }

    private Approach() {}
}
