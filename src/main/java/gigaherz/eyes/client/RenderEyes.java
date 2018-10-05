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
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

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

        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        //int light = entity.world.getLight(entity.getPosition(), false);

        int blockLight = entity.world.getLightFor(EnumSkyBlock.BLOCK, entity.getPosition());

        if (entity.world.provider.hasSkyLight() && entity.world.canSeeSky(entity.getPosition()))
        {
            int skyLight = entity.world.getLightFor(EnumSkyBlock.SKY, entity.getPosition())
                    - entity.world.getSkylightSubtracted();
            if (skyLight > 0)
            {
                float celestialAngle = entity.world.getCelestialAngleRadians(partialTicks);
                float f1 = celestialAngle < (float)Math.PI ? 0.0F : ((float)Math.PI * 2F);
                celestialAngle = celestialAngle + (f1 - celestialAngle) * 0.2F;
                skyLight = Math.round((float)skyLight * MathHelper.cos(celestialAngle));
            }

            skyLight = MathHelper.clamp(skyLight, 0, 15);

            blockLight = Math.max(blockLight, skyLight);
        }

        float mixAlpha = MathHelper.clamp((8-blockLight)/8.0f,0,1);

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        //GL14.glBlendColor(1,1,1, mixAlpha);
        GlStateManager.color(1.0F, 1.0F, 1.0F, mixAlpha);

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

        //GL14.glBlendColor(1,1,1, 1);
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
