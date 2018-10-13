package gigaherz.eyes.client;

import gigaherz.eyes.EyesInTheDarkness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.Rectangle;

public class JumpscareOverlay extends Gui
{
    private static final ResourceLocation TEXTURE_EYES = EyesInTheDarkness.location("textures/entity/eyes2.png");
    private static final ResourceLocation TEXTURE_FLASH = EyesInTheDarkness.location("textures/creepy.png");

    public static JumpscareOverlay INSTANCE = new JumpscareOverlay();

    private boolean visible = false;
    private float progress = 0;

    private Minecraft mc;

    private static final Rectangle[] FRAMES = {
            new Rectangle(0,0,13,6),
            new Rectangle(0,7,13,6),
            new Rectangle(0,14,13,6),
            new Rectangle(0,21,13,6),
            new Rectangle(15,1,15,8),
            new Rectangle(15,16,14,12),
    };
    private static final int ANIMATION_APPEAR = 10;
    private static final int ANIMATION_LINGER = 90;
    private static final int ANIMATION_BLINK = 60;
    private static final int ANIMATION_SCARE1 = 20;
    private static final int ANIMATION_SCARE2 = 20;
    private static final int ANIMATION_FADE = 20;
    private static final int ANIMATION_BLINK_START = ANIMATION_APPEAR + ANIMATION_LINGER;
    private static final int ANIMATION_SCARE_START = ANIMATION_BLINK_START + ANIMATION_BLINK;
    private static final int ANIMATION_FADE_START = ANIMATION_SCARE_START + ANIMATION_SCARE1 + ANIMATION_SCARE2;
    private static final int ANIMATION_TOTAL = ANIMATION_APPEAR + ANIMATION_LINGER + ANIMATION_BLINK
            + ANIMATION_SCARE1 + ANIMATION_SCARE2 + ANIMATION_FADE;

    private JumpscareOverlay()
    {
        mc = Minecraft.getMinecraft();
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void show(double ex, double ey, double ez)
    {
        visible = true;
        mc.world.playSound(ex, ey, ez, EyesInTheDarkness.eyes_jumpscare, SoundCategory.HOSTILE, 1, 1, false);
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

        ScaledResolution res = event.getResolution();

        mc.entityRenderer.setupOverlayRendering();

        float time = progress + event.getPartialTicks();
        if (time >= ANIMATION_TOTAL)
        {
            visible = false;
            progress = 0;
            return;
        }

        float darkening = MathHelper.clamp(
                Math.min(
                        time/ ANIMATION_APPEAR,
                        (ANIMATION_TOTAL-time)/ANIMATION_FADE
                ), 0, 1
        );
        int alpha = MathHelper.floor(darkening * 255);

        drawRect(0,0, res.getScaledWidth(), res.getScaledHeight(), alpha << 24);
        GlStateManager.color(1,1,1,1);
        GlStateManager.enableBlend();

        float scale = Float.MAX_VALUE;
        for(Rectangle r : FRAMES)
        {
            float s = Math.min(
                MathHelper.floor(res.getScaledWidth() * 0.8 / (float)r.getWidth()),
                MathHelper.floor(res.getScaledHeight() * 0.8 / (float)r.getHeight()));
            scale = Math.min(scale, s);
        }

        scale = Math.min(1, (1+time)/(1+ ANIMATION_APPEAR)) * scale;

        int currentFrame = Math.min(FRAMES.length-1, MathHelper.floor(FRAMES.length * time / ANIMATION_APPEAR));

        if (time >= ANIMATION_BLINK_START)
        {
            boolean showCreep;
            int blinkstate;
            if(time >= ANIMATION_SCARE_START)
            {
                blinkstate = 1;
                showCreep = (time - ANIMATION_SCARE_START) > ANIMATION_SCARE1 &&
                        time < ANIMATION_FADE_START;
            }
            else
            {
                float fade = Math.max(0, (time - ANIMATION_BLINK_START) / ANIMATION_BLINK);
                float blinkspeed = (float) (1 + Math.pow(fade, 3));
                blinkstate = MathHelper.floor(20 * blinkspeed) & 1;
                showCreep = blinkstate == 1;
            }

            if (showCreep)
            {
                int texW = 2048;
                int texH = 1024;

                float scale1 = res.getScaledHeight() / (float)texH;
                int drawY = 0;
                int drawH = res.getScaledHeight();
                int drawW = MathHelper.floor(texW * scale1);
                int drawX = (res.getScaledWidth()-drawW)/2;
                drawScaledCustomTexture(TEXTURE_FLASH, texW, texH, 0, 0, texW, texH, drawW, drawH, drawX, drawY);
            }
            if(blinkstate == 1)
            {
                return;
            }
        }

        Rectangle rect = FRAMES[currentFrame];
        int tx = rect.getX();
        int ty = rect.getY();
        int tw = rect.getWidth();
        int th = rect.getHeight();
        float texW = 32;
        float texH = 32;

        float drawW = (tw+1) * scale;
        float drawH = (th+1) * scale;
        float drawX = (float) ((res.getScaledWidth_double() - drawW)/2);
        float drawY = (float) ((res.getScaledHeight_double() - drawH)/2);

        drawScaledCustomTexture(TEXTURE_EYES, texW, texH, tx, ty, tw, th, drawW, drawH, drawX, drawY);
    }

    private void drawScaledCustomTexture(ResourceLocation tex, float texW, float texH, int tx, int ty, int tw, int th, float drawW, float drawH, float drawX, float drawY)
    {
        mc.getRenderManager().renderEngine.bindTexture(tex);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        buffer.pos(drawX, drawY, 0)
                .tex(tx / texW, ty / texH).endVertex();
        buffer.pos(drawX, drawY + drawH, 0)
                .tex(tx / texW, (ty + th) / texH).endVertex();
        buffer.pos(drawX + drawW, drawY + drawH, 0)
                .tex((tx + tw) / texW, (ty + th) / texH).endVertex();
        buffer.pos(drawX + drawW, drawY, 0)
                .tex((tx + tw) / texW, ty / texH).endVertex();

        tessellator.draw();
    }
}
