package gigaherz.eyes.client;

import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.entity.EyesEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(value= Dist.CLIENT, modid = EyesInTheDarkness.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEvents
{
    @SubscribeEvent
    public static void registerEntityRenders(final FMLClientSetupEvent event)
    {
        RenderingRegistry.registerEntityRenderingHandler(EyesEntity.class, RenderEyes::new);
    }

    /* *-/
    @SubscribeEvent
    public static void debug_inputEvent(InputEvent.KeyInputEvent event)
    {
        if (Keyboard.isKeyDown(Keyboard.KEY_P))
        {
            Minecraft mc = Minecraft.getMinecraft();
            JumpscareOverlay.INSTANCE.show(mc.player.posX,mc.player.posY,mc.player.posZ);
        }
    }
    /**/
}
