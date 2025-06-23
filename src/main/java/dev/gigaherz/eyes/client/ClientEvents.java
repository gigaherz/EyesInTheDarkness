package dev.gigaherz.eyes.client;

import dev.gigaherz.eyes.EyesInTheDarkness;
import dev.gigaherz.eyes.entity.EyesEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = EyesInTheDarkness.MODID)
public class ClientEvents
{
    @SubscribeEvent
    public static void registerEntityRenders(final EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(EyesInTheDarkness.EYES.get(), EyesRenderer::new);
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
