package gigaherz.eyes;

import com.google.common.base.Stopwatch;
import gigaherz.eyes.config.BiomeRules;
import gigaherz.eyes.config.ConfigData;
import gigaherz.eyes.config.DimensionRules;
import gigaherz.eyes.entity.EyesEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.INBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EyesSpawningManager
{
    private static final Logger LOGGER = LogManager.getLogger();

    @CapabilityInject(EyesSpawningManager.class)
    public static Capability<EyesSpawningManager> INSTANCE;

    private static final ResourceLocation CAP_KEY = new ResourceLocation("eyesinthedarkness:eyes_spawning_manager");

    public static void init()
    {
        CapabilityManager.INSTANCE.register(EyesSpawningManager.class, new Capability.IStorage<EyesSpawningManager>()
        {
            @Nullable
            @Override
            public INBT writeNBT(Capability<EyesSpawningManager> capability, EyesSpawningManager instance, Direction side)
            {
                throw new IllegalStateException("Serialization not supported on this object.");
            }

            @Override
            public void readNBT(Capability<EyesSpawningManager> capability, EyesSpawningManager instance, Direction side, INBT nbt)
            {
                throw new IllegalStateException("Serialization not supported on this object.");
            }
        }, () -> {
            throw new IllegalStateException("Default instance factory not supported on this object.");
        });

        MinecraftForge.EVENT_BUS.addGenericListener(World.class, EyesSpawningManager::onCapabilityAttach);
        MinecraftForge.EVENT_BUS.addListener(EyesSpawningManager::onWorldTick);
    }

    private static void onCapabilityAttach(AttachCapabilitiesEvent<World> event)
    {
        World eventWorld = event.getObject();
        if (eventWorld instanceof ServerWorld)
        {
            event.addCapability(CAP_KEY, new ICapabilityProvider()
            {
                final ServerWorld world = (ServerWorld) eventWorld;
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
        if (event.world.isRemote) return;
        event.world.getCapability(INSTANCE).ifPresent(EyesSpawningManager::tick);
    }

    private final Stopwatch watch = Stopwatch.createUnstarted();
    private final ServerWorld parent;
    private int cooldown;
    private int ticks;

    private EyesSpawningManager(ServerWorld world)
    {
        this.parent = world;
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

    private void tick()
    {
        if (!ConfigData.enableNaturalSpawn)
            return;

        if (--cooldown > 0)
            return;

        cooldown = 150;

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

            int count = parent.getEntities(EyesEntity.TYPE, e -> ((EyesEntity) e).countsTowardSpawnCap()).size();
            if (count >= maxTotalEyesPerDimension)
            {
                return;
            }

            float d = ConfigData.maxEyesSpawnDistance;
            AxisAlignedBB size = AxisAlignedBB.withSizeAtOrigin(d, d, d);

            for (ServerPlayerEntity player : parent.getPlayers())
            {
                if (((player.getEntityId() + ticks) % 20) == 0 && !player.isSpectator())
                {
                    List<EyesEntity> entities = parent.getEntitiesWithinAABB(EyesEntity.TYPE, size.offset(player.getPositionVec()), e -> !e.countsTowardSpawnCap() && e.getDistance(player) <= ConfigData.maxEyesSpawnDistance);
                    if (entities.size() < maxEyesAroundPlayer)
                    {
                        spawnOneAround(player.getPositionVec(), player, d);
                    }
                }
            }

        }
        finally
        {
            watch.stop();

            long us = watch.elapsed(TimeUnit.MICROSECONDS);
            if (us > 10000) // 10ms
            {
                LOGGER.warn("WARNING: Unexpectedly long spawn cycle. It ran for {}ms!", us/1000.0);
            }

            watch.reset();
        }
    }

    private int calculateSpawnCycleInterval(int daysUntilNextHalloween, int minutesToMidnight)
    {
        return Math.max(1, calculateTimeBasedValue(ConfigData.spawnCycleIntervalNormal, ConfigData.spawnCycleIntervalMidnight, ConfigData.spawnCycleIntervalHalloween, daysUntilNextHalloween, minutesToMidnight));
    }

    private int calculateMaxTotalEyesPerDimension(int daysUntilNextHalloween, int minutesToMidnight)
    {
        return Math.max(1, calculateTimeBasedValue(ConfigData.maxTotalEyesPerDimensionNormal, ConfigData.maxTotalEyesPerDimensionMidnight, ConfigData.maxTotalEyesPerDimensionHalloween, daysUntilNextHalloween, minutesToMidnight));
    }

    private int calculateMaxEyesAroundPlayer(int daysUntilNextHalloween, int minutesToMidnight)
    {
        return Math.max(1, calculateTimeBasedValue(ConfigData.maxEyesAroundPlayerNormal, ConfigData.maxEyesAroundPlayerMidnight, ConfigData.maxEyesAroundPlayerHalloween, daysUntilNextHalloween, minutesToMidnight));
    }

    private int calculateTimeBasedValue(int normal, int midnight, int halloween, int daysUntilNextHalloween, int minutesToMidnight)
    {
        int valueByDate = ((halloween - normal) * Math.max(0, 30 - daysUntilNextHalloween)) / 30;
        int valueByTime = ((midnight - normal) * Math.max(0, 240 - minutesToMidnight)) / 240;
        return normal + valueByDate + valueByTime;
    }

    private void spawnOneAround(Vector3d positionVec, ServerPlayerEntity player, float d)
    {
        float dSqr = d*d;

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for(int i=0;i<100;i++)
        {
            double sX = parent.rand.nextFloat() * d + positionVec.getX();
            double sZ = parent.rand.nextFloat() * d + positionVec.getZ();

            pos.setPos(sX, 0, sZ);

            int maxY = parent.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getY());

            double sY = MathHelper.clamp(parent.rand.nextFloat() * d + positionVec.getY(), 0, maxY);
            pos.setY((int) sY);

            double pX = pos.getX() + 0.5D;
            double pY = pos.getY();
            double pZ = pos.getZ() + 0.5D;

            double distanceSq = player.getDistanceSq(pX, pY, pZ);
            if (distanceSq < dSqr && isValidSpawnSpot(parent, EyesEntity.TYPE, pos, distanceSq))
            {
                EyesEntity entity = EyesEntity.TYPE.create(parent, null, null, null, pos, SpawnReason.NATURAL, false, false);
                if (entity == null)
                    continue;

                int canSpawn = net.minecraftforge.common.ForgeHooks.canEntitySpawn(entity, parent, pX, pY, pZ, null, SpawnReason.NATURAL);
                if (canSpawn != -1 && (canSpawn == 1 || entity.canSpawn(parent, SpawnReason.NATURAL) && entity.isNotColliding(parent)))
                {
                    parent.addEntity(entity);

                    entity.addPotionEffect(new EffectInstance(Effects.GLOWING, 2000));


                    LOGGER.info("Spawn succeeded after {} tries.", i);

                    return;
                }

                entity.remove();
            }
        }

        LOGGER.info("Spawn failed.");
    }

    private static boolean isValidSpawnSpot(ServerWorld serverWorld, EntityType<?> entityType, BlockPos pos, double sqrDistanceToClosestPlayer)
    {
        int instantDespawnDistance = entityType.getClassification().getInstantDespawnDistance();
        if (!entityType.func_225437_d() && sqrDistanceToClosestPlayer > (instantDespawnDistance * instantDespawnDistance))
        {
            return false;
        }

        if (!BiomeRules.isBiomeAllowed(serverWorld.getBiome(pos)))
        {
            return false;
        }

        return EntitySpawnPlacementRegistry.canSpawnEntity(entityType, serverWorld, SpawnReason.NATURAL, pos, serverWorld.rand)
                && serverWorld.hasNoCollisions(entityType.getBoundingBoxWithSizeApplied(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
    }
}
