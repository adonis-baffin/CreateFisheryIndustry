package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 简化的水下疾跑系统 - 适配1.21.1版本
 * 使用新的AttributeModifier API
 */
@Mixin(value = Player.class, priority = 1000)
public abstract class UnderwaterSprintMixin {

    // 疾跑系统状态
    @Unique private int cfi_sprintToggleTimer = 0;
    @Unique private boolean cfi_wasSprintKeyDown = false;
    @Unique private boolean cfi_underwaterSprintActive = false;
    @Unique private int cfi_sprintHungerTimer = 0;

    // 速度加成的ResourceLocation ID
    @Unique private static final ResourceLocation SPRINT_BOOST_ID = CreateFisheryMod.asResource("underwater_sprint_boost");

    @Inject(method = "tick", at = @At("HEAD"))
    private void cfi_handleUnderwaterSprint(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // 检查是否满足水下疾跑条件
        if (!cfi_canUseUnderwaterSprint(player)) {
            // 不满足条件时清理状态
            if (cfi_underwaterSprintActive) {
                cfi_deactivateUnderwaterSprint(player);
            }
            return;
        }

        // 获取前进输入
        LivingEntity livingPlayer = (LivingEntity) player;
        float forwardInput = livingPlayer.zza;
        boolean isSprintKeyDown = forwardInput > 0.8F;

        // 双击W检测
        if (isSprintKeyDown && !cfi_wasSprintKeyDown) {
            if (cfi_sprintToggleTimer > 0 && !cfi_underwaterSprintActive) {
                // 双击W成功，检查饥饿度
                int minHunger = CreateFisheryCommonConfig.getDivingMinHungerLevel();
                if (player.getFoodData().getFoodLevel() > minHunger || player.getAbilities().mayfly) {
                    // 激活水下疾跑
                    cfi_activateUnderwaterSprint(player);
                }
            } else {
                // 开始双击窗口
                cfi_sprintToggleTimer = CreateFisheryCommonConfig.getDivingSprintDoubleClickWindow();
            }
        }

        cfi_wasSprintKeyDown = isSprintKeyDown;

        // 更新计时器
        if (cfi_sprintToggleTimer > 0) {
            cfi_sprintToggleTimer--;
        }

        // 处理疾跑状态
        if (cfi_underwaterSprintActive) {
            cfi_maintainUnderwaterSprint(player);
        }
    }

