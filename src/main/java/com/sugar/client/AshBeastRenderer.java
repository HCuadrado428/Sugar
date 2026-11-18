package com.sugar.client;

import com.sugar.entity.AshBeast;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class AshBeastRenderer extends GeoEntityRenderer<AshBeast> {
    public AshBeastRenderer(EntityRendererProvider.Context context) {
        super(context, new AshBeastModel<>());
        this.shadowRadius = 2.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(AshBeast animatable) {
        return 0.0F;
    }
}
