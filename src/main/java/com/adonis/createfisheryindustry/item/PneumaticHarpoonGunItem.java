package com.adonis.createfisheryindustry.item;

import com.adonis.createfisheryindustry.client.renderer.PneumaticHarpoonGunItemRenderer;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.adonis.createfisheryindustry.procedures.PneumaticHarpoonGunItemInHandTickProcedure;
// import com.adonis.createfisheryindustry.registry.CreateFisheryEntityTypes; // Not used directly here
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.CustomArmPoseItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment; // Import for FMLEnvironment
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class PneumaticHarpoonGunItem extends Item implements CustomArmPoseItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(PneumaticHarpoonGunItem.class);
    private static final int LAUNCH_AIR_CONSUMPTION = 5; // 发射消耗
    private static final int COOLDOWN_TICKS = 10; // 0.5秒冷却（10刻）

    public PneumaticHarpoonGunItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    private static int maxUses() {
        return AllConfigs.server().equipment.maxExtendoGripActions.get();
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

            if (customData.copyTag().contains("CooldownEndTick")) {
                long cooldownEndTick = customData.copyTag().getLong("CooldownEndTick");
                if (world.getGameTime() < cooldownEndTick) {
                    return InteractionResultHolder.pass(itemstack);
                }
            }

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

            if (totalAir < LAUNCH_AIR_CONSUMPTION || backtanks.isEmpty()) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("Insufficient air to shoot"), true);
                }
                return InteractionResultHolder.fail(itemstack);
            }

            BacktankUtil.consumeAir(player, backtanks.get(0), LAUNCH_AIR_CONSUMPTION);
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 lookVec = player.getViewVector(1.0F);
            TetheredHarpoonEntity harpoon = new TetheredHarpoonEntity(world, player, eyePos);
            harpoon.shoot(lookVec, 2.0F);
            world.addFreshEntity(harpoon);

            CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                tag.putFloat("AccumulatedAirConsumption", 0.0F);
                tag.putBoolean("tagHooked", true);
            });

            player.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.arrow.shoot")), 1.0F, 1.0F);
        }

        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }

    // --- BAR METHODS ---
    @Override
    public boolean isBarVisible(ItemStack stack) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return isBarVisibleClient(stack);
        }
        return false; // Default server value
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
        return 0; // Default server value
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
        // Fallback for server or if client player is null - uses the stack itself for color, which might be a reasonable default
        return BacktankUtil.getBarColor(stack, maxUses());
    }

    @OnlyIn(Dist.CLIENT)
    private int getBarColorClient(ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return BacktankUtil.getBarColor(stack, maxUses()); // Fallback
        }
        List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
        if (backtanks.isEmpty()) {
            return BacktankUtil.getBarColor(stack, maxUses()); // Fallback
        }
        return BacktankUtil.getBarColor(backtanks.get(0), maxUses());
    }
    // --- END BAR METHODS ---

    @Nullable
    @Override
    public HumanoidModel.ArmPose getArmPose(ItemStack stack, AbstractClientPlayer player, InteractionHand hand) {
        // This method is part of CustomArmPoseItem and is expected to only be called on the client.
        // The AbstractClientPlayer parameter itself signals this.
        return player.getUsedItemHand() == hand ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.ITEM;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new PneumaticHarpoonGunItemRenderer()));
    }
}