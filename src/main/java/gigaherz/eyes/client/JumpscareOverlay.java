package gigaherz.eyes.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import gigaherz.eyes.config.ConfigData;
import gigaherz.eyes.EyesInTheDarkness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Rectangle2d;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class JumpscareOverlay extends AbstractGui
{
    private static final ResourceLocation TEXTURE_EYES = EyesInTheDarkness.location("textures/entity/eyes2.png");
    private static final ResourceLocation TEXTURE_FLASH = EyesInTheDarkness.location("textures/creepy.png");

    public static JumpscareOverlay INSTANCE = new JumpscareOverlay();

    private boolean visible = false;
    private float progress = 0;

    private Minecraft mc;

    private static final Rectangle2d[] FRAMES = {
            new Rectangle2d(0, 0, 13, 6),
            new Rectangle2d(0, 7, 13, 6),
            new Rectangle2d(0, 14, 13, 6),
            new Rectangle2d(0, 21, 13, 6),
            new Rectangle2d(15, 1, 15, 8),
            new Rectangle2d(15, 16, 15, 12),
    };
    private static final int ANIMATION_APPEAR = 10;
    private static final int ANIMATION_LINGER = 90;
    private static final int ANIMATION_BLINK = 60;
    private static final int ANIMATION_SCARE1 = 20;
    private static final int ANIMATION_FADE = 20;
    private static final int ANIMATION_BLINK_START = ANIMATION_APPEAR + ANIMATION_LINGER;
    private static final int ANIMATION_SCARE_START = ANIMATION_BLINK_START + ANIMATION_BLINK;
    private static final int ANIMATION_FADE_START = ANIMATION_SCARE_START + ANIMATION_SCARE1;
    private static final int ANIMATION_TOTAL = ANIMATION_APPEAR + ANIMATION_LINGER + ANIMATION_BLINK
            + ANIMATION_SCARE1 + ANIMATION_FADE;

    private JumpscareOverlay()
    {
        mc = Minecraft.getInstance();
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void show(double ex, double ey, double ez)
    {
        if (ConfigData.jumpscareClient)
        {
            visible = true;
            mc.level.playLocalSound(ex, ey, ez, EyesInTheDarkness.eyes_jumpscare, SoundCategory.HOSTILE, getJumpscareVolume(), 1, false);
        }
    }

    protected float getJumpscareVolume()
    {
        return (float)ConfigData.eyeIdleVolume;
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event)
    {
        if (visible)
        {
            progress++;
            if (progress >= ANIMATION_TOTAL)
            {
                visible = false;
                progress = 0;
            }
        }
    }

    @SubscribeEvent
    public void overlayEvent(RenderGameOverlayEvent.Pre event)
    {
        if (!visible || event.getType() != RenderGameOverlayEvent.ElementType.ALL)
            return;

        //1.14 : if not canceled, a mini screen of the game will still render while in a jumpscare.
        event.setCanceled(true);

        int screenWidth = event.getWindow().getScreenWidth();
        int screenHeight = event.getWindow().getScreenHeight();

        float time = progress + event.getPartialTicks();
        if (time >= ANIMATION_TOTAL)
        {
            visible = false;
            progress = 0;
            return;
        }

        RenderSystem.pushMatrix();
        RenderSystem.clear(256, false);
        RenderSystem.matrixMode(5889);
        RenderSystem.loadIdentity();
        RenderSystem.ortho(0.0D, screenWidth, screenHeight, 0.0D, 1000.0D, 3000.0D);
        RenderSystem.matrixMode(5888);
        RenderSystem.loadIdentity();
        RenderSystem.translatef(0.0F, 0.0F, -2000.0F);

        float darkening = MathHelper.clamp(
                Math.min(
                        time / ANIMATION_APPEAR,
                        (ANIMATION_TOTAL - time) / ANIMATION_FADE
                ), 0, 1
        );

        boolean showCreep = false;
        int blinkstate = 0;
        if (time >= ANIMATION_BLINK_START)
        {
            if (time >= ANIMATION_SCARE_START)
            {
                blinkstate = 1;
                showCreep = (time - ANIMATION_SCARE_START) > ANIMATION_SCARE1;
            }
            else
            {
                float fade = Math.max(0, (time - ANIMATION_BLINK_START) / ANIMATION_BLINK);
                float blinkspeed = (float) (1 + Math.pow(fade, 3));
                blinkstate = MathHelper.floor(20 * blinkspeed) & 1;
                showCreep = blinkstate == 1;
            }
        }

        int alpha = MathHelper.floor(darkening * 255);

        if (showCreep)
        {
            int texW = 2048;
            int texH = 1024;

            float scale1 = screenHeight / (float) texH;
            int drawY = 0;
            int drawH = screenHeight;
            int drawW = MathHelper.floor(texW * scale1);
            int drawX = (screenWidth - drawW) / 2;
            RenderSystem.enableBlend();
            drawScaledCustomTexture(TEXTURE_FLASH, texW, texH, 0, 0, texW, texH, drawX, drawY, drawW, drawH, (alpha << 24) | 0xFFFFFF);
        }
        else
        {
            // FIXME
            MatrixStack temp = new MatrixStack();
            fill(temp, 0, 0, screenWidth, screenHeight, alpha << 24);
            RenderSystem.color4f(1, 1, 1, 1);
            RenderSystem.enableBlend();
        }

        if (blinkstate != 1)
        {
            float scale = Float.MAX_VALUE;
            for (Rectangle2d r : FRAMES)
            {
                float s = Math.min(
                        MathHelper.floor(screenWidth * 0.8 / (float) r.getWidth()),
                        MathHelper.floor(screenHeight * 0.8 / (float) r.getHeight()));
                scale = Math.min(scale, s);
            }

            scale = Math.min(1, (1 + time) / (1 + ANIMATION_APPEAR)) * scale;

            int currentFrame = Math.min(FRAMES.length - 1, MathHelper.floor(FRAMES.length * time / ANIMATION_APPEAR));

            Rectangle2d rect = FRAMES[currentFrame];
            int tx = rect.getX();
            int ty = rect.getY();
            int tw = rect.getWidth();
            int th = rect.getHeight();

            float drawW = (tw) * scale;
            float drawH = (th) * scale;
            float drawX = ((screenWidth - drawW) / 2.0f);
            float drawY = ((screenHeight - drawH) / 2.0f);

            float texW = 32;
            float texH = 32;
            drawScaledCustomTexture(TEXTURE_EYES, texW, texH, tx, ty, tw, th, drawX, drawY, drawW, drawH);
        }

        RenderSystem.popMatrix();
    }

    private void drawScaledCustomTexture(ResourceLocation tex, float texW, float texH, int tx, int ty, int tw, int th, float targetX, float targetY, float targetW, float targetH)
    {
        mc.textureManager.bind(tex);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        buffer.vertex(targetX, targetY, 0)
                .uv(tx / texW, ty / texH).endVertex();
        buffer.vertex(targetX, targetY + targetH, 0)
                .uv(tx / texW, (ty + th) / texH).endVertex();
        buffer.vertex(targetX + targetW, targetY + targetH, 0)
                .uv((tx + tw) / texW, (ty + th) / texH).endVertex();
        buffer.vertex(targetX + targetW, targetY, 0)
                .uv((tx + tw) / texW, ty / texH).endVertex();

        tessellator.end();
    }

    private void drawScaledCustomTexture(ResourceLocation tex, float texW, float texH, int tx, int ty, int tw, int th, float targetX, float targetY, float targetW, float targetH, int color)
    {
        int a = (color >> 24) & 255;
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = (color >> 0) & 255;

        mc.textureManager.bind(tex);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        buffer.vertex(targetX, targetY, 0)
                .uv(tx / texW, ty / texH)
                .color(r, g, b, a).endVertex();
        buffer.vertex(targetX, targetY + targetH, 0)
                .uv(tx / texW, (ty + th) / texH)
                .color(r, g, b, a).endVertex();
        buffer.vertex(targetX + targetW, targetY + targetH, 0)
                .uv((tx + tw) / texW, (ty + th) / texH)
                .color(r, g, b, a).endVertex();
        buffer.vertex(targetX + targetW, targetY, 0)
                .uv((tx + tw) / texW, ty / texH)
                .color(r, g, b, a).endVertex();

        tessellator.end();
    }
}
