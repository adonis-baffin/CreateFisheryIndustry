package com.adonis.createfisheryindustry.procedures;

import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class PneumaticHarpoonGunChainsLineProcedure {
    private static final Logger LOGGER = LoggerFactory.getLogger(PneumaticHarpoonGunChainsLineProcedure.class);

    private static final ResourceLocation CHAIN_TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/chain.png");

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PneumaticHarpoonGunChainsLineProcedure::onRenderLevel);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        for (Player player : level.players()) {
            ItemStack gun = findHarpoonGun(player);
            if (gun.isEmpty()) {
                continue;
            }

            // 读取鱼叉枪是否处于 hooked 状态
            var customData = gun.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData == null) {
                continue;
            }
            var tag = customData.copyTag();
            if (!tag.getBoolean("tagHooked")) {
                continue;
            }

            Vec3 target = resolveTarget(level, player, tag);
            if (target == null) {
                continue;
            }

            renderChain(mc, event.getPoseStack(), player, target);
        }
    }

    private static ItemStack findHarpoonGun(Player player) {
        if (player.getMainHandItem().getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private static Vec3 resolveTarget(Level level, Player player, net.minecraft.nbt.CompoundTag tag) {
        // 策略1：从 NBT 中的实体 ID 获取（客户端可能已同步）
        if (tag.contains("tagHookedEntityId")) {
            int entityId = tag.getInt("tagHookedEntityId");
            Entity entity = level.getEntity(entityId);
            if (entity != null && entity.isAlive()) {
                return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            }
        }

        // 策略2：从 NBT 中的坐标获取（方块锚定）
        if (tag.contains("xPostion")) {
            return new Vec3(
                    tag.getDouble("xPostion"),
                    tag.getDouble("yPostion"),
                    tag.getDouble("zPostion")
            );
        }

        // 策略3：查找最近的非回收鱼叉实体（不依赖 getOwner() 在客户端有效）
        List<TetheredHarpoonEntity> hooks = level.getEntitiesOfClass(
                TetheredHarpoonEntity.class,
                player.getBoundingBox().inflate(100),
                hook -> !hook.isRetrieving()
        );
        if (hooks.isEmpty()) {
            return null;
        }

        TetheredHarpoonEntity closest = hooks.stream()
                .min(Comparator.comparingDouble(h -> h.distanceToSqr(player)))
                .orElse(null);

        return closest != null ? closest.position() : null;
    }

    private static void renderChain(Minecraft mc, PoseStack poseStack, Player player, Vec3 target) {
        Vec3 start = new Vec3(player.getX(), player.getEyeY() - 0.2, player.getZ());
        double distance = start.distanceTo(target);
        if (distance < 0.01) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = target.subtract(start).normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
        float horizontalDist = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float pitch = (float) Math.toDegrees(Math.atan2(dir.y, horizontalDist));

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90 - pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(45));

        BlockPos startPos = BlockPos.containing(start.x, start.y, start.z);
        BlockPos endPos = BlockPos.containing(target.x, target.y, target.z);
        int light1 = LevelRenderer.getLightColor(mc.level, startPos);
        int light2 = LevelRenderer.getLightColor(mc.level, endPos);

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        RenderType chainRenderType = RenderType.entityCutoutNoCull(CHAIN_TEXTURE);
        VertexConsumer vc = buffer.getBuffer(chainRenderType);

        float length = (float) distance;
        float radius = 1.5f / 16f;
        float minU = 0;
        float maxU = 3 / 16f;
        float minV = 0;
        float maxV = length;

        renderChainPart(poseStack, vc, length, radius, minU, maxU, minV, maxV, light1, light2);

        poseStack.popPose();
        buffer.endBatch(chainRenderType);
    }

    private static void renderChainPart(PoseStack poseStack, VertexConsumer consumer, float length,
                                         float radius, float minU, float maxU, float minV, float maxV,
                                         int light1, int light2) {
        PoseStack.Pose pose = poseStack.last();

        float uOffset = 3 / 16f;
        renderChainQuad(pose, consumer, 0, length, radius, 0, -radius, 0, minU, maxU, minV, maxV, light1, light2);
        renderChainQuad(pose, consumer, 0, length, -radius, 0, radius, 0, minU, maxU, minV, maxV, light1, light2);
        renderChainQuad(pose, consumer, 0, length, 0, radius, 0, -radius, minU + uOffset, maxU + uOffset, minV, maxV, light1, light2);
        renderChainQuad(pose, consumer, 0, length, 0, -radius, 0, radius, minU + uOffset, maxU + uOffset, minV, maxV, light1, light2);
    }

    private static void renderChainQuad(PoseStack.Pose pose, VertexConsumer consumer,
                                         float minY, float maxY, float minX, float minZ, float maxX, float maxZ,
                                         float minU, float maxU, float minV, float maxV, int light1, int light2) {
        addChainVertex(pose, consumer, maxY, minX, minZ, maxU, minV, light2);
        addChainVertex(pose, consumer, minY, minX, minZ, maxU, maxV, light1);
        addChainVertex(pose, consumer, minY, maxX, maxZ, minU, maxV, light1);
        addChainVertex(pose, consumer, maxY, maxX, maxZ, minU, minV, light2);
    }

    private static void addChainVertex(PoseStack.Pose pose, VertexConsumer consumer,
                                        float y, float x, float z, float u, float v, int light) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
