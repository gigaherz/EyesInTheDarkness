package gigaherz.eyes.entity;

import gigaherz.eyes.ConfigData;
import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.InitiateJumpscare;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import javax.vecmath.Vector3d;
import java.util.List;
import java.util.Random;

public class EyesEntity extends MonsterEntity
{
    public final static int BLINK_DURATION = 5;
    public boolean blinkingState;
    public int blinkProgress;

    private static Random RAND = new Random();

    public EyesEntity(EntityType<? extends EyesEntity> type, World worldIn)
    {
        super(type, worldIn);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(8, new WaterAvoidingRandomWalkingGoal(this, 0.1D));
        this.goalSelector.addGoal(8, new CreepTowardPlayer(this, 0.25D, false));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, true));
    }

    private static class CreepTowardPlayer extends MeleeAttackGoal
    {
        private final EyesEntity eyes;
        public CreepTowardPlayer(EyesEntity creature, double speedIn, boolean useLongMemory)
        {
            super(creature, speedIn, useLongMemory);
            eyes = creature;
        }

        @Override
        public boolean shouldContinueExecuting()
        {
            if (isPlayerLookingInMyGeneralDirection())
                return false;
            return super.shouldContinueExecuting();
        }

        private boolean isPlayerLookingInMyGeneralDirection()
        {
            BlockPos position = eyes.getBlockPosEyes();
            float blockLight = 0;
            if (ConfigData.SERVER.EyesCanAttackWhileLit.get())
            {
                if (eyes.world.dimension.hasSkyLight())
                {
                    float skyLight = eyes.world.getLightFor(LightType.SKY, position)
                            - (1 - eyes.world.dimension.getSunBrightness(1.0f)) * 11;

                    blockLight = Math.max(blockLight, skyLight);
                }
            }
            else
            {
                blockLight = eyes.world.getLight(position);
            }
            if (blockLight >= 8)
                return true;

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

        @Override
        public boolean shouldExecute()
        {
            if (isPlayerLookingInMyGeneralDirection())
                return false;
            return super.shouldExecute();
        }
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
        EyesInTheDarkness.channel.sendTo(new InitiateJumpscare(this.posX, this.posY, this.posZ), player.connection.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
    }

    @Override
    public void livingTick()
    {
        super.livingTick();
        if(world.isRemote)
        {
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
        }
        else
        {
            BlockPos position = getBlockPosEyes();
            if (world.getLight(position) < 8)
            {
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
                    disappear(true);
            }
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
}
