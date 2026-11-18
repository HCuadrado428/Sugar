package com.sugar.client;

import com.sugar.entity.DrummerHand;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class DrummerHandRenderer extends GeoEntityRenderer<DrummerHand> {
    public DrummerHandRenderer(EntityRendererProvider.Context context) {
        super(context, new DrummerHandModel());
        withScale(2.0F);
        this.shadowRadius = 1.4F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(DrummerHand animatable) {
        return 0.0F;
    }
}
