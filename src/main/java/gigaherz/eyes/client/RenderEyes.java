package gigaherz.eyes.client;

import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.entity.EntityEyes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

public class RenderEyes extends Render<EntityEyes>
{
    private static final ResourceLocation TEXTURE = EyesInTheDarkness.location("textures/entity/eyes1.png");

    public RenderEyes(RenderManager renderManager)
    {
        super(renderManager);
    }

    @Override
    public void doRender(EntityEyes entity, double x, double y, double z, float entityYaw, float partialTicks)
    {
        bindTexture(getEntityTexture(entity));

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        GlStateManager.translate(0, entity.getEyeHeight(), 0);
        //GlStateManager.rotate((float) MathHelper.clampedLerp(entity.prevRotationYaw, entity.rotationYaw, partialTicks), 0, 1, 0);
        //GlStateManager.rotate((float) MathHelper.clampedLerp(entity.prevRotationPitch, entity.rotationPitch, partialTicks), 1, 0, 0);

        float viewerYaw = this.renderManager.playerViewY;
        float viewerPitch = this.renderManager.playerViewX;
        GlStateManager.rotate(-viewerYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(viewerPitch, 1.0F, 0.0F, 0.0F);

        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();

        GlStateManager.depthMask(false);

        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 0xF0F0, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        Minecraft.getMinecraft().entityRenderer.setupFogColor(true);

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
            int half_blink = EntityEyes.BLINK_DURATION / 2;
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

        Minecraft.getMinecraft().entityRenderer.setupFogColor(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 0x00F0, 0x00F0);

        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityEyes entity)
    {
        return TEXTURE;
    }
}
