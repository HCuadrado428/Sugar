package com.sugar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sugar.SugarMod;
import com.sugar.entity.Keese;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Keese: una textura por variante. El de eco solo se ve con la Lente del Eco. */
public class KeeseRenderer extends GeoEntityRenderer<Keese> {
    private static final ResourceLocation[] TEX = {SugarMod.id("textures/entity/keese_fire.png"),
            SugarMod.id("textures/entity/keese_ash.png"), SugarMod.id("textures/entity/keese_echo.png")};

    public KeeseRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<Keese>(SugarMod.id("keese")) {
            @Override
            public ResourceLocation getTextureResource(Keese animatable) {
                return TEX[Math.max(0, Math.min(2, animatable.variant()))];
            }
        });
        this.shadowRadius = 0.25F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(Keese entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (entity.variant() == Keese.ECHO && !EchoDrummerRenderer.lensView()) return;
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
