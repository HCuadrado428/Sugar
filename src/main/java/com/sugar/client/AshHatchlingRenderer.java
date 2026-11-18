package com.sugar.client;

import com.sugar.entity.AshHatchling;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Las crías usan el modelo de la Bestia a un 28% del tamaño. */
public class AshHatchlingRenderer extends GeoEntityRenderer<AshHatchling> {
    public AshHatchlingRenderer(EntityRendererProvider.Context context) {
        super(context, new AshBeastModel<>());
        this.shadowRadius = 0.5F;
        withScale(0.28F);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
