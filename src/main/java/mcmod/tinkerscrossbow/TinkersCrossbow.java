package mcmod.tinkerscrossbow;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.util.ModifierDeferredRegister;
import slimeknights.tconstruct.library.modifiers.util.StaticModifier;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.ToolModule;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.part.PartCastItem;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import mcmod.tinkerscrossbow.item.ReceiverItem;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;
import mcmod.tinkerscrossbow.logic.CrossbowEvents;
import mcmod.tinkerscrossbow.modifier.CriticalStrikeModifier;
import mcmod.tinkerscrossbow.modifier.CruelModifier;
import mcmod.tinkerscrossbow.modifier.PureModifier;
import mcmod.tinkerscrossbow.modifier.PursuitModifier;
import mcmod.tinkerscrossbow.modifier.StoneboundModifier;
import mcmod.tinkerscrossbow.network.ReloadPayload;
import mcmod.tinkerscrossbow.recipe.ReceiverAssemblyRecipe;
import mcmod.tinkerscrossbow.recipe.RepeatingCrossbowRecipe;
import mcmod.tinkerscrossbow.stats.CrossbowStats;
import mcmod.tinkerscrossbow.stats.ReceiverBodyStats;
import mcmod.tinkerscrossbow.stats.ReceiverMagazineStats;
import mcmod.tinkerscrossbow.stats.RepeatingPartsModule;

@Mod(TinkersCrossbow.MOD_ID)
public final class TinkersCrossbow {
    public static final String MOD_ID = "tinkers_crossbow";
    public static final List<String> RECEIVER_MATERIALS = List.of("rock", "flint", "bone", "copper", "iron", "rose_gold", "amethyst_bronze", "cobalt", "manyullyn");
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);
    public static final ModifierDeferredRegister MODIFIERS = ModifierDeferredRegister.create(MOD_ID);
    public static final ToolDefinition DEFINITION = ToolDefinition.create(id("repeating_crossbow"));
    /** The receiver (机匣) joins a body (机体) and a magazine (弹舱), the parts made from materials. */
    public static final DeferredItem<ReceiverItem> RECEIVER = ITEMS.register("receiver", () -> new ReceiverItem(new Item.Properties()));
    public static final DeferredItem<ToolPartItem> RECEIVER_BODY = ITEMS.register("receiver_body", () -> new ToolPartItem(new Item.Properties(), ReceiverBodyStats.ID));
    public static final DeferredItem<ToolPartItem> RECEIVER_MAGAZINE = ITEMS.register("receiver_magazine", () -> new ToolPartItem(new Item.Properties(), ReceiverMagazineStats.ID));
    /** Gold, sand and red sand casts, like the Tinkers parts: stone and flint use the part builder, metals are cast. */
    public static final List<DeferredItem<PartCastItem>> BODY_CASTS = casts(RECEIVER_BODY);
    public static final List<DeferredItem<PartCastItem>> MAGAZINE_CASTS = casts(RECEIVER_MAGAZINE);
    // durability(0) makes the stack damageable, like the Tinkers launchers: tooltips and the bar then show durability.
    public static final DeferredItem<RepeatingCrossbowItem> REPEATING_CROSSBOW = ITEMS.register("repeating_crossbow",
        () -> new RepeatingCrossbowItem(new Item.Properties().stacksTo(1).durability(0), DEFINITION));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ReceiverAssemblyRecipe>> RECEIVER_ASSEMBLY =
        SERIALIZERS.register("receiver_assembly", () -> LoadableRecipeSerializer.of(ReceiverAssemblyRecipe.LOADER));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RepeatingCrossbowRecipe>> REPEATING_CROSSBOW_BUILDING =
        SERIALIZERS.register("repeating_crossbow_building", () -> LoadableRecipeSerializer.of(RepeatingCrossbowRecipe.LOADER));
    @SuppressWarnings("deprecation")
    public static final StaticModifier<PureModifier> PURE = MODIFIERS.register("pure", PureModifier::new);
    @SuppressWarnings("deprecation")
    public static final StaticModifier<CriticalStrikeModifier> CRITICAL_STRIKE = MODIFIERS.register("critical_strike", CriticalStrikeModifier::new);
    @SuppressWarnings("deprecation")
    public static final StaticModifier<StoneboundModifier> STONEBOUND = MODIFIERS.register("stonebound", StoneboundModifier::new);
    /** 扩容: +1 magazine size per level, a data pack modifier (tinkering/modifiers/expansion.json). */
    public static final ModifierId EXPANSION = new ModifierId(id("expansion"));
    /** 轻便 (cobalt receiver): +10% fire rate per level, a data pack modifier (tinkering/modifiers/lightweight.json). */
    public static final ModifierId LIGHTWEIGHT = new ModifierId(id("lightweight"));
    @SuppressWarnings("deprecation")
    public static final StaticModifier<PursuitModifier> PURSUIT = MODIFIERS.register("pursuit", PursuitModifier::new);
    @SuppressWarnings("deprecation")
    public static final StaticModifier<CruelModifier> CRUEL = MODIFIERS.register("cruel", CruelModifier::new);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.tinkers_crossbow"))
        .icon(() -> RECEIVER.get().withMaterials(MaterialVariantId.parse("tconstruct:iron"), MaterialVariantId.parse("tconstruct:iron")))
        .displayItems((parameters, output) -> {
            for (String name : RECEIVER_MATERIALS) {
                MaterialVariantId receiver = MaterialVariantId.parse("tconstruct:" + name);
                output.accept(RECEIVER_BODY.get().withMaterialForDisplay(receiver));
                output.accept(RECEIVER_MAGAZINE.get().withMaterialForDisplay(receiver));
                output.accept(RECEIVER.get().withMaterials(receiver, receiver));
                if (DEFINITION.isDataLoaded() && MaterialRegistry.isFullyLoaded()) {
                    output.accept(ToolBuildHandler.buildItemFromMaterials(REPEATING_CROSSBOW.get(), MaterialNBT.of(
                        MaterialVariant.of(MaterialVariantId.parse("tconstruct:wood")),
                        MaterialVariant.of(MaterialVariantId.parse("tconstruct:wood")),
                        MaterialVariant.of(MaterialVariantId.parse("tconstruct:string")),
                        MaterialVariant.of(receiver), MaterialVariant.of(receiver))));
                }
            }
            for (var cast : BODY_CASTS) output.accept(cast.get());
            for (var cast : MAGAZINE_CASTS) output.accept(cast.get());
        }).build());

    private static List<DeferredItem<PartCastItem>> casts(DeferredItem<ToolPartItem> part) {
        String name = part.getId().getPath();
        return java.util.stream.Stream.of("_cast", "_sand_cast", "_red_sand_cast")
            .map(suffix -> ITEMS.register(name + suffix, () -> new PartCastItem(new Item.Properties(), part))).toList();
    }

    public TinkersCrossbow(IEventBus bus) {
        CrossbowStats.init();
        ITEMS.register(bus);
        MODIFIERS.register(bus);
        TABS.register(bus);
        SERIALIZERS.register(bus);
        bus.addListener(this::setup);
        bus.addListener(ReloadPayload::register);
        CrossbowEvents.register();
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MaterialRegistry.getInstance().registerStatType(ReceiverBodyStats.TYPE);
            MaterialRegistry.getInstance().registerStatType(ReceiverMagazineStats.TYPE);
            ToolModule.LOADER.register(id("repeating_parts"), RepeatingPartsModule.LOADER);
        });
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
