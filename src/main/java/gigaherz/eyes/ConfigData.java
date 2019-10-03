package gigaherz.eyes;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class ConfigData
{

    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static
    {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static class ServerConfig
    {
        public final ForgeConfigSpec.BooleanValue EnableNaturalSpawn;
        public final ForgeConfigSpec.IntValue OverrideWeight;
        public final ForgeConfigSpec.IntValue MinimumPackSize;
        public final ForgeConfigSpec.IntValue MaximumPackSize;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> BiomeWhitelist;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> BiomeBlacklist;
        public final ForgeConfigSpec.BooleanValue Jumpscare;
        public final ForgeConfigSpec.IntValue JumpscareHurtLevel;
        public final ForgeConfigSpec.BooleanValue EyesCanAttackWhileLit;

        ServerConfig(ForgeConfigSpec.Builder builder)
        {
            builder.push("general");
            EnableNaturalSpawn = builder.comment("If false, the eyes entity will not spawn naturally during the night-")
                    .define("enableNaturalSpawn", true);
            OverrideWeight = builder.comment("If -1, the default spawn weight will be used.")
                    .defineInRange("overrideWeight", -1, -1, Integer.MAX_VALUE);
            MinimumPackSize = builder.defineInRange("minimumPackSize", 1, 1, Integer.MAX_VALUE);
            MaximumPackSize = builder.defineInRange("maximumPackSize", 2, 1, Integer.MAX_VALUE);
            BiomeWhitelist = builder.comment("If not empty, this list of biomes will be used instead of all biomes. The blacklist will not take effect.")
                    .defineList("whitelist", Lists.newArrayList(), o -> o instanceof String);
            BiomeBlacklist = builder.comment("If the whitelist is not used, this list contains biomes excluded from the list of all biomes.")
                    .defineList("blacklist", Lists.newArrayList("minecraft:void"), o -> o instanceof String);
            Jumpscare = builder.comment("Set to false to disable the jumpscare system.")
                    .define("jumpscare", true);
            JumpscareHurtLevel = builder.comment("Set to a number > 0 to cause the jumpscare to apply poison the player. A value of 5 will take around half of the health. ")
                    .defineInRange("jumpscareHurtLevel", 1, 0, 6);
            EyesCanAttackWhileLit = builder.comment("While set to true, the eyes entity will ignore the artificial light level and will jumpscare even if it's lit. Daylight will still disable it's AI.")
                    .define("eyesCanAttackWhileLit", true);
            builder.pop();
        }
    }

    @Mod.EventBusSubscriber(modid = EyesInTheDarkness.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    private static class EventHandler
    {

        @SubscribeEvent
        public static void onLoad(final ModConfig.Loading configEvent)
        {
            if (configEvent.getConfig().getSpec() != SERVER_SPEC) return;
            if(ConfigData.SERVER.EnableNaturalSpawn.get())
            {

                EntitySpawnPlacementRegistry.register(EyesInTheDarkness.eyes_entity, EntitySpawnPlacementRegistry.PlacementType.NO_RESTRICTIONS, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MonsterEntity::func_223325_c);

                int currentWeight = ConfigData.SERVER.OverrideWeight.get();

                if(currentWeight < 0)
                {
                    int daysBefore = EyesInTheDarkness.getDaysUntilNextHalloween();

                    int weightMin = 15;
                    int weightMax = 150;

                    currentWeight = weightMin + ((weightMax - weightMin) * (30 - daysBefore)) / 30;
                }

                if (currentWeight > 0)
                {
                    Collection<Biome> biomes = ForgeRegistries.BIOMES.getValues();

                    if (ConfigData.SERVER.BiomeWhitelist.get() != null && ConfigData.SERVER.BiomeWhitelist.get().size() > 0)
                    {
                        Set<String> whitelist = Sets.newHashSet(ConfigData.SERVER.BiomeWhitelist.get());
                        biomes = biomes.stream().filter(b -> whitelist.contains(b.getRegistryName().toString())).collect(Collectors.toList());
                    }
                    else if (ConfigData.SERVER.BiomeBlacklist.get() != null && ConfigData.SERVER.BiomeBlacklist.get().size() > 0)
                    {
                        Set<String> blacklist = Sets.newHashSet(ConfigData.SERVER.BiomeBlacklist.get());
                        biomes = biomes.stream().filter(b -> !blacklist.contains(b.getRegistryName().toString())).collect(Collectors.toList());
                    }
                    final Biome.SpawnListEntry entry = new Biome.SpawnListEntry(EyesInTheDarkness.eyes_entity, currentWeight, ConfigData.SERVER.MinimumPackSize.get(), ConfigData.SERVER.MaximumPackSize.get());
                    biomes.stream().map(biome -> biome.getSpawns(EntityClassification.MONSTER)).forEach(list -> list.add(entry));

                }
            }
        }
    }
}
