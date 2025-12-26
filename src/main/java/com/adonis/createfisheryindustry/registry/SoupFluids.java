package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.fluid.SoupBucketFluidHandler;
import com.adonis.createfisheryindustry.fluid.SoupLiquidBlock;
import com.adonis.createfisheryindustry.fluid.ThickSoupFluidType;
import com.tterrag.registrate.util.entry.FluidEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

public class SoupFluids {

    public static final FluidEntry<BaseFlowingFluid.Flowing> LOBSTER_SOUP = createSoup(
            "lobster_soup", "Lobster Soup", 0xFFCC4D33);

    public static final FluidEntry<BaseFlowingFluid.Flowing> MUSSEL_SOUP = createSoup(
            "mussel_soup", "Mussel Soup", 0xFFB89C7A);

    public static final FluidEntry<BaseFlowingFluid.Flowing> SEAFOOD_CHOWDER = createSoup(
            "seafood_chowder", "Seafood Chowder", 0xFFF5E6CC);

    private static FluidEntry<BaseFlowingFluid.Flowing> createSoup(String name, String langName, int color) {
        return CreateFisheryMod.REGISTRATE
                .fluid(name, ThickSoupFluidType.create(color))  // 只传 color
                .lang(langName)
                .properties(p -> p
                        .density(1600)
                        .viscosity(3000)
                        .canSwim(true)
                        .canDrown(true)
                        .supportsBoating(true)
                        .fallDistanceModifier(0f)            // 掉进去无摔落伤害
                        .canExtinguish(true)                 // 可以灭火
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY))
                .fluidProperties(p -> p
                        .levelDecreasePerBlock(2)
                        .tickRate(30)
                        .slopeFindDistance(1)
                        .explosionResistance(100f))
                .source(BaseFlowingFluid.Source::new)
                .block(SoupLiquidBlock::new)
                .blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry(), prov.models()
                        .getBuilder(ctx.getName())
                        .texture("particle", CreateFisheryMod.asResource("fluid/" + name + "_still"))))
                .build()
                .bucket()
                .properties(p -> p.stacksTo(1).craftRemainder(Items.BUCKET))
                .lang(langName + " Bucket")
                .build()
                .register();
    }

    public static void register() {
        CreateFisheryMod.LOGGER.info("Registered thick soup fluids (real flowing fluids now)...");
    }
}