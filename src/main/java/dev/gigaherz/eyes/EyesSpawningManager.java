package dev.gigaherz.eyes;

import com.google.common.base.Stopwatch;
import dev.gigaherz.eyes.config.BiomeRules;
import dev.gigaherz.eyes.config.ConfigData;
import dev.gigaherz.eyes.config.DimensionRules;
import dev.gigaherz.eyes.entity.EyesEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EyesSpawningManager
{
    private static final Logger LOGGER = LogManager.getLogger();

    public static Capability<EyesSpawningManager> INSTANCE = CapabilityManager.get(new CapabilityToken<>(){});

    private static final ResourceLocation CAP_KEY = new ResourceLocation("eyesinthedarkness:eyes_spawning_manager");

    public static void init(RegisterCapabilitiesEvent event)
    {
        event.register(EyesSpawningManager.class);

        MinecraftForge.EVENT_BUS.addGenericListener(Level.class, EyesSpawningManager::onCapabilityAttach);
        MinecraftForge.EVENT_BUS.addListener(EyesSpawningManager::onWorldTick);
    }

    private static void onCapabilityAttach(AttachCapabilitiesEvent<Level> event)
    {
        Level eventWorld = event.getObject();
        if (eventWorld instanceof ServerLevel)
        {
            event.addCapability(CAP_KEY, new ICapabilityProvider()
            {
                final ServerLevel world = (ServerLevel) eventWorld;
                final LazyOptional<EyesSpawningManager> supplier = LazyOptional.of(() -> new EyesSpawningManager(world));

                @Nonnull
                @Override
                public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
                {
                    if (cap == INSTANCE)
                        return supplier.cast();
                    return LazyOptional.empty();
                }
            });
        }
    }

    private static void onWorldTick(TickEvent.WorldTickEvent event)
    {
        if (event.world.isClientSide) return;
        if (event.phase != TickEvent.Phase.START) return;
        event.world.getCapability(INSTANCE).ifPresent(EyesSpawningManager::tick);
    }

    private final Stopwatch watch = Stopwatch.createUnstarted();
    private final ServerLevel parent;
    private final ServerChunkCache chunkSource;
    private int cooldown;
    private int ticks;

    private EyesSpawningManager(ServerLevel world)
    {
        this.parent = world;
        this.chunkSource = parent.getChunkSource();
        this.cooldown = 0;
    }

    public static int getDaysUntilNextHalloween()
    {
        Calendar now = Calendar.getInstance();
        Calendar nextHalloween = new Calendar.Builder()
                .setDate(now.get(Calendar.YEAR), 9, 31)
                .setTimeOfDay(23, 59, 59, 999).build();
        if (now.after(nextHalloween))
        {
            nextHalloween.add(Calendar.YEAR, 1);
        }
        return (int) Math.min(ChronoUnit.DAYS.between(now.toInstant(), nextHalloween.toInstant()), 30);
    }

    public static int getMinutesToMidnight()
    {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        if (hour > 12) hour -= 24;
        return Math.abs(hour * 24 + minute);
    }

    private static final Field f_spawnEnemies = ObfuscationReflectionHelper.findField(ServerChunkCache.class, "f_8335_");
    private boolean isEnemySpawnEnabled()
    {
        try
        {
            return (boolean) f_spawnEnemies.get(chunkSource);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException("Error accessing field", e);
        }
    }

    private void tick()
    {
        if (--cooldown > 0)
            return;

        cooldown = 150;

        if (!ConfigData.enableNaturalSpawn || !parent.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))
            return;

        if (!isEnemySpawnEnabled())
            return;

        if (!DimensionRules.isDimensionAllowed(parent))
            return;

        try
        {
            watch.start();

            ticks++;

            int daysUntilNextHalloween = getDaysUntilNextHalloween();
            int minutesToMidnight = getMinutesToMidnight();

            cooldown = calculateSpawnCycleInterval(daysUntilNextHalloween, minutesToMidnight);

            int maxTotalEyesPerDimension = calculateMaxTotalEyesPerDimension(daysUntilNextHalloween, minutesToMidnight);
            int maxEyesAroundPlayer = calculateMaxEyesAroundPlayer(daysUntilNextHalloween, minutesToMidnight);

            int count = parent.getEntities(EyesInTheDarkness.EYES.get(), e -> ((EyesEntity) e).countsTowardSpawnCap()).size();
            if (count >= maxTotalEyesPerDimension)
            {
                return;
            }

            float d = ConfigData.maxEyesSpawnDistance * 1.5f;
            float dSqr = d * d;
            AABB size = AABB.ofSize(Vec3.ZERO, d, d, d);

            List<ServerPlayer> players = parent.players();
            int wrap = 20; //Math.min(players.size(), 20);
            for (ServerPlayer player : players)
            {
                if (((player.getId() + ticks) % wrap) == 0 && !player.isSpectator())
                {
                    List<EyesEntity> entities = parent.getEntities(EyesInTheDarkness.EYES.get(), size.move(player.position()), e -> !e.countsTowardSpawnCap() && e.distanceToSqr(player) <= dSqr);
                    if (entities.size() < maxEyesAroundPlayer)
                    {
                        spawnOneAround(player.position(), player, ConfigData.maxEyesSpawnDistance);
                    }
                }
            }

        }
        finally
        {
            watch.stop();

            long us = watch.elapsed(TimeUnit.MICROSECONDS);
            if (us > ConfigData.longSpawnCycleWarning) // default = 50ms
            {
                LOGGER.warn("WARNING: Unexpectedly long spawn cycle. It ran for {}ms!", us/1000.0);
            }

            watch.reset();
        }
    }

    private int calculateSpawnCycleInterval(int daysUntilNextHalloween, int minutesToMidnight)
    {
        return Math.max(1, calculateTimeBasedValueMin(ConfigData.spawnCycleIntervalNormal, ConfigData.spawnCycleIntervalMidnight, ConfigData.spawnCycleIntervalHalloween, daysUntilNextHalloween, minutesToMidnight));
    }

    private int calculateMaxTotalEyesPerDimension(int daysUntilNextHalloween, int minutesToMidnight)
    {
        return Math.max(1, calculateTimeBasedValueMax(ConfigData.maxTotalEyesPerDimensionNormal, ConfigData.maxTotalEyesPerDimensionMidnight, ConfigData.maxTotalEyesPerDimensionHalloween, daysUntilNextHalloween, minutesToMidnight));
    }

    private int calculateMaxEyesAroundPlayer(int daysUntilNextHalloween, int minutesToMidnight)
    {
        return Math.max(1, calculateTimeBasedValueMax(ConfigData.maxEyesAroundPlayerNormal, ConfigData.maxEyesAroundPlayerMidnight, ConfigData.maxEyesAroundPlayerHalloween, daysUntilNextHalloween, minutesToMidnight));
    }

    private int calculateTimeBasedValueMax(int normal, int midnight, int halloween, int daysUntilNextHalloween, int minutesToMidnight)
    {
        int valueByTime = normal + ((midnight - normal) * Math.max(0, 240 - minutesToMidnight)) / 240;
        int valueByDate = normal + ((halloween - normal) * Math.max(0, 30 - daysUntilNextHalloween)) / 30;
        return Math.max(valueByDate, valueByTime);
    }

    private int calculateTimeBasedValueMin(int normal, int midnight, int halloween, int daysUntilNextHalloween, int minutesToMidnight)
    {
        int valueByTime = normal + ((midnight - normal) * Math.max(0, 240 - minutesToMidnight)) / 240;
        int valueByDate = normal + ((halloween - normal) * Math.max(0, 30 - daysUntilNextHalloween)) / 30;
        return Math.min(valueByDate, valueByTime);
    }

    private void spawnOneAround(Vec3 positionVec, ServerPlayer player, float d)
    {
        float dSqr = d*d;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for(int i=0;i<100;i++)
        {
            double sX = (1-2*parent.random.nextFloat()) * d + positionVec.x();
            double sZ = (1-2*parent.random.nextFloat()) * d + positionVec.z();

            pos.set(sX, 0, sZ);

            int maxY = parent.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());

            double sY = Mth.clamp(parent.random.nextFloat() * d + positionVec.y(), 0, maxY);
            pos.setY((int) sY);

            double pX = pos.getX() + 0.5D;
            double pY = pos.getY();
            double pZ = pos.getZ() + 0.5D;

            double distanceSq = player.distanceToSqr(pX, pY, pZ);
            if (distanceSq < dSqr && isValidSpawnSpot(parent, EyesInTheDarkness.EYES.get(), pos, distanceSq))
            {
                EyesEntity entity = EyesInTheDarkness.EYES.get().create(parent, null, null, null, pos, MobSpawnType.NATURAL, false, false);
                if (entity == null)
                    continue;

                int canSpawn = net.minecraftforge.common.ForgeHooks.canEntitySpawn(entity, parent, pX, pY, pZ, null, MobSpawnType.NATURAL);
                if (canSpawn != -1 && (canSpawn == 1 || entity.checkSpawnRules(parent, MobSpawnType.NATURAL) && entity.checkSpawnObstruction(parent)))
                {
                    parent.addFreshEntity(entity);

                    return;
                }

                entity.discard();
            }
        }
    }

    private static boolean isValidSpawnSpot(ServerLevel serverWorld, EntityType<?> entityType, BlockPos pos, double sqrDistanceToClosestPlayer)
    {
        int instantDespawnDistance = entityType.getCategory().getDespawnDistance();
        if (!entityType.canSpawnFarFromPlayer() && sqrDistanceToClosestPlayer > (instantDespawnDistance * instantDespawnDistance))
        {
            return false;
        }

        if (!BiomeRules.isBiomeAllowed(serverWorld, serverWorld.getBiome(pos)))
        {
            return false;
        }

        return SpawnPlacements.checkSpawnRules(entityType, serverWorld, MobSpawnType.NATURAL, pos, serverWorld.random)
                && serverWorld.noCollision(entityType.getAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
    }
}
