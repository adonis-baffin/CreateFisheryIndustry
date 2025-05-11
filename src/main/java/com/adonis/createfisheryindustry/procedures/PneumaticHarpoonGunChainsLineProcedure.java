//package com.adonis.createfisheryindustry.procedures;
//
//import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
//import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
//import com.mojang.blaze3d.vertex.PoseStack;
//import com.simibubi.create.content.equipment.armor.BacktankUtil;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.renderer.LevelRenderer;
//import net.minecraft.client.renderer.MultiBufferSource;
//import net.minecraft.client.renderer.entity.ItemRenderer;
//import net.minecraft.client.renderer.texture.OverlayTexture;
//import net.minecraft.core.BlockPos;
//import net.minecraft.core.component.DataComponents;
//import net.minecraft.util.Mth;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.item.ItemDisplayContext;
//import net.minecraft.world.item.ItemStack;
//import net.minecraft.world.item.component.CustomData;
//import net.minecraft.world.level.Level;
//import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//import net.neoforged.neoforge.common.NeoForge;
//import com.mojang.math.Axis;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.joml.Vector3f;
//
//import java.util.List;
//
//public class PneumaticHarpoonGunChainsLineProcedure {
//    private static final Logger LOGGER = LogManager.getLogger("CreateFisheryIndustry/PneumaticHarpoonGunChainsLineProcedure");
//
//    public static void register() {
//        NeoForge.EVENT_BUS.addListener(PneumaticHarpoonGunChainsLineProcedure::onRenderLevel);
//    }
//
//    public static void onRenderLevel(RenderLevelStageEvent event) {
//        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
//
//        Minecraft minecraft = Minecraft.getInstance();
//        Level level = minecraft.level;
//        if (level == null) return;
//
//        for (Player player : level.players()) {
//            ItemStack stack = player.getMainHandItem().getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get() ? player.getMainHandItem() : player.getOffhandItem();
//            if (stack.isEmpty() || stack.getItem() != CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) continue;
//
//            List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
//            if (backtanks.isEmpty()) {
//                LOGGER.debug("No backtanks found for player: {}", player.getName().getString());
//                continue;
//            }
//
//            List<TetheredHarpoonEntity> hooks = player.level().getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100))
//                    .stream().filter(hook -> hook.getOwner() == player && (hook.isAnchored() || hook.isRetrieving())).toList();
//            if (hooks.isEmpty()) {
//                LOGGER.debug("No tethered hooks found for player: {}", player.getName().getString());
//                continue;
//            }
//
//            TetheredHarpoonEntity hook = hooks.get(0);
//            LOGGER.info("Rendering chains for player: {}, hook state: anchored={}, retrieving={}", player.getName().getString(), hook.isAnchored(), hook.isRetrieving());
//
//            double targetX, targetY, targetZ;
//            if (hook.isAnchored()) {
//                targetX = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("xPostion") + 0.5;
//                targetY = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("yPostion") + 0.5;
//                targetZ = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("zPostion") + 0.5;
//            } else {
//                float progress = hook.getRetrieveProgress();
//                targetX = Mth.lerp(progress, hook.getX(), player.getX());
//                targetY = Mth.lerp(progress, hook.getY(), player.getEyeY());
//                targetZ = Mth.lerp(progress, hook.getZ(), player.getZ());
//            }
//            LOGGER.info("Chain target position: x={}, y={}, z={}", targetX, targetY, targetZ);
//
//            double distance = Math.sqrt(Math.pow(player.getX() - targetX, 2.0) + Math.pow(player.getEyeY() - targetY, 2.0) + Math.pow(player.getZ() - targetZ, 2.0));
//            MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
//            PoseStack poseStack = event.getPoseStack();
//            ItemRenderer itemRenderer = minecraft.getItemRenderer();
//            ItemStack chains = new ItemStack(CreateFisheryItems.CHAINS.get());
//
//            poseStack.pushPose();
//            // 转换为世界坐标
//            poseStack.translate(-player.getX(), -player.getEyeY(), -player.getZ());
//
//            for (int i = 0; i < (int)(Math.ceil(distance) * 2.0); ++i) {
//                double t = i / (distance * 2.0);
//                float x = (float) Mth.lerp(t, player.getX(), targetX);
//                float y = (float) Mth.lerp(t, player.getEyeY(), targetY);
//                float z = (float) Mth.lerp(t, player.getZ(), targetZ);
//
//                // 计算动态光照
//                BlockPos pos = BlockPos.containing(x, y, z);
//                int light = LevelRenderer.getLightColor(level, pos);
//
//                poseStack.pushPose();
//                poseStack.translate(x, y, z);
//                poseStack.mulPose(Axis.YN.rotationDegrees((float)(Math.atan2(targetX - player.getX(), targetZ - player.getZ()) * -57.29577951308232 + 180.0)));
//                LOGGER.debug("Rendering chain at: x={}, y={}, z={}, light={}", x, y, z, light);
//                itemRenderer.renderStatic(player, chains, ItemDisplayContext.GROUND, false, poseStack, buffer, level, light, OverlayTexture.NO_OVERLAY, 0);
//                poseStack.popPose();
//            }
//
//            poseStack.popPose();
//            buffer.endBatch(); // 提交渲染缓冲区
//            LOGGER.info("Finished rendering chains for player: {}", player.getName().getString());
//        }
//    }
//}