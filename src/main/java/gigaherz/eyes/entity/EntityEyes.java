package gigaherz.eyes.entity;

import gigaherz.eyes.EyesInTheDarkness;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
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
        EyesInTheDarkness.LOGGER.warn("Entity created.");
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
            float maxWatchDistance = 16;
            Vec3d eyes = getPositionEyes(1);
            List<EntityPlayer> entities = world.getEntitiesWithinAABB(EntityPlayer.class,
                    new AxisAlignedBB(eyes.x-maxWatchDistance, eyes.y-maxWatchDistance, eyes.z-maxWatchDistance,
                            eyes.x+maxWatchDistance, eyes.y+maxWatchDistance, eyes.z+maxWatchDistance), (player) -> {

                if (player.getPositionEyes(1).distanceTo(eyes) > maxWatchDistance)
                    return false;

                Vec3d vec3d = player.getLook(1.0F).normalize();
                Vec3d vec3d1 = new Vec3d(this.posX - player.posX, this.getEntityBoundingBox().minY + (double)this.getEyeHeight() - (player.posY + (double)player.getEyeHeight()), this.posZ - player.posZ);
                double d0 = vec3d1.length();
                vec3d1 = vec3d1.normalize();
                double d1 = vec3d.dotProduct(vec3d1);
                return (d1 > 1.0D - 0.025D / d0) && player.canEntityBeSeen(this);
            });

            if(entities.size() > 0)
                disappear();
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

    @Override
    public void setDead()
    {
        EyesInTheDarkness.LOGGER.warn("Entity dead.");
        super.setDead();
    }
}
