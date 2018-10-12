package gigaherz.eyes.client;

import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.entity.EntityEyes;
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
    private static final ResourceLocation TEXTURE = EyesInTheDarkness.location("textures/entity/eyes2.png");

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
    private static final int ANIMATION_LENGTH = 10;
    private static final int ANIMATION_LINGER = 90;
    private static final int ANIMATION_FADE = 60;
    private static final int ANIMATION_FADE_START = ANIMATION_LENGTH + ANIMATION_LINGER;
    private static final int ANIMATION_TOTAL = ANIMATION_LENGTH + ANIMATION_LINGER + ANIMATION_FADE;

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
        GlStateManager.enableBlend();

        float time = progress + event.getPartialTicks();
        if (time >= ANIMATION_TOTAL)
        {
            visible = false;
            progress = 0;
            return;
        }

        float scale = Float.MAX_VALUE;
        for(Rectangle r : FRAMES)
        {
            float s = Math.min(
                MathHelper.floor(res.getScaledWidth() * 0.8 / (float)r.getWidth()),
                MathHelper.floor(res.getScaledHeight() * 0.8 / (float)r.getHeight()));
            scale = Math.min(scale, s);
        }

        scale = Math.min(1, (1+time)/(1+ANIMATION_LENGTH)) * scale;

        int currentFrame = Math.min(FRAMES.length-1, MathHelper.floor(FRAMES.length * time / ANIMATION_LENGTH));

        if (time >= ANIMATION_FADE_START)
        {
            float fade = Math.max(0, (time - ANIMATION_FADE_START) / ANIMATION_FADE);
            float blinkspeed = (float) (1 + Math.pow(fade, 3));
            int blinkstate = MathHelper.floor(20 * blinkspeed) & 1;

            if (blinkstate != 0)
                return;
        }

        Rectangle rect = FRAMES[currentFrame];
        int tx = rect.getX();
        int ty = rect.getY();
        int tw = rect.getWidth();
        int th = rect.getHeight();
        float texW = 32;
        float texH = 32;

        float drawW = tw * scale;
        float drawH = th * scale;
        float drawX = (res.getScaledWidth() - drawW)/2;
        float drawY = (res.getScaledHeight() - drawH)/2;

        mc.getRenderManager().renderEngine.bindTexture(TEXTURE);

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
