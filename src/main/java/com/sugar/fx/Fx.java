package com.sugar.fx;

import com.sugar.SugarConfig;
import com.sugar.net.SugarNetwork;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Efectos visuales del servidor pensados para arenas grandes: las partículas se mandan con
 * "largo alcance" (se ven hasta a 96 bloques, no solo a 32) y la sacudida de pantalla va
 * por paquete propio.
 */
public final class Fx {
    private static final double RANGE = 96.0;

    public static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
                                 double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) < RANGE * RANGE) level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** Una partícula con dirección (count 0: dx/dy/dz son la velocidad). */
    public static void spray(ServerLevel level, ParticleOptions type, Vec3 at, Vec3 velocity, double speed) {
        particles(level, type, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, speed);
    }

    /** Línea de partículas en el suelo, para avisar de por dónde pasará un ataque. */
    public static void line(ServerLevel level, ParticleOptions type, Vec3 from, Vec3 to, double step, double spread) {
        Vec3 d = to.subtract(from);
        int n = Math.max(1, (int) (d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            particles(level, type, p.x, p.y, p.z, 1, spread, 0.02, spread, 0.0);
        }
    }

    /** Anillo de partículas. */
    public static void ring(ServerLevel level, ParticleOptions type, Vec3 center, double radius, int points, double spread) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            particles(level, type, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, spread, 0.02, spread, 0.0);
        }
    }

    /** Sacude la pantalla a los jugadores cercanos; más fuerte cuanto más cerca. */
    public static void shake(ServerLevel level, Vec3 at, double radius, float intensity, int ticks) {
        float mult = SugarConfig.SCREEN_SHAKE.get().floatValue();
        if (mult <= 0) return;
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(at);
            if (d > radius) continue;
            float k = (float) (1.0 - 0.6 * d / radius);
            SugarNetwork.shake(p, intensity * k * mult, ticks);
        }
    }

    private Fx() {}
}
