package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput; // Keep this
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack; // For getResultItem() if needed
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ParametersAreNonnullByDefault
public class PeelingRecipe extends ProcessingRecipe<SingleRecipeInput> implements IAssemblyRecipe {

    protected final PeelingRecipeParams peelingParams;

    public PeelingRecipe(PeelingRecipeParams params) {
        super(CreateFisheryRecipeTypes.PEELING, params);
        this.peelingParams = params;
    }

    // --- New methods to distinguish outputs ---
    public List<ProcessingOutput> getPrimaryResults() {
        if (results.isEmpty()) {
            return Collections.emptyList();
        }
        // Assuming the first result is the primary one that flows out
        return List.of(results.getFirst());
    }

    public List<ProcessingOutput> getSecondaryResults() {
        if (results.size() <= 1) {
            return Collections.emptyList();
        }
        // The rest are secondary and go into the machine's inventory
        return results.stream().skip(1).collect(Collectors.toList());
    }

    /**
     * Gets the primary output item stack if it exists.
     * This is typically what would be "ejected" or flow out.
     */
    public ItemStack getPrimaryOutputStack() {
        List<ProcessingOutput> primary = getPrimaryResults();
        return primary.isEmpty() ? ItemStack.EMPTY : primary.getFirst().getStack();
    }

    /**
     * Gets a list of secondary output item stacks.
     * These are typically what would be stored in the machine.
     */
    public List<ItemStack> getSecondaryOutputStacks() {
        return getSecondaryResults().stream()
                .map(ProcessingOutput::getStack)
                .filter(stack -> !stack.isEmpty())
                .collect(Collectors.toList());
    }
    // --- End of new methods ---


    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        // This should reflect the total number of possible distinct outputs (primary + secondary)
        // If primary is 1 and secondary can be up to 3, then total is 4.
        return 4; // Adjust if you have more potential secondary outputs
    }

    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        if (getIngredients().isEmpty()) return false;
        return getIngredients().getFirst().test(input.item());
    }

    @Override
    public void addAssemblyIngredients(List<Ingredient> list) {
        list.addAll(getIngredients());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public Component getDescriptionForAssembly() {
        return CreateLang.translateDirect("recipe.assembly.peeling");
    }

    @Override
    public void addRequiredMachines(Set<ItemLike> list) {
        list.add(CreateFisheryBlocks.MECHANICAL_PEELER.get());
    }

    @Override
    public Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        // Keeping this as cutting for now, but you might want a custom one
        return () -> SequencedAssemblySubCategory.AssemblyCutting::new;
    }
}