package gigaherz.eyes;

import gigaherz.eyes.config.ConfigData;
import gigaherz.eyes.entity.EyesEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.NonNullLazy;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fmllegacy.network.NetworkDirection;
import net.minecraftforge.fmllegacy.network.NetworkRegistry;
import net.minecraftforge.fmllegacy.network.simple.SimpleChannel;
import net.minecraftforge.registries.ObjectHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EyesInTheDarkness.MODID)
public class EyesInTheDarkness
{
    // Needed to keep a dedicated spawn cap for the eyes.
    public static final MobCategory CLASSIFICATION = MobCategory.create("EITD_EYES", "eitd_eyes", 15, false, false, 64);

    public static final String MODID = "eyesinthedarkness";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @ObjectHolder(MODID + ":eyes_laugh")
    public static SoundEvent eyes_laugh;

    @ObjectHolder(MODID + ":eyes_disappear")
    public static SoundEvent eyes_disappear;

    @ObjectHolder(MODID + ":eyes_jumpscare")
    public static SoundEvent eyes_jumpscare;

    private static final String CHANNEL = "main";
    private static final String PROTOCOL_VERSION = "1.0";
    public static SimpleChannel channel = NetworkRegistry.ChannelBuilder
            .named(location(CHANNEL))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    /*The EntityType is static-initialized because of the spawnEgg, which needs a nonnull EntityType by the time it is registered.*/
    /*If Forge moves/patches spawnEggs to use a delegate, remove this hack in favor of the ObjectHolder.*/
    private static final NonNullLazy<EntityType<EyesEntity>> eyesInit = NonNullLazy.of(() -> EntityType.Builder.of(EyesEntity::new, CLASSIFICATION)
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
        modEventBus.addListener(this::entityAttributes);

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

        SpawnPlacements.register(
                eyesInit.get(),
                SpawnPlacements.Type.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ConfigData::canEyesSpawnAt);
    }

    public void entityAttributes(EntityAttributeCreationEvent event)
    {
        event.put(EyesEntity.TYPE, EyesEntity.prepareAttributes().build());
    }

    public void registerItems(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().registerAll(
                new SpawnEggItem(eyesInit.get(), 0x000000, 0x7F0000, new Item.Properties().tab(CreativeModeTab.TAB_MISC)).setRegistryName(location("eyes_spawn_egg"))
        );
    }

    public void commonSetup(FMLCommonSetupEvent event)
    {
        int messageNumber = 0;
        channel.messageBuilder(InitiateJumpscarePacket.class, messageNumber++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(InitiateJumpscarePacket::encode).decoder(InitiateJumpscarePacket::new).consumer(InitiateJumpscarePacket::handle)
                .add();
        LOGGER.debug("Final message number: " + messageNumber);

        EyesSpawningManager.init();
    }

    public void entityInit(EntityJoinWorldEvent event)
    {
        Entity e = event.getEntity();
        if (e instanceof Wolf)
        {
            Wolf wolf = (Wolf) e;
            wolf.targetSelector.addGoal(5,
                    new NearestAttackableTargetGoal<>(wolf, EyesEntity.class, false));
        }
        if (e instanceof Ocelot)
        {
            Ocelot cat = (Ocelot) e;
            cat.goalSelector.addGoal(3, new AvoidEntityGoal<>(cat, EyesEntity.class, 6.0F, 1.0D, 1.2D));
        }
    }

    public static ResourceLocation location(String location)
    {
        return new ResourceLocation(MODID, location);
    }
}
