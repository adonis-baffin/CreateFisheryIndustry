package com.adonis.createfisheryindustry.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * LivingEntity的访问器，用于访问私有/受保护字段
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    /**
     * 获取jumping字段（跳跃状态）
     */
    @Accessor("jumping")
    boolean getJumping();

    /**
     * 设置jumping字段
     */
    @Accessor("jumping")
    void setJumping(boolean jumping);

    /**
     * 获取noJumpDelay字段（跳跃冷却）
     */
    @Accessor("noJumpDelay")
    int getNoJumpDelay();

    /**
     * 设置noJumpDelay字段
     */
    @Accessor("noJumpDelay")
    void setNoJumpDelay(int delay);
}