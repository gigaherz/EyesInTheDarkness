package dev.gigaherz.eyes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gigaherz.eyes.EyesInTheDarkness;
import dev.gigaherz.eyes.entity.EyesEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix4f;

public class EyesRenderer extends EntityRenderer<EyesEntity>
{
    private static final ResourceLocation TEXTURE = EyesInTheDarkness.location("textures/entity/eyes1.png");
    private final RenderType renderType = RenderType.entityTranslucent(TEXTURE);

    public EyesRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(EyesEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferIn, int packedLightIn)
    {
        BlockPos position = entity.getBlockPosEyes();

        float blockLight = entity.level.getBrightness(LightLayer.BLOCK, position);

        if (entity.level.dimensionType().hasSkyLight())
        {
            float skyLight = entity.level.getBrightness(LightLayer.SKY, position)
                    - (1 - ((ClientLevel) entity.level).getSkyDarken(partialTicks)) * 11;

            blockLight = Math.max(blockLight, skyLight);
        }

        float mixAlpha = Mth.clamp((8 - blockLight) / 8.0f, 0, 1);

        if (mixAlpha <= 0)
            return;

        poseStack.pushPose();
        poseStack.translate(0, entity.getEyeHeight(), 0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        float aggro = entity.getAggroLevel();

        float aggroColorAdjust = 1 - Mth.clamp(aggro, 0, 1);


        final float w = .25f;
        final float h = w * 5 / 13f;

        final float tw = 13 / 32f;
        final float th = 5 / 32f;
        float hoff = getBlinkState(entity, partialTicks, th);

        int packedOverlayCoords = OverlayTexture.NO_OVERLAY;
        int packedLightmapCoords = 0x00F000F0;
        VertexConsumer buffer = bufferIn.getBuffer(renderType);
        Matrix4f matrix = poseStack.last().pose();
        buffer.vertex(matrix, -w, -h, 0)
                .color(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .uv(0, hoff + th)
                .overlayCoords(packedOverlayCoords)
                .uv2(packedLightmapCoords)
                .normal(0, 0, 1)
                .endVertex();
        buffer.vertex(matrix, -w, h, 0)
                .color(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .uv(0, hoff)
                .overlayCoords(packedOverlayCoords)
                .uv2(packedLightmapCoords)
                .normal(0, 0, 1)
                .endVertex();
        buffer.vertex(matrix, w, h, 0)
                .color(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .uv(tw, hoff)
                .overlayCoords(packedOverlayCoords)
                .uv2(packedLightmapCoords)
                .normal(0, 0, 1)
                .endVertex();
        buffer.vertex(matrix, w, -h, 0)
                .color(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .uv(tw, hoff + th)
                .overlayCoords(packedOverlayCoords)
                .uv2(packedLightmapCoords)
                .normal(0, 0, 1)
                .endVertex();

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, bufferIn, packedLightIn);
    }

    private float getBlinkState(EyesEntity entity, float partialTicks, float th)
    {
        float hoff = 0;
        if (entity.blinkingState)
        {
            int half_blink = EyesEntity.BLINK_DURATION / 2;
            if (entity.blinkProgress < half_blink)
            {
                hoff = Mth.floor((entity.blinkProgress + partialTicks) * 4f / half_blink) * th;
            }
            else
            {
                hoff = Math.max(0, 8 - Mth.floor((entity.blinkProgress + partialTicks) * 4f / half_blink)) * th;
            }
        }
        return hoff;
    }

    @Override
    public ResourceLocation getTextureLocation(EyesEntity entity)
    {
        return TEXTURE;
    }
}
