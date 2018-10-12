package gigaherz.eyes.entity;

import gigaherz.eyes.EyesInTheDarkness;
import gigaherz.eyes.InitiateJumpscare;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import javax.vecmath.Vector3d;
import java.util.List;
import java.util.Random;

public class EntityEyes extends EntityMob
{
    public final static int BLINK_DURATION = 5;
    public boolean blinkingState;
    public int blinkProgress;

    private static Random RAND = new Random();

    public EntityEyes(World worldIn)
    {
        super(worldIn);
    }

    @Override
    protected void initEntityAI()
    {
        this.tasks.addTask(8, new EntityAIWanderAvoidWater(this, 0.1D));
        this.tasks.addTask(8, new CreepTowardPlayer(this, 0.25D, false));
        this.targetTasks.addTask(1, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
    }

    private static class CreepTowardPlayer extends EntityAIAttackMelee
    {
        private final EntityEyes eyes;
        public CreepTowardPlayer(EntityEyes creature, double speedIn, boolean useLongMemory)
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
            if (eyes.world.getLight(position, false) >= 8)
                return true;

            Vector3d selfPos = new Vector3d(eyes.posX, eyes.posY, eyes.posZ);
            EntityLivingBase target = eyes.getAttackTarget();
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
        if (entityIn instanceof EntityPlayerMP)
            jumpscare((EntityPlayerMP)entityIn);
        // Don't play the disappear laugh here.
        //disappear();
        damageEntity(DamageSource.GENERIC, 1);
        return true;
    }

    public void jumpscare(EntityPlayerMP player)
    {
        EyesInTheDarkness.channel.sendTo(new InitiateJumpscare(this.posX, this.posY, this.posZ), player);
    }

    @Override
    public void onLivingUpdate()
    {
        super.onLivingUpdate();

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
            if (world.getLight(position, false) < 8)
            {
                float maxWatchDistance = 16;
                Vec3d eyes = getPositionEyes(1);
                List<EntityPlayer> entities = world.getEntitiesWithinAABB(EntityPlayer.class,
                        new AxisAlignedBB(eyes.x - maxWatchDistance, eyes.y - maxWatchDistance, eyes.z - maxWatchDistance,
                                eyes.x + maxWatchDistance, eyes.y + maxWatchDistance, eyes.z + maxWatchDistance), (player) -> {

                            if (player.getPositionEyes(1).distanceTo(eyes) > maxWatchDistance)
                                return false;

                            Vec3d vec3d = player.getLook(1.0F).normalize();
                            Vec3d vec3d1 = new Vec3d(this.posX - player.posX, this.getEntityBoundingBox().minY + (double) this.getEyeHeight() - (player.posY + (double) player.getEyeHeight()), this.posZ - player.posZ);
                            double d0 = vec3d1.length();
                            vec3d1 = vec3d1.normalize();
                            double d1 = vec3d.dotProduct(vec3d1);
                            return (d1 > 1.0D - 0.025D / d0) && player.canEntityBeSeen(this);
                        });

                if (entities.size() > 0)
                    disappear();
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
        if (entityIn instanceof EntityPlayer)
        {
            disappear();
        }
        //super.collideWithEntity(entityIn);
    }

    private void disappear()
    {
        damageEntity(DamageSource.GENERIC, 1);
        this.playSound(getDeathSound(), this.getSoundVolume(), this.getSoundPitch());
    }

    @Override
    protected void collideWithNearbyEntities()
    {
        super.collideWithNearbyEntities();
    }

    @Override
    public EnumPushReaction getPushReaction()
    {
        return EnumPushReaction.IGNORE;
    }

    @Override
    protected void onDeathUpdate()
    {
        deathTime = 19;
        super.onDeathUpdate();
    }

    protected void applyEntityAttributes()
    {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1.0D);
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

}
