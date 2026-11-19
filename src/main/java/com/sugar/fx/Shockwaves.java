package com.sugar.fx;

import com.sugar.SugarMod;
import com.sugar.net.SugarNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ondas expansivas: un anillo que sale de un punto por el suelo. Golpea a quien esté apoyado en el
 * suelo justo cuando el anillo pasa por debajo; saltar a tiempo lo esquiva.
 * El servidor solo calcula los golpes; cada cliente dibuja el anillo (un único paquete por onda).
 */
@Mod.EventBusSubscriber(modid = SugarMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Shockwaves {
    public static final int FIRE = 0, ASH = 1, ECHO = 2;

    private static final class Wave {
        final ServerLevel level; final Vec3 center; final double speed, max, lift; final float damage; final int style;
        @Nullable final Entity owner;
        final Set<UUID> hit = new HashSet<>();
        double radius;
        Wave(ServerLevel level, Vec3 center, double speed, double max, float damage, double lift, int style, @Nullable Entity owner) {
            this.level = level; this.center = center; this.speed = speed; this.max = max; this.damage = damage; this.lift = lift;
            this.style = style; this.owner = owner;
        }
    }

    private static final List<Wave> ACTIVE = new ArrayList<>();

    /** Lanza una onda. speed en bloques/tick (0.35-0.6 se puede saltar bien). */
    public static void spawn(ServerLevel level, Vec3 center, double speed, double maxRadius, float damage, double lift, int style, @Nullable Entity owner) {
        ACTIVE.add(new Wave(level, center, speed, maxRadius, damage, lift, style, owner));
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(center) < (maxRadius + 64) * (maxRadius + 64)) SugarNetwork.wave(p, center, speed, maxRadius, style);
        }
    }

    public static int active() {
        return ACTIVE.size();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        Iterator<Wave> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            if (w.level != level) continue;
            w.radius += w.speed;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.isCreative() || !p.isAlive() || w.hit.contains(p.getUUID())) continue;
                double dx = p.getX() - w.center.x, dz = p.getZ() - w.center.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < w.radius - w.speed - 0.6 || d > w.radius + 0.5) continue;
                if (Math.abs(p.getY() - w.center.y) > 1.6 || !p.onGround()) continue;      // en el aire: la esquiva
                w.hit.add(p.getUUID());
                if (w.owner instanceof LivingEntity le && le.isAlive()) p.hurt(level.damageSources().mobAttack(le), w.damage);
                else p.hurt(level.damageSources().magic(), w.damage);
                double k = d < 0.01 ? 0 : 0.7 / d;
                p.push(dx * k, w.lift, dz * k);
                p.hurtMarked = true;
            }
            if (w.radius >= w.max) it.remove();
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private Shockwaves() {}
}
