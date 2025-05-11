//package com.adonis.createfisheryindustry.event;
//
//import com.adonis.createfisheryindustry.CreateFisheryMod;
//import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
//import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
//import net.minecraft.core.BlockPos;
//import net.minecraft.core.component.DataComponents;
//import net.minecraft.world.entity.Entity;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.item.ItemStack;
//import net.minecraft.world.item.component.CustomData;
//import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
//
//@EventBusSubscriber(modid = CreateFisheryMod.ID)
//public class TetheredHarpoonHitHandler {
//    @SubscribeEvent
//    public static void onProjectileImpact(ProjectileImpactEvent event) {
//        if (!(event.getProjectile() instanceof TetheredHarpoonEntity hook)) {
//            System.out.println("ProjectileImpactEvent: Not a TetheredHarpoonEntity, type=" + event.getProjectile().getType());
//            return;
//        }
//
//        if (hook.level().isClientSide) {
//            System.out.println("ProjectileImpactEvent: Ignored on client side");
//            return;
//        }
//
//        System.out.println("ProjectileImpactEvent triggered for harpoon, type=" + event.getRayTraceResult().getType() + ", pos=" + event.getRayTraceResult().getLocation());
//
//        Player owner = (Player) hook.getOwner();
//        if (owner == null) {
//            hook.startRetrieving();
//            System.out.println("Owner null, retrieving");
//            return;
//        }
//
//        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() == owner) {
//            System.out.println("Ignoring hit on owner");
//            return;
//        }
//
//        if (hook.isAnchored() || hook.isRetrieving()) {
//            System.out.println("Harpoon already anchored or retrieving, skipping");
//            return;
//        }
//
//        ItemStack harpoon = owner.getMainHandItem().getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get() ?
//                owner.getMainHandItem() : owner.getOffhandItem();
//
//        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
//            BlockPos pos = blockHit.getBlockPos();
//            hook.setAnchored(pos);
//            CustomData.update(DataComponents.CUSTOM_DATA, harpoon, tag -> {
//                tag.putBoolean("tagHooked", true);
//                tag.putDouble("xPostion", pos.getX() + 0.5);
//                tag.putDouble("yPostion", pos.getY() + 0.5);
//                tag.putDouble("zPostion", pos.getZ() + 0.5);
//            });
//            System.out.println("Harpoon hit block at: " + pos + ", tagHooked set to true");
//        } else if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
//            Entity target = entityHit.getEntity();
//            if (target != owner) {
//                hook.setHitEntity(target, hook.tickCount);
//                CustomData.update(DataComponents.CUSTOM_DATA, harpoon, tag -> {
//                    tag.putBoolean("tagHooked", true);
//                });
//                System.out.println("Harpoon hit entity: " + target.getType() + ", tagHooked set to true");
//            }
//        }
//    }
//}