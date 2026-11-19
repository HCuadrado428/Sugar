package com.sugar.temple;

import com.sugar.block.EchoDrumBlock;
import com.sugar.block.EchoGuideBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sala de los Tambores: "Simón dice" para todo el grupo.
 * Al tocar el gong, los tambores tocan una secuencia; el grupo la repite golpeando los tambores en orden.
 * Tres rondas (3, 4 y 5 golpes). Un fallo reinicia el puzle con otra secuencia.
 * Al terminar se abren las puertas del canal {@link #CHANNEL}.
 */
public final class EchoPuzzle {
    public static final int CHANNEL = 7;
    public static final int[] ROUNDS = {3, 4, 5};
    private static final int BEAT_TICKS = 14;
    private static final float[] PITCH = {0.5F, 0.7F, 0.94F, 1.26F};
    private static final Vector3f[] COLOR = {
            new Vector3f(0.9F, 0.2F, 0.2F), new Vector3f(0.95F, 0.8F, 0.2F),
            new Vector3f(0.3F, 0.9F, 0.3F), new Vector3f(0.3F, 0.5F, 1.0F)};

    enum Mode { PLAYBACK, INPUT, SOLVED }

    static final class State {
        final List<Integer> sequence = new ArrayList<>();
        int round;
        int step;
        Mode mode = Mode.PLAYBACK;
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();

    // ---------- Gong ----------

    public static void start(ServerLevel level, BlockPos gong, EchoGuideBlock block) {
        Optional<TempleData> data = TempleSavedData.get(level.getServer()).findAt(level.dimension(), gong);
        level.playSound(null, gong, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.5F);
        if (data.isEmpty() || !data.get().active) {
            message(level, gong, Component.literal("El gong retumba, pero nada responde...").withStyle(ChatFormatting.GRAY));
            return;
        }
        State s = STATES.get(gong);
        if (s != null && s.mode == Mode.SOLVED) {
            message(level, gong, Component.literal("Los tambores ya guardan silencio. El paso está abierto.").withStyle(ChatFormatting.GREEN));
            return;
        }
        if (s != null && s.mode == Mode.PLAYBACK) return;          // ya está sonando
        if (s == null) {
            s = new State();
            newSequence(s, level.random);
            STATES.put(gong.immutable(), s);
        }
        s.mode = Mode.PLAYBACK;
        s.step = 0;
        message(level, gong, Component.literal("Escuchad... (ronda " + (s.round + 1) + "/" + ROUNDS.length + ")").withStyle(ChatFormatting.LIGHT_PURPLE));
        level.scheduleTick(gong, block, 30);
    }

    /** Toca la secuencia golpe a golpe (lo llama el tick programado del gong). */
    public static void playbackTick(ServerLevel level, BlockPos gong, EchoGuideBlock block) {
        State s = STATES.get(gong);
        if (s == null || s.mode != Mode.PLAYBACK) return;
        int length = ROUNDS[s.round];
        if (s.step < length) {
            int note = s.sequence.get(s.step);
            for (BlockPos drum : drums(level, gong, note)) {
                playNote(level, drum, note);
                highlight(level, drum, note, 18);
            }
            s.step++;
            level.scheduleTick(gong, block, BEAT_TICKS);
        } else {
            s.mode = Mode.INPUT;
            s.step = 0;
            message(level, gong, Component.literal("¡Vuestro turno! Repetid el ritmo golpeando los tambores.").withStyle(ChatFormatting.AQUA));
        }
    }

    // ---------- Tambores ----------

    public static void drumHit(ServerLevel level, BlockPos drum, int note, @org.jetbrains.annotations.Nullable ServerPlayer player) {
        playNote(level, drum, note);
        highlight(level, drum, note, 8);
        Map.Entry<BlockPos, State> entry = find(level, drum);
        if (entry == null) return;
        State s = entry.getValue();
        if (s.mode != Mode.INPUT || s.step < 0) return;     // step < 0: tras un fallo, espera al gong
        BlockPos gong = entry.getKey();
        if (s.sequence.get(s.step) != note) {
            level.playSound(null, drum, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.BLOCKS, 2.0F, 0.5F);
            level.sendParticles(ParticleTypes.SMOKE, drum.getX() + 0.5, drum.getY() + 1.1, drum.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.02);
            message(level, gong, Component.literal("✖ " + (player != null ? player.getName().getString() : "Alguien") + " ha fallado el ritmo. Volved a tocar el gong.").withStyle(ChatFormatting.RED));
            s.round = 0;
            s.step = -1;
            newSequence(s, level.random);
            return;
        }
        s.step++;
        if (s.step < ROUNDS[s.round]) return;
        s.round++;
        s.step = 0;
        if (s.round >= ROUNDS.length) {
            s.mode = Mode.SOLVED;
            level.playSound(null, gong, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.5F, 0.8F);
            TempleSavedData.get(level.getServer()).findAt(level.dimension(), gong).ifPresent(d -> {
                TempleService.setDoors(level.getServer(), d, CHANNEL, true);
            });
            return;
        }
        level.playSound(null, gong, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.4F);
        s.mode = Mode.PLAYBACK;
        message(level, gong, Component.literal("¡Bien! Siguiente ritmo...").withStyle(ChatFormatting.GREEN));
        if (level.getBlockState(gong).getBlock() instanceof EchoGuideBlock g) level.scheduleTick(gong, g, 40);
    }

    public static void playNote(ServerLevel level, BlockPos pos, int note) {
        float pitch = PITCH[Math.max(0, Math.min(3, note))];
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 2.0F, pitch);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.2F, pitch);
    }

    private static void highlight(ServerLevel level, BlockPos drum, int note, int count) {
        DustParticleOptions dust = new DustParticleOptions(COLOR[note], 1.6F);
        level.sendParticles(dust, drum.getX() + 0.5, drum.getY() + 1.15, drum.getZ() + 0.5, count, 0.3, 0.05, 0.3, 0.0);
        level.sendParticles(ParticleTypes.NOTE, drum.getX() + 0.5, drum.getY() + 1.4, drum.getZ() + 0.5, 0, note * 6 / 24.0, 0, 0, 1.0);
    }

    // ---------- Estado ----------

    /** Devuelve el templo al estado inicial (lo llama /event reset a través del gong). */
    public static void reset(ServerLevel level, BlockPos gong) {
        STATES.remove(gong);
    }

    /** Para las pruebas: la secuencia de la ronda actual (o vacía si no hay puzle en marcha). */
    public static List<Integer> currentSequence(BlockPos gong) {
        State s = STATES.get(gong);
        if (s == null || s.round >= ROUNDS.length) return List.of();
        return List.copyOf(s.sequence.subList(0, ROUNDS[s.round]));
    }

    public static String describe(BlockPos gong) {
        State s = STATES.get(gong);
        return s == null ? "sin estado" : "ronda " + s.round + ", paso " + s.step + ", modo " + s.mode + ", secuencia " + s.sequence;
    }

    public static boolean isSolved(BlockPos gong) {
        State s = STATES.get(gong);
        return s != null && s.mode == Mode.SOLVED;
    }

    public static boolean isWaitingInput(BlockPos gong) {
        State s = STATES.get(gong);
        return s != null && s.mode == Mode.INPUT && s.step >= 0;
    }

    private static void newSequence(State s, RandomSource random) {
        s.sequence.clear();
        int last = -1;
        for (int i = 0; i < ROUNDS[ROUNDS.length - 1]; i++) {
            int n;
            do { n = random.nextInt(4); } while (n == last && random.nextInt(3) > 0);   // pocas repeticiones seguidas
            s.sequence.add(n);
            last = n;
        }
    }

    /** El gong del mismo templo que este tambor. */
    private static Map.Entry<BlockPos, State> find(ServerLevel level, BlockPos drum) {
        Optional<TempleData> data = TempleSavedData.get(level.getServer()).findAt(level.dimension(), drum);
        for (Map.Entry<BlockPos, State> e : STATES.entrySet()) {
            if (data.isPresent() ? data.get().contains(level.dimension(), e.getKey()) : e.getKey().closerThan(drum, 24)) return e;
        }
        return null;
    }

    private static List<BlockPos> drums(ServerLevel level, BlockPos gong, int note) {
        List<BlockPos> list = new ArrayList<>();
        Optional<TempleData> data = TempleSavedData.get(level.getServer()).findAt(level.dimension(), gong);
        if (data.isEmpty()) return list;
        for (BlockPos p : TempleService.positionsOf(level, data.get(), EchoDrumBlock.class)) {
            BlockState st = level.getBlockState(p);
            if (st.getValue(EchoDrumBlock.NOTE) == note && p.closerThan(gong, 32)) list.add(p);
        }
        return list;
    }

    private static void message(ServerLevel level, BlockPos gong, Component msg) {
        Optional<TempleData> data = TempleSavedData.get(level.getServer()).findAt(level.dimension(), gong);
        if (data.isPresent()) {
            for (ServerPlayer p : TempleService.playersInside(level, data.get())) {
                if (p.blockPosition().closerThan(gong, 40)) p.displayClientMessage(msg, true);
            }
        } else {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(gong).inflate(24))) p.displayClientMessage(msg, true);
        }
    }

    private EchoPuzzle() {}
}
