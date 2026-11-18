package com.sugar.client;

import com.sugar.entity.MagmaSerpent;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class MagmaSerpentRenderer extends GeoEntityRenderer<MagmaSerpent> {
    public MagmaSerpentRenderer(EntityRendererProvider.Context context) {
        super(context, new MagmaSerpentModel<>());
        this.shadowRadius = 1.5F;
        // Grietas y ojos brillantes: textures/entity/magma_serpent_glowmask.png
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(MagmaSerpent animatable) {
        return 0.0F;
    }
}
