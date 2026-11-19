package com.sugar.client;

import com.sugar.SugarMod;
import com.sugar.entity.ForgeForeman;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class ForgeForemanRenderer extends GeoEntityRenderer<ForgeForeman> {
    public ForgeForemanRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(SugarMod.id("forge_foreman")));
        this.shadowRadius = 1.2F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(ForgeForeman animatable) {
        return 0.0F;
    }
}
