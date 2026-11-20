package com.sugar.temple;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Ambiente de cada templo, solo para los jugadores que están dentro:
 *  - Volcán: chispas, lava que burbujea y rugidos lejanos de la montaña.
 *  - Ceniza: copos de ceniza que caen y viento entre las grietas.
 *  - Eco: motas de sculk, latidos y susurros del Santuario.
 */
public final class TempleAmbience {
    private TempleAmbience() {}

    public static void tick(ServerLevel level, TempleData data, int tick) {
        if (tick % 10 != 0) return;
        RandomSource r = level.random;
        for (ServerPlayer p : TempleService.playersInside(level, data)) {
            ParticleOptions dust = switch (data.type) {
                case VOLCAN -> ParticleTypes.LAVA;
                case CENIZA -> ParticleTypes.WHITE_ASH;
                case ECO -> ParticleTypes.SCULK_SOUL;
            };
            int n = data.type == TempleType.VOLCAN ? 1 : 6;
            level.sendParticles(p, data.type == TempleType.CENIZA ? ParticleTypes.ASH : ParticleTypes.SMOKE, true,
                    p.getX(), p.getY() + 3, p.getZ(), 8, 6, 2, 6, 0.0);
            level.sendParticles(p, dust, true, p.getX(), p.getY() + 2, p.getZ(), n, 5, 2, 5, 0.01);
            if (tick % 120 == 0 && r.nextInt(3) == 0) {
                SoundEvent s = switch (data.type) {
                    case VOLCAN -> r.nextBoolean() ? SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value() : SoundEvents.LAVA_AMBIENT;
                    case CENIZA -> r.nextBoolean() ? SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value() : SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS.value();
                    case ECO -> r.nextBoolean() ? SoundEvents.WARDEN_HEARTBEAT : SoundEvents.SCULK_SHRIEKER_SHRIEK;
                };
                float vol = data.type == TempleType.ECO ? 0.35F : 0.7F;
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                        net.minecraft.core.Holder.direct(s), SoundSource.AMBIENT,
                        p.getX() + r.nextInt(17) - 8, p.getY() + 2, p.getZ() + r.nextInt(17) - 8, vol, 0.8F + r.nextFloat() * 0.3F, r.nextLong()));
            }
        }
    }
}
