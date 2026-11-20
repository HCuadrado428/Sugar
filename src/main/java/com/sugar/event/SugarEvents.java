package com.sugar.event;

import com.sugar.SugarConfig;
import com.sugar.SugarMod;
import com.sugar.command.SugarCommands;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SugarMod.MODID)
public final class SugarEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SugarCommands.register(event.getDispatcher());
    }

    /** Si el jugador muere durante un evento activo y tiene checkpoint, reaparece allí. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        com.sugar.item.HeartContainerItem.apply(event.getEntity());
        if (event.isEndConquered() || !SugarConfig.RESPAWN_AT_CHECKPOINT.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        for (TempleData data : TempleSavedData.get(player.getServer()).all()) {
            if (data.active && data.checkpoints.containsKey(player.getUUID())) {
                TempleService.sendToTemple(player, data);
                return;
            }
        }
    }

    /** Los contenedores de corazón se guardan en el jugador; se vuelven a aplicar al entrar. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        com.sugar.item.HeartContainerItem.apply(event.getEntity());
    }

    private SugarEvents() {}
}
