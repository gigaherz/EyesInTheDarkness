package dev.gigaherz.eyes;

import dev.gigaherz.eyes.config.ConfigData;
import dev.gigaherz.eyes.entity.EyesEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(EyesInTheDarkness.MODID)
public class EyesInTheDarkness
{
    // Needed to keep a dedicated spawn cap for the eyes.
    public static final MobCategory CLASSIFICATION = Enum.valueOf(MobCategory.class, "EYESINTHEDARKNESS_EYES");

    public static final String MODID = "eyesinthedarkness";

    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> EYES_LAUGH = SOUND_EVENTS.register("eyes_laugh", () -> SoundEvent.createVariableRangeEvent(location("mob.eyes.laugh")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EYES_DISAPPEAR = SOUND_EVENTS.register("eyes_disappear", () -> SoundEvent.createVariableRangeEvent(location("mob.eyes.disappear")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EYES_JUMPSCARE = SOUND_EVENTS.register("eyes_jumpscare", () -> SoundEvent.createVariableRangeEvent(location("mob.eyes.jumpscare")));

    public static final DeferredHolder<EntityType<?>, EntityType<EyesEntity>> EYES = ENTITY_TYPES.register("eyes", name ->
            EntityType.Builder.of(EyesEntity::new, CLASSIFICATION)
            .setTrackingRange(80)
            .setUpdateInterval(3)
            .setShouldReceiveVelocityUpdates(true)
            .build(ResourceKey.create(ENTITY_TYPES.getRegistryKey(), name)));

    public static final DeferredItem<SpawnEggItem> EYES_EGG = ITEMS.registerItem("eyes_spawn_egg", props ->
            new DeferredSpawnEggItem(EYES, 0x000000, 0x7F0000, props));

    public EyesInTheDarkness(ModContainer container, IEventBus modEventBus)
    {
        SOUND_EVENTS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(this::registerPackets);
        modEventBus.addListener(this::spawnPlacement);
        modEventBus.addListener(this::entityAttributes);
        modEventBus.addListener(this::addItemsToTabs);

        container.registerConfig(ModConfig.Type.SERVER, ConfigData.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ConfigData.CLIENT_SPEC);

        NeoForge.EVENT_BUS.addListener(this::addGoalsToEntity);
    }

    private void addItemsToTabs(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS)
        {
            event.accept(EYES_EGG.get());
        }
    }

    public void spawnPlacement(RegisterSpawnPlacementsEvent event)
    {
        event.register(
                EYES.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ConfigData::canEyesSpawnAt,
                RegisterSpawnPlacementsEvent.Operation.AND);
    }

    public void entityAttributes(EntityAttributeCreationEvent event)
    {
        event.put(EYES.get(), EyesEntity.prepareAttributes().build());
    }

    private void registerPackets(RegisterPayloadHandlersEvent event)
    {
        final PayloadRegistrar registrar = event.registrar(MODID).versioned("1.0");
        registrar.playToClient(InitiateJumpscarePacket.TYPE, InitiateJumpscarePacket.STREAM_CODEC, InitiateJumpscarePacket::handle);
    }

    public void addGoalsToEntity(FinalizeSpawnEvent event)
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
        return ResourceLocation.fromNamespaceAndPath(MODID, location);
    }
}
