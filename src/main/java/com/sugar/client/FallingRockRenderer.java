package com.sugar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sugar.entity.FallingRock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Dibuja la roca como un bloque de magma. */
public class FallingRockRenderer extends EntityRenderer<FallingRock> {
    private final BlockRenderDispatcher dispatcher;

    public FallingRockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8F;
        this.shadowStrength = 1.0F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(FallingRock entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(-0.5, 0.0, -0.5);
        dispatcher.renderSingleBlock(FallingRock.LOOK, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FallingRock entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
