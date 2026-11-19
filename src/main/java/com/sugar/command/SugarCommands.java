package com.sugar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.sugar.boss.SugarBoss;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleGenerator;
import com.sugar.temple.TempleRegistry;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import com.sugar.temple.TempleType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;

/**
 * /event      open|start|stop|reset|door|join
 * /eventtemple define|place|scan|setlobby|info|remove
 * /eventboss  summon|kill|info
 */
public final class SugarCommands {
    private static final String TEMPLE = "templo";

    private static final DynamicCommandExceptionType UNKNOWN_TEMPLE =
            new DynamicCommandExceptionType(id -> Component.literal("Templo desconocido: " + id + ". Usa: " + String.join(", ", TempleType.ids())));
    private static final DynamicCommandExceptionType NOT_DEFINED =
            new DynamicCommandExceptionType(id -> Component.literal("El templo '" + id + "' no está definido. Usa /eventtemple define o /eventtemple place."));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("event")
                .then(Commands.literal("join").then(temple().executes(SugarCommands::join)))
                .then(Commands.literal("open").requires(SugarCommands::admin).then(temple().executes(c -> {
                    TempleData data = data(c);
                    TempleService.open(c.getSource().getServer(), data);
                    return ok(c, "Templo abierto para reunirse: " + data.type.id() + ". Cuando estéis todos: /event start " + data.type.id());
                })))
                .then(Commands.literal("start").requires(SugarCommands::admin).then(temple().executes(c -> {
                    TempleData data = data(c);
                    TempleService.start(c.getSource().getServer(), data, 200);
                    return ok(c, "Evento iniciado: " + data.type.id());
                })))
                .then(Commands.literal("stop").requires(SugarCommands::admin).then(temple().executes(c -> {
                    TempleData data = data(c);
                    TempleService.stop(c.getSource().getServer(), data);
                    return ok(c, "Evento detenido: " + data.type.id());
                })))
                .then(Commands.literal("reset").requires(SugarCommands::admin).then(temple().executes(c -> {
                    TempleData data = data(c);
                    TempleService.reset(c.getSource().getServer(), data);
                    return ok(c, "Templo restaurado: " + data.type.id());
                })))
                .then(Commands.literal("door").requires(SugarCommands::admin).then(temple()
                        .then(Commands.argument("canal", IntegerArgumentType.integer(0, 15))
                                .then(Commands.literal("open").executes(c -> door(c, true)))
                                .then(Commands.literal("close").executes(c -> door(c, false)))))));

        dispatcher.register(Commands.literal("eventtemple").requires(SugarCommands::admin)
                .then(Commands.literal("define").then(temple()
                        .then(Commands.argument("desde", BlockPosArgument.blockPos())
                                .then(Commands.argument("hasta", BlockPosArgument.blockPos()).executes(SugarCommands::define)))))
                .then(Commands.literal("place").then(temple().executes(SugarCommands::place)))
                .then(Commands.literal("scan").then(temple().executes(c -> {
                    TempleData data = data(c);
                    ServerLevel level = level(c, data);
                    int found = TempleService.scan(level, data);
                    return ok(c, "Bloques especiales encontrados: " + found + (data.altar != null ? " (altar incluido)" : " (sin altar)"));
                })))
                .then(Commands.literal("setlobby").then(temple().executes(c -> {
                    TempleData data = data(c);
                    data.lobby = BlockPos.containing(c.getSource().getPosition());
                    TempleSavedData.get(c.getSource().getServer()).setDirty();
                    return ok(c, "Vestíbulo de " + data.type.id() + " fijado en " + data.lobby.toShortString());
                })))
                .then(Commands.literal("info").then(temple().executes(SugarCommands::info)))
                .then(Commands.literal("remove").then(temple().executes(c -> {
                    TempleType type = type(c);
                    TempleSavedData.get(c.getSource().getServer()).remove(type);
                    return ok(c, "Templo " + type.id() + " eliminado de la lista (los bloques siguen en el mundo).");
                }))));

        dispatcher.register(Commands.literal("eventboss").requires(SugarCommands::admin)
                .then(Commands.literal("summon").then(temple().executes(SugarCommands::summon)))
                .then(Commands.literal("kill").then(temple().executes(c -> {
                    TempleData data = data(c);
                    boolean removed = TempleService.removeBoss(level(c, data), data);
                    TempleSavedData.get(c.getSource().getServer()).setDirty();
                    return removed ? ok(c, "Boss eliminado (sin recompensas).") : fail(c, "No hay ningún boss activo en ese templo.");
                })))
                .then(Commands.literal("info").then(temple().executes(c -> {
                    TempleData data = data(c);
                    SugarBoss boss = TempleService.findBoss(level(c, data), data);
                    if (boss == null) return fail(c, "No hay ningún boss activo en ese templo.");
                    return ok(c, String.format("%s: %.0f%% de vida, escalado para %d jugadores (vida x%.2f)",
                            boss.getDisplayName().getString(), boss.getHealth() / boss.getMaxHealth() * 100,
                            boss.scaledPlayers(), boss.healthScale()));
                }))));
    }

    // ---------- Acciones ----------

    private static int join(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        TempleData data = data(c);
        if (!data.open && !data.active) return fail(c, "Ese templo está cerrado ahora mismo.");
        TempleService.sendToTemple(player, data);
        player.displayClientMessage(Component.literal("Bienvenido a ").append(data.type.displayName()), false);
        return 1;
    }

    private static int door(CommandContext<CommandSourceStack> c, boolean open) throws CommandSyntaxException {
        TempleData data = data(c);
        int channel = IntegerArgumentType.getInteger(c, "canal");
        int changed = TempleService.setDoors(c.getSource().getServer(), data, channel, open);
        return ok(c, (open ? "Abiertas " : "Cerradas ") + changed + " puertas del canal " + channel);
    }

    private static int define(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        TempleType type = type(c);
        BlockPos from = BlockPosArgument.getBlockPos(c, "desde");
        BlockPos to = BlockPosArgument.getBlockPos(c, "hasta");
        BoundingBox box = BoundingBox.fromCorners(from, to);
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        if (volume > 16_000_000L) return fail(c, "La zona es demasiado grande (máx. 16 millones de bloques, ~250x250x250).");

        ServerLevel level = c.getSource().getLevel();
        BlockPos lobby = BlockPos.containing(c.getSource().getPosition());
        TempleData data = TempleSavedData.get(c.getSource().getServer()).define(type, level.dimension(), box, lobby);
        int found = TempleService.scan(level, data);
        return ok(c, "Templo " + type.id() + " definido. Vestíbulo en tu posición. Bloques especiales: " + found
                + (data.altar == null ? ". ¡Falta colocar un Altar del Jefe!" : "."));
    }

    private static int place(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        TempleType type = type(c);
        Optional<TempleGenerator> gen = TempleRegistry.generator(type);
        if (gen.isEmpty()) return fail(c, "El templo por defecto de '" + type.id() + "' todavía no está disponible en esta versión.");

        ServerLevel level = c.getSource().getLevel();
        BlockPos origin = BlockPos.containing(c.getSource().getPosition());
        TempleGenerator.Result result = gen.get().generate(level, origin);
        TempleData data = TempleSavedData.get(c.getSource().getServer()).define(type, level.dimension(), result.zone(), result.lobby());
        TempleService.scan(level, data);
        return ok(c, "Templo " + type.id() + " construido y definido.");
    }

    private static int summon(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        TempleData data = data(c);
        ServerLevel level = level(c, data);
        String[] err = new String[1];
        SugarBoss boss = TempleService.summonBoss(level, data, e -> err[0] = e);
        if (boss == null) return fail(c, err[0]);
        return ok(c, "Boss invocado, escalado para " + boss.scaledPlayers() + " jugadores.");
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        TempleData data = data(c);
        BoundingBox z = data.zone;
        String text = data.type.id()
                + "\n  Dimensión: " + data.dimension.location()
                + "\n  Zona: " + z.minX() + " " + z.minY() + " " + z.minZ() + " → " + z.maxX() + " " + z.maxY() + " " + z.maxZ()
                + "\n  Vestíbulo: " + data.lobby.toShortString()
                + "\n  Altar: " + (data.altar != null ? data.altar.toShortString() : "no colocado")
                + "\n  Estado: " + (data.active ? "en marcha" : data.open ? "abierto (reuniéndose)" : "cerrado")
                + "\n  Bloques especiales: " + data.blocks.size()
                + "\n  Checkpoints guardados: " + data.checkpoints.size()
                + "\n  Pasos cumplidos: " + com.sugar.temple.TempleObjectives.done(data) + "/" + com.sugar.temple.TempleObjectives.of(data.type).size()
                + (com.sugar.temple.TempleObjectives.current(data) != null ? " (ahora: " + com.sugar.temple.TempleObjectives.current(data).name() + ")" : "")
                + "\n  Tiempo: " + com.sugar.temple.TempleService.elapsed(c.getSource().getLevel(), data) + "   Caídas: " + data.deaths
                + (data.keys != 0 ? "\n  Llaves del grupo: " + keyNames(data.keys) : "");
        return ok(c, text);
    }

    // ---------- Utilidades ----------

    private static String keyNames(int keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) if ((keys & (1 << i)) != 0) sb.append(sb.length() > 0 ? ", " : "").append(i + 1);
        if ((keys & TempleData.BOSS_KEY) != 0) sb.append(sb.length() > 0 ? ", " : "").append("del Jefe");
        return sb.toString();
    }

    private static boolean admin(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> temple() {
        return Commands.argument(TEMPLE, StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TempleType.ids(), builder));
    }

    private static TempleType type(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, TEMPLE);
        TempleType type = TempleType.byId(id);
        if (type == null) throw UNKNOWN_TEMPLE.create(id);
        return type;
    }

    private static TempleData data(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        TempleType type = type(c);
        return TempleSavedData.get(c.getSource().getServer()).get(type).orElseThrow(() -> NOT_DEFINED.create(type.id()));
    }

    private static ServerLevel level(CommandContext<CommandSourceStack> c, TempleData data) throws CommandSyntaxException {
        MinecraftServer server = c.getSource().getServer();
        ServerLevel level = TempleService.levelOf(server, data);
        if (level == null) throw NOT_DEFINED.create(data.type.id());
        return level;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    private SugarCommands() {}
}
