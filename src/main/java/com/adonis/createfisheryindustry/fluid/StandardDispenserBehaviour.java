package com.adonis.createfisheryindustry.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

public class StandardDispenserBehaviour extends DefaultDispenseItemBehavior {
    public static final StandardDispenserBehaviour INSTANCE = new StandardDispenserBehaviour();

    private final DefaultDispenseItemBehavior defaultBehavior = new DefaultDispenseItemBehavior();

    @Override
    public ItemStack execute(BlockSource source, ItemStack stack) {
        DispensibleContainerItem container = (DispensibleContainerItem) stack.getItem();
        BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        Level level = source.level();

        if (container.emptyContents(null, level, pos, null, stack)) {
            container.checkExtraContent(null, level, stack, pos);
            return this.consumeWithRemainder(source, stack, new ItemStack(Items.BUCKET));
        } else {
            return defaultBehavior.dispense(source, stack);
        }
    }
}