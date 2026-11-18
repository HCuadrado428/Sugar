package com.sugar.temple;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Construye un templo por defecto en el mundo. Cada templo tendrá el suyo. */
@FunctionalInterface
public interface TempleGenerator {

    Result generate(ServerLevel level, BlockPos origin);

    /** Zona ocupada por el templo y punto de reunión de los jugadores. */
    record Result(BoundingBox zone, BlockPos lobby) {}
}
