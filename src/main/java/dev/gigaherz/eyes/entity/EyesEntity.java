package dev.gigaherz.eyes.entity;

import dev.gigaherz.eyes.EyesInTheDarkness;
import dev.gigaherz.eyes.InitiateJumpscarePacket;
import dev.gigaherz.eyes.config.ConfigData;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.function.DoubleSupplier;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.neoforged.neoforge.network.PacketDistributor;

public class EyesEntity extends Monster
{
    private static final EntityDataAccessor<Boolean> IS_DORMANT = SynchedEntityData.defineId(EyesEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> AGGRO = SynchedEntityData.defineId(EyesEntity.class, EntityDataSerializers.FLOAT);

    private static final float AGGRO_ESCALATION_PER_TICK = 1f / (20 * 60 * 5); // 300 seconds to reach max (5 minutes)

    public final static int BLINK_DURATION = 5;

    public boolean blinkingState;
    public int blinkProgress;

    public EyesEntity(EntityType<? extends EyesEntity> type, Level worldIn)
    {
        super(type, worldIn);
    }

    public static AttributeSupplier.Builder prepareAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(AGGRO, 0.1f);
        this.entityData.define(IS_DORMANT, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.1D));
        this.goalSelector.addGoal(8, new CreepTowardPlayer(this, this::getSpeedFromAggro));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public float getAggroLevel()
    {
        return this.getEntityData().get(AGGRO);
    }

    public void setAggroLevel(float aggro)
    {
        this.getEntityData().set(AGGRO, Mth.clamp(aggro, 0, 1));
    }

    public boolean getIsDormant()
    {
        return this.getEntityData().get(IS_DORMANT);
    }

    public void setIsDormant(boolean value)
    {
        this.getEntityData().set(IS_DORMANT, value);
    }

    private double getSpeedFromAggro()
    {
        if (getIsDormant())
            return 0;
        return Mth.clampedLerp(getAggroLevel(), ConfigData.speedNoAggro, ConfigData.speedFullAggro);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor worldIn, DifficultyInstance difficultyIn, MobSpawnType reason, @Nullable SpawnGroupData spawnDataIn, @Nullable CompoundTag dataTag)
    {
        if (ConfigData.eyeAggressionDependsOnLocalDifficulty)
        {
            float difficulty = difficultyIn.getSpecialMultiplier();
            setAggroLevel(level().random.nextFloat() * difficulty);
        }
        return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound)
    {
        super.readAdditionalSaveData(compound);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound)
    {
        super.addAdditionalSaveData(compound);
    }

