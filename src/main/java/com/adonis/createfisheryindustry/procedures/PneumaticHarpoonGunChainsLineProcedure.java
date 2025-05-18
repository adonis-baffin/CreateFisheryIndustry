package com.adonis.createfisheryindustry.procedures;

import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.math.Axis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT) // <--- 这是添加的注解
public class PneumaticHarpoonGunChainsLineProcedure {
    private static final Logger LOGGER = LoggerFactory.getLogger(PneumaticHarpoonGunChainsLineProcedure.class);

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PneumaticHarpoonGunChainsLineProcedure::onRenderLevel);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }

        for (Player player : level.players()) {
            // 检查玩家是否持有鱼叉枪
            ItemStack stack = player.getMainHandItem().getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get() ? player.getMainHandItem() : player.getOffhandItem();
            if (stack.isEmpty() || stack.getItem() != CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
                continue;
            }

            // 检查背罐
            List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
            if (backtanks.isEmpty()) {
                continue;
            }

            // 获取活跃的鱼叉实体（未被收回）
            List<TetheredHarpoonEntity> hooks = level.getEntitiesOfClass(TetheredHarpoonEntity.class,
                    player.getBoundingBox().inflate(100),
                    hook -> hook.getOwner() == player && !hook.isRetrieving());
            if (hooks.isEmpty()) {
                continue;
            }

            TetheredHarpoonEntity hook = hooks.get(0);

            // 使用鱼叉实体的实际位置作为目标点
            double targetX = hook.getX();
            double targetY = hook.getY();
            double targetZ = hook.getZ();

            // 计算起点（玩家手部）
            double startX = player.getX();
            double startY = player.getEyeY() - 0.2; // 略低于眼睛，模拟枪口
            double startZ = player.getZ();

            // 计算距离和链条数量
            double distance = Math.sqrt(Math.pow(startX - targetX, 2.0) + Math.pow(startY - targetY, 2.0) + Math.pow(startZ - targetZ, 2.0));
            int chainCount = (int) Math.ceil(distance * 4.0); // 每0.25格一个链条

            MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
            PoseStack poseStack = event.getPoseStack();
            ItemRenderer itemRenderer = minecraft.getItemRenderer();
            ItemStack chains = new ItemStack(CreateFisheryItems.CHAINS.get());

            poseStack.pushPose();
            // 转换为世界坐标（相对摄像机）
            double camX = minecraft.gameRenderer.getMainCamera().getPosition().x;
            double camY = minecraft.gameRenderer.getMainCamera().getPosition().y;
            double camZ = minecraft.gameRenderer.getMainCamera().getPosition().z;
            poseStack.translate(-camX, -camY, -camZ);

            if (chainCount > 1) { // 避免除以零，并且至少需要两个点来形成线段
                for (int i = 0; i < chainCount; i++) {
                    float t = (float) i / (chainCount - 1);
                    float x = (float) Mth.lerp(t, startX, targetX);
                    float y = (float) Mth.lerp(t, startY, targetY);
                    float z = (float) Mth.lerp(t, startZ, targetZ);

                    // 计算光照
                    BlockPos pos = BlockPos.containing(x, y, z);
                    int light = LevelRenderer.getLightColor(level, pos);

                    poseStack.pushPose();
                    poseStack.translate(x, y, z);
                    // 旋转链条使其朝向连线方向
                    Vec3 direction = new Vec3(targetX - startX, targetY - startY, targetZ - startZ).normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
                    float pitch = (float) Math.toDegrees(Math.asin(-direction.y));
                    poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                    poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                    poseStack.scale(0.5f, 0.5f, 0.5f); // 缩小链条模型
                    itemRenderer.renderStatic(chains, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack, buffer, level, 0);
                    poseStack.popPose();
                }
            }

            poseStack.popPose();
            buffer.endBatch(); // 确保在循环外调用，或者如果每个玩家独立渲染，则在玩家循环外
        }

    }
}