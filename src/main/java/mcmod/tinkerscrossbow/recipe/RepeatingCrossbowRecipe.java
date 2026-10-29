package mcmod.tinkerscrossbow.recipe;

import javax.annotation.Nullable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.modifiers.hook.build.ModifierRemovalHook;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.tools.TinkerToolParts;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import mcmod.tinkerscrossbow.item.ReceiverItem;
import mcmod.tinkerscrossbow.stats.RepeatingPartsModule;

/**
 * Anvil recipe for the repeating crossbow: a bow limb, a bow grip, a bowstring and a receiver, in any input slots.
 * The receiver gives the tool its body and magazine materials. With a crossbow in the centre slot, a lone receiver
 * replaces both of them instead.
 */
public record RepeatingCrossbowRecipe(ResourceLocation id) implements ITinkerStationRecipe {
    public static final RecordLoadable<RepeatingCrossbowRecipe> LOADER = RecordLoadable.create(ContextKey.ID.requiredField(), RepeatingCrossbowRecipe::new);
    public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return TinkersCrossbow.REPEATING_CROSSBOW_BUILDING.get(); }

    /** The only input stack of this item, or null if missing or doubled. */
    @Nullable private static ItemStack find(ITinkerStationContainer inv, Item item) {
        ItemStack found = null;
        for (int i = 0; i < inv.getInputCount(); i++) {
            ItemStack stack = inv.getInput(i);
            if (stack.is(item)) {
                if (found != null) return null;
                found = stack;
            }
        }
        return found;
    }
    private static int filled(ITinkerStationContainer inv) {
        int count = 0;
        for (int i = 0; i < inv.getInputCount(); i++) if (!inv.getInput(i).isEmpty()) count++;
        return count;
    }
    private static boolean swapping(ITinkerStationContainer inv) {
        return inv.getTinkerableStack().is(TinkersCrossbow.REPEATING_CROSSBOW.get());
    }

    @Override public boolean matches(ITinkerStationContainer inv, Level level) {
        if (inv.getInputCount() < 4) return false;
        ItemStack receiver = find(inv, TinkersCrossbow.RECEIVER.get());
        if (receiver == null || !ReceiverItem.isComplete(receiver)) return false;
        if (swapping(inv)) return filled(inv) == 1;
        return inv.getTinkerableStack().isEmpty() && filled(inv) == 4 && find(inv, TinkerToolParts.bowLimb.get()) != null
            && find(inv, TinkerToolParts.bowGrip.get()) != null && find(inv, TinkerToolParts.bowstring.get()) != null;
    }
    private static MaterialVariant material(ITinkerStationContainer inv, IToolPart part) {
        return MaterialVariant.of(part.getMaterial(find(inv, part.asItem())));
    }
    @Override public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, RegistryAccess access) {
        ItemStack receiver = find(inv, TinkersCrossbow.RECEIVER.get());
        MaterialVariant body = MaterialVariant.of(ReceiverItem.material(receiver, ReceiverItem.BODY));
        MaterialVariant magazine = MaterialVariant.of(ReceiverItem.material(receiver, ReceiverItem.MAGAZINE));
        if (!swapping(inv)) {
            return LazyToolStack.success(ToolBuildHandler.buildItemFromMaterials(TinkersCrossbow.REPEATING_CROSSBOW.get(), MaterialNBT.of(
                material(inv, TinkerToolParts.bowLimb.get()), material(inv, TinkerToolParts.bowGrip.get()),
                material(inv, TinkerToolParts.bowstring.get()), body, magazine)));
        }
        ToolStack original = inv.getTinkerable();
        MaterialNBT materials = original.getMaterials();
        if (materials.get(RepeatingPartsModule.BODY).sameVariant(body.getVariant())
            && materials.get(RepeatingPartsModule.MAGAZINE).sameVariant(magazine.getVariant())) return RecipeResult.pass();
        ToolStack copy = original.copy();
        copy.setMaterials(materials.replaceMaterial(RepeatingPartsModule.BODY, body).replaceMaterial(RepeatingPartsModule.MAGAZINE, magazine));
        Component error = copy.tryValidate();
        if (error == null) error = ModifierRemovalHook.onRemoved(original, copy);
        return error != null ? RecipeResult.failure(error) : LazyToolStack.successCopy(copy, 1, inv.getTinkerableStack());
    }
    @Override public int shrinkToolSlotBy() { return 1; }
    @Override public void updateInputs(LazyToolStack result, IMutableTinkerStationContainer inv, boolean server) {
        for (int i = 0; i < inv.getInputCount(); i++) if (!inv.getInput(i).isEmpty()) inv.shrinkInput(i, 1);
    }
}
