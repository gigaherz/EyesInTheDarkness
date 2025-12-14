package dev.gigaherz.eyes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gigaherz.eyes.EyesInTheDarkness;
import dev.gigaherz.eyes.entity.EyesEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix4f;

public class EyesRenderer extends EntityRenderer<EyesEntity, EyesRenderer.EyesRenderState>
{
    private static final Identifier TEXTURE = EyesInTheDarkness.location("textures/entity/eyes1.png");
    private final RenderType renderTypeBase = RenderTypes.entityTranslucentEmissive(TEXTURE);
    private final RenderType renderTypeGlow = RenderTypes.eyes(TEXTURE);

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

        var level = entity.level();

        var position = entity.getBlockPosEyes();
        state.blockLight = level.getBrightness(LightLayer.BLOCK, position);

        if (level.dimensionType().hasSkyLight())
        {
            float skyLight = level.getBrightness(LightLayer.SKY, position) - level.getSkyDarken();

            state.blockLight = Math.max(state.blockLight, skyLight);
        }

        state.hoff = getBlinkState(entity, partialTick, TEXHEIGHT);

        state.aggro = entity.getAggroLevel();
    }

    @Override
    public void submit(EyesRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState)
    {
        float blockLight = state.blockLight;
        float hoff = state.hoff;
        float aggro = state.aggro;

        float mixAlpha = Mth.clamp((8 - blockLight) / 8.0f, 0, 1);

        if (mixAlpha <= 0)
            return;

        poseStack.pushPose();
        poseStack.translate(0, state.eyeHeight, 0);
        poseStack.mulPose(cameraRenderState.orientation);

        float aggroColorAdjust = 1 - Mth.clamp(aggro, 0, 1);

        collector.order(1).submitCustomGeometry(poseStack, renderTypeBase, (pose, vertexConsumer) ->
                renderEye(pose, vertexConsumer, state.lightCoords, mixAlpha, aggroColorAdjust, hoff));
        poseStack.translate(0,0, -0.001f);
        collector.order(1).submitCustomGeometry(poseStack, renderTypeGlow, (pose, vertexConsumer) ->
                renderEye(pose, vertexConsumer, LightTexture.FULL_SKY, mixAlpha, aggroColorAdjust, hoff));

        poseStack.popPose();

        super.submit(state, poseStack, collector, cameraRenderState);
    }

    private static void renderEye(PoseStack.Pose pose, VertexConsumer buffer,
                                  int packedLightmapCoords,
                                  float mixAlpha, float aggroColorAdjust, float hoff)
    {
        Matrix4f matrix = pose.pose();
        buffer.addVertex(matrix, -WIDTH, -HEIGHT, 0)
                .setColor(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .setUv(0, hoff + TEXHEIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLightmapCoords)
                .setNormal(0, 0, 1);
        buffer.addVertex(matrix, -WIDTH, HEIGHT, 0)
                .setColor(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .setUv(0, hoff)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLightmapCoords)
                .setNormal(0, 0, 1);
        buffer.addVertex(matrix, WIDTH, HEIGHT, 0)
                .setColor(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .setUv(TEXWIDTH, hoff)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLightmapCoords)
                .setNormal(0, 0, 1);
        buffer.addVertex(matrix, WIDTH, -HEIGHT, 0)
                .setColor(1.0F, aggroColorAdjust, aggroColorAdjust, mixAlpha)
                .setUv(TEXWIDTH, hoff + TEXHEIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY)
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
