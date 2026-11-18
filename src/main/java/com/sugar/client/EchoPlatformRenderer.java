package com.sugar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sugar.block.entity.EchoPlatformBlockEntity;
import com.sugar.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.model.data.ModelData;

/** Dibuja la Plataforma Oculta solo para quien lleva la Lente del Eco (o está en creativo). */
public class EchoPlatformRenderer implements BlockEntityRenderer<EchoPlatformBlockEntity> {

    public EchoPlatformRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(EchoPlatformBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!EchoDrummerRenderer.lensView()) return;
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.ECHO_PLATFORM_SHOWN.get().defaultBlockState(),
                poseStack, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, Sheets.translucentCullBlockSheet());
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
