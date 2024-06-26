package dev.gigaherz.eyes.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.gigaherz.eyes.EyesInTheDarkness;
import dev.gigaherz.eyes.config.ConfigData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

@EventBusSubscriber(value = Dist.CLIENT, modid = EyesInTheDarkness.MODID, bus= EventBusSubscriber.Bus.MOD)
public class JumpscareOverlay implements LayeredDraw.Layer
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
    public static void register(RegisterGuiLayersEvent event)
    {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, EyesInTheDarkness.location("jumpscare"), INSTANCE);
    }

    private final Minecraft mc;
    private boolean visible = false;
    private float progress = 0;

    private JumpscareOverlay()
    {
        mc = Minecraft.getInstance();
        NeoForge.EVENT_BUS.addListener(this::clientTick);
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

    public void clientTick(ClientTickEvent.Pre event)
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
    public void render(GuiGraphics graphics, DeltaTracker partialTicks)
    {
        if (!visible) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float time = progress + partialTicks.getGameTimeDeltaPartialTick(true);
        if (time >= ANIMATION_TOTAL)
        {
            visible = false;
            progress = 0;
            return;
        }

        var poseStack = graphics.pose();

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
            graphics.fill(0, 0, screenWidth, screenHeight, alpha << 24);
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
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, tex);

        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, targetX, targetY, 0)
                .setUv(tx / texW, ty / texH);
        buffer.addVertex(matrix, targetX, targetY + targetH, 0)
                .setUv(tx / texW, (ty + th) / texH);
        buffer.addVertex(matrix, targetX + targetW, targetY + targetH, 0)
                .setUv((tx + tw) / texW, (ty + th) / texH);
        buffer.addVertex(matrix, targetX + targetW, targetY, 0)
                .setUv((tx + tw) / texW, ty / texH);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void drawScaledCustomTexture(ResourceLocation tex, PoseStack poseStack, float texW, float texH, int tx, int ty, int tw, int th, float targetX, float targetY, float targetW, float targetH, int color)
    {
        int a = (color >> 24) & 255;
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = (color >> 0) & 255;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, tex);

        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, targetX, targetY, 0)
                .setUv(tx / texW, ty / texH)
                .setColor(r, g, b, a);
        buffer.addVertex(matrix, targetX, targetY + targetH, 0)
                .setUv(tx / texW, (ty + th) / texH)
                .setColor(r, g, b, a);
        buffer.addVertex(matrix, targetX + targetW, targetY + targetH, 0)
                .setUv((tx + tw) / texW, (ty + th) / texH)
                .setColor(r, g, b, a);
        buffer.addVertex(matrix, targetX + targetW, targetY, 0)
                .setUv((tx + tw) / texW, ty / texH)
                .setColor(r, g, b, a);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }
}
