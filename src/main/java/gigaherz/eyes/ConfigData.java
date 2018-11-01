package gigaherz.eyes;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.Map;

@Config(modid = EyesInTheDarkness.MODID)
public class ConfigData
{
    @Config.Comment("If false, the eyes entity will not spawn naturally during the night-")
    @Config.RequiresMcRestart()
    public static boolean EnableNaturalSpawn = true;

    @Config.Comment("If -1, the default spawn weight will be used.")
    @Config.RequiresMcRestart()
    public static int OverrideWeight = -1;

    @Config.RangeInt(min = 1)
    @Config.RequiresMcRestart()
    public static int MinimumPackSize = 1;

    @Config.RangeInt(min = 1)
    @Config.RequiresMcRestart()
    public static int MaximumPackSize = 2;

    @Config.Comment("If the whitelist is not used, this list contains biomes excluded from the list of all biomes.")
    @Config.RequiresMcRestart()
    public static String[] BiomeBlacklist = new String[]{"minecraft:void"};

    @Config.Comment("If not empty, this list of biomes will be used instead of all biomes. The blacklist will not take effect.")
    @Config.RequiresMcRestart()
    public static String[] BiomeWhitelist = new String[0];

    @Config.Comment("Set to a number > 0 to cause the jumpscare to apply poison the player. A value of 5 will take around half of the health. ")
    @Config.RangeInt(min = 0, max = 6)
    public static int JumpscareHurtLevel = 1;

    @Config.Comment("While set to true, the eyes entity will ignore the artificial light level and will jumpscare even if it's lit. Daylight will still disable it's AI.")
    public static boolean EyesCanAttackWhileLit = true;

    @Mod.EventBusSubscriber(modid = EyesInTheDarkness.MODID)
    private static class EventHandler
    {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event)
        {
            if (event.getModID().equals(EyesInTheDarkness.MODID))
            {
                ConfigManager.sync(EyesInTheDarkness.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
