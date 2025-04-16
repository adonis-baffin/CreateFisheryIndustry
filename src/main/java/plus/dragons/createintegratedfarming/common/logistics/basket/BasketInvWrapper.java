/*
 * Copyright (C) 2025  DragonsPlus
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package plus.dragons.createintegratedfarming.common.logistics.basket;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import plus.dragons.createintegratedfarming.mixin.accessor.BasketBlockEntityAccessor;
import vectorwing.farmersdelight.common.block.entity.BasketBlockEntity;

public class BasketInvWrapper extends InvWrapper {
    private final BasketBlockEntityAccessor basket;

    public BasketInvWrapper(BasketBlockEntity basket) {
        super(basket);
        this.basket = (BasketBlockEntityAccessor) basket;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (simulate) {
            return super.insertItem(slot, stack, true);
        } else {
            boolean wasEmpty = getInv().isEmpty();
            int originalStackSize = stack.getCount();
            stack = super.insertItem(slot, stack, false);
            if (wasEmpty && originalStackSize > stack.getCount()) {
                if (basket.getTransferCooldown() <= 8) {
                    basket.setTransferCooldown(8);
                }
            }
            return stack;
        }
    }
}
