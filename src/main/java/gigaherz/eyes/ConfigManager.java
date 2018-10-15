package gigaherz.eyes;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

@Config(modid=EyesInTheDarkness.MODID)
public class ConfigManager
{
    @Config.Comment("If false, the eyes entity will not spawn naturally during the night-")
    public static boolean EnableNaturalSpawn = true;

    @Config.Comment("If -1, the default spawn weight will be used.")
    public static int OverrideWeight = -1;

    @Config.RangeInt(min = 1)
    public static int MinimumPackSize = 1;

    @Config.RangeInt(min = 1)
    public static int MaximumPackSize = 2;

    @Config.Comment("If the whitelist is not used, this list contains biomes excluded from the list of all biomes.")
    public static String[] BiomeBlacklist = new String[] { "minecraft:void" };

    @Config.Comment("If not empty, this list of biomes will be used instead of all biomes. The blacklist will not take effect.")
    public static String[] BiomeWhitelist = new String[0];
}
