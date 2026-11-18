package com.sugar.event;

import com.sugar.SugarMod;
import com.sugar.entity.MagmaDecoy;
import com.sugar.entity.MagmaSerpent;
import com.sugar.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SugarMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModBusEvents {

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MAGMA_SERPENT.get(), MagmaSerpent.createAttributes().build());
        event.put(ModEntities.MAGMA_DECOY.get(), MagmaDecoy.createAttributes().build());
    }

    private ModBusEvents() {}
}
