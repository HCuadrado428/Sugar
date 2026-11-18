package com.sugar.boss;

import com.sugar.SugarConfig;
import com.sugar.temple.TempleService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reparte el botín entre todos los que participaron, no solo quien da el último golpe. */
public final class BossRewards {

    public static void distribute(ServerLevel level, SugarBoss boss) {
        MinecraftServer server = level.getServer();
        Map<UUID, Float> damage = boss.damageByPlayer();
        float total = 0;
        for (float v : damage.values()) total += v;

        // Participantes: suficiente daño o presentes en el templo
        Set<ServerPlayer> participants = new LinkedHashSet<>();
        double minShare = SugarConfig.MIN_DAMAGE_SHARE.get();
        for (Map.Entry<UUID, Float> e : damage.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && total > 0 && e.getValue() / total >= minShare) participants.add(p);
        }
        if (SugarConfig.PRESENCE_COUNTS.get()) {
            boss.templeData().ifPresent(data -> participants.addAll(TempleService.playersInside(level, data)));
        }

        // Ranking de daño
        List<Map.Entry<UUID, Float>> ranking = new ArrayList<>(damage.entrySet());
        ranking.sort(Map.Entry.<UUID, Float>comparingByValue().reversed());
        int mvpCount = Math.min(SugarConfig.MVP_COUNT.get(), ranking.size());

        ResourceLocation base = boss.rewardTable();
        ResourceLocation participantTable = new ResourceLocation(base.getNamespace(), base.getPath() + "_participante");
        ResourceLocation mvpTable = new ResourceLocation(base.getNamespace(), base.getPath() + "_mvp");

        for (ServerPlayer p : participants) {
            give(server, p, participantTable);
            p.displayClientMessage(Component.literal("Has recibido tu recompensa del evento.").withStyle(ChatFormatting.GREEN), true);
        }
        for (int i = 0; i < mvpCount; i++) {
            ServerPlayer p = server.getPlayerList().getPlayer(ranking.get(i).getKey());
            if (p != null) give(server, p, mvpTable);
        }

        // Anuncio
        MutableComponent msg = Component.literal("☠ ").withStyle(ChatFormatting.GOLD)
                .append(boss.getDisplayName())
                .append(Component.literal(" ha sido derrotado. Participantes: " + participants.size()).withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < mvpCount; i++) {
            Map.Entry<UUID, Float> e = ranking.get(i);
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            String name = p != null ? p.getGameProfile().getName() : "?";
            int pct = total > 0 ? Math.round(e.getValue() / total * 100) : 0;
            msg.append(Component.literal("\n  " + (i + 1) + ". " + name + " — " + pct + "% del daño").withStyle(ChatFormatting.YELLOW));
        }
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    private static void give(MinecraftServer server, ServerPlayer player, ResourceLocation tableId) {
        LootTable table = server.getLootData().getLootTable(tableId);
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.GIFT);
        for (ItemStack stack : table.getRandomItems(params)) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private BossRewards() {}
}
