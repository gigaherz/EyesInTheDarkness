package gigaherz.eyes.entity;

import gigaherz.eyes.config.ConfigData;
import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.InitiateJumpscarePacket;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.Path;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.registries.ObjectHolder;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.function.DoubleSupplier;

import net.minecraft.entity.ai.goal.Goal.Flag;

public class EyesEntity extends MonsterEntity
{
    @ObjectHolder("eyesinthedarkness:eyes")
    public static EntityType<EyesEntity> TYPE = null;

    private static final DataParameter<Boolean> IS_DORMANT = EntityDataManager.defineId(EyesEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Float> AGGRO = EntityDataManager.defineId(EyesEntity.class, DataSerializers.FLOAT);

    private static final float AGGRO_ESCALATION_PER_TICK = 1f / (20 * 60 * 5); // 300 seconds to reach max (5 minutes)

    public final static int BLINK_DURATION = 5;

    public boolean blinkingState;
    public int blinkProgress;

    public EyesEntity(EntityType<? extends EyesEntity> type, World worldIn)
    {
        super(type, worldIn);
    }

    public static AttributeModifierMap.MutableAttribute prepareAttributes()
    {
        return MonsterEntity.createMonsterAttributes()
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
        this.goalSelector.addGoal(8, new WaterAvoidingRandomWalkingGoal(this, 0.1D));
        this.goalSelector.addGoal(8, new CreepTowardPlayer(this, this::getSpeedFromAggro));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, true));
    }

    public float getAggroLevel()
    {
        return this.getEntityData().get(AGGRO);
    }

    public void setAggroLevel(float aggro)
    {
        this.getEntityData().set(AGGRO, MathHelper.clamp(aggro, 0, 1));
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
        return MathHelper.clampedLerp(getAggroLevel(), ConfigData.speedNoAggro, ConfigData.speedFullAggro);
    }

    @Nullable
    @Override
    public ILivingEntityData finalizeSpawn(IServerWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag)
    {
        if (ConfigData.eyeAggressionDependsOnLocalDifficulty)
        {
            float difficulty = difficultyIn.getSpecialMultiplier();
            setAggroLevel(level.random.nextFloat() * difficulty);
        }
        return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT compound)
    {
        super.readAdditionalSaveData(compound);
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT compound)
    {
        super.addAdditionalSaveData(compound);
    }

    @Override
    public boolean doHurtTarget(Entity entityIn)
    {
        boolean jumpScared = ConfigData.jumpscare && entityIn instanceof ServerPlayerEntity;
        if (jumpScared)
        {
            jumpscare((ServerPlayerEntity) entityIn);
        }

        if (ConfigData.jumpscareHurtLevel > 0 && entityIn instanceof LivingEntity)
        {
            LivingEntity living = (LivingEntity) entityIn;
            living.addEffect(new EffectInstance(Effects.POISON, 5 * 20, ConfigData.jumpscareHurtLevel - 1));
        }

        // Don't play the disappear laugh if we initiated a jumpscare.
        disappear(!jumpScared);
        return true;
    }

    public void jumpscare(ServerPlayerEntity player)
    {
        EyesInTheDarkness.channel.sendTo(new InitiateJumpscarePacket(this.getX(), this.getY(), this.getZ()), player.connection.getConnection(), NetworkDirection.PLAY_TO_CLIENT);
    }

    @Override
    public void aiStep()
    {
        super.aiStep();

        if (level.isClientSide)
        {
            if (getIsDormant())
                return;

            if (!blinkingState)
            {
                if (level.random.nextFloat() < .02f)
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
        Vector3d eyes = getEyePosition(1);
        List<PlayerEntity> entities = level.getEntitiesOfClass(PlayerEntity.class,
                new AxisAlignedBB(eyes.x - maxWatchDistance, eyes.y - maxWatchDistance, eyes.z - maxWatchDistance,
                        eyes.x + maxWatchDistance, eyes.y + maxWatchDistance, eyes.z + maxWatchDistance), (player) -> {

                    if (player.isSpectator() || !player.isAlive())
                        return false;

                    if (player.getEyePosition(1).distanceTo(eyes) > maxWatchDistance)
                        return false;

                    Vector3d vec3d = player.getViewVector(1.0F).normalize();
                    Vector3d vec3d1 = new Vector3d(this.getX() - player.getX(), this.getBoundingBox().minY + (double) this.getEyeHeight() - (player.getY() + (double) player.getEyeHeight()), this.getZ() - player.getZ());
                    double d0 = vec3d1.length();
                    vec3d1 = vec3d1.normalize();
                    double d1 = vec3d.dot(vec3d1);
                    return (d1 > 1.0D - 0.025D / d0) && player.canSee(this);
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

        if (entityIn instanceof PlayerEntity)
        {
            disappear(true);
        }
        //super.collideWithEntity(entityIn);
    }

    private void disappear(boolean playDeathSound)
    {
        actuallyHurt(DamageSource.GENERIC, 1);
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
        if (getIsDormant())
            return null;
        return EyesInTheDarkness.eyes_laugh;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return EyesInTheDarkness.eyes_disappear;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn)
    {
        return EyesInTheDarkness.eyes_disappear;
    }

    /**
     * Get the position in the world. <b>{@code null} is not allowed!</b> If you are not an entity in the world, return
     * the coordinates 0, 0, 0
     */
    public BlockPos getBlockPosEyes()
    {
        return new BlockPos(this.getX(), this.getY() + getEyeHeight(), this.getZ());
    }

    @Override
    public IPacket<?> getAddEntityPacket()
    {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public float getSunBrightness()
    {
        float angleRadians = level.getSunAngle(1);
        float f1 = 1.0F - (MathHelper.cos(angleRadians) * 2.0F + 0.2F);
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        f1 = 1.0F - f1;
        f1 = (float) ((double) f1 * (1.0D - (double) (level.getRainLevel(1) * 5.0F) / 16.0D));
        f1 = (float) ((double) f1 * (1.0D - (double) (level.getThunderLevel(1) * 5.0F) / 16.0D));
        return f1 * 0.8F + 0.2F;
    }

    private float getLightLevel(boolean excludeDaylight)
    {
        BlockPos position = getBlockPosEyes();
        float blockLight = 0;
        if (excludeDaylight)
        {
            if (level.dimensionType().hasSkyLight())
            {
                float skyLight = level.getBrightness(LightType.SKY, position)
                        - (1 - getSunBrightness()) * 11;
                float skyLight1 = level.getBrightness(LightType.SKY, position)
                        - level.getSkyDarken();

                if (skyLight != skyLight1)
                {
                    skyLight = skyLight;
                }

                blockLight = Math.max(blockLight, skyLight);
            }
        }
        else
        {
            blockLight = level.getMaxLocalRawBrightness(position);
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
        protected final CreatureEntity attacker;
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

            Vector3d selfPos = eyes.position();
            LivingEntity target = eyes.getTarget();
            if (target == null)
                return false;
            Vector3d playerPos = target.position();
            Vector3d lookVec = target.getLookAngle();
            Vector3d playerLook = new Vector3d(lookVec.x, lookVec.y, lookVec.z);
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
            if (!EntityPredicates.NO_CREATIVE_OR_SPECTATOR.test(livingentity))
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
            if (this.attacker.getSensing().canSee(targetPlayer)
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
                this.attacker.swing(Hand.MAIN_HAND);
                this.attacker.doHurtTarget(enemy);
            }
        }

        protected double getAttackReachSqr(LivingEntity attackTarget)
        {
            return this.attacker.getBbWidth() * 2.0F * this.attacker.getBbWidth() * 2.0F + attackTarget.getBbWidth();
        }
    }
}
