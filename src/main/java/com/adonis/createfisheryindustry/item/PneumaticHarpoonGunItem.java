package com.adonis.createfisheryindustry.item;

import com.adonis.createfisheryindustry.client.renderer.PneumaticHarpoonGunItemRenderer;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.adonis.createfisheryindustry.procedures.PneumaticHarpoonGunItemInHandTickProcedure;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.CustomArmPoseItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class PneumaticHarpoonGunItem extends Item implements CustomArmPoseItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(PneumaticHarpoonGunItem.class);
    private static final int LAUNCH_AIR_CONSUMPTION = 5;
    private static final int COOLDOWN_TICKS = 10;
    private static final double MAX_RANGE = 50.0; // 最大射程



    public PneumaticHarpoonGunItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    private static int maxUses() {
        return AllConfigs.server().equipment.maxExtendoGripActions.get();
    }

    /**
     * 判断实体是否为有效目标
     */
    private boolean isValidTarget(Entity entity) {
        if (entity.isSpectator()) {
            return false;
        }
        if (entity instanceof LivingEntity) {
            return true;
        }
        if (entity instanceof ItemEntity) {
            return true;
        }
        if (entity instanceof ExperienceOrb) {
            return true;
        }
        return entity.isPickable();
    }

    /**
     * 获取玩家瞄准的目标（实体或方块）
     */
    private HitResult getPlayerTarget(Player player, double range) {
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(range));

        // 使用更宽松的实体检测
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.5D);

        // 手动查找实体，包括物品
        Entity closestEntity = null;
        double closestDistance = range * range;

        for (Entity entity : player.level().getEntities(player, searchBox)) {
            if (!isValidTarget(entity)) {
                continue;
            }

            // 计算射线到实体的距离
            AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> hitPos = entityBox.clip(eyePos, endPos);

            if (hitPos.isPresent()) {
                double distance = eyePos.distanceToSqr(hitPos.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }

        // 检查方块（不包括流体）
        ClipContext clipContext = new ClipContext(
                eyePos,
                endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        );
        BlockHitResult blockHit = player.level().clip(clipContext);

        // 比较距离，返回更近的目标
        if (closestEntity != null) {
            double blockDist = eyePos.distanceToSqr(blockHit.getLocation());
            if (closestDistance < blockDist) {
                return new EntityHitResult(closestEntity);
            }
        }

        return blockHit;
    }



    @Override
    public void inventoryTick(ItemStack itemstack, Level world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(itemstack, world, entity, slot, selected);
        if (entity instanceof Player player && (selected || player.getOffhandItem() == itemstack)) {
            PneumaticHarpoonGunItemInHandTickProcedure.execute(world, player.getX(), player.getY(), player.getZ(), player, itemstack);

            CustomData customData = itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            if (customData.copyTag().contains("CooldownEndTick")) {
                long cooldownEndTick = customData.copyTag().getLong("CooldownEndTick");
                if (world.getGameTime() >= cooldownEndTick) {
                    CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.remove("CooldownEndTick"));
                }
            }


        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!world.isClientSide()) {
            List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
            int totalAir = backtanks.stream().map(BacktankUtil::getAir).reduce(0, Integer::sum);
            CustomData customData = itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            boolean tagHooked = customData.copyTag().getBoolean("tagHooked");

            // 检查冷却
            if (customData.copyTag().contains("CooldownEndTick")) {
                long cooldownEndTick = customData.copyTag().getLong("CooldownEndTick");
                if (world.getGameTime() < cooldownEndTick) {
                    return InteractionResultHolder.pass(itemstack);
                }
            }

            // 检查是否有活跃的鱼叉
            List<TetheredHarpoonEntity> activeHarpoons = world.getEntitiesOfClass(
                    TetheredHarpoonEntity.class,
                    player.getBoundingBox().inflate(100),
                    e -> e.getOwner() == player && !e.isRetrieving()
            );
            if (!activeHarpoons.isEmpty() || tagHooked) {
                activeHarpoons.forEach(TetheredHarpoonEntity::startRetrieving);
                CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                    tag.putBoolean("tagHooked", false);
                    tag.remove("tagHookedEntityId");
                    tag.remove("xPostion");
                    tag.remove("yPostion");
                    tag.remove("zPostion");
                    tag.remove("AccumulatedAirConsumption");
                    tag.putLong("CooldownEndTick", world.getGameTime() + COOLDOWN_TICKS);
                });
                player.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("item.armor.equip_chain")), 1.0F, 1.0F);
                return InteractionResultHolder.sidedSuccess(itemstack, false);
            }

            // 检查气压
            if (totalAir < LAUNCH_AIR_CONSUMPTION || backtanks.isEmpty()) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("Insufficient air to shoot"), true);
                }
                return InteractionResultHolder.fail(itemstack);
            }

            // 获取瞄准目标
            HitResult hitResult = getPlayerTarget(player, MAX_RANGE);

            // 如果玩家蹲下且没有瞄准实体，则不发射
            if (player.isShiftKeyDown() && !(hitResult instanceof EntityHitResult)) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("Must aim at entity while sneaking"), true);
                }
                return InteractionResultHolder.fail(itemstack);
            }

            // 如果没有有效目标，不发射
            if (hitResult.getType() == HitResult.Type.MISS) {
                return InteractionResultHolder.fail(itemstack);
            }

            // 消耗气压
            BacktankUtil.consumeAir(player, backtanks.get(0), LAUNCH_AIR_CONSUMPTION);

            // 创建鱼叉实体（先在玩家位置创建，然后移动到目标位置）
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 hitPos = hitResult.getLocation();
            TetheredHarpoonEntity harpoon = new TetheredHarpoonEntity(world, player, eyePos);

            // 设置鱼叉的朝向（从玩家指向目标）
            Vec3 direction = hitPos.subtract(eyePos).normalize();
            harpoon.setYRot((float)(Math.atan2(direction.x, direction.z) * (180.0 / Math.PI)));
            harpoon.setXRot((float)(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180.0 / Math.PI)));

            // 添加鱼叉到世界（先添加，这样它有了 tickCount）
            world.addFreshEntity(harpoon);

            // 立即移动到目标位置并设置状态
            if (hitResult instanceof EntityHitResult entityHit) {
                Entity target = entityHit.getEntity();

                if (target instanceof LivingEntity living) {
                    // 处理生物实体
                    Vec3 targetPos = living.position().add(0, living.getBbHeight() * 0.5, 0);
                    harpoon.setPos(targetPos);
                    harpoon.setHitEntity(living, harpoon.tickCount);
                    // 造成伤害
                    living.hurt(world.damageSources().trident(harpoon, player), 7.0F);
                    living.setLastHurtByPlayer(player);
                    harpoon.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.trident.hit")), 1.0F, 1.0F);
                } else if (target instanceof ItemEntity || target instanceof ExperienceOrb) {
                    // 处理物品和经验球（直接拉取）
                    Vec3 targetPos = target.position();
                    harpoon.setPos(targetPos);

                    // 给实体添加牵引标记并设置速度向玩家
                    long tetherEndTime = world.getGameTime() + 200; // 10秒牵引时间
                    CompoundTag tag = target.getPersistentData();
                    tag.putBoolean("HarpoonTethered", true);
                    tag.putInt("TetherPlayerId", player.getId());
                    tag.putLong("TetherEndTime", tetherEndTime);

                    // 设置初始速度向玩家
                    Vec3 pullDirection = eyePos.subtract(targetPos).normalize();
                    target.setDeltaMovement(pullDirection.scale(0.8)); // 增加初始速度
                    target.hasImpulse = true;

                    // 如果是物品实体，减少拾取延迟
                    if (target instanceof ItemEntity itemEntity) {
                        itemEntity.setPickUpDelay(10); // 0.5秒后可拾取
                    }

                    // 立即开始收回鱼叉
                    harpoon.startRetrieving();

                    // 更新鱼叉枪状态为"正在拉取物品"
                    CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag2 -> {
                        tag2.putBoolean("tagPullingItem", true);
                        tag2.putInt("tagPulledEntityId", target.getId());
                    });

                    harpoon.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.fishing_bobber.retrieve")), 1.0F, 1.0F);
                } else {
                    // 其他实体类型
                    Vec3 targetPos = target.position();
                    harpoon.setPos(targetPos);
                    harpoon.startRetrieving();
                    harpoon.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.fishing_bobber.retrieve")), 1.0F, 1.0F);
                }
            } else if (hitResult instanceof BlockHitResult blockHit) {
                // 移动鱼叉到方块位置
                harpoon.setPos(hitPos);
                harpoon.setAnchored(blockHit.getBlockPos());
                harpoon.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.trident.hit_ground")), 1.0F, 1.0F);
            }

            CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                tag.putFloat("AccumulatedAirConsumption", 0.0F);
                tag.putBoolean("tagHooked", true);
            });

            player.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.arrow.shoot")), 1.0F, 1.0F);
        }

        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }

    // --- BAR METHODS (保持不变) ---
    @Override
    public boolean isBarVisible(ItemStack stack) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return isBarVisibleClient(stack);
        }
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    private boolean isBarVisibleClient(ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
        if (backtanks.isEmpty()) {
            return false;
        }
        return BacktankUtil.isBarVisible(backtanks.get(0), maxUses());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return getBarWidthClient(stack);
        }
        return 0;
    }

    @OnlyIn(Dist.CLIENT)
    private int getBarWidthClient(ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
        if (backtanks.isEmpty()) {
            return 0;
        }
        return BacktankUtil.getBarWidth(backtanks.get(0), maxUses());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return getBarColorClient(stack);
        }
        return BacktankUtil.getBarColor(stack, maxUses());
    }

    @OnlyIn(Dist.CLIENT)
    private int getBarColorClient(ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return BacktankUtil.getBarColor(stack, maxUses());
        }
        List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
        if (backtanks.isEmpty()) {
            return BacktankUtil.getBarColor(stack, maxUses());
        }
        return BacktankUtil.getBarColor(backtanks.get(0), maxUses());
    }

    @Nullable
    @Override
    public HumanoidModel.ArmPose getArmPose(ItemStack stack, AbstractClientPlayer player, InteractionHand hand) {
        return player.getUsedItemHand() == hand ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.ITEM;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new PneumaticHarpoonGunItemRenderer()));
    }
}