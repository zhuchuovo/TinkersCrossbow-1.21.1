package mcmod.tinkerscrossbow.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import mcmod.tinkerscrossbow.TinkersCrossbow;

/** Joins one body and one magazine, in any input slots, into a receiver carrying both materials. */
public record ReceiverAssemblyRecipe(ResourceLocation id) implements ITinkerStationRecipe {
    public static final RecordLoadable<ReceiverAssemblyRecipe> LOADER = RecordLoadable.create(ContextKey.ID.requiredField(), ReceiverAssemblyRecipe::new);
    public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return TinkersCrossbow.RECEIVER_ASSEMBLY.get(); }

    /** Material of the only stack of this part among the inputs, unknown if missing or doubled. */
    private static MaterialVariantId find(ITinkerStationContainer inv, IMaterialItem part) {
        MaterialVariantId found = IMaterial.UNKNOWN_ID;
        for (int i = 0; i < inv.getInputCount(); i++) {
            ItemStack stack = inv.getInput(i);
            if (stack.is(part.asItem())) {
                if (!found.equals(IMaterial.UNKNOWN_ID)) return IMaterial.UNKNOWN_ID;
                found = part.getMaterial(stack);
            }
        }
        return found;
    }
    @Override public boolean matches(ITinkerStationContainer inv, Level level) {
        if (!inv.getTinkerableStack().isEmpty()) return false;
        int count = 0;
        for (int i = 0; i < inv.getInputCount(); i++) if (!inv.getInput(i).isEmpty()) count++;
        return count == 2 && !find(inv, TinkersCrossbow.RECEIVER_BODY.get()).equals(IMaterial.UNKNOWN_ID)
            && !find(inv, TinkersCrossbow.RECEIVER_MAGAZINE.get()).equals(IMaterial.UNKNOWN_ID);
    }
    @Override public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, RegistryAccess access) {
        return LazyToolStack.success(TinkersCrossbow.RECEIVER.get().withMaterials(
            find(inv, TinkersCrossbow.RECEIVER_BODY.get()), find(inv, TinkersCrossbow.RECEIVER_MAGAZINE.get())));
    }
    @Override public void updateInputs(LazyToolStack result, IMutableTinkerStationContainer inv, boolean server) {
        for (int i = 0; i < inv.getInputCount(); i++) if (!inv.getInput(i).isEmpty()) inv.shrinkInput(i, 1);
    }
}
