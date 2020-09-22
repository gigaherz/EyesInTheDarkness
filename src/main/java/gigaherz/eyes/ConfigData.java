package gigaherz.eyes;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import gigaherz.eyes.entity.EyesEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.MobSpawnInfo;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> BiomeRules;
        public final ForgeConfigSpec.BooleanValue Jumpscare;
        public final ForgeConfigSpec.IntValue JumpscareHurtLevel;
        public final ForgeConfigSpec.BooleanValue EyesCanAttackWhileLit;
        public final ForgeConfigSpec.BooleanValue EnableEyeAggressionEscalation;
        public final ForgeConfigSpec.BooleanValue EyeAggressionDependsOnLocalDifficulty;
        public final ForgeConfigSpec.BooleanValue EyeAggressionDependsOnLightLevel;

        ServerConfig(ForgeConfigSpec.Builder builder)
        {
            builder.push("general");
            EnableNaturalSpawn = builder.comment("If false, the eyes entity will not spawn naturally during the night-")
                    .define("enableNaturalSpawn", true);
            OverrideWeight = builder.comment("If -1, the default spawn weight will be used.")
                    .defineInRange("overrideWeight", -1, -1, Integer.MAX_VALUE);
            MinimumPackSize = builder.defineInRange("minimumPackSize", 1, 1, Integer.MAX_VALUE);
            MaximumPackSize = builder.defineInRange("maximumPackSize", 2, 1, Integer.MAX_VALUE);
            BiomeRules = builder.comment(
                    "Specifies rules for accepting or rejecting biomes.",
                    "The rules are scanned one by one until a rule matches, This means the first rule to match takes precedence over any other subsequent rule, so more specific rules should go first.",
                    "Rules:",
                    "  \"biome:name\"    -- ALLOWS spawning in the given biome.",
                    "  \"!biome:name\"   -- DISALLOWS spawning in the given biome.",
                    "  \"#biome_label\"  -- ALLOWS spawning in the given biome dictionary label.",
                    "  \"!#biome_label\" -- DISALLOWS spawning in the given biome dictionary label.",
                    "  \"!*\"            -- DISALLOWS spawning unconditionally. Place this at the end of the list to disable spawning if no other rules pass (defaults to allow otherwise).",
                    "Examples:",
                    "  To disable spawning in the end: [ \"!#END\" ]",
                    "  To disable spawning in the nether biome: [ \"!minecraft:nether\" ]",
                    "  To disable spawning in forest areas, but allow them in dark fores: [ \"minecraft:dark_forest\", \"!#FOREST\" ]",
                    "NOTE: VOID type biomes are disabled by default, internally. You can explicitly enable those by adding \"#VOID\" to the rules, but this is not recommended."
            ).defineList("biomeRules", Lists.newArrayList(), o -> o instanceof String);
            Jumpscare = builder.comment("Set to false to disable the jumpscare system.")
                    .define("jumpscare", true);
            JumpscareHurtLevel = builder.comment("Set to a number > 0 to cause the jumpscare to apply poison the player. A value of 5 will take around half of the health. ")
                    .defineInRange("jumpscareHurtLevel", 1, 0, 6);
            EyesCanAttackWhileLit = builder.comment("While set to true, the eyes entity will ignore the artificial light level and will jumpscare even if it's lit. Daylight will still disable it's AI.")
                    .define("eyesCanAttackWhileLit", true);
            builder.pop();
            builder.push("eye_aggression");
            EnableEyeAggressionEscalation = builder.comment("While set to true, the eyes entities will progressively get more bold, and move faster, the longer they live.")
                    .define("enableEscalation", true);
            EyeAggressionDependsOnLocalDifficulty = builder.comment("While set to true, the eyes entities will spawn with higher aggresion levels in higher local difficulties.")
                    .define("localDifficulty", true);
            EyeAggressionDependsOnLightLevel = builder.comment("While set to true, the eyes entities will have higher aggression values on lower light levels.")
                    .define("lightLevel", true);
            builder.pop();
        }
    }

    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static
    {
        final Pair<ClientConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class ClientConfig
    {
        public final ForgeConfigSpec.BooleanValue Jumpscare;

        ClientConfig(ForgeConfigSpec.Builder builder)
        {
            builder.push("general");
            Jumpscare = builder.comment("Set to false to prevent jumpscares from displaying client-side.\n" +
                    "NOTE: Jumpscare effects such as poison still apply, this only prevents the visual and sound.")
                    .define("jumpscare", true);
            builder.pop();
        }
    }

    @Mod.EventBusSubscriber(modid = EyesInTheDarkness.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    private static class EventHandler
    {
        private static final List<BiomeRule> rules = Lists.newArrayList();
        private static int currentWeight = 0;

        @SubscribeEvent
        public static void biomeLoading(final ModConfig.ModConfigEvent event)
        {
            ModConfig config = event.getConfig();
            if (config.getSpec() != SERVER_SPEC)
                return;

            if(ConfigData.SERVER.EnableNaturalSpawn.get())
            {
                currentWeight = ConfigData.SERVER.OverrideWeight.get();

                if(currentWeight < 0)
                {
                    int daysBefore = EyesInTheDarkness.getDaysUntilNextHalloween();

                    int weightMin = 15;
                    int weightMax = 150;

                    currentWeight = weightMin + ((weightMax - weightMin) * (30 - daysBefore)) / 30;
                }
            }

            List<? extends String> biomeRules = orDefault(ConfigData.SERVER.BiomeRules.get(), Collections::emptyList);

            rules.clear();
            biomeRules.forEach(r -> rules.add(BiomeRule.parse(r)));
            rules.add(BiomeRule.disallowLabel("void")); // Added at the end to make sure it's lowest priority.
        }

        @SubscribeEvent
        public static void biomeLoading(final BiomeLoadingEvent event)
        {
            if (currentWeight > 0) // If spawn is enabled
            {
                if (isBiomeAllowed(event.getName()))
                {
                    event.getSpawns().getSpawner(EyesEntity.CLASSIFICATION)
                            .add(new MobSpawnInfo.Spawners(EyesEntity.TYPE, currentWeight, ConfigData.SERVER.MinimumPackSize.get(), ConfigData.SERVER.MaximumPackSize.get()));
                }
            }
        }

        private static boolean isBiomeAllowed(ResourceLocation name)
        {
            for (BiomeRule rule : rules)
            {
                if (rule.test(name))
                    return rule.allow;
            }
            return true;
        }

        @Nonnull
        private static <T> T orDefault(@Nullable T value, @Nonnull Supplier<T> defaultSupplier)
        {
            if (value != null)
                return value;
            return defaultSupplier.get();
        }

        private static class BiomeRule implements Predicate<ResourceLocation>
        {
            public final boolean allow;
            public final boolean isLabel;
            public final String labelName;
            public final ResourceLocation registryName;
            public final String labelType;

            private BiomeRule(boolean allow, boolean isLabel, String labelName)
            {
                this.allow = allow;
                this.isLabel = isLabel;
                this.labelName = labelName;
                if (labelName == null)
                {
                    this.registryName = null;
                    this.labelType = null;
                }
                else if(isLabel)
                {
                    this.registryName = null;
                    this.labelType = labelName;
                }
                else
                {
                    this.registryName = new ResourceLocation(labelName);
                    this.labelType = null;
                }
            }

            @Override
            public boolean test(ResourceLocation biome)
            {
                if (labelName == null)
                    return true;
                if (isLabel)
                {
                    return BiomeDictionary.hasType(biome, labelType);
                }
                else
                {
                    return registryName.equals(biome);
                }
            }

            public static BiomeRule parse(String rule)
            {
                boolean allow = true;
                if (rule.startsWith("!"))
                {
                    allow = false;
                    rule = rule.substring(1);
                }
                boolean isLabel = false;
                if (rule.startsWith("#"))
                {
                    isLabel = true;
                    rule = rule.substring(1);
                }
                else if (rule.equals("*"))
                {
                    rule = null;
                }
                return new BiomeRule(allow, isLabel, rule);
            }

            public static BiomeRule disallowLabel(String label)
            {
                return new BiomeRule(false, true, label);
            }
        }
    }

    public static boolean canEyesSpawnAt(EntityType<EyesEntity> entityType, IWorld world, SpawnReason reason, BlockPos pos, Random random)
    {
        return MonsterEntity.canMonsterSpawnInLight(entityType, world, reason, pos, random);
    }
}
