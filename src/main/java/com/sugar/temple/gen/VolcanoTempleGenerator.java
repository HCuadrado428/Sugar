package com.sugar.temple.gen;

import com.sugar.block.BigChestBlock;
import com.sugar.block.EventDoorBlock;
import com.sugar.block.EventSwitchBlock;
import com.sugar.block.FireGeyserBlock;
import com.sugar.block.GroupPlateBlock;
import com.sugar.block.KeyChestBlock;
import com.sugar.block.LockedDoorBlock;
import com.sugar.block.WaveAltarBlock;
import com.sugar.block.entity.MagmaRiserBlockEntity;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.sugar.temple.TempleObjectives.*;

/**
 * Templo del Volcán al estilo Ocarina of Time, pensado para 25-50 jugadores.
 *
 * Fuera: una montaña de unos 180 bloques de base y 56 de alto. El admin se pone donde quiere el
 * campamento base (pie sur); un sendero en espiral de dos vueltas sube hasta el borde del cráter
 * (géiseres, coladas de lava con puentes que se hunden, cornisa con desprendimientos, campamento a media
 * subida con la reliquia 1 en una cueva y mirador arriba). En el fondo del cráter está la fachada del templo.
 *
 * Dentro: la Gran Caldera (pozo circular con balcones en tres pisos y un lago de magma al fondo) y,
 * alrededor, las salas:
 *   Piso 3 (y 36): Vestíbulo, Pasarela de lava (Llave 1), Galería de las Cadenas (Brújula, reliquia 2)
 *   Piso 2 (y 26): La Forja (oleadas + Capataz: Martillo para todos), Sala de los sellos (🔒1 → Llave 2),
 *                  Puente de Brasas (🔒2+3, 8 placas: vacía el lago y da la Llave 4)
 *   Piso 1 (y 16): Pozo de ascenso (muro agrietado → Llave 3), Sala del Martillo (🔒4, 6 interruptores →
 *                  Llave del Jefe), Cámara oculta (muro agrietado, reliquia 3) y tesoro (canal 8)
 *   Fondo (y 3):   Puerta del Jefe (canal 11) → túnel → Corazón del Volcán (y -17), la guarida de la Sierpe.
 *
 * Coordenadas locales: x este, z sur; el origen queda 84 bloques al norte del admin y uno por debajo.
 */
public class VolcanoTempleGenerator implements TempleGenerator {
    // Montaña
    private static final int MZ = -14, TOP = 56, BASE_R = 90, FLAT_R = 26;
    // Cráter
    private static final int CRATER_FLOOR = 36, CRATER_FLAT = 10, CRATER_RIM = 30;
    // Gran Caldera
    private static final int HX = 0, HZ = -34, HUB_FLOOR = 3, HUB_ROOF = 44;
    private static final double HUB_IN = 11.5, HUB_OUT = 13.2, BALCONY_IN = 8.5;
    private static final int F3 = 36, F2 = 26, F1 = 16;
    // Guarida
    private static final int LX = 0, LZ = -92, LAIR_FLOOR = -17, LAIR_R = 24;
    // Campamento base (el admin está en su centro)
    private static final int CAMP_Z = 85;

    private Supplier<BlockState> rock, wall, floor, path;

    @Override
    public Result generate(ServerLevel level, BlockPos feet) {
        StructureBuilder b = new StructureBuilder(level, feet.offset(0, -1, -CAMP_Z));
        rock = b.mix(6, Blocks.BASALT, 4, Blocks.BLACKSTONE, 2, Blocks.SMOOTH_BASALT, 1, Blocks.TUFF);
        wall = b.mix(6, Blocks.POLISHED_BLACKSTONE_BRICKS, 2, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, 1, Blocks.BLACKSTONE);
        floor = b.mix(5, Blocks.POLISHED_BASALT, 3, Blocks.BLACKSTONE, 1, Blocks.POLISHED_BLACKSTONE);
        path = b.mix(4, Blocks.TUFF, 3, Blocks.SMOOTH_BASALT, 2, Blocks.COARSE_DIRT, 1, Blocks.GRAVEL);

        mountain(b);
        List<int[]> trail = trail(b);
        trailFeatures(b, trail);
        baseCamp(b);
        crater(b);

        hub(b);
        vestibule(b);
        lavaWalk(b);
        gallery(b);
        forge(b);
        seals(b);
        bridge(b);
        shaft(b);
        hammerRoom(b);
        hiddenRoom(b);
        bossPath(b);
        lair(b);
        OotKit.nests(b, new int[][]{{-30, F3 + 5, -27, 0}, {25, F3 + 6, -34, 0}, {0, F3 - 3, -34, 0}, {25, F2 + 1, -28, 3},
                {17, F1 + 1, -30, 3}, {34, F1 + 5, -42, 0}, {-27, F1 + 18, -45, 0}, {0, F2 + 5, -60, 0}});

        return new Result(b.bounds(), feet);
    }

    // ================================================================= Exterior

    private static double mountainH(double d) {
        if (d <= FLAT_R) return TOP;
        if (d >= BASE_R) return -1;
        double u = (d - FLAT_R) / (BASE_R - FLAT_R);
        return TOP * (1 - u * u);
    }

