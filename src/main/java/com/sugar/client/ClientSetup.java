package com.sugar.client;

import com.sugar.SugarMod;
import com.sugar.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SugarMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MAGMA_SERPENT.get(), MagmaSerpentRenderer::new);
        event.registerEntityRenderer(ModEntities.MAGMA_DECOY.get(), MagmaDecoyRenderer::new);
        event.registerEntityRenderer(ModEntities.MAGMA_BOLT.get(), ctx -> new ThrownItemRenderer<>(ctx, 2.0F, true));
        event.registerEntityRenderer(ModEntities.FALLING_ROCK.get(), FallingRockRenderer::new);
    }

    private ClientSetup() {}
}
