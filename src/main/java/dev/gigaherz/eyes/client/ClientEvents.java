package dev.gigaherz.eyes.client;

import dev.gigaherz.eyes.EyesInTheDarkness;
import dev.gigaherz.eyes.entity.EyesEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = EyesInTheDarkness.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEvents
{
    @SubscribeEvent
    public static void registerEntityRenders(final EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(EyesEntity.TYPE, EyesRenderer::new);
    }

    /* *\/
    @Mod.EventBusSubscriber(value= Dist.CLIENT, modid = EyesInTheDarkness.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    static class Debug
    {
        @SubscribeEvent
        public static void debug_inputEvent(InputEvent.KeyInputEvent event)
        {
            Minecraft mc = Minecraft.getInstance();
            if (InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_P))
            {
                JumpscareOverlay.INSTANCE.show(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            }
        }
    }
    /* */
}