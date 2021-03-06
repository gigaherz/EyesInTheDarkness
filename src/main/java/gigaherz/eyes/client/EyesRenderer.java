package gigaherz.eyes.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.entity.EyesEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.world.LightType;

import javax.annotation.Nullable;

public class EyesRenderer extends EntityRenderer<EyesEntity>
{
    private static final ResourceLocation TEXTURE = EyesInTheDarkness.location("textures/entity/eyes1.png");
    private final RenderType renderType = RenderType.entityTranslucent(TEXTURE);

    public EyesRenderer(EntityRendererManager renderManager)
    {
        super(renderManager);
    }

    @Override
    public void render(EyesEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer bufferIn, int packedLightIn)
    {
        BlockPos position = entity.getBlockPosEyes();

        float blockLight = entity.level.getBrightness(LightType.BLOCK, position);

        if (entity.level.dimensionType().hasSkyLight())
        {
            float skyLight = entity.level.getBrightness(LightType.SKY, position)
                    - (1 - ((ClientWorld) entity.level).getSkyDarken(partialTicks)) * 11;

            blockLight = Math.max(blockLight, skyLight);
        }

        float mixAlpha = MathHelper.clamp((8 - blockLight) / 8.0f, 0, 1);

        if (mixAlpha <= 0)
            return;

        matrixStack.pushPose();
        matrixStack.translate(0, entity.getEyeHeight(), 0);
        matrixStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        float aggro = entity.getAggroLevel();

        float aggroColorAdjust = 1 - MathHelper.clamp(aggro, 0, 1);


        final float w = .25f;
        final float h = w * 5 / 13f;

        final float tw = 13 / 32f;
        final float th = 5 / 32f;
        float hoff = getBlinkState(entity, partialTicks, th);

        int packedOverlayCoords = OverlayTexture.NO_OVERLAY;
        int packedLightmapCoords = 0x00F000F0;
        IVertexBuilder buffer = bufferIn.getBuffer(renderType);
        Matrix4f matrix = matrixStack.last().pose();
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

        matrixStack.popPose();

        super.render(entity, entityYaw, partialTicks, matrixStack, bufferIn, packedLightIn);
    }

    private float getBlinkState(EyesEntity entity, float partialTicks, float th)
    {
        float hoff = 0;
        if (entity.blinkingState)
        {
            int half_blink = EyesEntity.BLINK_DURATION / 2;
            if (entity.blinkProgress < half_blink)
            {
                hoff = MathHelper.floor((entity.blinkProgress + partialTicks) * 4f / half_blink) * th;
            }
            else
            {
                hoff = Math.max(0, 8 - MathHelper.floor((entity.blinkProgress + partialTicks) * 4f / half_blink)) * th;
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
