package dev.gigaherz.eyes;

import dev.gigaherz.eyes.config.ConfigData;
import dev.gigaherz.eyes.entity.EyesEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EyesInTheDarkness.MODID)
public class EyesInTheDarkness
{
    // Needed to keep a dedicated spawn cap for the eyes.
    public static final MobCategory CLASSIFICATION = MobCategory.create("EITD_EYES", "eitd_eyes", 15, false, false, 64);

    public static final String MODID = "eyesinthedarkness";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<SoundEvent> EYES_LAUGH = SOUND_EVENTS.register("eyes_laugh", () -> SoundEvent.createVariableRangeEvent(location("mob.eyes.laugh")));
    public static final RegistryObject<SoundEvent> EYES_DISAPPEAR = SOUND_EVENTS.register("eyes_disappear", () -> SoundEvent.createVariableRangeEvent(location("mob.eyes.disappear")));
    public static final RegistryObject<SoundEvent> EYES_JUMPSCARE = SOUND_EVENTS.register("eyes_jumpscare", () -> SoundEvent.createVariableRangeEvent(location("mob.eyes.jumpscare")));

    public static final RegistryObject<EntityType<EyesEntity>> EYES = ENTITY_TYPES.register("eyes", () ->
            EntityType.Builder.of(EyesEntity::new, CLASSIFICATION)
            .setTrackingRange(80)
            .setUpdateInterval(3)
            .setCustomClientFactory((ent, world) -> EyesInTheDarkness.EYES.get().create(world))
            .setShouldReceiveVelocityUpdates(true)
            .build(MODID + ":eyes"));

    public static final RegistryObject<SpawnEggItem> EYES_EGG = ITEMS.register("eyes_spawn_egg", () ->
            new ForgeSpawnEggItem(EYES, 0x000000, 0x7F0000, new Item.Properties()));


    private static final String PROTOCOL_VERSION = "1.0";
    public static final SimpleChannel channel = NetworkRegistry.ChannelBuilder
            .named(location("main"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public EyesInTheDarkness()
    {
        final ModLoadingContext modLoadingContext = ModLoadingContext.get();
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        SOUND_EVENTS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(this::spawnPlacement);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::entityAttributes);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::addItemsToTabs);

        modLoadingContext.registerConfig(ModConfig.Type.SERVER, ConfigData.SERVER_SPEC);
        modLoadingContext.registerConfig(ModConfig.Type.CLIENT, ConfigData.CLIENT_SPEC);

        MinecraftForge.EVENT_BUS.addListener(this::entityInit);
    }

    private void addItemsToTabs(CreativeModeTabEvent.BuildContents event)
    {
        if (event.getTab() == CreativeModeTabs.SPAWN_EGGS)
        {
            event.accept(EYES_EGG);
        }
    }

    public void spawnPlacement(SpawnPlacementRegisterEvent event)
    {
        event.register(
                EYES.get(),
                SpawnPlacements.Type.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ConfigData::canEyesSpawnAt,
                SpawnPlacementRegisterEvent.Operation.AND);
    }

    public void entityAttributes(EntityAttributeCreationEvent event)
    {
        event.put(EYES.get(), EyesEntity.prepareAttributes().build());
    }

    public void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        EyesSpawningManager.init(event);
    }

    public void commonSetup(FMLCommonSetupEvent event)
    {
        int messageNumber = 0;
        channel.messageBuilder(InitiateJumpscarePacket.class, messageNumber++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(InitiateJumpscarePacket::encode).decoder(InitiateJumpscarePacket::new).consumerNetworkThread(InitiateJumpscarePacket::handle)
                .add();
        LOGGER.debug("Final message number: " + messageNumber);
    }

    public void entityInit(MobSpawnEvent.FinalizeSpawn event)
    {
        Entity e = event.getEntity();
        if (e instanceof Wolf wolf)
        {
            wolf.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(wolf, EyesEntity.class, false));
        }
        if (e instanceof Ocelot cat)
        {
            cat.goalSelector.addGoal(3, new AvoidEntityGoal<>(cat, EyesEntity.class, 6.0F, 1.0D, 1.2D));
        }
        if (e instanceof Cat cat)
        {
            cat.goalSelector.addGoal(3, new AvoidEntityGoal<>(cat, EyesEntity.class, 6.0F, 1.0D, 1.2D));
        }
    }

    public static ResourceLocation location(String location)
    {
        return new ResourceLocation(MODID, location);
    }
}