    @Override
    public boolean doHurtTarget(Entity entityIn)
    {
        boolean jumpScared = ConfigData.jumpscare && entityIn instanceof ServerPlayer;
        if (jumpScared)
        {
            jumpscare((ServerPlayer) entityIn);
        }

        if (ConfigData.jumpscareHurtLevel > 0 && entityIn instanceof LivingEntity)
        {
            LivingEntity living = (LivingEntity) entityIn;
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 5 * 20, ConfigData.jumpscareHurtLevel - 1));
        }

        // Don't play the disappear laugh if we initiated a jumpscare.
        disappear(!jumpScared);
        return true;
    }

    public void jumpscare(ServerPlayer player)
    {
        PacketDistributor.PLAYER.with(player).send(new InitiateJumpscarePacket(this.getX(), this.getY(), this.getZ()));
    }

    @Override
    public void aiStep()
    {
        super.aiStep();

        if (level().isClientSide)
        {
            if (getIsDormant())
                return;

            if (!blinkingState)
            {
                if (level().random.nextFloat() < .02f)
                {
                    blinkingState = true;
                    blinkProgress = 0;
                }
            }
            else
            {
                blinkProgress++;
                if (blinkProgress >= BLINK_DURATION)
                {
                    blinkingState = false;
                }
            }
            return;
        }

        setIsDormant(isIlluminated(ConfigData.eyesCanAttackWhileLit));

        if (getIsDormant())
            return;

        float maxWatchDistance = 16;
        Vec3 eyes = getEyePosition(1);
        List<Player> entities = level().getEntitiesOfClass(Player.class,
                new AABB(eyes.x - maxWatchDistance, eyes.y - maxWatchDistance, eyes.z - maxWatchDistance,
                        eyes.x + maxWatchDistance, eyes.y + maxWatchDistance, eyes.z + maxWatchDistance), (player) -> {

                    if (player.isSpectator() || !player.isAlive())
                        return false;

                    if (player.getEyePosition(1).distanceTo(eyes) > maxWatchDistance)
                        return false;

                    Vec3 vec3d = player.getViewVector(1.0F).normalize();
                    Vec3 vec3d1 = new Vec3(this.getX() - player.getX(), this.getBoundingBox().minY + (double) this.getEyeHeight() - (player.getY() + (double) player.getEyeHeight()), this.getZ() - player.getZ());
                    double d0 = vec3d1.length();
                    vec3d1 = vec3d1.normalize();
                    double d1 = vec3d.dot(vec3d1);
                    return (d1 > 1.0D - 0.025D / d0) && player.hasLineOfSight(this);
                });

        if (entities.size() > 0)
        {
            disappear(true);
            return;
        }

        if (ConfigData.enableEyeAggressionEscalation && !getIsDormant())
        {
            setAggroLevel(getAggroLevel() + AGGRO_ESCALATION_PER_TICK);
        }
    }

    @Override
    public boolean isInvertedHealAndHarm()
    {
        return true;
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public void push(Entity entityIn)
    {
        //super.applyEntityCollision(entityIn);
    }

    @Override
    protected void doPush(Entity entityIn)
    {
        if (getIsDormant())
            return;

        if (entityIn instanceof Player)
        {
            disappear(true);
        }
        //super.collideWithEntity(entityIn);
    }

    private void disappear(boolean playDeathSound)
    {
        actuallyHurt(damageSources().generic(), 1);
        if (playDeathSound)
        {
            this.playSound(getDeathSound(), this.getDisappearVolume(), this.getVoicePitch());
        }
    }

    @Override
    protected float getSoundVolume()
    {
        return super.getSoundVolume() * (float)ConfigData.eyeIdleVolume;
    }

    protected float getDisappearVolume()
    {
        return super.getSoundVolume() * (float)ConfigData.eyeDisappearVolume;
    }

    @Override
    protected void pushEntities()
    {
        super.pushEntities();
    }

    @Override
    public PushReaction getPistonPushReaction()
    {
        return PushReaction.IGNORE;
    }

    @Override
    protected void tickDeath()
    {
        deathTime = 19;
        super.tickDeath();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound()
    {
        if (tickCount == 0 || getIsDormant())
            return null;
        return EyesInTheDarkness.EYES_LAUGH.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return EyesInTheDarkness.EYES_DISAPPEAR.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn)
    {
        return EyesInTheDarkness.EYES_DISAPPEAR.get();
    }

    /**
     * Get the position in the world. <b>{@code null} is not allowed!</b> If you are not an entity in the world, return
     * the coordinates 0, 0, 0
     */
    public BlockPos getBlockPosEyes()
    {
        return BlockPos.containing(this.getX(), this.getY() + getEyeHeight(), this.getZ());
    }

    public float getSunBrightness()
    {
        float angleRadians = level().getSunAngle(1);
        float f1 = 1.0F - (Mth.cos(angleRadians) * 2.0F + 0.2F);
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        f1 = 1.0F - f1;
        f1 = (float) ((double) f1 * (1.0D - (double) (level().getRainLevel(1) * 5.0F) / 16.0D));
        f1 = (float) ((double) f1 * (1.0D - (double) (level().getThunderLevel(1) * 5.0F) / 16.0D));
        return f1 * 0.8F + 0.2F;
    }

    private float getLightLevel(boolean excludeDaylight)
    {
        BlockPos position = getBlockPosEyes();
        float blockLight = 0;
        if (excludeDaylight)
        {
            if (level().dimensionType().hasSkyLight())
            {
                float skyLight = level().getBrightness(LightLayer.SKY, position)
                        - (1 - getSunBrightness()) * 11;
                float skyLight1 = level().getBrightness(LightLayer.SKY, position)
                        - level().getSkyDarken();

                if (skyLight != skyLight1)
                {
                    skyLight = skyLight;
                }

                blockLight = Math.max(blockLight, skyLight);
            }
        }
        else
        {
            blockLight = level().getMaxLocalRawBrightness(position);
        }
        return blockLight;
    }

    private boolean isIlluminated(boolean excludeDaylight)
    {
        float blockLight = getLightLevel(excludeDaylight);
        return blockLight >= 8;
    }

    public boolean countsTowardSpawnCap()
    {
        return requiresCustomPersistence() || isPersistenceRequired();
    }

    @Override
    public void onRemovedFromWorld()
    {
        super.onRemovedFromWorld();
    }

    private static class CreepTowardPlayer extends Goal
    {
        protected final PathfinderMob attacker;
        private final EyesEntity eyes;
        private final DoubleSupplier speedGetter;
        protected int attackTick;
        private Path path;
        private int delayCounter;
        private double targetX;
        private double targetY;
        private double targetZ;

        public CreepTowardPlayer(EyesEntity creature, DoubleSupplier speedGetter)
        {
            this.attacker = creature;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            this.speedGetter = speedGetter;
            eyes = creature;
        }

        /**
         * Returns whether the EntityAIBase should begin execution.
         */
        @Override
        public boolean canUse()
        {
            if (eyes.getIsDormant())
                return false;

            LivingEntity targetPlayer = this.attacker.getTarget();
            if (targetPlayer == null || !targetPlayer.isAlive())
            {
                return false;
            }

            if (isPlayerLookingInMyGeneralDirection())
                return false;

            this.path = this.attacker.getNavigation().createPath(targetPlayer, 0);
            return this.path != null || isWithinRange(targetPlayer);
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void start()
        {
            this.attacker.getNavigation().moveTo(this.path, this.speedGetter.getAsDouble());
            this.attacker.setAggressive(true);
            this.delayCounter = 0;
        }

        @Override
        public boolean canContinueToUse()
        {
            if (eyes.getIsDormant())
                return false;

            if (isPlayerLookingInMyGeneralDirection())
                return false;

            LivingEntity livingentity = this.attacker.getTarget();

            if (livingentity == null || !livingentity.isAlive())
                return false;

            return !this.attacker.getNavigation().isDone();
        }

        private boolean isWithinRange(LivingEntity targetPlayer)
        {
            return this.getAttackReachSqr(targetPlayer) >= this.attacker.distanceToSqr(targetPlayer.getX(), targetPlayer.getBoundingBox().minY, targetPlayer.getZ());
        }

        private boolean isPlayerLookingInMyGeneralDirection()
        {
            if (eyes.getIsDormant())
                return false;

            Vec3 selfPos = eyes.position();
            LivingEntity target = eyes.getTarget();
            if (target == null)
                return false;
            Vec3 playerPos = target.position();
            Vec3 lookVec = target.getLookAngle();
            Vec3 playerLook = new Vec3(lookVec.x, lookVec.y, lookVec.z);
            playerLook.normalize();

            playerPos.subtract(selfPos);
            playerPos.normalize();

            return playerLook.dot(playerPos) < 0;
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void stop()
        {
            LivingEntity livingentity = this.attacker.getTarget();
            if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(livingentity))
            {
                this.attacker.setTarget(null);
            }

            this.attacker.setAggressive(false);
            this.attacker.getNavigation().stop();
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick()
        {
            LivingEntity targetPlayer = this.attacker.getTarget();
            this.attacker.getLookControl().setLookAt(targetPlayer, 30.0F, 30.0F);
            double distanceSquared = this.attacker.distanceToSqr(targetPlayer.getX(), targetPlayer.getBoundingBox().minY, targetPlayer.getZ());
            --this.delayCounter;
            if (this.attacker.getSensing().hasLineOfSight(targetPlayer)
                    && this.delayCounter <= 0
                    && (this.targetX == 0.0D && this.targetY == 0.0D && this.targetZ == 0.0D
                    || targetPlayer.distanceToSqr(this.targetX, this.targetY, this.targetZ) >= 1.0D
                    || this.attacker.getRandom().nextFloat() < 0.05F))
            {
                this.targetX = targetPlayer.getX();
                this.targetY = targetPlayer.getBoundingBox().minY;
                this.targetZ = targetPlayer.getZ();
                this.delayCounter = 4 + this.attacker.getRandom().nextInt(7);

                if (distanceSquared > 1024.0D)
                {
                    this.delayCounter += 10;
                }
                else if (distanceSquared > 256.0D)
                {
                    this.delayCounter += 5;
                }

                if (!this.attacker.getNavigation().moveTo(targetPlayer, this.speedGetter.getAsDouble()))
                {
                    this.delayCounter += 15;
                }
            }

            this.attackTick = Math.max(this.attackTick - 1, 0);
            this.checkAndPerformAttack(targetPlayer, distanceSquared);
        }

        protected void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr)
        {
            double reachSqr = this.getAttackReachSqr(enemy);
            if (distToEnemySqr <= reachSqr && this.attackTick <= 0)
            {
                this.attackTick = 20;
                this.attacker.swing(InteractionHand.MAIN_HAND);
                this.attacker.doHurtTarget(enemy);
            }
        }

        protected double getAttackReachSqr(LivingEntity attackTarget)
        {
            return this.attacker.getBbWidth() * 2.0F * this.attacker.getBbWidth() * 2.0F + attackTarget.getBbWidth();
        }
    }
}