    @Unique
    private void cfi_activateUnderwaterSprint(Player player) {
        cfi_underwaterSprintActive = true;
        cfi_sprintToggleTimer = 0;

        // 设置疾跑状态（触发FOV效果）
        player.setSprinting(true);

        // 添加速度属性修改器 - 使用新的API
        AttributeInstance speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute != null && !speedAttribute.hasModifier(SPRINT_BOOST_ID)) {
            // 疾跑通常增加30%速度
            AttributeModifier sprintBoost = new AttributeModifier(
                    SPRINT_BOOST_ID,
                    0.3,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
            speedAttribute.addTransientModifier(sprintBoost);
        }

        // 设置标记
        player.getPersistentData().putBoolean("CFI_UnderwaterSprint", true);
        player.getPersistentData().putBoolean("CFI_UnderwaterSprintActive", true);

        // 音效反馈
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.DOLPHIN_SWIM, SoundSource.PLAYERS, 1.0F, 1.5F);

        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.literal("§b[CFI] 水下疾跑已激活"), true);
        }
    }

    @Unique
    private void cfi_deactivateUnderwaterSprint(Player player) {
        cfi_underwaterSprintActive = false;

        // 移除速度属性修改器 - 使用新的API
        AttributeInstance speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.removeModifier(SPRINT_BOOST_ID);
        }

        // 移除标记
        player.getPersistentData().remove("CFI_UnderwaterSprint");
        player.getPersistentData().remove("CFI_UnderwaterSprintActive");

        // 停止疾跑
        player.setSprinting(false);
    }

    @Unique
    private boolean cfi_canUseUnderwaterSprint(Player player) {
        // 必须装备潜水靴和潜水护腿
        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasDivingLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

        // 必须在水中或岩浆中
        boolean inFluid = player.isInWater() || player.isInLava();

        return hasDivingBoots && hasDivingLeggings && inFluid;
    }

    @Unique
    private void cfi_maintainUnderwaterSprint(Player player) {
        LivingEntity livingPlayer = (LivingEntity) player;
        float forwardInput = livingPlayer.zza;

        // 检查是否应该停止疾跑
        int minHunger = CreateFisheryCommonConfig.getDivingMinHungerLevel();
        boolean hasEnoughHunger = player.getFoodData().getFoodLevel() > minHunger || player.getAbilities().mayfly;

        if (!hasEnoughHunger || player.horizontalCollision || forwardInput <= 0) {
            // 停止疾跑
            cfi_deactivateUnderwaterSprint(player);

            if (!player.level().isClientSide) {
                String reason = !hasEnoughHunger ? "饥饿度不足" :
                        player.horizontalCollision ? "碰撞障碍" : "停止前进";
                player.displayClientMessage(Component.literal("§c[CFI] 水下疾跑已停止 - " + reason), true);
            }
            return;
        }

        // 保持疾跑状态（触发FOV效果）
        player.setSprinting(true);

        // 应用额外的速度加成（方向性推进）
        cfi_applySprintSpeedBoost(player);

        // 饥饿度消耗（每秒）
        cfi_sprintHungerTimer++;
        if (cfi_sprintHungerTimer >= 20) {
            cfi_sprintHungerTimer = 0;
            if (!player.getAbilities().mayfly) {
                float hungerCost = (float) CreateFisheryCommonConfig.getDivingSprintHungerCost();
                player.causeFoodExhaustion(hungerCost);
            }
        }
    }

    @Unique
    private void cfi_applySprintSpeedBoost(Player player) {
        // 获取速度倍率
        double speedMultiplier = CreateFisheryCommonConfig.getDivingCopperSprintSpeed();
        if (player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem) {
            speedMultiplier = CreateFisheryCommonConfig.getDivingNetheriteSprintSpeed();
        }

        // 获取玩家输入和朝向
        LivingEntity livingPlayer = (LivingEntity) player;
        float forwardInput = livingPlayer.zza;
        float sideInput = livingPlayer.xxa;

        if (Math.abs(forwardInput) > 0.01 || Math.abs(sideInput) > 0.01) {
            float yaw = player.getYRot() * 0.017453292F; // 转换为弧度
            float pitch = player.getXRot() * 0.017453292F;

            // 计算3D移动向量
            double moveX = -Math.sin(yaw) * Math.cos(pitch) * speedMultiplier * forwardInput;
            double moveY = -Math.sin(pitch) * speedMultiplier * forwardInput * 0.5; // 垂直移动减半
            double moveZ = Math.cos(yaw) * Math.cos(pitch) * speedMultiplier * forwardInput;

            // 添加侧向移动
            moveX += Math.cos(yaw) * speedMultiplier * sideInput * 0.8; // 侧向移动稍慢
            moveZ += Math.sin(yaw) * speedMultiplier * sideInput * 0.8;

            // 应用速度加成
            Vec3 currentMotion = player.getDeltaMovement();
            Vec3 newMotion = currentMotion.add(moveX, moveY, moveZ);

            // 限制最大速度，防止过快
            double maxSpeed = 0.5;
            double horizontalSpeed = Math.sqrt(newMotion.x * newMotion.x + newMotion.z * newMotion.z);
            if (horizontalSpeed > maxSpeed) {
                double scale = maxSpeed / horizontalSpeed;
                newMotion = new Vec3(newMotion.x * scale, newMotion.y, newMotion.z * scale);
            }

            player.setDeltaMovement(newMotion);
        }
    }

    /**
     * 防止在水下疾跑时进入游泳姿态
     */
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void cfi_preventSwimmingPoseWhileSprinting(CallbackInfo ci) {
        if (cfi_underwaterSprintActive) {
            Player player = (Player) (Object) this;
            // 保持站立姿态
            player.refreshDimensions();
            ci.cancel();
        }
    }
}