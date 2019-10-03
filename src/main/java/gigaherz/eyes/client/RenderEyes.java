package gigaherz.eyes.client;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.entity.EyesEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

public class RenderEyes extends EntityRenderer<EyesEntity>
{
    private static final ResourceLocation TEXTURE = EyesInTheDarkness.location("textures/entity/eyes1.png");

    public RenderEyes(EntityRendererManager renderManager)
    {
        super(renderManager);
    }

    @Override
    public void doRender(EyesEntity entity, double x, double y, double z, float entityYaw, float partialTicks)
    {
        BlockPos position = entity.getBlockPosEyes();

        float blockLight = entity.world.getLightFor(LightType.BLOCK, position);

        if (entity.world.dimension.hasSkyLight())
        {
            float skyLight = entity.world.getLightFor(LightType.SKY, position)
                    - (1 - entity.world.dimension.getSunBrightness(partialTicks)) * 11;

            blockLight = Math.max(blockLight, skyLight);
        }

        float mixAlpha = MathHelper.clamp((8-blockLight)/8.0f,0,1);

        if (mixAlpha <= 0)
            return;

        bindTexture(getEntityTexture(entity));

        GlStateManager.pushMatrix();
        GlStateManager.translated(x, y, z);

        GlStateManager.translated(0, entity.getEyeHeight(), 0);
        //GlStateManager.rotate((float) MathHelper.clampedLerp(entity.prevRotationYaw, entity.rotationYaw, partialTicks), 0, 1, 0);
        //GlStateManager.rotate((float) MathHelper.clampedLerp(entity.prevRotationPitch, entity.rotationPitch, partialTicks), 1, 0, 0);

        float viewerYaw = this.renderManager.playerViewY;
        float viewerPitch = this.renderManager.playerViewX;
        GlStateManager.rotatef(-viewerYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(viewerPitch, 1.0F, 0.0F, 0.0F);

        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableLighting();

        GlStateManager.depthMask(false);

        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        GLX.glMultiTexCoord2f(GLX.GL_TEXTURE1, 240, 240);
        //GL14.glBlendColor(1,1,1, mixAlpha);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, mixAlpha);

        Minecraft.getInstance().gameRenderer.setupFogColor(true);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        final float w = .5f;
        final float h = w * 5 / 13f;

        final float tw = 13 / 32f;
        final float th = 5 / 32f;
        float hoff = 0;
        if (entity.blinkingState)
        {
            int half_blink = EyesEntity.BLINK_DURATION / 2;
            if (entity.blinkProgress < half_blink)
            {
                hoff = MathHelper.floor((entity.blinkProgress+partialTicks) * 4f / half_blink) * th;
            }
            else
            {
                hoff = Math.max(0, 8 - MathHelper.floor((entity.blinkProgress+partialTicks) * 4f / half_blink)) * th;
            }
        }

        buffer.pos(-.5 * w, -.5 * h, 0).tex(0, hoff + th).endVertex();
        buffer.pos(-.5 * w, .5 * h, 0).tex(0, hoff).endVertex();
        buffer.pos(.5 * w, .5 * h, 0).tex(tw, hoff).endVertex();
        buffer.pos(.5 * w, -.5 * h, 0).tex(tw, hoff + th).endVertex();

        tessellator.draw();

        GlStateManager.popMatrix();

        Minecraft.getInstance().gameRenderer.setupFogColor(false);
        GLX.glMultiTexCoord2f(GLX.GL_TEXTURE1, 0x00F0, 0x00F0);

        //GL14.glBlendColor(1,1,1, 1);
        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EyesEntity entity)
    {
        return TEXTURE;
    }
}
