package com.sugar.client;

import com.sugar.SugarMod;
import com.sugar.entity.TempleMiniBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderizador de los minijefes humanoides (modelo con proporciones de jugador, escalado). */
public class MiniBossRenderer<T extends TempleMiniBoss> extends GeoEntityRenderer<T> {
    public MiniBossRenderer(EntityRendererProvider.Context context, String name, float scale) {
        super(context, new DefaultedEntityGeoModel<>(SugarMod.id(name)));
        withScale(scale);
        this.shadowRadius = 0.9F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0.0F;
    }
}
