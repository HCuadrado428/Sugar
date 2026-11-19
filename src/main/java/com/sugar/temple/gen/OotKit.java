package com.sugar.temple.gen;

import com.sugar.block.BigChestBlock;
import com.sugar.block.EventDoorBlock;
import com.sugar.block.KeyChestBlock;
import com.sugar.block.LockedDoorBlock;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleObjectives;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Piezas comunes de los templos al estilo Ocarina: una sala central circular de varios pisos (balcones
 * unidos por rampas), pasillos hasta ella, jaulas que abre un canal, cofres y cerraduras.
 */
final class OotKit {
    static final double IN = 11.5, OUT = 13.2, BALCONY = 8.5;

    final StructureBuilder b;
    final int hx, hz, bottom, roof;
    final int[] floors;
    final Supplier<BlockState> wall, floor;

    OotKit(StructureBuilder b, int hx, int hz, int bottom, int roof, int[] floors, Supplier<BlockState> wall, Supplier<BlockState> floor) {
        this.b = b; this.hx = hx; this.hz = hz; this.bottom = bottom; this.roof = roof; this.floors = floors; this.wall = wall; this.floor = floor;
    }

    double dist(double x, double z) {
        return Math.hypot(x - hx, z - hz);
    }

    /** La sala central: cilindro con balcones en cada piso. Si openBottom, el fondo es un foso sin suelo hasta pitY. */
    void hub(BlockState light, int pitY) {
        int R = (int) Math.ceil(OUT);
        int low = Math.min(bottom, pitY);
        for (int x = hx - R; x <= hx + R; x++)
            for (int z = hz - R; z <= hz + R; z++) {
                double d = dist(x, z);
                if (d > OUT) continue;
                for (int y = low; y <= roof; y++) {
                    boolean isFloor = false;
                    for (int f : floors) if (y == f && d > BALCONY) isFloor = true;
                    boolean solid = d > IN || y == low || y == roof || isFloor;
                    b.set(x, y, z, solid ? (isFloor && d <= IN ? floor.get() : wall.get()) : Blocks.AIR.defaultBlockState());
                }
            }
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8 + Math.PI / 8;
            int x = hx + (int) Math.round(Math.cos(a) * 12), z = hz + (int) Math.round(Math.sin(a) * 12);
            for (int y = low + 4; y < roof; y += 9) b.set(x, y, z, light);
        }
    }

    /** Rampa de 3 de ancho pegada a la pared, de a0 a a1 grados, bajando de top a low. */
    void ramp(double a0, double a1, int top, int low) {
        for (double a = a0; a <= a1; a += 0.4) {
            int y = (int) Math.round(top - (a - a0) / (a1 - a0) * (top - low));
            double rad = Math.toRadians(a);
            for (double r = BALCONY + 0.1; r <= IN - 0.1; r += 0.35) {
                int x = hx + (int) Math.round(Math.cos(rad) * r), z = hz + (int) Math.round(Math.sin(rad) * r);
                if (dist(x, z) > IN) continue;
                b.set(x, y, z, wall.get());
                for (int yy = y + 1; yy <= y + 3 && yy < roof; yy++) b.set(x, yy, z, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Pasillo de 3 de alto (suelo en y) que no toca el interior de la sala central. */
    void corridor(int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                if (dist(x, z) <= IN) continue;
                b.set(x, y, z, floor.get());
                b.air(x, y + 1, z, x, y + 3, z);
            }
    }

    /** Jaula de puertas de evento alrededor de (x, y, z): se abre con su canal. */
    void cage(int x, int y, int z, int channel) {
        BlockState d = door(channel);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) b.set(x + dx, y, z + dz, d);
                b.set(x + dx, y + 1, z + dz, d);
            }
    }

    static BlockState door(int channel) {
        return ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel);
    }

    static BlockState lock(int channel, int keys) {
        return ModBlocks.LOCKED_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel).setValue(LockedDoorBlock.KEYS, keys);
    }

    static BlockState bossDoor() {
        return ModBlocks.BOSS_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, TempleObjectives.V_BOSS_DOOR)
                .setValue(LockedDoorBlock.KEYS, TempleData.BOSS_KEY);
    }

    static BlockState keyChest(int key) {
        return ModBlocks.KEY_CHEST.get().defaultBlockState().setValue(KeyChestBlock.KEY, key);
    }

    static BlockState bigChest(int kind) {
        return ModBlocks.BIG_CHEST.get().defaultBlockState().setValue(BigChestBlock.KIND, kind);
    }

    /** Puerta de 3x3 en una pared x (de z a z+2) o z (de x a x+2). */
    void doorX(int x, int y, int z, BlockState s) { b.fill(x, y, z, x, y + 2, z + 2, s); }
    void doorZ(int x, int y, int z, BlockState s) { b.fill(x, y, z, x + 2, y + 2, z, s); }
}
