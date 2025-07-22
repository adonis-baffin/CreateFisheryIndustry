package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.adonis.createfisheryindustry.mixin.accessor.LivingEntityAccessor;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 陆地化水下系统 - 让水下行为完全模拟陆地
 */
@Mixin(value = Player.class, priority = 1400) // 最高优先级
public abstract class TerrestrialUnderwaterSystemMixin {

    // 跳跃系统
    @Unique private int cfi_jumpCooldown = 0;
    @Unique private boolean cfi_lastJumpKeyState = false;
    @Unique private int cfi_jumpKeyReleaseTimer = 0;

    // 疾跑系统
    @Unique private int cfi_sprintToggleTimer = 0;
    @Unique private boolean cfi_wasSprintKeyDown = false;
    @Unique private boolean cfi_underwaterSprintActive = false;

    // 系统状态
    @Unique private boolean cfi_terrestrialModeActive = false;
    @Unique private int cfi_debugMessageCooldown = 0;

    // 跳跃状态
    @Unique private boolean cfi_superJumpExecuted = false;
    @Unique private int cfi_jumpStartTick = 0;

    @Unique
    private boolean cfi_hasCompleteDivingGear(Player player) {
        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;
        return hasDivingBoots && hasLeggings;
    }

    @Unique
    private boolean cfi_isInAnyFluid(Player player) {
        // 检查玩家是否接触任何流体（不仅仅是头部）
        BlockPos pos = player.blockPosition();

        // 检查脚部、身体中部、头部位置
        for (int y = 0; y <= 1; y++) {
            BlockPos checkPos = pos.offset(0, y, 0);
            FluidState fluidState = player.level().getFluidState(checkPos);
            if (!fluidState.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cfi_mainTick(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // 更新计时器
        if (cfi_jumpCooldown > 0) cfi_jumpCooldown--;
        if (cfi_debugMessageCooldown > 0) cfi_debugMessageCooldown--;
        if (cfi_jumpKeyReleaseTimer > 0) cfi_jumpKeyReleaseTimer--;

        // 检查陆地化模式
        boolean shouldActivate = cfi_hasCompleteDivingGear(player) && cfi_isInAnyFluid(player);
        if (shouldActivate != cfi_terrestrialModeActive) {
            cfi_terrestrialModeActive = shouldActivate;
            if (shouldActivate) {
                cfi_activateTerrestrialMode(player);
            } else {
                cfi_deactivateTerrestrialMode(player);
            }
        }

        // 处理陆地化系统
        if (cfi_terrestrialModeActive) {
            cfi_processTerrestrialMode(player);
        }

        // 调试信息
        cfi_displayDebugInfo(player);
    }

    @Unique
    private void cfi_activateTerrestrialMode(Player player) {
        // 完全禁用Create的所有水下效果
        player.getPersistentData().remove("HeavyBoots");
        player.getPersistentData().remove("LavaGrounded");

        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.literal("§a[CFI] 陆地化模式激活 - 水下如陆地"), false);
            CreateFisheryMod.LOGGER.info("Terrestrial underwater mode activated for player: {}", player.getName().getString());
        }
    }

    @Unique
    private void cfi_deactivateTerrestrialMode(Player player) {
        cfi_underwaterSprintActive = false;
        cfi_superJumpExecuted = false;

        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.literal("§c[CFI] 陆地化模式停用"), true);
        }
    }

    @Unique
    private void cfi_processTerrestrialMode(Player player) {
        // 持续禁用Create效果
        player.getPersistentData().remove("HeavyBoots");
        player.getPersistentData().remove("LavaGrounded");

        // 处理跳跃逻辑
        cfi_handleJumpLogic(player);

        // 应用陆地化物理
        cfi_applyTerrestrialPhysics(player);

        // 处理疾跑
        cfi_handleUnderwaterSprint(player);
    }

    @Unique
    private void cfi_handleJumpLogic(Player player) {
        LivingEntityAccessor accessor = (LivingEntityAccessor) player;
        boolean jumpKeyDown = accessor.getJumping();

        // 检测跳跃键的按下（边缘检测）
        if (jumpKeyDown && !cfi_lastJumpKeyState) {
            // 跳跃键刚被按下
            cfi_jumpKeyReleaseTimer = 5; // 5 tick的窗口期
            cfi_superJumpExecuted = false;
            cfi_jumpStartTick = player.tickCount;
        } else if (!jumpKeyDown && cfi_lastJumpKeyState) {
            // 跳跃键刚被释放
            if (cfi_jumpKeyReleaseTimer > 0 && !cfi_superJumpExecuted && cfi_jumpCooldown == 0) {
                // 这是一个短按，执行超级跳跃
                cfi_executeSuperJump(player);
            }
        }

        cfi_lastJumpKeyState = jumpKeyDown;
    }

    @Unique
    private void cfi_executeSuperJump(Player player) {
        cfi_superJumpExecuted = true;

        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.literal("§a[CFI-JUMP] 超级跳跃触发条件满足！"), false);
        }

        // 获取配置的跳跃力度
        double jumpPower = CreateFisheryCommonConfig.getDivingBaseJumpPower();
        if (cfi_underwaterSprintActive) {
            jumpPower = CreateFisheryCommonConfig.getDivingSprintJumpPower();
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.literal("§a[CFI-JUMP] 使用疾跑跳跃力度"), false);
            }
        }

        // 强力向上推进 - 确保能跳4格高
        // 在水中需要更大的力度来克服阻力
        double actualJumpPower = jumpPower * 2.5; // 增强跳跃力度

        Vec3 oldMotion = player.getDeltaMovement();
        Vec3 newMotion = new Vec3(oldMotion.x, actualJumpPower, oldMotion.z);
        player.setDeltaMovement(newMotion);
        player.hasImpulse = true;
        player.setOnGround(false);

        if (!player.level().isClientSide) {
            String message = String.format("§a[CFI-JUMP] 动量设置 旧Y:%.3f 新Y:%.3f (%.1fx%.1f=%.1f)",
                    oldMotion.y, actualJumpPower, jumpPower, 2.5, actualJumpPower);
            player.displayClientMessage(Component.literal(message), false);
        }

        // 设置冷却和标记 - 强制在客户端和服务端都设置
        cfi_jumpCooldown = CreateFisheryCommonConfig.getDivingJumpCooldown();

        // 确保NBT标记被设置
        player.getPersistentData().putBoolean("CFI_SuperJump", true);
        player.getPersistentData().putFloat("CFI_JumpStartY", (float)player.getY());
        player.getPersistentData().putLong("CFI_JumpTime", player.level().getGameTime());

        // 额外的保护标记
        player.getPersistentData().putBoolean("CFI_FallProtection", true);
        player.getPersistentData().putInt("CFI_ProtectionTick", player.tickCount);

        if (!player.level().isClientSide) {
            String protectionMsg = String.format("§a[CFI-JUMP] 摔落保护已设置 开始Y:%.1f 时间:%d tick:%d",
                    player.getY(), player.level().getGameTime(), player.tickCount);
            player.displayClientMessage(Component.literal(protectionMsg), false);

            // 验证NBT标记确实被设置了
            boolean verified = player.getPersistentData().getBoolean("CFI_SuperJump");
            player.displayClientMessage(Component.literal("§a[CFI-JUMP] NBT标记验证: " + verified), false);
        }

        // 音效和粒子
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 2.0F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.5F);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SPLASH,
                    player.getX(), player.getY(), player.getZ(), 30, 0.8, 0.8, 0.8, 0.2);
            serverLevel.sendParticles(ParticleTypes.BUBBLE_POP,
                    player.getX(), player.getY(), player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);

            player.displayClientMessage(Component.literal("§a[CFI-JUMP] 音效和粒子效果已播放"), false);
        }

        // 消耗和统计
        if (!player.getAbilities().instabuild) {
            float hungerCost = (float) CreateFisheryCommonConfig.getDivingJumpHungerCost();
            player.causeFoodExhaustion(hungerCost);
            if (!player.level().isClientSide) {
                String hungerMsg = String.format("§a[CFI-JUMP] 饥饿度消耗:%.1f", hungerCost);
                player.displayClientMessage(Component.literal(hungerMsg), false);
            }
        }
        player.awardStat(net.minecraft.stats.Stats.JUMP);

        CreateFisheryMod.LOGGER.info("Super jump executed - Player: {}, Configured: {}, Actual: {}, Position: ({},{},{}), NBT set: {}",
                player.getName().getString(), jumpPower, actualJumpPower, player.getX(), player.getY(), player.getZ(),
                player.getPersistentData().getBoolean("CFI_SuperJump"));
    }

    @Unique
    private void cfi_applyTerrestrialPhysics(Player player) {
        if (!cfi_isInAnyFluid(player)) return;

        Vec3 motion = player.getDeltaMovement();
        LivingEntityAccessor accessor = (LivingEntityAccessor) player;
        boolean jumpKeyDown = accessor.getJumping();

        // 获取玩家输入
        LivingEntity livingPlayer = (LivingEntity) player;
        float forwardInput = livingPlayer.zza;
        float sideInput = livingPlayer.xxa;

        Vec3 oldMotion = motion;

        // 垂直运动处理
        if (jumpKeyDown && !cfi_superJumpExecuted && player.onGround()) {
            // 长按跳跃：持续向上推力（模拟在陆地上跳跃后的持续上升）
            motion = motion.add(0, 0.2, 0);
            if (!player.level().isClientSide && player.tickCount % 10 == 0) {
                player.displayClientMessage(Component.literal("§e[CFI-PHYSICS] 长按跳跃，持续上升"), false);
            }
        } else if (player.isShiftKeyDown()) {
            // Shift下蹲：快速下沉
            motion = motion.add(0, -0.3, 0);
            if (!player.level().isClientSide && player.tickCount % 20 == 0) {
                player.displayClientMessage(Component.literal("§e[CFI-PHYSICS] Shift下沉"), false);
            }
        } else if (!player.onGround() && !jumpKeyDown) {
            // 自由下降：模拟重力，但比Create的慢一些，更像陆地
            motion = motion.add(0, -0.08, 0);
        }

        // 水平运动处理：只有在玩家有输入时才增强
        if ((Math.abs(forwardInput) > 0.1 || Math.abs(sideInput) > 0.1) && !player.isShiftKeyDown()) {
            // 计算玩家想要移动的方向
            float yaw = player.getYRot() * 0.017453292F;

            // 基于玩家输入计算移动向量
            double moveX = (-Math.sin(yaw) * forwardInput + Math.cos(yaw) * sideInput) * 0.1;
            double moveZ = (Math.cos(yaw) * forwardInput + Math.sin(yaw) * sideInput) * 0.1;

            // 只增强玩家想要的移动方向，不放大现有动量
            motion = new Vec3(moveX, motion.y, moveZ);

            if (!player.level().isClientSide && player.tickCount % 20 == 0) {
                String moveMsg = String.format("§e[CFI-PHYSICS] 玩家移动 前:%.2f 侧:%.2f 生成X:%.3f Z:%.3f",
                        forwardInput, sideInput, moveX, moveZ);
                player.displayClientMessage(Component.literal(moveMsg), false);
            }
        } else {
            // 没有输入时，快速减少水平动量（模拟摩擦力）
            double friction = 0.8;
            motion = new Vec3(motion.x * friction, motion.y, motion.z * friction);

            if (!player.level().isClientSide && player.tickCount % 40 == 0 &&
                    (Math.abs(motion.x) > 0.01 || Math.abs(motion.z) > 0.01)) {
                String frictionMsg = String.format("§e[CFI-PHYSICS] 摩擦力减速 X:%.3f->%.3f Z:%.3f->%.3f",
                        oldMotion.x, motion.x, oldMotion.z, motion.z);
                player.displayClientMessage(Component.literal(frictionMsg), false);
            }
        }

        // 设置"接地"状态，让玩家能够正常跳跃
        if (player.verticalCollision && motion.y <= 0) {
            player.setOnGround(true);
            if (!player.level().isClientSide && !player.onGround()) {
                player.displayClientMessage(Component.literal("§e[CFI-PHYSICS] 设置接地状态"), false);
            }
        }

        // 显示物理变化（每20tick一次）
        if (!player.level().isClientSide && player.tickCount % 20 == 0) {
            String physicsMsg = String.format("§e[CFI-PHYSICS] 动量变化 Y:%.3f->%.3f 接地:%s->%s",
                    oldMotion.y, motion.y, player.onGround(), player.verticalCollision);
            player.displayClientMessage(Component.literal(physicsMsg), false);
        }

        player.setDeltaMovement(motion);
    }

    /**
     * 完全拦截jumpFromGround，防止原版和Create的干扰
     */
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void cfi_interceptJumpFromGround(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (cfi_terrestrialModeActive) {
            // 完全取消原版跳跃，我们用自己的系统处理
            ci.cancel();

            // 如果这是一个超级跳跃的触发，在handleJumpLogic中会处理
            // 这里只是阻止原版逻辑
        }
    }

    @Unique
    private void cfi_handleUnderwaterSprint(Player player) {
        // 使用zza (向前输入) 而不是isInWater()来检测
        LivingEntity livingPlayer = (LivingEntity) player;
        float forwardInput = livingPlayer.zza;
        boolean isSprintKeyDown = forwardInput > 0.8F;

        if (isSprintKeyDown && !cfi_wasSprintKeyDown) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.literal("§b[CFI-SPRINT] 疾跑键刚按下"), false);
            }

            if (cfi_sprintToggleTimer > 0 && !cfi_underwaterSprintActive) {
                // 双击W，开始疾跑
                int minHunger = CreateFisheryCommonConfig.getDivingMinHungerLevel();
                boolean hasEnoughHunger = player.getFoodData().getFoodLevel() > minHunger || player.getAbilities().mayfly;

                if (!player.level().isClientSide) {
                    String hungerMsg = String.format("§b[CFI-SPRINT] 双击检测到！饥饿度:%d>%d=%s",
                            player.getFoodData().getFoodLevel(), minHunger, hasEnoughHunger);
                    player.displayClientMessage(Component.literal(hungerMsg), false);
                }

                if (hasEnoughHunger) {
                    cfi_underwaterSprintActive = true;
                    cfi_sprintToggleTimer = 0;

                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.DOLPHIN_SWIM, SoundSource.PLAYERS, 1.0F, 1.5F);

                    if (!player.level().isClientSide) {
                        player.displayClientMessage(Component.literal("§a[CFI-SPRINT] 水下疾跑激活成功！"), false);
                    }
                } else {
                    if (!player.level().isClientSide) {
                        player.displayClientMessage(Component.literal("§c[CFI-SPRINT] 饥饿度不足，无法激活疾跑"), false);
                    }
                }
            } else {
                cfi_sprintToggleTimer = CreateFisheryCommonConfig.getDivingSprintDoubleClickWindow();
                if (!player.level().isClientSide) {
                    String windowMsg = String.format("§b[CFI-SPRINT] 开始双击窗口，时间:%d", cfi_sprintToggleTimer);
                    player.displayClientMessage(Component.literal(windowMsg), false);
                }
            }
        }

        cfi_wasSprintKeyDown = isSprintKeyDown;
        if (cfi_sprintToggleTimer > 0) cfi_sprintToggleTimer--;

        // 维持疾跑状态
        if (cfi_underwaterSprintActive) {
            int minHunger = CreateFisheryCommonConfig.getDivingMinHungerLevel();
            boolean hasEnoughHunger = player.getFoodData().getFoodLevel() > minHunger || player.getAbilities().mayfly;

            if (!hasEnoughHunger) {
                cfi_underwaterSprintActive = false;
                if (!player.level().isClientSide) {
                    player.displayClientMessage(Component.literal("§c[CFI-SPRINT] 饥饿度不足，疾跑停止"), false);
                }
            } else if (player.horizontalCollision) {
                cfi_underwaterSprintActive = false;
                if (!player.level().isClientSide) {
                    player.displayClientMessage(Component.literal("§c[CFI-SPRINT] 水平碰撞，疾跑停止"), false);
                }
            } else if (forwardInput <= 0) {
                cfi_underwaterSprintActive = false;
                if (!player.level().isClientSide) {
                    player.displayClientMessage(Component.literal("§c[CFI-SPRINT] 停止前进，疾跑停止"), false);
                }
            } else {
                // 疾跑维持中
                player.setSprinting(true);
                cfi_applySprintBoost(player);

                // 每秒显示一次疾跑维持信息
                if (player.tickCount % 20 == 0 && !player.level().isClientSide) {
                    String maintainMsg = String.format("§a[CFI-SPRINT] 疾跑维持中 前进:%.2f 碰撞:%s",
                            forwardInput, player.horizontalCollision);
                    player.displayClientMessage(Component.literal(maintainMsg), false);
                }
            }
        }
    }

    @Unique
    private void cfi_applySprintBoost(Player player) {
        double speedMultiplier = CreateFisheryCommonConfig.getDivingCopperSprintSpeed();
        if (player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem) {
            speedMultiplier = CreateFisheryCommonConfig.getDivingNetheriteSprintSpeed();
        }

        Vec3 oldMotion = player.getDeltaMovement();
        LivingEntity livingPlayer = (LivingEntity) player;
        float forwardInput = livingPlayer.zza;
        float sideInput = livingPlayer.xxa;

        if (Math.abs(forwardInput) > 0.1 || Math.abs(sideInput) > 0.1) {
            float yaw = player.getYRot() * 0.017453292F;
            float pitch = player.getXRot() * 0.017453292F;

            // 3D移动计算，包括垂直方向
            double moveX = -Math.sin(yaw) * Math.cos(pitch) * speedMultiplier * forwardInput;
            double moveY = -Math.sin(pitch) * speedMultiplier * forwardInput * 0.4; // 增强垂直移动
            double moveZ = Math.cos(yaw) * Math.cos(pitch) * speedMultiplier * forwardInput;

            // 侧向移动
            moveX += Math.cos(yaw) * speedMultiplier * sideInput;
            moveZ += Math.sin(yaw) * speedMultiplier * sideInput;

            Vec3 newMotion = oldMotion.add(moveX, moveY, moveZ);
            player.setDeltaMovement(newMotion);

            // 每10tick显示一次疾跑加速信息
            if (player.tickCount % 10 == 0 && !player.level().isClientSide) {
                String boostMsg = String.format("§a[CFI-SPRINT] 加速应用 倍数:%.1f 增量X:%.3f Y:%.3f Z:%.3f",
                        speedMultiplier, moveX, moveY, moveZ);
                player.displayClientMessage(Component.literal(boostMsg), false);
            }

            if (!player.getAbilities().mayfly && player.tickCount % 20 == 0) {
                float hungerCost = (float) CreateFisheryCommonConfig.getDivingSprintHungerCost();
                player.causeFoodExhaustion(hungerCost);

                if (!player.level().isClientSide) {
                    String costMsg = String.format("§a[CFI-SPRINT] 饥饿度消耗:%.1f", hungerCost);
                    player.displayClientMessage(Component.literal(costMsg), false);
                }
            }
        }
    }

    @Unique
    private void cfi_displayDebugInfo(Player player) {
        if (!player.level().isClientSide) return;

        if (cfi_terrestrialModeActive) {
            // 高频调试信息 - 每tick更新
            LivingEntityAccessor accessor = (LivingEntityAccessor) player;
            boolean jumpKeyDown = accessor.getJumping();
            Vec3 motion = player.getDeltaMovement();

            // 超级跳跃条件检查
            boolean jumpKeyJustPressed = jumpKeyDown && !cfi_lastJumpKeyState;
            boolean jumpKeyJustReleased = !jumpKeyDown && cfi_lastJumpKeyState;
            boolean inReleaseWindow = cfi_jumpKeyReleaseTimer > 0;
            boolean notExecuted = !cfi_superJumpExecuted;
            boolean noCooldown = cfi_jumpCooldown == 0;

            // 疾跑条件检查
            LivingEntity livingPlayer = (LivingEntity) player;
            float forwardInput = livingPlayer.zza;
            float sideInput = livingPlayer.xxa;
            boolean sprintKeyDown = forwardInput > 0.8F;
            boolean sprintKeyJustPressed = sprintKeyDown && !cfi_wasSprintKeyDown;
            boolean inSprintWindow = cfi_sprintToggleTimer > 0;
            int minHunger = CreateFisheryCommonConfig.getDivingMinHungerLevel();
            boolean hasEnoughHunger = player.getFoodData().getFoodLevel() > minHunger || player.getAbilities().mayfly;

            // 物理状态
            boolean isInAnyFluid = cfi_isInAnyFluid(player);
            boolean onGround = player.onGround();
            boolean verticalCollision = player.verticalCollision;

            // 第一行：基础状态
            String line1 = String.format("§a[CFI-1] 陆地模式:%s 流体:%s 接地:%s 垂直碰撞:%s",
                    cfi_terrestrialModeActive, isInAnyFluid, onGround, verticalCollision);
            player.displayClientMessage(Component.literal(line1), true);

            // 第二行：跳跃系统详情
            String line2 = String.format("§b[CFI-2] 跳跃键:%s 刚按:%s 刚放:%s 窗口:%d 未执行:%s 无冷却:%s",
                    jumpKeyDown, jumpKeyJustPressed, jumpKeyJustReleased, cfi_jumpKeyReleaseTimer, notExecuted, noCooldown);
            player.displayClientMessage(Component.literal(line2), false);

            // 第三行：超级跳跃状态
            String line3 = String.format("§e[CFI-3] 超跳已执行:%s 开始tick:%d 当前tick:%d 冷却:%d",
                    cfi_superJumpExecuted, cfi_jumpStartTick, player.tickCount, cfi_jumpCooldown);
            player.displayClientMessage(Component.literal(line3), false);

            // 第四行：疾跑系统详情
            String line4 = String.format("§c[CFI-4] 前进:%.2f 疾跑键:%s 刚按:%s 窗口:%d 饥饿:%s 疾跑:%s",
                    forwardInput, sprintKeyDown, sprintKeyJustPressed, cfi_sprintToggleTimer, hasEnoughHunger, cfi_underwaterSprintActive);
            player.displayClientMessage(Component.literal(line4), false);

            // 第五行：物理动量
            String line5 = String.format("§d[CFI-5] 动量 X:%.3f Y:%.3f Z:%.3f 输入 前:%.2f 侧:%.2f",
                    motion.x, motion.y, motion.z, forwardInput, sideInput);
            player.displayClientMessage(Component.literal(line5), false);

            // 第六行：配置值
            double baseJump = CreateFisheryCommonConfig.getDivingBaseJumpPower();
            double sprintJump = CreateFisheryCommonConfig.getDivingSprintJumpPower();
            String line6 = String.format("§f[CFI-6] 配置 基础跳跃:%.1f 疾跑跳跃:%.1f 冷却时间:%d",
                    baseJump, sprintJump, CreateFisheryCommonConfig.getDivingJumpCooldown());
            player.displayClientMessage(Component.literal(line6), false);

        } else if (cfi_debugMessageCooldown == 0) {
            // 非陆地化模式的低频信息
            boolean hasGear = cfi_hasCompleteDivingGear(player);
            boolean inFluid = cfi_isInAnyFluid(player);

            String message = String.format("§7[CFI] 未激活 - 装备:%s 流体:%s", hasGear, inFluid);
            player.displayClientMessage(Component.literal(message), true);
            cfi_debugMessageCooldown = 60; // 3秒显示一次
        }
    }

    /**
     * 摔落保护 - 使用更早的注入点和更全面的检查
     */
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void cfi_preventSuperJumpFallDamage(float fallDistance, float multiplier,
                                                net.minecraft.world.damagesource.DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;

        // 检查所有相关标记
        boolean hasSuperJumpTag = player.getPersistentData().getBoolean("CFI_SuperJump");
        boolean shouldPrevent = CreateFisheryCommonConfig.shouldPreventDivingFallDamage();
        boolean hasCompleteDivingGear = cfi_hasCompleteDivingGear(player);

        // 强制显示摔落保护检查信息（客户端和服务端都显示）
        String damageMsg = String.format("§c[CFI-DAMAGE] 摔落检查 距离:%.1f 超跳标记:%s 配置防护:%s 装备:%s",
                fallDistance, hasSuperJumpTag, shouldPrevent, hasCompleteDivingGear);
        player.displayClientMessage(Component.literal(damageMsg), false);

        CreateFisheryMod.LOGGER.warn("Fall damage check - Player: {}, Distance: {}, SuperJump: {}, Config: {}, Gear: {}",
                player.getName().getString(), fallDistance, hasSuperJumpTag, shouldPrevent, hasCompleteDivingGear);

        // 更宽松的保护条件：只要有装备就保护
        if ((hasSuperJumpTag || hasCompleteDivingGear) && shouldPrevent) {
            // 取消摔落伤害
            cir.setReturnValue(false);

            // 获取跳跃信息（如果存在）
            float jumpStartY = player.getPersistentData().getFloat("CFI_JumpStartY");
            long jumpTime = player.getPersistentData().getLong("CFI_JumpTime");
            long currentTime = player.level().getGameTime();

            // 清除保护标记
            player.getPersistentData().remove("CFI_SuperJump");
            player.getPersistentData().remove("CFI_JumpStartY");
            player.getPersistentData().remove("CFI_JumpTime");

            String protectionMsg;
            if (jumpStartY > 0) {
                float actualFallDistance = jumpStartY - (float)player.getY();
                protectionMsg = String.format("§a[CFI-DAMAGE] 摔落保护生效！开始Y:%.1f 当前Y:%.1f 实际下降:%.1f 持续:%d ticks",
                        jumpStartY, player.getY(), actualFallDistance, currentTime - jumpTime);
            } else {
                protectionMsg = String.format("§a[CFI-DAMAGE] 摔落保护生效！(装备保护) 摔落距离:%.1f", fallDistance);
            }

            player.displayClientMessage(Component.literal(protectionMsg), false);

            CreateFisheryMod.LOGGER.info("Fall damage prevented for player: {} - Fall distance: {}, Reason: {}",
                    player.getName().getString(), fallDistance, hasSuperJumpTag ? "SuperJump" : "Gear");

            return; // 确保方法结束
        }

        // 如果不保护，显示原因
        String noProtectionMsg = String.format("§c[CFI-DAMAGE] 摔落保护未激活 - 原因: 超跳标记:%s 配置:%s 装备:%s",
                hasSuperJumpTag, shouldPrevent, hasCompleteDivingGear);
        player.displayClientMessage(Component.literal(noProtectionMsg), false);

        CreateFisheryMod.LOGGER.warn("Fall damage NOT prevented - Player: {}, SuperJump: {}, Config: {}, Gear: {}",
                player.getName().getString(), hasSuperJumpTag, shouldPrevent, hasCompleteDivingGear);
    }
}