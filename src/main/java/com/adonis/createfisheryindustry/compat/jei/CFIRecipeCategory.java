package com.adonis.createfisheryindustry.compat.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Base class for Create Fishery Industry JEI recipe categories.
 * Extends CreateRecipeCategory to leverage Create's JEI integration utilities.
 */
public abstract class CFIRecipeCategory<T extends Recipe<?>> extends CreateRecipeCategory<T> {

    public CFIRecipeCategory(Info<T> info) {
        super(info);
    }
}