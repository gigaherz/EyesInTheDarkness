package gigaherz.eyes.config;

import com.google.common.collect.Lists;
import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.entity.EyesEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IServerWorld;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class ConfigData
{
    private static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static
    {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    private static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static
    {
        final Pair<ClientConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    private static class ServerConfig
    {
        public final ForgeConfigSpec.BooleanValue jumpscare;
        public final ForgeConfigSpec.IntValue jumpscareHurtLevel;
        public final ForgeConfigSpec.BooleanValue eyesCanAttackWhileLit;
        public final ForgeConfigSpec.BooleanValue enableEyeAggressionEscalation;
        public final ForgeConfigSpec.BooleanValue eyeAggressionDependsOnLocalDifficulty;
        public final ForgeConfigSpec.BooleanValue eyeAggressionDependsOnLightLevel;
        public final ForgeConfigSpec.DoubleValue eyeIdleVolume;
        public final ForgeConfigSpec.DoubleValue eyeDisappearVolume;
        public final ForgeConfigSpec.DoubleValue eyeJumpscareVolume;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> biomeRules;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> dimensionRules;
        public final ForgeConfigSpec.BooleanValue enableNaturalSpawn;
        public final ForgeConfigSpec.IntValue maxEyesSpawnDistance;

        public final ForgeConfigSpec.IntValue spawnCycleIntervalNormal;
        public final ForgeConfigSpec.IntValue maxEyesAroundPlayerNormal;
        public final ForgeConfigSpec.IntValue maxTotalEyesPerDimensionNormal;

        public final ForgeConfigSpec.IntValue spawnCycleIntervalMidnight;
        public final ForgeConfigSpec.IntValue maxEyesAroundPlayerMidnight;
        public final ForgeConfigSpec.IntValue maxTotalEyesPerDimensionMidnight;

        public final ForgeConfigSpec.IntValue spawnCycleIntervalHalloween;
        public final ForgeConfigSpec.IntValue maxEyesAroundPlayerHalloween;
        public final ForgeConfigSpec.IntValue maxTotalEyesPerDimensionHalloween;

        public final ForgeConfigSpec.DoubleValue speedNoAggro;
        public final ForgeConfigSpec.DoubleValue speedFullAggro;

        public final ForgeConfigSpec.LongValue longSpawnCycleWarning;

        ServerConfig(ForgeConfigSpec.Builder builder)
        {
            builder.push("general");
            {
                jumpscare = builder.comment("Set to false to disable the jumpscare system.")
                        .define("Jumpscare", true);
                jumpscareHurtLevel = builder.comment("Set to a number > 0 to cause the jumpscare to apply poison the player. A value of 5 will take around half of the health. ")
                        .defineInRange("JumpscareHurtLevel", 1, 0, 6);
                eyesCanAttackWhileLit = builder.comment("While set to true, the eyes entity will ignore the artificial light level and will jumpscare even if it's lit. Daylight will still disable it's AI.")
                        .define("EyesCanAttackWhileLit", true);
                speedNoAggro = builder.comment("The speed at which the eyes move, when not aggressive.")
                        .defineInRange("SpeedNoAggro", 0.1f, 0.0f, Double.MAX_VALUE);
                speedFullAggro = builder.comment("The speed at which the eyes move when aggressive.")
                        .defineInRange("SpeedFullAggro", 0.5f, 0.0f, Double.MAX_VALUE);
                longSpawnCycleWarning = builder.comment("The time the spawn cycle can take before a warning is printed to the log. In Microseconds. Default = 50ms")
                        .defineInRange("LongSpawnCycleWarning", 50000L, 0, Long.MAX_VALUE);
            }
            builder.pop();
            builder.push("eye_aggression");
            {
                enableEyeAggressionEscalation = builder.comment("While set to true, the eyes entities will progressively get more bold, and move faster, the longer they live.")
                        .define("EnableEscalation", true);
                eyeAggressionDependsOnLocalDifficulty = builder.comment("While set to true, the eyes entities will spawn with higher aggresion levels in higher local difficulties.")
                        .define("LocalDifficulty", true);
                eyeAggressionDependsOnLightLevel = builder.comment("While set to true, the eyes entities will have higher aggression values on lower light levels.")
                        .define("LightLevel", true);
            }
            builder.pop();
            builder.push("sound_volumes");
            {
                eyeIdleVolume = builder.comment("Changes the volume of the idle sounds, relative to the volume of the hostile mob category.")
                        .defineInRange("IdleNoiseVolume", 1.0, 0.0, 1.0);
                eyeDisappearVolume = builder.comment("Changes the volume of the death/disappear sounds, relative to the volume of the hostile mob category.")
                        .defineInRange("DisappearNoiseVolume", 1.0, 0.0, 1.0);
                eyeJumpscareVolume = builder.comment("Changes the volume of the jumpscare sounds, relative to the volume of the hostile mob category.")
                        .defineInRange("JumpscareVolume", 1.0, 0.0, 1.0);
            }
            builder.pop();

            builder.push("spawning");
            {
                enableNaturalSpawn = builder.comment("If false, the eyes entity will not spawn naturally during the night.")
                        .define("EnableNaturalSpawn", true);
                maxEyesSpawnDistance = builder.comment("Max block distance from a player at which the eyes will spawn.")
                        .defineInRange("MaxEyesSpawnDistance", 64, 1, Integer.MAX_VALUE);
                biomeRules = builder
                        .comment(
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
                        ).defineList("BiomeRules", Lists.newArrayList(), o -> o instanceof String);
                dimensionRules = builder
                        .comment(
                                "Specifies rules for accepting or rejecting dimensions.",
                                "The rules are scanned one by one until a rule matches, This means the first rule to match takes precedence over any other subsequent rule, so more specific rules should go first.",
                                "Rules:",
                                "  \"dimension:name\"   -- ALLOWS spawning in the given dimension.",
                                "  \"!dimension:name\"  -- DISALLOWS spawning in the given dimension.",
                                "  \"#dimension:type\"  -- ALLOWS spawning in any dimension with the given dimension type.",
                                "  \"!#dimension:type\" -- DISALLOWS spawning in any dimension with the given dimension type.",
                                "  \"!*\"               -- DISALLOWS spawning unconditionally. Place this at the end of the list to disable spawning if no other rules pass (defaults to allow otherwise).",
                                "Examples:",
                                "  To disable spawning in the end dimension: [ \"!minecraft:the_end\" ]",
                                "  To disable spawning in the nether dimension: [ \"!minecraft:nether\" ]",
                                "  To disable spawning in all secondary overworlds, but allow the vanilla overworld: [ \"minecraft:overworld\", \"!#minecraft:overworld\" ]"
                        ).defineList("DimensionRules", Lists.newArrayList(), o -> o instanceof String);
            }
            builder.pop();
            builder.comment("Default spawn settings")
                    .push("spawning_normal");
            {
                spawnCycleIntervalNormal = builder.comment("Number of ticks between spawn cycles.")
                        .defineInRange("SpawnCycleInterval", 150, 1, Integer.MAX_VALUE);
                maxEyesAroundPlayerNormal = builder.comment("Max number of eyes entities that will spawn around any one player.")
                        .defineInRange("MaxEyesAroundPlayer", 2, 1, Integer.MAX_VALUE);
                maxTotalEyesPerDimensionNormal = builder.comment("Max number of eyes entities that will spawn in each dimension.")
                        .defineInRange("MaxTotalEyesPerDimension", 15, 1, Integer.MAX_VALUE);
            }
            builder.pop();
            builder.comment("Spawn settings in the minutes around midnight")
                    .push("spawning_midnight");
            {
                spawnCycleIntervalMidnight = builder.comment("Number of ticks between spawn cycles.")
                        .defineInRange("SpawnCycleInterval", 50, 1, Integer.MAX_VALUE);
                maxEyesAroundPlayerMidnight = builder.comment("Max number of eyes entities that will spawn around any one player.")
                        .defineInRange("MaxEyesAroundPlayer", 3, 1, Integer.MAX_VALUE);
                maxTotalEyesPerDimensionMidnight = builder.comment("Max number of eyes entities that will spawn in each dimension.")
                        .defineInRange("MaxTotalEyesPerDimension", 15, 1, Integer.MAX_VALUE);
            }
            builder.pop();
            builder.comment("Spawn settings in the days leading to halloween")
                    .push("spawning_halloween");
            {
                spawnCycleIntervalHalloween = builder.comment("Number of ticks between spawn cycles.")
                        .defineInRange("SpawnCycleInterval", 50, 1, Integer.MAX_VALUE);
                maxEyesAroundPlayerHalloween = builder.comment("Max number of eyes entities that will spawn around any one player.")
                        .defineInRange("MaxEyesAroundPlayer", 5, 1, Integer.MAX_VALUE);
                maxTotalEyesPerDimensionHalloween = builder.comment("Max number of eyes entities that will spawn in each dimension.")
                        .defineInRange("MaxTotalEyesPerDimension", 25, 1, Integer.MAX_VALUE);
            }
            builder.pop();
        }
    }

    private static class ClientConfig
    {
        public final ForgeConfigSpec.BooleanValue jumpscare;

        ClientConfig(ForgeConfigSpec.Builder builder)
        {
            builder.push("general");
            jumpscare = builder.comment("Set to false to prevent jumpscares from displaying client-side.\n" +
                    "NOTE: Jumpscare effects such as poison still apply, this only prevents the visual and sound.")
                    .define("jumpscare", true);
            builder.pop();
        }
    }

    // Server
    public static boolean jumpscare;
    public static int jumpscareHurtLevel;
    public static boolean eyesCanAttackWhileLit;
    public static boolean enableEyeAggressionEscalation;
    public static boolean eyeAggressionDependsOnLocalDifficulty;
    public static boolean eyeAggressionDependsOnLightLevel;
    public static double eyeIdleVolume;
    public static double eyeDisappearVolume;
    public static double eyeJumpscareVolume;

    public static boolean enableNaturalSpawn;
    public static int maxEyesSpawnDistance;

    public static int spawnCycleIntervalNormal;
    public static int maxEyesAroundPlayerNormal;
    public static int maxTotalEyesPerDimensionNormal;
    public static int spawnCycleIntervalMidnight;
    public static int maxEyesAroundPlayerMidnight;
    public static int maxTotalEyesPerDimensionMidnight;
    public static int spawnCycleIntervalHalloween;
    public static int maxEyesAroundPlayerHalloween;
    public static int maxTotalEyesPerDimensionHalloween;

    public static double speedNoAggro;
    public static double speedFullAggro;

    public static long longSpawnCycleWarning;

    // Client
    public static boolean jumpscareClient;

    @Mod.EventBusSubscriber(modid = EyesInTheDarkness.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    private static class ModEventHandler
    {
        @SubscribeEvent
        public static void configLoading(final ModConfig.ModConfigEvent event)
        {
            ModConfig config = event.getConfig();

            if (config.getSpec() == SERVER_SPEC)
            {
                jumpscare = SERVER.jumpscare.get();
                jumpscareHurtLevel = SERVER.jumpscareHurtLevel.get();
                eyesCanAttackWhileLit = SERVER.eyesCanAttackWhileLit.get();
                enableEyeAggressionEscalation = SERVER.enableEyeAggressionEscalation.get();
                eyeAggressionDependsOnLocalDifficulty = SERVER.eyeAggressionDependsOnLocalDifficulty.get();
                eyeAggressionDependsOnLightLevel = SERVER.eyeAggressionDependsOnLightLevel.get();
                eyeIdleVolume = SERVER.eyeIdleVolume.get();
                eyeDisappearVolume = SERVER.eyeDisappearVolume.get();
                eyeJumpscareVolume = SERVER.eyeJumpscareVolume.get();

                enableNaturalSpawn = SERVER.enableNaturalSpawn.get();
                maxEyesSpawnDistance = SERVER.maxEyesSpawnDistance.get();

                spawnCycleIntervalNormal = SERVER.spawnCycleIntervalNormal.get();
                maxEyesAroundPlayerNormal = SERVER.maxEyesAroundPlayerNormal.get();
                maxTotalEyesPerDimensionNormal = SERVER.maxTotalEyesPerDimensionNormal.get();

                spawnCycleIntervalMidnight = SERVER.spawnCycleIntervalMidnight.get();
                maxEyesAroundPlayerMidnight = SERVER.maxEyesAroundPlayerMidnight.get();
                maxTotalEyesPerDimensionMidnight = SERVER.maxTotalEyesPerDimensionMidnight.get();

                spawnCycleIntervalHalloween = SERVER.spawnCycleIntervalHalloween.get();
                maxEyesAroundPlayerHalloween = SERVER.maxEyesAroundPlayerHalloween.get();
                maxTotalEyesPerDimensionHalloween = SERVER.maxTotalEyesPerDimensionHalloween.get();

                speedNoAggro = SERVER.speedNoAggro.get();
                speedFullAggro = SERVER.speedFullAggro.get();

                longSpawnCycleWarning = SERVER.longSpawnCycleWarning.get();

                List<? extends String> biomeRules = orDefault(SERVER.biomeRules.get(), Collections::emptyList);
                List<? extends String> dimensionRules = orDefault(SERVER.dimensionRules.get(), Collections::emptyList);

                BiomeRules.parseRules(biomeRules);
                DimensionRules.parseRules(dimensionRules);
            }

            if (config.getSpec() == CLIENT_SPEC)
            {
                jumpscareClient = CLIENT.jumpscare.get();
            }
        }
    }

    @Nonnull
    private static <T> T orDefault(@Nullable T value, @Nonnull Supplier<T> defaultSupplier)
    {
        if (value != null)
            return value;
        return defaultSupplier.get();
    }

    public static boolean canEyesSpawnAt(EntityType<EyesEntity> entityType, IServerWorld world, SpawnReason reason, BlockPos pos, Random random)
    {
        return MonsterEntity.checkMonsterSpawnRules(entityType, world, reason, pos, random);
    }
}
