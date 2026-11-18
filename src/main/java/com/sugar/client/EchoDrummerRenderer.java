package com.sugar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sugar.SugarMod;
import com.sugar.entity.EchoDrummer;
import com.sugar.item.EchoLensItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * La máscara solo se dibuja si está revelada (presentación, caída o muerte) o si el jugador
 * lleva la Lente del Eco; en ese caso se ve como un fantasma semitransparente.
 */
public class EchoDrummerRenderer extends GeoEntityRenderer<EchoDrummer> {
    public EchoDrummerRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(SugarMod.id("echo_drummer")));
        withScale(1.8F);
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    static boolean lensView() {
        Player p = Minecraft.getInstance().player;
        return p != null && (EchoLensItem.isHolding(p) || p.isCreative() || p.isSpectator());
    }

    @Override
    public void render(EchoDrummer entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isRevealed() && !lensView()) return;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public RenderType getRenderType(EchoDrummer animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void actuallyRender(PoseStack poseStack, EchoDrummer animatable, BakedGeoModel model, RenderType renderType, MultiBufferSource bufferSource,
                               VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        float a = animatable.isRevealed() ? alpha : alpha * 0.45F;
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, a);
    }

    @Override
    protected float getDeathMaxRotation(EchoDrummer animatable) {
        return 0.0F;
    }
}
