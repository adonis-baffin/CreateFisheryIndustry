package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * 处理鱼叉枪瞄准实体时的高亮显示
 * 使用类似动力臂的渲染方式
 */
@OnlyIn(Dist.CLIENT)
public class PneumaticHarpoonTargetHandler {

    private static final double MAX_RANGE = 50.0;
    private static Entity currentTargetEntity = null;
    private static final int HIGHLIGHT_COLOR = 0xFFFFFFFF; // 白色，ARGB格式
    private static final int OUTLINE_COLOR = 0xFFFFFF; // 白色边框

    /**
     * 初始化处理器，注册事件
     */
    public static void init() {
        NeoForge.EVENT_BUS.register(PneumaticHarpoonTargetHandler.class);
    }

    /**
     * 每tick更新瞄准的实体
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || mc.level == null) {
            currentTargetEntity = null;
            return;
        }

        // 检查玩家是否手持鱼叉枪
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean holdingHarpoonGun = mainHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()
                || offHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get();

        if (!holdingHarpoonGun) {
            currentTargetEntity = null;
            return;
        }

        // 更新瞄准的实体
        HitResult hit = getPlayerTarget(player, MAX_RANGE);
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            // 接受任何可瞄准的实体：生物、物品、经验球等
            if (entity != null && isValidTarget(entity)) {
                currentTargetEntity = entity;
            } else {
                currentTargetEntity = null;
            }
        } else {
            currentTargetEntity = null;
        }

        // 如果有瞄准的实体，使用 Outliner 显示边框（这只在空气中有效）
        if (currentTargetEntity != null && !player.isUnderWater()) {
            AABB bb = currentTargetEntity.getBoundingBox();
            Outliner.getInstance()
                    .showAABB(currentTargetEntity, bb)
                    .colored(HIGHLIGHT_COLOR)
                    .lineWidth(1 / 16f);
        }
    }

    /**
     * 渲染高亮效果（用于水下和 Outliner 不工作的情况）
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // 如果没有目标或在空气中（Outliner已处理），跳过
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (currentTargetEntity == null || player == null) {
            return;
        }

        // 只在水下或 Outliner 无法工作时渲染
        if (!player.isUnderWater() && Outliner.getInstance() != null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // 获取实体边界框
        AABB bb = currentTargetEntity.getBoundingBox();

        // 渲染边框线条
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                bb.minX, bb.minY, bb.minZ,
                bb.maxX, bb.maxY, bb.maxZ,
                1.0f, 1.0f, 1.0f, 1.0f // 白色
        );

        poseStack.popPose();
        bufferSource.endBatch();
    }

    /**
     * 判断实体是否为有效目标
     */
    private static boolean isValidTarget(Entity entity) {
        // 排除观察者模式的玩家
        if (entity.isSpectator()) {
            return false;
        }

        // 接受生物实体
        if (entity instanceof LivingEntity) {
            return true;
        }

        // 接受物品实体
        if (entity instanceof ItemEntity) {
            return true;
        }

        // 接受经验球
        if (entity instanceof ExperienceOrb) {
            return true;
        }

        // 其他可拾取的实体
        return entity.isPickable();
    }

    /**
     * 获取玩家瞄准的目标（实体或方块）
     * 使用增强的检测方法
     */
    private static HitResult getPlayerTarget(Player player, double range) {
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(range));

        // 使用更宽松的实体检测
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.5D); // 增加检测范围

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
                ClipContext.Fluid.NONE,  // 忽略流体，避免水面拦截
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

    /**
     * 获取当前瞄准的实体（供其他类使用）
     */
    public static Entity getTargetEntity() {
        return currentTargetEntity;
    }

    /**
     * 检查是否瞄准了有效目标
     */
    public static boolean hasValidTarget() {
        return currentTargetEntity != null;
    }
}

