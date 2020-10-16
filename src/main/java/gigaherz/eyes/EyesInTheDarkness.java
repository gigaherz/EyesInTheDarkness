package gigaherz.eyes;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import gigaherz.eyes.entity.EyesEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.NonNullLazy;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.registries.ObjectHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;

@Mod(EyesInTheDarkness.MODID)
public class EyesInTheDarkness
{
    // Needed to keep a dedicated spawn cap for the eyes.
    public static final EntityClassification CLASSIFICATION = EntityClassification.create("EITD_EYES", "eitd_eyes", 15, false, false, 64);

    public static final String MODID = "eyesinthedarkness";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @ObjectHolder(MODID + ":eyes_laugh")
    public static SoundEvent eyes_laugh;

    @ObjectHolder(MODID + ":eyes_disappear")
    public static SoundEvent eyes_disappear;

    @ObjectHolder(MODID + ":eyes_jumpscare")
    public static SoundEvent eyes_jumpscare;

    private static final String CHANNEL="main";
    private static final String PROTOCOL_VERSION = "1.0";
    public static SimpleChannel channel = NetworkRegistry.ChannelBuilder
            .named(location(CHANNEL))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    /*The EntityType is static-initialized because of the spawnEgg, which needs a nonnull EntityType by the time it is registered.*/
    /*If Forge moves/patches spawnEggs to use a delegate, remove this hack in favor of the ObjectHolder.*/
    private static final NonNullLazy<EntityType<EyesEntity>> eyesInit = NonNullLazy.of(() -> EntityType.Builder.create(EyesEntity::new, CLASSIFICATION)
            .setTrackingRange(80)
            .setUpdateInterval(3)
            .setCustomClientFactory((ent, world) -> EyesEntity.TYPE.create(world))
            .setShouldReceiveVelocityUpdates(true)
            .build(MODID + ":eyes"));

    public EyesInTheDarkness()
    {
        final ModLoadingContext modLoadingContext = ModLoadingContext.get();
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addGenericListener(SoundEvent.class, this::registerSounds);
        modEventBus.addGenericListener(EntityType.class, this::registerEntities);
        modEventBus.addGenericListener(Item.class, this::registerItems);
        modEventBus.addListener(this::commonSetup);

        modLoadingContext.registerConfig(ModConfig.Type.SERVER, ConfigData.SERVER_SPEC);
        modLoadingContext.registerConfig(ModConfig.Type.CLIENT, ConfigData.CLIENT_SPEC);

        MinecraftForge.EVENT_BUS.addListener(this::entityInit);
    }

    public void registerSounds(RegistryEvent.Register<SoundEvent> event)
    {
        event.getRegistry().registerAll(
                new SoundEvent(location("mob.eyes.laugh")).setRegistryName(location("eyes_laugh")),
                new SoundEvent(location("mob.eyes.disappear")).setRegistryName(location("eyes_disappear")),
                new SoundEvent(location("mob.eyes.jumpscare")).setRegistryName(location("eyes_jumpscare"))
        );
    }

    public void registerEntities(RegistryEvent.Register<EntityType<?>> event)
    {
        event.getRegistry().registerAll(
                eyesInit.get().setRegistryName(MODID + ":eyes")
        );

        EntitySpawnPlacementRegistry.register(
                eyesInit.get(),
                EntitySpawnPlacementRegistry.PlacementType.NO_RESTRICTIONS,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                ConfigData::canEyesSpawnAt);
    }

    public void registerItems(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().registerAll(
                new SpawnEggItem(eyesInit.get(), 0x000000, 0x7F0000, new Item.Properties().group(ItemGroup.MISC)).setRegistryName(location("eyes_spawn_egg"))
        );
    }

    public void commonSetup(FMLCommonSetupEvent event)
    {
        int messageNumber = 0;
        channel.registerMessage(messageNumber++, InitiateJumpscarePacket.class, InitiateJumpscarePacket::encode, InitiateJumpscarePacket::new, InitiateJumpscarePacket::handle);
        LOGGER.debug("Final message number: " + messageNumber);

        GlobalEntityTypeAttributes.put(EyesEntity.TYPE, EyesEntity.prepareAttributes().create());
    }

    @SuppressWarnings("unchecked")
    public void entityInit(EntityJoinWorldEvent event)
    {
        Entity e = event.getEntity();
        if (e instanceof WolfEntity)
        {
            WolfEntity wolf = (WolfEntity)e;
            wolf.targetSelector.addGoal(5,
                    new NearestAttackableTargetGoal<>(wolf, EyesEntity.class, false));
        }
        if (e instanceof OcelotEntity)
        {
            OcelotEntity cat = (OcelotEntity)e;
            cat.goalSelector.addGoal(3, new AvoidEntityGoal<>(cat, EyesEntity.class, 6.0F, 1.0D, 1.2D));
        }
    }

    public static ResourceLocation location(String location)
    {
        return new ResourceLocation(MODID, location);
    }
}
