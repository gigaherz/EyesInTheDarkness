package gigaherz.eyes.client;

import com.mojang.blaze3d.platform.InputConstants;
import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.entity.EyesEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fmlclient.registry.RenderingRegistry;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = EyesInTheDarkness.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEvents
{
    @SubscribeEvent
    public static void registerEntityRenders(final FMLClientSetupEvent event)
    {
        RenderingRegistry.registerEntityRenderingHandler(EyesEntity.TYPE, EyesRenderer::new);
    }

    /* */
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
