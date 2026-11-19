package com.sugar.client;

import com.sugar.SugarMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sacudida de pantalla: un temblor que se apaga poco a poco. Si llegan varias, manda la más fuerte. */
@Mod.EventBusSubscriber(modid = SugarMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientFx {
    private static final class Wave {
        final double x, y, z, speed, max; final int style; double r;
        Wave(double x, double y, double z, double speed, double max, int style) { this.x = x; this.y = y; this.z = z; this.speed = speed; this.max = max; this.style = style; }
    }
    private static final java.util.List<Wave> WAVES = new java.util.ArrayList<>();
    private static final net.minecraft.core.particles.ParticleOptions BASALT =
            new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.BASALT.defaultBlockState());
    private static final net.minecraft.core.particles.ParticleOptions SCULK =
            new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.SCULK.defaultBlockState());

    public static void addWave(double x, double y, double z, double speed, double max, int style) {
        WAVES.add(new Wave(x, y, z, speed, max, style));
    }

    /** Dibuja el anillo de cada onda: una cresta de partículas a ras de suelo con restos que saltan. */
    private static void tickWaves() {
        var level = Minecraft.getInstance().level;
        if (level == null) { WAVES.clear(); return; }
        var rnd = level.random;
        var it = WAVES.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            w.r += w.speed;
            int n = Math.max(16, (int) (w.r * Math.PI * 2 / 0.55));
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * (i + rnd.nextDouble() * 0.5) / n;
                double px = w.x + Math.cos(a) * w.r, pz = w.z + Math.sin(a) * w.r;
                double ox = Math.cos(a) * 0.05, oz = Math.sin(a) * 0.05;
                switch (w.style) {
                    case 0 -> {
                        level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, px, w.y + 0.1, pz, ox, 0.06 + rnd.nextDouble() * 0.06, oz);
                        if (i % 4 == 0) level.addParticle(BASALT, px, w.y + 0.2, pz, 0, 0.25, 0);
                        if (i % 9 == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, px, w.y + 0.2, pz, 0, 0, 0);
                    }
                    case 1 -> {
                        level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, px, w.y + 0.2, pz, ox, 0.03, oz);
                        if (i % 2 == 0) level.addParticle(BASALT, px, w.y + 0.2, pz, 0, 0.3, 0);
                        if (i % 3 == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, px, w.y + 0.6, pz, 0, 0.1, 0);
                    }
                    default -> {
                        level.addParticle(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, px, w.y + 0.15, pz, ox * 0.5, 0.04, oz * 0.5);
                        if (i % 2 == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, px, w.y + 0.1, pz, 0, 0.05, 0);
                        if (i % 5 == 0) level.addParticle(SCULK, px, w.y + 0.2, pz, 0, 0.25, 0);
                    }
                }
            }
            if (w.r >= w.max) it.remove();
        }
    }

    private static float intensity;
    private static int total, left;

    public static void startShake(float strength, int ticks) {
        float current = left > 0 ? intensity * left / (float) total : 0;
        if (strength < current) return;
        intensity = Math.min(strength, 6.0F);
        total = left = Math.max(1, ticks);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) return;
        if (left > 0) left--;
        if (!WAVES.isEmpty()) tickWaves();
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (left <= 0) return;
        double t = (total - left) + event.getPartialTick();
        float fade = (left - (float) event.getPartialTick()) / total;
        float k = intensity * Math.max(0, fade) * fade;
        event.setPitch(event.getPitch() + (float) (k * (Math.sin(t * 2.7) * 0.6 + Math.sin(t * 6.1) * 0.4)));
        event.setYaw(event.getYaw() + (float) (k * (Math.sin(t * 2.1 + 1.3) * 0.6 + Math.sin(t * 5.3) * 0.4)));
        event.setRoll(event.getRoll() + (float) (k * 0.5 * Math.sin(t * 3.4 + 0.7)));
    }

    private ClientFx() {}
}
