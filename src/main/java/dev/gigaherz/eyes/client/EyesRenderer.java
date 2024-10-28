package dev.gigaherz.eyes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gigaherz.eyes.EyesInTheDarkness;
import dev.gigaherz.eyes.entity.EyesEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix4f;

public class EyesRenderer extends EntityRenderer<EyesEntity, EyesRenderer.EyesRenderState>
{
    private static final ResourceLocation TEXTURE = EyesInTheDarkness.location("textures/entity/eyes1.png");
    private final RenderType renderTypeBase = RenderType.entityTranslucentEmissive(TEXTURE);
    private final RenderType renderTypeGlow = RenderType.eyes(TEXTURE);

    private static final float WIDTH = .25f;
    private static final float HEIGHT = WIDTH * 5 / 13f;
    private static final float TEXWIDTH = 13 / 32f;
    private static final float TEXHEIGHT = 5 / 32f;

    public EyesRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    public static class EyesRenderState extends EntityRenderState
    {
        public float blockLight;
        public float hoff;
        public float aggro;
    }

    @Override
    public EyesRenderState createRenderState()
    {
        return new EyesRenderState();
    }

    @Override
    public void extractRenderState(EyesEntity entity, EyesRenderState state, float partialTick)
    {
        super.extractRenderState(entity, state, partialTick);

        var position = entity.getBlockPosEyes();
        state.blockLight = entity.level().getBrightness(LightLayer.BLOCK, position);

        if (entity.level().dimensionType().hasSkyLight())
        {
            float skyLight = entity.level().getBrightness(LightLayer.SKY, position)
                    - (1 - ((ClientLevel) entity.level()).getSkyDarken(partialTick)) * 11;

            state.blockLight = Math.max(state.blockLight, skyLight);
        }

        state.hoff = getBlinkState(entity, partialTick, TEXHEIGHT);

        state.aggro = entity.getAggroLevel();
    }

    @Override
    public void render(EyesRenderState state, PoseStack poseStack, MultiBufferSource bufferIn, int packedLightmapCoords)
    {
        float blockLight = state.blockLight;
        float hoff = state.hoff;
        float aggro = state.aggro;

        float mixAlpha = Mth.clamp((8 - blockLight) / 8.0f, 0, 1);

        if (mixAlpha <= 0)
            return;

        poseStack.pushPose();
        poseStack.translate(0, state.eyeHeight, 0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        float aggroColorAdjust = 1 - Mth.clamp(aggro, 0, 1);

        int packedOverlayCoords = OverlayTexture.NO_OVERLAY;

        renderEye(poseStack, bufferIn, renderTypeBase, packedLightmapCoords,  packedOverlayCoords, mixAlpha, aggroColorAdjust, hoff);
        poseStack.translate(0,0, -0.001f);
        renderEye(poseStack, bufferIn, renderTypeGlow, LightTexture.FULL_SKY, packedOverlayCoords, mixAlpha, aggroColorAdjust, hoff);

        poseStack.popPose();

        super.render(state, poseStack, bufferIn, packedLightmapCoords);
    }

    private static void renderEye(PoseStack poseStack,
                                  MultiBufferSource bufferIn, RenderType renderType,
                                  int packedLightmapCoords, int packedOverlayCoords,
                                  float mixAlpha, float aggroColorAdjust, float hoff)
    {
        VertexConsumer buffer = bufferIn.getBuffer(renderType);
        Matrix4f matrix = poseStack.last().pose();
        buffer.addVertex(matrix, -WIDTH, -HEIGHT, 0)
                .setColor(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .setUv(0, hoff + TEXHEIGHT)
                .setOverlay(packedOverlayCoords)
                .setLight(packedLightmapCoords)
                .setNormal(0, 0, 1);
        buffer.addVertex(matrix, -WIDTH, HEIGHT, 0)
                .setColor(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .setUv(0, hoff)
                .setOverlay(packedOverlayCoords)
                .setLight(packedLightmapCoords)
                .setNormal(0, 0, 1);
        buffer.addVertex(matrix, WIDTH, HEIGHT, 0)
                .setColor(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .setUv(TEXWIDTH, hoff)
                .setOverlay(packedOverlayCoords)
                .setLight(packedLightmapCoords)
                .setNormal(0, 0, 1);
        buffer.addVertex(matrix, WIDTH, -HEIGHT, 0)
                .setColor(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .setUv(TEXWIDTH, hoff + TEXHEIGHT)
                .setOverlay(packedOverlayCoords)
                .setLight(packedLightmapCoords)
                .setNormal(0, 0, 1);
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
}
