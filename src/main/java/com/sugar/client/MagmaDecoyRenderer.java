package com.sugar.client;

import com.sugar.entity.MagmaDecoy;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Mismo modelo que la Sierpe pero sin capa brillante: los ojos apagados delatan al señuelo. */
public class MagmaDecoyRenderer extends GeoEntityRenderer<MagmaDecoy> {
    public MagmaDecoyRenderer(EntityRendererProvider.Context context) {
        super(context, new MagmaSerpentModel<>());
        this.shadowRadius = 1.2F;
    }
}
