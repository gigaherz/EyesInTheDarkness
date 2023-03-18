package dev.gigaherz.eyes.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.gigaherz.eyes.EyesInTheDarkness;
import dev.gigaherz.eyes.config.ConfigData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = EyesInTheDarkness.MODID, bus= Mod.EventBusSubscriber.Bus.MOD)
public class JumpscareOverlay extends GuiComponent implements IGuiOverlay
{
    private static final ResourceLocation TEXTURE_EYES = EyesInTheDarkness.location("textures/entity/eyes2.png");
    private static final ResourceLocation TEXTURE_FLASH = EyesInTheDarkness.location("textures/creepy.png");

    public static JumpscareOverlay INSTANCE = new JumpscareOverlay();

    private static final Rect2i[] FRAMES = {
            new Rect2i(0, 0, 13, 6),
            new Rect2i(0, 7, 13, 6),
            new Rect2i(0, 14, 13, 6),
            new Rect2i(0, 21, 13, 6),
            new Rect2i(15, 1, 15, 8),
            new Rect2i(15, 16, 15, 12),
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

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event)
    {
        event.registerAbove(VanillaGuiOverlay.PORTAL.id(), "jumpscare", INSTANCE);
    }

    private Minecraft mc;
    private boolean visible = false;
    private float progress = 0;

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
            mc.level.playLocalSound(ex, ey, ez, EyesInTheDarkness.EYES_JUMPSCARE.get(), SoundSource.HOSTILE, getJumpscareVolume(), 1, false);
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


    @Override
    public void render(ForgeGui gui, PoseStack poseStack, float partialTicks, int width, int height)
    {
        if (!visible) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float time = progress + mc.getFrameTime();
        if (time >= ANIMATION_TOTAL)
        {
            visible = false;
            progress = 0;
            return;
        }

        RenderSystem.clear(256, false);
        poseStack.pushPose();
        //poseStack.translatef(0.0F, 0.0F, -2000.0F);

        float darkening = Mth.clamp(
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
                blinkstate = Mth.floor(20 * blinkspeed) & 1;
                showCreep = blinkstate == 1;
            }
        }

        int alpha = Mth.floor(darkening * 255);

        if (showCreep)
        {
            int texW = 2048;
            int texH = 1024;

            float scale1 = screenHeight / (float) texH;
            int drawY = 0;
            int drawH = screenHeight;
            int drawW = Mth.floor(texW * scale1);
            int drawX = (screenWidth - drawW) / 2;

            drawScaledCustomTexture(TEXTURE_FLASH, poseStack, texW, texH, 0, 0, texW, texH, drawX, drawY, drawW, drawH, (alpha << 24) | 0xFFFFFF);
        }
        else
        {
            // FIXME
            PoseStack temp = new PoseStack();
            fill(temp, 0, 0, screenWidth, screenHeight, alpha << 24);
        }

        if (blinkstate != 1)
        {
            float scale = Float.MAX_VALUE;
            for (Rect2i r : FRAMES)
            {
                float s = Math.min(
                        Mth.floor(screenWidth * 0.8 / (float) r.getWidth()),
                        Mth.floor(screenHeight * 0.8 / (float) r.getHeight()));
                scale = Math.min(scale, s);
            }

            scale = Math.min(1, (1 + time) / (1 + ANIMATION_APPEAR)) * scale;

            int currentFrame = Math.min(FRAMES.length - 1, Mth.floor(FRAMES.length * time / ANIMATION_APPEAR));

            Rect2i rect = FRAMES[currentFrame];
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
            drawScaledCustomTexture(TEXTURE_EYES, poseStack, texW, texH, tx, ty, tw, th, drawX, drawY, drawW, drawH);
        }

        poseStack.popPose();
    }

    private void drawScaledCustomTexture(ResourceLocation tex, PoseStack poseStack, float texW, float texH, int tx, int ty, int tw, int th, float targetX, float targetY, float targetW, float targetH)
    {
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, tex);

        Matrix4f matrix = poseStack.last().pose();

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, targetX, targetY, 0).uv(tx / texW, ty / texH).endVertex();
        buffer.vertex(matrix, targetX, targetY + targetH, 0).uv(tx / texW, (ty + th) / texH).endVertex();
        buffer.vertex(matrix, targetX + targetW, targetY + targetH, 0).uv((tx + tw) / texW, (ty + th) / texH).endVertex();
        buffer.vertex(matrix, targetX + targetW, targetY, 0).uv((tx + tw) / texW, ty / texH).endVertex();
        tessellator.end();

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private void drawScaledCustomTexture(ResourceLocation tex, PoseStack poseStack, float texW, float texH, int tx, int ty, int tw, int th, float targetX, float targetY, float targetW, float targetH, int color)
    {
        int a = (color >> 24) & 255;
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = (color >> 0) & 255;

        RenderSystem.enableBlend();
        RenderSystem.enableTexture();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, tex);

        Matrix4f matrix = poseStack.last().pose();

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(matrix, targetX, targetY, 0).uv(tx / texW, ty / texH).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, targetX, targetY + targetH, 0).uv(tx / texW, (ty + th) / texH).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, targetX + targetW, targetY + targetH, 0).uv((tx + tw) / texW, (ty + th) / texH).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, targetX + targetW, targetY, 0).uv((tx + tw) / texW, ty / texH).color(r, g, b, a).endVertex();
        tessellator.end();

        RenderSystem.disableBlend();
    }
}
