package com.sugar.temple.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.function.Supplier;

/** Ayudante para construir por código en coordenadas relativas a un origen. Registra la caja que ocupa. */
public class StructureBuilder {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public final ServerLevel level;
    public final BlockPos origin;
    public final RandomSource random;
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public StructureBuilder(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        this.random = RandomSource.create(origin.asLong());
    }

    /** Desplazamiento en X para las secciones que van después de las salas nuevas. */
    public int ox;
    /** Desplazamiento en Y (para construir una pieza más abajo, como la guarida del jefe). */
    public int oy;

    public BlockPos abs(int x, int y, int z) {
        return origin.offset(x + ox, y + oy, z);
    }

    public void set(int x, int y, int z, BlockState state) {
        BlockPos p = abs(x, y, z);
        level.setBlock(p, state, Block.UPDATE_CLIENTS);
        minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
        maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
    }

    public void set(int x, int y, int z, Block block) {
        set(x, y, z, block.defaultBlockState());
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Supplier<BlockState> state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, state.get());
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        fill(x1, y1, z1, x2, y2, z2, () -> state);
    }

    public void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, AIR);
    }

    /** Sala cerrada: paredes/suelo/techo con el material dado y el interior vacío. */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        fill(x1, y1, z1, x2, y2, z2, wall);
        fill(x1 + 1, y1, z1 + 1, x2 - 1, y1, z2 - 1, floor);
        air(x1 + 1, y1 + 1, z1 + 1, x2 - 1, y2 - 1, z2 - 1);
    }

    /** Elige un estado al azar según pesos. */
    public final Supplier<BlockState> mix(Object... weightsAndStates) {
        return () -> {
            int total = 0;
            for (int i = 0; i < weightsAndStates.length; i += 2) total += (Integer) weightsAndStates[i];
            int r = random.nextInt(total);
            for (int i = 0; i < weightsAndStates.length; i += 2) {
                r -= (Integer) weightsAndStates[i];
                if (r < 0) return toState(weightsAndStates[i + 1]);
            }
            return toState(weightsAndStates[1]);
        };
    }

    private static BlockState toState(Object o) {
        return o instanceof Block b ? b.defaultBlockState() : (BlockState) o;
    }

    public BoundingBox bounds() {
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
