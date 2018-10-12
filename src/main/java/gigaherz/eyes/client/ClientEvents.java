package gigaherz.eyes.client;

import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.entity.EntityEyes;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

@Mod.EventBusSubscriber(value= Side.CLIENT, modid = EyesInTheDarkness.MODID)
public class ClientEvents
{
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event)
    {
        RenderingRegistry.registerEntityRenderingHandler(EntityEyes.class, RenderEyes::new);
    }
/*
    @SubscribeEvent
    public static void debug_inputEvent(InputEvent.KeyInputEvent event)
    {
        if (Keyboard.isKeyDown(Keyboard.KEY_P))
        {
            Minecraft mc = Minecraft.getMinecraft();
            JumpscareOverlay.INSTANCE.show(mc.player.posX,mc.player.posY,mc.player.posZ);
        }
    }
    */
}