    /** Altura del suelo exterior en (x, z) (montaña y cráter), o -1 fuera de la montaña. */
    static int surface(int x, int z) {
        double h = mountainH(Math.hypot(x, z - MZ));
        if (h < 0) return -1;
        double dc = Math.hypot(x, z);
        if (dc < CRATER_RIM) h = Math.min(h, CRATER_FLOOR + Math.max(0, dc - CRATER_FLAT));
        return (int) Math.floor(h);
    }

    private void mountain(StructureBuilder b) {
        for (int x = -BASE_R; x <= BASE_R; x++) {
            for (int z = MZ - BASE_R; z <= MZ + BASE_R; z++) {
                int s = surface(x, z);
                if (s < 0) continue;
                int bottom = Math.max(0, s - 9);
                for (int y = bottom; y < s; y++) b.set(x, y, z, rock.get());
                double dc = Math.hypot(x, z);
                BlockState top = dc < CRATER_FLAT + 1 ? (b.random.nextInt(9) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState())
                        : b.random.nextInt(40) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : s < 10 && b.random.nextInt(3) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : rock.get();
                b.set(x, s, z, top);
                // Quita árboles y terreno que asomen por encima de la ladera
                for (int y = s + 1; y <= s + 14; y++) {
                    BlockPos p = b.abs(x, y, z);
                    if (!b.level.getBlockState(p).isAir()) b.set(x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** Sendero en espiral: dos vueltas desde el pie sur (radio 90) hasta el borde sur del cráter (radio 44). Devuelve el centro cada ~0,5 bloques: {x, y, z}. */
    private List<int[]> trail(StructureBuilder b) {
        List<int[]> centers = new ArrayList<>();
        Map<Long, Integer> cells = new HashMap<>();
        int steps = 3400;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double a = Math.toRadians(90 - 720 * t), r = 90 - 46 * t;
            double cx = Math.cos(a) * r, cz = MZ + Math.sin(a) * r;
            int y = (int) Math.round(Math.max(0, mountainH(r)));
            centers.add(new int[]{(int) Math.round(cx), y, (int) Math.round(cz)});
            for (double w = -2.4; w <= 2.4; w += 0.4) {
                int x = (int) Math.round(cx + Math.cos(a) * w), z = (int) Math.round(cz + Math.sin(a) * w);
                cells.merge(((long) x << 32) | (z & 0xffffffffL), y, Math::max);
            }
        }
        for (Map.Entry<Long, Integer> e : cells.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey(), y = e.getValue();
            for (int yy = Math.max(0, y - 3); yy < y; yy++) b.set(x, yy, z, rock.get());
            b.set(x, y, z, path.get());
            for (int yy = y + 1; yy <= y + 4; yy++) b.set(x, yy, z, Blocks.AIR.defaultBlockState());
        }
        // Piedras sueltas en el borde de fuera (no tapan el paso)
        for (int i = 0; i < centers.size(); i += 37) {
            double t = i / (double) steps, a = Math.toRadians(90 - 720 * t);
            int[] c = centers.get(i);
            int x = (int) Math.round(c[0] + Math.cos(a) * 3), z = (int) Math.round(c[2] + Math.sin(a) * 3);
            if (!cells.containsKey(((long) x << 32) | (z & 0xffffffffL))) b.set(x, c[1] + 1, z, Blocks.BASALT);
        }
        return centers;
    }

    private void trailFeatures(StructureBuilder b, List<int[]> c) {
        int n = c.size() - 1;
        // 1. Ladera de ceniza: géiseres en el camino, en fases alternas
        for (int k = 0; k < 7; k++) {
            int[] p = c.get((int) (n * (0.06 + 0.022 * k)));
            int[] q = side(c, (int) (n * (0.06 + 0.022 * k)), k % 2 == 0 ? -1 : 1);
            b.set(q[0], p[1], q[2], ModBlocks.FIRE_GEYSER.get().defaultBlockState().setValue(FireGeyserBlock.PHASE, k % 4));
        }
        b.set(c.get((int) (n * 0.05))[0], c.get((int) (n * 0.05))[1], c.get((int) (n * 0.05))[2], ModBlocks.CHECKPOINT_STONE.get());
        // 2. Campamento de la ladera, con la cueva de la reliquia 1
        midCamp(b, c, (int) (n * 0.30));
        // 3. Coladas de lava con puentes que se hunden
        for (double t : new double[]{0.40, 0.46, 0.52}) lavaRiver(b, c, (int) (n * t));
        // 4. Cornisa con techo y desprendimientos
        ledge(b, c, (int) (n * 0.60), (int) (n * 0.72));
        b.set(c.get((int) (n * 0.58))[0], c.get((int) (n * 0.58))[1], c.get((int) (n * 0.58))[2], ModBlocks.CHECKPOINT_STONE.get());
        // 5. Borde del cráter: el volcán escupe fuego sobre el camino
        for (int k = 0; k < 8; k++) {
            int i = (int) (n * (0.80 + 0.021 * k));
            int[] q = side(c, i, k % 3 - 1);
            b.set(q[0], c.get(i)[1], q[2], ModBlocks.FIRE_GEYSER.get().defaultBlockState().setValue(FireGeyserBlock.PHASE, (k * 3) % 4));
        }
        // 6. Mirador y bajada al cráter
        int[] end = c.get(n);
        b.fill(end[0] - 3, end[1], end[2] - 3, end[0] + 3, end[1], end[2] + 3, floor);
        b.air(end[0] - 3, end[1] + 1, end[2] - 3, end[0] + 3, end[1] + 5, end[2] + 3);
        b.set(end[0], end[1], end[2], ModBlocks.CHECKPOINT_STONE.get());
        for (int z = end[2] - 4; z >= CRATER_FLAT - 1; z--) {               // escalera tallada hacia el fondo
            int y = Math.max(CRATER_FLOOR, surface(0, z));
            b.fill(-1, y, z, 1, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            b.air(-1, y + 1, z, 1, y + 4, z);
        }
    }

    /** Celda a un lado del centro del sendero (w = -1 dentro, +1 fuera). */
    private static int[] side(List<int[]> c, int i, int w) {
        int n = c.size() - 1;
        double a = Math.toRadians(90 - 720 * (i / (double) n));
        int[] p = c.get(i);
        return new int[]{(int) Math.round(p[0] + Math.cos(a) * w * 1.6), p[1], (int) Math.round(p[2] + Math.sin(a) * w * 1.6)};
    }

    private void midCamp(StructureBuilder b, List<int[]> c, int i) {
        int[] p = c.get(i);
        b.fill(p[0] - 4, p[1] - 2, p[2] - 4, p[0] + 4, p[1], p[2] + 4, rock);
        b.fill(p[0] - 4, p[1], p[2] - 4, p[0] + 4, p[1], p[2] + 4, path);
        b.air(p[0] - 4, p[1] + 1, p[2] - 4, p[0] + 4, p[1] + 5, p[2] + 4);
        b.set(p[0], p[1], p[2], ModBlocks.CHECKPOINT_STONE.get());
        b.set(p[0] - (int) Math.signum(-p[0]) * 3, p[1] + 1, p[2] - (int) Math.signum(MZ - p[2]) * 3, Blocks.CAMPFIRE);
        b.set(p[0] - 3, p[1] + 1, p[2] + 3, ModBlocks.SUPPLY_CRATE.get());
        b.set(p[0] + 3, p[1] + 1, p[2] - 3, ModBlocks.SUPPLY_CRATE.get());
        // Cueva hacia el interior de la montaña
        double dx = -p[0], dz = MZ - p[2], len = Math.hypot(dx, dz);
        dx /= len; dz /= len;
        int[] last = null;
        for (int k = 4; k <= 12; k++) {
            int x = (int) Math.round(p[0] + dx * k), z = (int) Math.round(p[2] + dz * k);
            b.fill(x - 2, p[1], z - 2, x + 2, p[1] + 5, z + 2, rock);
            b.air(x - 1, p[1] + 1, z - 1, x + 1, p[1] + 3, z + 1);
            b.set(x, p[1], z, floor.get());
            last = new int[]{x, z};
        }
        b.set(last[0], p[1] + 4, last[1], Blocks.SHROOMLIGHT);
        EventRooms.relic(b, last[0], p[1] + 1, last[1]);
    }

    private void lavaRiver(StructureBuilder b, List<int[]> c, int i) {
        int n = c.size() - 1;
        double a = Math.toRadians(90 - 720 * (i / (double) n));
        double ox = Math.cos(a), oz = Math.sin(a);                         // hacia fuera (cuesta abajo)
        double tx = -Math.sin(a), tz = Math.cos(a);                        // a lo largo del sendero
        int[] p = c.get(i);
        BlockState magma = ModBlocks.RISING_MAGMA.get().defaultBlockState();
        BlockState crumble = ModBlocks.CRUMBLING_BASALT.get().defaultBlockState();
        for (double s = -1.5; s <= 1.5; s += 0.5) {
            for (double w = -2.4; w <= 2.4; w += 0.4) {
                int x = (int) Math.round(p[0] + tx * s + ox * w), z = (int) Math.round(p[2] + tz * s + oz * w);
                b.set(x, p[1] - 2, z, rock.get());
                b.set(x, p[1] - 1, z, magma);
                b.set(x, p[1], z, Math.abs(w) <= 1.3 ? crumble : magma);
            }
        }
        // La colada sigue ladera abajo por la superficie y baja también desde arriba
        for (int w = 3; w <= 14; w++) {
            for (double s = -1; s <= 1; s += 0.5) {
                int x = (int) Math.round(p[0] + ox * w + tx * s), z = (int) Math.round(p[2] + oz * w + tz * s);
                int y = surface(x, z);
                if (y > 0 && y < p[1]) b.set(x, y, z, magma);
            }
        }
        for (int w = 3; w <= 8; w++) {
            for (double s = -1; s <= 1; s += 0.5) {
                int x = (int) Math.round(p[0] - ox * w + tx * s), z = (int) Math.round(p[2] - oz * w + tz * s);
                int y = surface(x, z);
                if (y > p[1] + 4) b.set(x, y, z, magma);
            }
        }
    }

    private void ledge(StructureBuilder b, List<int[]> c, int from, int to) {
        int n = c.size() - 1;
        for (int i = from; i <= to; i += 2) {
            double a = Math.toRadians(90 - 720 * (i / (double) n));
            int[] p = c.get(i);
            for (double w = -2.4; w <= 2.4; w += 0.4) {
                int x = (int) Math.round(p[0] + Math.cos(a) * w), z = (int) Math.round(p[2] + Math.sin(a) * w);
                b.set(x, p[1] + 5, z, rock.get());
                b.set(x, p[1] + 6, z, rock.get());
            }
            if ((i - from) % 40 == 0) {                                     // columnas del lado de fuera
                int x = (int) Math.round(p[0] + Math.cos(a) * 2.8), z = (int) Math.round(p[2] + Math.sin(a) * 2.8);
                b.fill(x, p[1], z, x, p[1] + 5, z, Blocks.BASALT.defaultBlockState());
            }
            if ((i - from) % 70 == 0) b.set(p[0], p[1] + 5, p[2], ModBlocks.ROCKFALL.get());
        }
    }

    private void baseCamp(StructureBuilder b) {
        int z1 = CAMP_Z - 7, z2 = CAMP_Z + 7;
        b.fill(-10, -3, z1, 10, -1, z2, Blocks.DIRT.defaultBlockState());
        b.fill(-10, 0, z1, 10, 0, z2, b.mix(5, Blocks.COARSE_DIRT, 3, Blocks.PODZOL, 2, Blocks.GRAVEL));
        b.air(-10, 1, z1, 10, 8, z2);
        // Empalizada
        for (int x = -10; x <= 10; x++) {
            b.fill(x, 1, z1, x, 3, z1, Blocks.SPRUCE_LOG.defaultBlockState());
            b.fill(x, 1, z2, x, 3, z2, Blocks.SPRUCE_LOG.defaultBlockState());
        }
        for (int z = z1; z <= z2; z++) {
            b.fill(-10, 1, z, -10, 3, z, Blocks.SPRUCE_LOG.defaultBlockState());
            b.fill(10, 1, z, 10, 3, z, Blocks.SPRUCE_LOG.defaultBlockState());
        }
        BlockState gateIn = ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, ENTRANCE);
        BlockState gateOut = ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, 0);
        b.fill(-1, 1, z2, 1, 3, z2, gateIn);          // sur: entrada al campamento (/event open)
        b.fill(-1, 1, z1, 1, 3, z1, gateOut);         // norte: camino a la montaña (/event start)
        for (int x : new int[]{-2, 2}) { b.set(x, 4, z1, Blocks.LANTERN); b.set(x, 4, z2, Blocks.LANTERN); }
        // Tiendas, fuego, cajas y piedra de checkpoint
        for (int[] t : new int[][]{{-7, CAMP_Z - 4}, {-7, CAMP_Z + 3}, {6, CAMP_Z - 4}, {6, CAMP_Z + 3}}) {
            b.fill(t[0], 1, t[1], t[0] + 2, 1, t[1] + 1, Blocks.WHITE_WOOL.defaultBlockState());
            b.fill(t[0], 2, t[1], t[0] + 2, 2, t[1] + 1, Blocks.WHITE_WOOL.defaultBlockState());
            b.air(t[0] + 1, 1, t[1], t[0] + 1, 1, t[1]);
            b.fill(t[0] + 1, 3, t[1], t[0] + 1, 3, t[1] + 1, Blocks.RED_WOOL.defaultBlockState());
        }
        b.set(0, 1, CAMP_Z + 3, Blocks.CAMPFIRE);
        for (int[] s : new int[][]{{-4, CAMP_Z}, {4, CAMP_Z}, {-4, CAMP_Z - 5}, {4, CAMP_Z - 5}}) b.set(s[0], 1, s[1], ModBlocks.SUPPLY_CRATE.get());
        b.set(0, 0, CAMP_Z - 1, ModBlocks.CHECKPOINT_STONE.get());
        // Camino hasta el principio del sendero
        for (int z = z1 - 1; z >= z1 - 5; z--) {
            int y = Math.max(0, surface(0, z));
            b.fill(-2, 0, z, 2, y, z, path);
            b.air(-2, y + 1, z, 2, y + 4, z);
        }
    }

    private void crater(StructureBuilder b) {
        // Fachada del templo en la pared norte del cráter
        b.fill(-7, CRATER_FLOOR, -14, 7, CRATER_FLOOR + 10, -14, wall);
        for (int x : new int[]{-7, -4, 4, 7}) b.fill(x, CRATER_FLOOR, -13, x, CRATER_FLOOR + 9, -13, Blocks.POLISHED_BASALT.defaultBlockState());
        b.fill(-7, CRATER_FLOOR + 10, -13, 7, CRATER_FLOOR + 10, -13, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        b.fill(-2, CRATER_FLOOR + 6, -13, 2, CRATER_FLOOR + 6, -13, Blocks.MAGMA_BLOCK.defaultBlockState());
        b.set(-2, CRATER_FLOOR + 8, -13, Blocks.SHROOMLIGHT); b.set(2, CRATER_FLOOR + 8, -13, Blocks.SHROOMLIGHT);
        // Paso desde el fondo del cráter
        for (int z = -14; z <= -10; z++) {
            b.fill(-1, CRATER_FLOOR, z, 1, CRATER_FLOOR, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            b.air(-1, CRATER_FLOOR + 1, z, 1, CRATER_FLOOR + 4, z);
        }
    }

    // ================================================================= Gran Caldera

    private static double hubDist(double x, double z) {
        return Math.hypot(x - HX, z - HZ);
    }

    private void hub(StructureBuilder b) {
        int R = (int) Math.ceil(HUB_OUT);
        for (int x = HX - R; x <= HX + R; x++) {
            for (int z = HZ - R; z <= HZ + R; z++) {
                double d = hubDist(x, z);
                if (d > HUB_OUT) continue;
                for (int y = HUB_FLOOR; y <= HUB_ROOF; y++) {
                    boolean solid = d > HUB_IN || y == HUB_FLOOR || y == HUB_ROOF
                            || (d > BALCONY_IN && (y == F3 || y == F2 || y == F1));
                    b.set(x, y, z, solid ? (y == F3 || y == F2 || y == F1) && d <= HUB_IN ? floor.get() : wall.get() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // Luces en la pared y cadenas colgando del techo
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8 + Math.PI / 8;
            int x = HX + (int) Math.round(Math.cos(a) * 12), z = HZ + (int) Math.round(Math.sin(a) * 12);
            for (int y : new int[]{10, 21, 31, 41}) b.set(x, y, z, Blocks.SHROOMLIGHT);
        }
        for (int[] p : new int[][]{{-4, -2}, {4, 2}, {3, -5}, {-3, 4}, {0, 0}}) {
            int len = 6 + Math.abs(p[0] * 2 + p[1]);
            for (int y = HUB_ROOF - 1; y >= HUB_ROOF - len; y--) b.set(HX + p[0], y, HZ + p[1], Blocks.CHAIN);
            b.set(HX + p[0], HUB_ROOF - len - 1, HZ + p[1], Blocks.LANTERN);
        }
        // Rampas entre pisos, pegadas a la pared
        ramp(b, 225, 315, F3, F2);
        ramp(b, 100, 190, F2, F1);
        ramp(b, 300, 420, F1, HUB_FLOOR);
        // Lago de magma (se vacía con las placas del Puente de Brasas)
        BlockState lake = ModBlocks.MAGMA_LAKE.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, V_BRIDGE);
        for (int x = HX - 12; x <= HX + 12; x++)
            for (int z = HZ - 12; z <= HZ + 12; z++)
                if (hubDist(x, z) <= HUB_IN)
                    for (int y = HUB_FLOOR + 1; y <= HUB_FLOOR + 3; y++)
                        if (b.level.getBlockState(b.abs(x, y, z)).isAir()) b.set(x, y, z, lake);
        // Puerta del jefe al norte, visible por encima del magma
        BlockState bossDoor = ModBlocks.BOSS_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, V_BOSS_DOOR)
                .setValue(LockedDoorBlock.KEYS, TempleData.BOSS_KEY);
        b.fill(HX - 1, HUB_FLOOR + 1, HZ - 12, HX + 1, HUB_FLOOR + 4, HZ - 12, bossDoor);
        b.fill(HX - 2, HUB_FLOOR + 5, HZ - 12, HX + 2, HUB_FLOOR + 5, HZ - 12, Blocks.GOLD_BLOCK.defaultBlockState());
        b.set(HX, HUB_FLOOR + 6, HZ - 12, Blocks.SHROOMLIGHT);
    }

    /** Rampa de 3 de ancho pegada a la pared de la Caldera, de a0 a a1 grados, bajando de top a bottom. */
    private void ramp(StructureBuilder b, double a0, double a1, int top, int bottom) {
        for (double a = a0; a <= a1; a += 0.4) {
            int y = (int) Math.round(top - (a - a0) / (a1 - a0) * (top - bottom));
            double rad = Math.toRadians(a);
            for (double r = BALCONY_IN + 0.1; r <= HUB_IN - 0.1; r += 0.35) {
                int x = HX + (int) Math.round(Math.cos(rad) * r), z = HZ + (int) Math.round(Math.sin(rad) * r);
                if (hubDist(x, z) > HUB_IN) continue;
                b.set(x, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                for (int yy = y + 1; yy <= y + 3 && yy < HUB_ROOF; yy++) b.set(x, yy, z, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Pasillo de 3 de alto desde una sala hasta el balcón de la Caldera (no toca el interior de la Caldera). */
    private void corridor(StructureBuilder b, int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                if (hubDist(x, z) <= HUB_IN) continue;
                b.set(x, y, z, floor.get());
                b.air(x, y + 1, z, x, y + 3, z);
            }
    }

    private static BlockState door(int channel) {
        return ModBlocks.EVENT_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel);
    }

    private static BlockState lock(int channel, int keys) {
        return ModBlocks.LOCKED_DOOR.get().defaultBlockState().setValue(EventDoorBlock.CHANNEL, channel).setValue(LockedDoorBlock.KEYS, keys);
    }

    /** Jaula de puertas de evento alrededor de (x, y, z): se abre con su canal. */
    private void cage(StructureBuilder b, int x, int y, int z, int channel) {
        BlockState d = door(channel);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) b.set(x + dx, y, z + dz, d);
                b.set(x + dx, y + 1, z + dz, d);
            }
    }

    private static BlockState keyChest(int key) {
        return ModBlocks.KEY_CHEST.get().defaultBlockState().setValue(KeyChestBlock.KEY, key);
    }

    private static BlockState bigChest(int kind) {
        return ModBlocks.BIG_CHEST.get().defaultBlockState().setValue(BigChestBlock.KIND, kind);
    }

    // ================================================================= Piso 3

    private void vestibule(StructureBuilder b) {
        b.room(-6, F3, -20, 6, F3 + 6, -14, wall, floor);
        b.air(-1, F3 + 1, -14, 1, F3 + 4, -14);
        corridor(b, -1, -22, 1, -20, F3);
        b.set(0, F3, -17, ModBlocks.CHECKPOINT_STONE.get());
        for (int x : new int[]{-4, 4}) b.set(x, F3 + 6, -17, Blocks.SHROOMLIGHT);
        for (int x : new int[]{-5, 5}) b.set(x, F3 + 1, -19, ModBlocks.SUPPLY_CRATE.get());
    }

    private void lavaWalk(StructureBuilder b) {
        b.room(-46, F3 - 6, -34, -14, F3 + 8, -20, wall, floor);
        b.fill(-45, F3 - 5, -33, -15, F3 - 3, -21, ModBlocks.RISING_MAGMA.get().defaultBlockState());
        b.fill(-19, F3 - 5, -33, -15, F3, -21, floor);       // rellano de entrada
        b.fill(-45, F3 - 5, -33, -41, F3, -21, floor);       // rellano del fondo
        BlockState crumbling = ModBlocks.CRUMBLING_BASALT.get().defaultBlockState();
        int[] laneA = {-32, -30, -31, -32, -30}, laneB = {-25, -23, -24, -25, -23};
        for (int i = 0; i < 5; i++) {
            int x = -21 - 4 * i;
            b.fill(x - 1, F3, laneA[i], x, F3, laneA[i] + 1, crumbling);
            b.fill(x - 1, F3, laneB[i], x, F3, laneB[i] + 1, crumbling);
        }
        for (int x : new int[]{-24, -30, -36}) { b.set(x, F3 + 8, -31, ModBlocks.ROCKFALL.get()); b.set(x, F3 + 8, -24, ModBlocks.ROCKFALL.get()); }
        for (int x : new int[]{-18, -27, -35, -43}) b.set(x, F3 + 8, -27, Blocks.SHROOMLIGHT);
        corridor(b, -14, -28, -9, -26, F3);
        b.set(-17, F3, -27, ModBlocks.CHECKPOINT_STONE.get());
        b.set(-43, F3 + 1, -27, keyChest(1));
    }

    private void gallery(StructureBuilder b) {
        b.room(14, F3, -45, 36, F3 + 11, -23, wall, floor);
        b.fill(15, F3 + 1, -44, 35, F3 + 1, -24, ModBlocks.RISING_MAGMA.get().defaultBlockState());
        b.fill(15, F3 + 1, -36, 17, F3 + 1, -32, floor);    // rellano de entrada
        corridor(b, 9, -35, 14, -33, F3);
        int[] xs = {21, 26, 31}, zs = {-40, -34, -28};
        int k = 0;
        for (int xi = 0; xi < 3; xi++)
            for (int zi = 0; zi < 3; zi++) {
                int x = xs[xi], z = zs[zi], top = F3 + 2 + (xi + zi) % 2;
                b.fill(x - 1, top, z - 1, x + 1, top, z + 1, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                for (int y = top + 1; y < F3 + 11; y++) { b.set(x - 1, y, z - 1, Blocks.CHAIN); b.set(x + 1, y, z + 1, Blocks.CHAIN); }
                if (zi == 1) b.set(x, top, z, ModBlocks.FIRE_GEYSER.get().defaultBlockState().setValue(FireGeyserBlock.PHASE, k++ % 4));
            }
        b.fill(34, F3 + 1, -44, 35, F3 + 2, -24, floor);    // plataforma del fondo
        b.set(35, F3 + 3, -34, bigChest(BigChestBlock.COMPASS));
        EventRooms.relic(b, 35, F3 + 3, -26);
        for (int[] l : new int[][]{{20, -34}, {30, -34}, {25, -42}, {25, -26}}) b.set(l[0], F3 + 11, l[1], Blocks.SHROOMLIGHT);
    }

    // ================================================================= Piso 2

    private void forge(StructureBuilder b) {
        b.room(-16, F2, -20, 16, F2 + 9, 2, wall, floor);
        corridor(b, -1, -22, 1, -20, F2);
        b.fill(-1, F2 + 1, -20, 1, F2 + 3, -20, door(V_FORGE_GATE));     // se cierra al empezar las oleadas
        b.set(0, F2, -9, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(0, F2 + 1, -9, ModBlocks.WAVE_ALTAR.get().defaultBlockState().setValue(WaveAltarBlock.CHANNEL, V_FORGE));
        for (int[] c : new int[][]{{-9, -15}, {7, -15}, {-9, -4}, {7, -4}}) b.fill(c[0], F2 + 1, c[1], c[0] + 1, F2 + 3, c[1] + 1, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        for (int x = -12; x <= 12; x += 6) { b.set(x, F2, -18, Blocks.MAGMA_BLOCK); b.set(x, F2, 0, Blocks.MAGMA_BLOCK); }
        for (int z = -16; z <= -2; z += 7) { b.set(-15, F2 + 1, z, Blocks.BLAST_FURNACE); b.set(15, F2 + 1, z, Blocks.BLAST_FURNACE); }
        for (int x : new int[]{-12, 12}) { b.set(x, F2 + 1, 1, Blocks.ANVIL); b.set(x, F2 + 1, -19, Blocks.ANVIL); }
        b.set(0, F2 + 1, 0, bigChest(BigChestBlock.HAMMER));
        cage(b, 0, F2 + 1, 0, V_FORGE);
        for (int[] l : new int[][]{{-8, -9}, {8, -9}, {0, -15}, {0, -3}}) b.set(l[0], F2 + 9, l[1], Blocks.SHROOMLIGHT);
    }

    private void seals(StructureBuilder b) {
        b.room(14, F2, -45, 36, F2 + 9, -23, wall, floor);
        corridor(b, 9, -35, 13, -33, F2);
        b.fill(14, F2 + 1, -35, 14, F2 + 3, -33, lock(V_LOCK_SEALS, 1));
        BlockState step = Blocks.POLISHED_BASALT.defaultBlockState();
        // Repisa norte (y 28) y repisa sur alta (y 31), con escalones
        b.fill(16, F2 + 1, -44, 16, F2 + 1, -43, step);
        b.fill(17, F2 + 1, -44, 30, F2 + 2, -43, step);
        for (int x = 16; x <= 30; x++) {
            int top = Math.min(F2 + 5, F2 + 1 + (x - 16));
            b.fill(x, F2 + 1, -25, x, top, -24, step);
        }
        BlockState seal = ModBlocks.EVENT_SWITCH.get().defaultBlockState().setValue(EventSwitchBlock.CHANNEL, V_SEALS);
        b.set(29, F2 + 3, -44, seal);
        b.set(29, F2 + 6, -24, seal);
        b.set(35, F2 + 1, -34, seal);
        b.set(25, F2 + 1, -34, keyChest(2));
        cage(b, 25, F2 + 1, -34, V_SEALS);
        for (int[] l : new int[][]{{20, -34}, {31, -34}, {25, -40}, {25, -28}}) b.set(l[0], F2 + 9, l[1], Blocks.SHROOMLIGHT);
    }

    private void bridge(StructureBuilder b) {
        int bottom = F2 - 6;
        b.room(-14, bottom, -72, 14, F2 + 10, -48, wall, floor);
        b.fill(-13, bottom + 1, -71, 13, bottom + 2, -49, ModBlocks.RISING_MAGMA.get().defaultBlockState());
        corridor(b, -1, -47, 1, -45, F2);
        b.fill(-1, F2 + 1, -48, 1, F2 + 3, -48, lock(V_LOCK_BRIDGE, 2 | 4));
        b.fill(-13, bottom + 1, -51, 13, F2, -49, floor);    // rellano de entrada
        b.fill(-13, bottom + 1, -71, 13, F2, -69, floor);    // rellano del fondo
        b.set(0, F2, -50, ModBlocks.CHECKPOINT_STONE.get());
        BlockState stone = Blocks.POLISHED_BASALT.defaultBlockState();
        BlockState plate = ModBlocks.GROUP_PLATE.get().defaultBlockState().setValue(GroupPlateBlock.CHANNEL, V_BRIDGE);
        for (int cx : new int[]{-10, -4, 4, 10}) {
            for (int cz : new int[]{-56, -64}) {
                b.fill(cx - 1, bottom + 1, cz - 1, cx + 1, F2, cz + 1, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                b.set(cx, F2, cz, plate);
                b.set(cx, F2 + 10, cz, Blocks.SHROOMLIGHT);
            }
            for (int sz : new int[]{-53, -59, -61, -67}) b.fill(cx, bottom + 1, sz, cx, F2, sz, stone);
        }
        b.set(0, F2 + 1, -70, keyChest(4));
        cage(b, 0, F2 + 1, -70, V_BRIDGE);
    }

    // ================================================================= Piso 1

    private void shaft(StructureBuilder b) {
        int x1 = -34, z1 = -52, x2 = -20, z2 = -38;
        b.room(x1, F1, z1, x2, F3 + 6, z2, wall, floor);
        BlockPos riserPos = b.abs(-27, F1, -45);
        b.set(-27, F1, -45, ModBlocks.MAGMA_RISER.get());
        if (b.level.getBlockEntity(riserPos) instanceof MagmaRiserBlockEntity riser) riser.configure(6, 14, 30);
        // Entrada por abajo: pasillo con un muro agrietado
        corridor(b, -20, -46, -3, -44, F1);
        b.fill(-15, F1 + 1, -46, -15, F1 + 3, -44, ModBlocks.CRACKED_WALL.get().defaultBlockState());
        // Espiral de escalones (cada dos celdas sube uno) y balcón arriba (y 36)
        List<int[]> ring = path(ring(x1 + 1, z1 + 1, x2 - 1, z2 - 1), x2 - 1, -44);
        int n = ring.size();
        BlockState step = Blocks.POLISHED_BASALT.defaultBlockState();
        for (int k = 1; k <= F3 - F1; k++) {
            int[] c = ring.get((2 * k) % n);
            b.set(c[0], F1 + k, c[1], step);
        }
        for (int i = 0; i < n; i++) {
            if (i >= 2 * (F3 - F1 - 3) && i <= 2 * (F3 - F1) + 1) continue;     // hueco para los últimos escalones
            int[] c = ring.get(i);
            b.set(c[0], F3, c[1], Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        }
        b.set(x2 - 1, F3 + 1, -48, keyChest(3));
        // Salida por arriba hacia el balcón del piso 3
        corridor(b, -20, -42, -9, -40, F3);
        for (int y = F1 + 6; y <= F3 + 2; y += 10) { b.set(x1, y, -45, Blocks.SHROOMLIGHT); b.set(-27, y, z1, Blocks.SHROOMLIGHT); }
    }

    private void hammerRoom(StructureBuilder b) {
        b.room(14, F1, -47, 40, F1 + 9, -21, wall, floor);
        corridor(b, 9, -35, 13, -33, F1);
        b.fill(14, F1 + 1, -35, 14, F1 + 3, -33, lock(V_LOCK_HAMMER, 8));
        BlockState crack = ModBlocks.CRACKED_WALL.get().defaultBlockState();
        b.fill(21, F1 + 1, -46, 21, F1 + 8, -22, crack);
        b.fill(28, F1 + 1, -46, 28, F1 + 8, -22, crack);
        int k = 0;
        for (int[] g : new int[][]{{17, -40}, {17, -28}, {19, -34}, {24, -38}, {24, -30}})
            b.set(g[0], F1, g[1], ModBlocks.FIRE_GEYSER.get().defaultBlockState().setValue(FireGeyserBlock.PHASE, k++ % 4));
        BlockState sw = ModBlocks.HAMMER_SWITCH.get().defaultBlockState().setValue(EventSwitchBlock.CHANNEL, V_SWITCHES);
        for (int[] s : new int[][]{{24, -45}, {24, -23}, {32, -45}, {32, -23}, {39, -40}, {39, -28}}) b.set(s[0], F1 + 1, s[1], sw);
        b.set(35, F1 + 1, -34, bigChest(BigChestBlock.BOSS_KEY));
        cage(b, 35, F1 + 1, -34, V_SWITCHES);
        for (int[] l : new int[][]{{17, -34}, {24, -34}, {32, -34}, {36, -40}, {36, -28}}) b.set(l[0], F1 + 9, l[1], Blocks.SHROOMLIGHT);
    }

    private void hiddenRoom(StructureBuilder b) {
        // Tesoro primero (comparte pared con la cámara)
        b.room(-42, F1, -32, -30, F1 + 6, -22, wall, floor);
        for (int z : new int[]{-29, -27, -25}) b.set(-40, F1 + 1, z, ModBlocks.TREASURE_CRATE.get());
        b.set(-41, F1 + 1, -31, Blocks.GOLD_BLOCK); b.set(-41, F1 + 1, -23, Blocks.GOLD_BLOCK);
        b.set(-36, F1 + 6, -27, Blocks.SHROOMLIGHT);
        b.room(-30, F1, -32, -15, F1 + 6, -22, wall, floor);
        b.fill(-30, F1 + 1, -28, -30, F1 + 3, -26, door(VAULT));
        corridor(b, -15, -28, -9, -26, F1);
        b.fill(-15, F1 + 1, -28, -15, F1 + 3, -26, ModBlocks.CRACKED_WALL.get().defaultBlockState());
        EventRooms.relic(b, -28, F1 + 1, -24);
        b.set(-22, F1 + 6, -27, Blocks.SHROOMLIGHT);
    }

    // ================================================================= Fondo y guarida

    private void bossPath(StructureBuilder b) {
        int zDoor = HZ - 12;
        for (int k = 0; k <= 19; k++) {
            int z = zDoor - 1 - k, y = HUB_FLOOR - k;
            b.fill(-2, y - 1, z, 2, y + 5, z, wall);
            b.fill(-1, y, z, 1, y, z, floor);
            b.air(-1, y + 1, z, 1, y + 4, z);
            if (k % 5 == 2) { b.set(-2, y + 3, z, Blocks.SHROOMLIGHT); b.set(2, y + 3, z, Blocks.SHROOMLIGHT); }
        }
        b.set(0, HUB_FLOOR - 18, zDoor - 19, ModBlocks.CHECKPOINT_STONE.get());
    }

    private void lair(StructureBuilder b) {
        int top = LAIR_FLOOR + 16;
        for (int x = LX - LAIR_R - 2; x <= LX + LAIR_R + 2; x++) {
            for (int z = LZ - LAIR_R - 2; z <= LZ + LAIR_R + 2; z++) {
                double d = Math.hypot(x - LX, z - LZ);
                if (d > LAIR_R + 1.5) continue;
                for (int y = LAIR_FLOOR - 1; y <= top; y++) {
                    boolean shell = d > LAIR_R || y == LAIR_FLOOR - 1 || y == top;
                    BlockState s = shell ? wall.get() : y == LAIR_FLOOR
                            ? (d < 3.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : ((int) d) % 6 == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : floor.get())
                            : Blocks.AIR.defaultBlockState();
                    b.set(x, y, z, s);
                }
            }
        }
        // Entrada desde el túnel
        int zEdge = LZ + LAIR_R;
        b.air(-1, LAIR_FLOOR + 1, zEdge - 1, 1, LAIR_FLOOR + 4, zEdge + 1);
        b.fill(-1, LAIR_FLOOR, zEdge - 1, 1, LAIR_FLOOR, zEdge + 1, floor);
        b.set(LX, LAIR_FLOOR, LZ, ModBlocks.BOSS_ALTAR.get());
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int vx = LX + (int) Math.round(Math.cos(a) * 11), vz = LZ + (int) Math.round(Math.sin(a) * 11);
            b.set(vx, LAIR_FLOOR, vz, ModBlocks.MAGMA_VENT.get());
            b.set(vx, LAIR_FLOOR - 1, vz, Blocks.MAGMA_BLOCK);
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            b.set(LX + (int) Math.round(Math.cos(a) * (LAIR_R + 1)), LAIR_FLOOR + 5, LZ + (int) Math.round(Math.sin(a) * (LAIR_R + 1)), Blocks.SHROOMLIGHT);
        }
    }

    // ================================================================= Utilidades

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
        for (int i = 0; i < r.size(); i++) if (r.get(i)[0] == sx && r.get(i)[1] == sz) start = i;
        List<int[]> out = new ArrayList<>(r.subList(start, r.size()));
        out.addAll(r.subList(0, start));
        return out;
    }
}
