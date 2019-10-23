package gigaherz.eyes.entity;

import gigaherz.eyes.ConfigData;
import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.InitiateJumpscarePacket;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IWorld;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import javax.vecmath.Vector3d;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleSupplier;

public class EyesEntity extends MonsterEntity
{
    private static final DataParameter<Boolean> IS_DORMANT = EntityDataManager.createKey(EyesEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Float> AGGRO = EntityDataManager.createKey(EyesEntity.class, DataSerializers.FLOAT);

    private static final float AGGRO_ESCALATION_PER_TICK = 1f / (20 * 60 * 5); // 300 seconds to reach max (5 minutes)
    private static final double SPEED_NO_AGGRO = 0.1f;
    private static final double SPEED_FULL_AGGRO = 0.5f;

    public final static int BLINK_DURATION = 5;

    public boolean blinkingState;
    public int blinkProgress;

    private static Random RAND = new Random();

    public EyesEntity(EntityType<? extends EyesEntity> type, World worldIn)
    {
        super(type, worldIn);
    }

    @Override
    protected void registerData()
    {
        super.registerData();
        this.dataManager.register(AGGRO, 0.1f);
        this.dataManager.register(IS_DORMANT, false);
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
        return this.getDataManager().get(AGGRO);
    }

    public void setAggroLevel(float aggro)
    {
        this.getDataManager().set(AGGRO, MathHelper.clamp(aggro, 0, 1));
    }

    public boolean getIsDormant()
    {
        return this.getDataManager().get(IS_DORMANT);
    }

    public void setIsDormant(boolean value)
    {
        this.getDataManager().set(IS_DORMANT, value);
    }

    private double getSpeedFromAggro()
    {
        if (getIsDormant())
            return 0;
        return MathHelper.clampedLerp(getAggroLevel(), SPEED_NO_AGGRO, SPEED_FULL_AGGRO);
    }

    @Nullable
    @Override
    public ILivingEntityData onInitialSpawn(IWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag)
    {
        if (ConfigData.SERVER.EyeAggressionDependsOnLocalDifficulty.get())
        {
            float difficulty = difficultyIn.getClampedAdditionalDifficulty();
            setAggroLevel(RAND.nextFloat() * difficulty);
        }
        return super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    @Override
    public boolean attackEntityAsMob(Entity entityIn)
    {
        boolean jumpScared = ConfigData.SERVER.Jumpscare.get() && entityIn instanceof ServerPlayerEntity;
        if (jumpScared)
        {
            jumpscare((ServerPlayerEntity) entityIn);
        }

        if (ConfigData.SERVER.JumpscareHurtLevel.get() > 0 && entityIn instanceof LivingEntity)
        {
            LivingEntity living = (LivingEntity) entityIn;
            living.addPotionEffect(new EffectInstance(Effects.POISON, 5 * 20, ConfigData.SERVER.JumpscareHurtLevel.get() - 1));
        }

        // Don't play the disappear laugh if we initiated a jumpscare.
        disappear(!jumpScared);
        return true;
    }

    public void jumpscare(ServerPlayerEntity player)
    {
        EyesInTheDarkness.channel.sendTo(new InitiateJumpscarePacket(this.posX, this.posY, this.posZ), player.connection.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
    }

    @Override
    public void livingTick()
    {
        super.livingTick();

        if (world.isRemote)
        {
            if (getIsDormant())
                return;

            if (!blinkingState)
            {
                if (RAND.nextFloat() < .02f)
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

        setIsDormant(isIlluminated(ConfigData.SERVER.EyesCanAttackWhileLit.get()));

        if (getIsDormant())
            return;

        float maxWatchDistance = 16;
        Vec3d eyes = getEyePosition(1);
        List<PlayerEntity> entities = world.getEntitiesWithinAABB(PlayerEntity.class,
                new AxisAlignedBB(eyes.x - maxWatchDistance, eyes.y - maxWatchDistance, eyes.z - maxWatchDistance,
                        eyes.x + maxWatchDistance, eyes.y + maxWatchDistance, eyes.z + maxWatchDistance), (player) -> {

                    if (player.getEyePosition(1).distanceTo(eyes) > maxWatchDistance)
                        return false;

                    Vec3d vec3d = player.getLook(1.0F).normalize();
                    Vec3d vec3d1 = new Vec3d(this.posX - player.posX, this.getBoundingBox().minY + (double) this.getEyeHeight() - (player.posY + (double) player.getEyeHeight()), this.posZ - player.posZ);
                    double d0 = vec3d1.length();
                    vec3d1 = vec3d1.normalize();
                    double d1 = vec3d.dotProduct(vec3d1);
                    return (d1 > 1.0D - 0.025D / d0) && player.canEntityBeSeen(this);
                });

        if (entities.size() > 0)
        {
            disappear(true);
            return;
        }

        if (ConfigData.SERVER.EnableEyeAggressionEscalation.get() && !getIsDormant())
        {
            setAggroLevel(getAggroLevel() + AGGRO_ESCALATION_PER_TICK);
        }
    }

    @Override
    public boolean isEntityUndead()
    {
        return true;
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    @Override
    public boolean canBePushed()
    {
        return false;
    }

    @Override
    public void applyEntityCollision(Entity entityIn)
    {
        //super.applyEntityCollision(entityIn);
    }

    @Override
    protected void collideWithEntity(Entity entityIn)
    {
        if (entityIn instanceof PlayerEntity)
        {
            disappear(true);
        }
        //super.collideWithEntity(entityIn);
    }

    private void disappear(boolean playDeathSound)
    {
        damageEntity(DamageSource.GENERIC, 1);
        if (playDeathSound)
        {
            this.playSound(getDeathSound(), this.getSoundVolume(), this.getSoundPitch());
        }
    }

    @Override
    protected void collideWithNearbyEntities()
    {
        super.collideWithNearbyEntities();
    }

    @Override
    public PushReaction getPushReaction()
    {
        return PushReaction.IGNORE;
    }

    @Override
    protected void onDeathUpdate()
    {
        deathTime = 19;
        super.onDeathUpdate();
    }

    @Override
    protected void registerAttributes()
    {
        super.registerAttributes();
        this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1.0D);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound()
    {
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
        return new BlockPos(this.posX, this.posY + getEyeHeight(), this.posZ);
    }

    @Override
    public IPacket<?> createSpawnPacket()
    {
        return NetworkHooks.getEntitySpawningPacket(this);
    }


    private float getLightLevel(boolean excludeDaylight)
    {
        BlockPos position = getBlockPosEyes();
        float blockLight = 0;
        if (excludeDaylight)
        {
            if (world.dimension.hasSkyLight())
            {
                float skyLight = world.getLightFor(LightType.SKY, position)
                        - (1 - world.dimension.getSunBrightness(1.0f)) * 11;

                blockLight = Math.max(blockLight, skyLight);
            }
        }
        else
        {
            blockLight = world.getLight(position);
        }
        return blockLight;
    }

    private boolean isIlluminated(boolean excludeDaylight)
    {
        float blockLight = getLightLevel(excludeDaylight);
        return blockLight >= 8;
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
            this.setMutexFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            this.speedGetter = speedGetter;
            eyes = creature;
        }

        /**
         * Returns whether the EntityAIBase should begin execution.
         */
        @Override
        public boolean shouldExecute()
        {
            if (eyes.getIsDormant())
                return false;

            LivingEntity targetPlayer = this.attacker.getAttackTarget();
            if (targetPlayer == null || !targetPlayer.isAlive())
            {
                return false;
            }

            if (isPlayerLookingInMyGeneralDirection())
                return false;

            this.path = this.attacker.getNavigator().getPathToEntityLiving(targetPlayer, 0);
            return this.path != null || isWithinRange(targetPlayer);
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting()
        {
            this.attacker.getNavigator().setPath(this.path, this.speedGetter.getAsDouble());
            this.attacker.setAggroed(true);
            this.delayCounter = 0;
        }

        @Override
        public boolean shouldContinueExecuting()
        {
            if (eyes.getIsDormant())
                return false;

            if (isPlayerLookingInMyGeneralDirection())
                return false;

            LivingEntity livingentity = this.attacker.getAttackTarget();

            if (livingentity == null || !livingentity.isAlive())
                return false;

            return !this.attacker.getNavigator().noPath();
        }

        private boolean isWithinRange(LivingEntity targetPlayer)
        {
            return this.getAttackReachSqr(targetPlayer) >= this.attacker.getDistanceSq(targetPlayer.posX, targetPlayer.getBoundingBox().minY, targetPlayer.posZ);
        }

        private boolean isPlayerLookingInMyGeneralDirection()
        {
            if (eyes.getIsDormant())
                return false;

            Vector3d selfPos = new Vector3d(eyes.posX, eyes.posY, eyes.posZ);
            LivingEntity target = eyes.getAttackTarget();
            if (target == null)
                return false;
            Vector3d playerPos = new Vector3d(target.posX, target.posY, target.posZ);
            Vec3d lookVec = target.getLookVec();
            Vector3d playerLook = new Vector3d(lookVec.x, lookVec.y, lookVec.z);
            playerLook.normalize();

            playerPos.sub(selfPos);
            playerPos.normalize();

            return playerLook.dot(playerPos) < 0;
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void resetTask()
        {
            LivingEntity livingentity = this.attacker.getAttackTarget();
            if (!EntityPredicates.CAN_AI_TARGET.test(livingentity))
            {
                this.attacker.setAttackTarget(null);
            }

            this.attacker.setAggroed(false);
            this.attacker.getNavigator().clearPath();
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick()
        {
            LivingEntity targetPlayer = this.attacker.getAttackTarget();
            this.attacker.getLookController().setLookPositionWithEntity(targetPlayer, 30.0F, 30.0F);
            double distanceSquared = this.attacker.getDistanceSq(targetPlayer.posX, targetPlayer.getBoundingBox().minY, targetPlayer.posZ);
            --this.delayCounter;
            if (this.attacker.getEntitySenses().canSee(targetPlayer)
                    && this.delayCounter <= 0
                    && (this.targetX == 0.0D && this.targetY == 0.0D && this.targetZ == 0.0D
                        || targetPlayer.getDistanceSq(this.targetX, this.targetY, this.targetZ) >= 1.0D
                        || this.attacker.getRNG().nextFloat() < 0.05F))
            {
                this.targetX = targetPlayer.posX;
                this.targetY = targetPlayer.getBoundingBox().minY;
                this.targetZ = targetPlayer.posZ;
                this.delayCounter = 4 + this.attacker.getRNG().nextInt(7);

                if (distanceSquared > 1024.0D)
                {
                    this.delayCounter += 10;
                }
                else if (distanceSquared > 256.0D)
                {
                    this.delayCounter += 5;
                }

                if (!this.attacker.getNavigator().tryMoveToEntityLiving(targetPlayer, this.speedGetter.getAsDouble()))
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
                this.attacker.swingArm(Hand.MAIN_HAND);
                this.attacker.attackEntityAsMob(enemy);
            }
        }

        protected double getAttackReachSqr(LivingEntity attackTarget)
        {
            return this.attacker.getWidth() * 2.0F * this.attacker.getWidth() * 2.0F + attackTarget.getWidth();
        }
    }
}
