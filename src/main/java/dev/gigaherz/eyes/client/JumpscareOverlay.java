package dev.gigaherz.eyes.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import dev.gigaherz.eyes.EyesInTheDarkness;
import dev.gigaherz.eyes.config.ConfigData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix3x2f;

import javax.annotation.Nullable;

@EventBusSubscriber(value = Dist.CLIENT, modid = EyesInTheDarkness.MODID)
public class JumpscareOverlay implements GuiLayer
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

    private boolean visible = false;
    private float progress = 0;

    private JumpscareOverlay()
    {
        NeoForge.EVENT_BUS.addListener(this::clientTick);
    }

    public void show(double ex, double ey, double ez)
    {
        if (ConfigData.jumpscareClient)
        {
            visible = true;
            Minecraft.getInstance().level.playLocalSound(ex, ey, ez, EyesInTheDarkness.EYES_JUMPSCARE.get(), SoundSource.HOSTILE, getJumpscareVolume(), 1, false);
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

        var mc = Minecraft.getInstance();
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

        //FIXME: RenderSystem.clear(256);

        poseStack.pushMatrix();
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

        if (alpha > 0)
        {
            graphics.fill(0, 0, screenWidth, screenHeight, alpha << 24);
            if (showCreep)
            {
                int texW = 2048;
                int texH = 1024;

                float scale1 = screenHeight / (float) texH;
                float drawY = 0;
                float drawH = screenHeight;
                float drawW = Mth.floor(texW * scale1);
                float drawX = (screenWidth - drawW) / 2;

                customBlit(graphics, RenderPipelines.GUI_TEXTURED, TEXTURE_FLASH, drawX, drawY, drawW, drawH, 0, 0, texW, texH, texW, texH, (alpha << 24) | 0xFFFFFF);
            }
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

            int texW = 32;
            int texH = 32;

            customBlit(graphics, RenderPipelines.GUI_TEXTURED, TEXTURE_EYES, drawX, drawY, drawW, drawH, tx, ty, tw, th, texW, texH, 0xFFFFFFFF);
        }

        poseStack.popMatrix();
    }

    public void customBlit(
            GuiGraphics graphics,
            RenderPipeline pipeline,
            ResourceLocation texture,
            float x0, float y0,
            float xw, float yh,
            float u0, float v0,
            float uw, float vw,
            float tw, float th,
            int color
    ) {
        GpuTextureView gputextureview = Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView();
        graphics.submitGuiElementRenderState(
                        new BlitRenderStateF(
                                pipeline,
                                TextureSetup.singleTexture(gputextureview),
                                new Matrix3x2f(graphics.pose()),
                                x0,
                                y0,
                                x0 + xw,
                                y0 + yh,
                                (u0 + 0.0F) / tw,
                                (u0 + uw) / tw,
                                (v0 + 0.0F) / th,
                                (v0 + vw) / th,
                                color,
                                graphics.peekScissorStack()
                        )
                );
    }

    public record BlitRenderStateF(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            float x0,
            float y0,
            float x1,
            float y1,
            float u0,
            float u1,
            float v0,
            float v1,
            int color,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState
    {
        public BlitRenderStateF(
                RenderPipeline pipeline,
                TextureSetup textureSetup,
                Matrix3x2f pose,
                float x0,
                float y0,
                float x1,
                float y1,
                float u0,
                float v0,
                float u1,
                float v1,
                int color,
                @Nullable ScreenRectangle bounds
        ) {
            this(
                    pipeline,
                    textureSetup,
                    pose,
                    x0,
                    y0,
                    x1,
                    y1,
                    u0,
                    v0,
                    u1,
                    v1,
                    color,
                    bounds,
                    getBounds(x0, y0, x1, y1, pose, bounds)
            );
        }

        @Override
        public void buildVertices(VertexConsumer consumer, float z) {
            consumer.addVertexWith2DPose(this.pose(), this.x0(), this.y0(), z).setUv(this.u0(), this.v0()).setColor(this.color());
            consumer.addVertexWith2DPose(this.pose(), this.x0(), this.y1(), z).setUv(this.u0(), this.v1()).setColor(this.color());
            consumer.addVertexWith2DPose(this.pose(), this.x1(), this.y1(), z).setUv(this.u1(), this.v1()).setColor(this.color());
            consumer.addVertexWith2DPose(this.pose(), this.x1(), this.y0(), z).setUv(this.u1(), this.v0()).setColor(this.color());
        }

        @Nullable
        private static ScreenRectangle getBounds(
                float x0, float y0, float x1, float y1, Matrix3x2f pose, @Nullable ScreenRectangle rect
        ) {
            ScreenRectangle screenrectangle = new ScreenRectangle(Mth.floor(x0), Mth.floor(y0), Mth.ceil(x1 - x0), Mth.ceil(y1 - y0)).transformMaxBounds(pose);
            return rect != null ? rect.intersection(screenrectangle) : screenrectangle;
        }
    }

}
