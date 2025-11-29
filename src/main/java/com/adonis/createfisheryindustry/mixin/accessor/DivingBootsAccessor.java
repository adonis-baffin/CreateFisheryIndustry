package com.adonis.createfisheryindustry.mixin.accessor;

import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Create DivingBootsItem的访问器，用于访问私有方法和字段
 */
@Mixin(DivingBootsItem.class)
public interface DivingBootsAccessor {
    
    /**
     * 调用affects静态方法
     */
    @Invoker("affects")
    static boolean invokeAffects(LivingEntity entity) {
        throw new AssertionError();
    }
}