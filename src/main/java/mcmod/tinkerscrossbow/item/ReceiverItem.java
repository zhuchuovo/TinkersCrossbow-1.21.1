package mcmod.tinkerscrossbow.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.client.materials.MaterialTooltipCache;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.context.ToolRebuildContext;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.MaterialIdNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.MaterialItem;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.ItemStackUtil;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import mcmod.tinkerscrossbow.stats.CrossbowStats;
import mcmod.tinkerscrossbow.stats.ReceiverBodyStats;
import mcmod.tinkerscrossbow.stats.ReceiverMagazineStats;

/**
 * 机匣: a body and a magazine joined in the station, carrying both materials into the crossbow. Not a tool: it only
 * stores the two materials, in the Tinkers material list so the tool model can draw them.
 */
public final class ReceiverItem extends Item {
    public static final int BODY = 0, MAGAZINE = 1;
    /**
     * Default data of a receiver without materials. The tool model reads every receiver as a tool stack, which logs a
     * warning for stacks without data. Never edit a receiver's tag in place: replace it, as this one is shared.
     */
    public static final CustomData NO_MATERIALS = data(new ListTag());

    public ReceiverItem(Properties properties) { super(properties.component(net.minecraft.core.component.DataComponents.CUSTOM_DATA, NO_MATERIALS)); }

    private static CustomData data(ListTag materials) {
        CompoundTag tag = new CompoundTag();
        tag.put(ToolStack.TAG_MATERIALS, materials);
        return CustomData.of(tag);
    }
    private static CustomData data(MaterialVariantId body, MaterialVariantId magazine) {
        return data(new MaterialIdNBT(List.of(body, magazine)).serializeToNBT());
    }
    public ItemStack withMaterials(MaterialVariantId body, MaterialVariantId magazine) {
        ItemStack stack = new ItemStack(this);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, data(body, magazine));
        return stack;
    }
    public static MaterialVariantId material(ItemStack stack, int index) {
        List<MaterialVariantId> materials = MaterialIdNBT.from(stack).getMaterials();
        return index < materials.size() ? materials.get(index) : IMaterial.UNKNOWN_ID;
    }
    /** Both materials are known and have the stats of their part. */
    public static boolean isComplete(ItemStack stack) {
        IMaterialRegistry registry = MaterialRegistry.getInstance();
        return valid(registry, material(stack, BODY), ReceiverBodyStats.ID) && valid(registry, material(stack, MAGAZINE), ReceiverMagazineStats.ID);
    }
    private static boolean valid(IMaterialRegistry registry, MaterialVariantId material, MaterialStatsId stats) {
        return !material.equals(IMaterial.UNKNOWN_ID) && registry.getMaterialStats(material.getId(), stats).isPresent();
    }

    /** Receivers from 1.0.2 and earlier were single-material parts: that material becomes the body and the magazine. */
    @Override public void verifyComponentsAfterLoad(ItemStack stack) {
        CompoundTag tag = ItemStackUtil.getTag(stack);
        if (tag == null || tag.contains(ToolStack.TAG_MATERIALS)) return;
        MaterialVariantId old = MaterialItem.getMaterialId(tag);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, old.equals(IMaterial.UNKNOWN_ID) ? NO_MATERIALS : data(old, old));
    }

    @Override public Component getName(ItemStack stack) {
        MaterialVariantId body = material(stack, BODY);
        if (body.equals(IMaterial.UNKNOWN_ID)) return super.getName(stack);
        return Component.translatable(getDescriptionId() + ".format", Component.translatable(MaterialTooltipCache.getKey(body)));
    }
    /**
     * What a receiver adds to the crossbow: its body and magazine stats, combined as the crossbow combines them, and the
     * stat changes of their traits (the rose gold magazine size).
     */
    public static ModifierStatsBuilder combined(ReceiverBodyStats body, ReceiverMagazineStats magazine, ModifierNBT traits) {
        ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
        body.apply(builder, 1);
        magazine.apply(builder, 1);
        var context = new ToolRebuildContext(TinkersCrossbow.REPEATING_CROSSBOW.get(), TinkersCrossbow.DEFINITION, MaterialNBT.EMPTY,
            ModifierNBT.EMPTY, traits, IModDataView.EMPTY);
        for (ModifierEntry trait : traits.getModifiers()) trait.getHook(ModifierHooks.TOOL_STATS).addToolStats(context, trait, builder);
        return builder;
    }
    public static List<Component> totalStats(ReceiverBodyStats body, ReceiverMagazineStats magazine, ModifierNBT traits) {
        ModifierStatsBuilder builder = combined(body, magazine, traits);
        return List.of(ToolStats.DURABILITY.formatValue(builder.getStat(ToolStats.DURABILITY)),
            ToolStats.PROJECTILE_DAMAGE.formatValue(builder.getStat(ToolStats.PROJECTILE_DAMAGE)),
            CrossbowStats.FIRE_RATE.formatValue(builder.getStat(CrossbowStats.FIRE_RATE)),
            // The crossbow rounds its magazine size to whole arrows.
            CrossbowStats.MAGAZINE_SIZE.formatValue((float) Math.round(builder.getStat(CrossbowStats.MAGAZINE_SIZE))),
            CrossbowStats.RELOAD_SPEED.formatValue(builder.getStat(CrossbowStats.RELOAD_SPEED)));
    }
    /** Traits of both materials, levels added up like on the crossbow. */
    public static ModifierNBT traits(MaterialVariantId body, MaterialVariantId magazine) {
        IMaterialRegistry registry = MaterialRegistry.getInstance();
        ModifierNBT.Builder builder = ModifierNBT.builder();
        registry.getTraits(body.getId(), ReceiverBodyStats.ID).forEach(builder::add);
        registry.getTraits(magazine.getId(), ReceiverMagazineStats.ID).forEach(builder::add);
        return builder.build();
    }

    // Like the Tinkers tools: materials and traits, shift for the combined stats, control for the stats of each part.
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        MaterialVariantId body = material(stack, BODY), magazine = material(stack, MAGAZINE);
        part(lines, "body", body, flag);
        part(lines, "magazine", magazine, flag);
        if (!isComplete(stack)) return;
        IMaterialRegistry registry = MaterialRegistry.getInstance();
        ReceiverBodyStats bodyStats = registry.<ReceiverBodyStats>getMaterialStats(body.getId(), ReceiverBodyStats.ID).orElseThrow();
        ReceiverMagazineStats magazineStats = registry.<ReceiverMagazineStats>getMaterialStats(magazine.getId(), ReceiverMagazineStats.ID).orElseThrow();
        ModifierNBT traits = traits(body, magazine);
        for (ModifierEntry trait : traits.getModifiers()) lines.add(trait.getDisplayName());
        TooltipKey key = SafeClientAccess.getTooltipKey();
        lines.add(Component.empty());
        if (key == TooltipKey.SHIFT || key == TooltipKey.UNKNOWN) {
            lines.add(Component.translatable("tooltip.tinkers_crossbow.receiver.stats").withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE));
            lines.addAll(totalStats(bodyStats, magazineStats, traits));
        } else if (key == TooltipKey.CONTROL) {
            partStats(lines, TinkersCrossbow.RECEIVER_BODY.get(), body, bodyStats);
            lines.add(Component.empty());
            partStats(lines, TinkersCrossbow.RECEIVER_MAGAZINE.get(), magazine, magazineStats);
        } else {
            lines.add(TooltipUtil.TOOLTIP_HOLD_SHIFT);
            lines.add(TooltipUtil.TOOLTIP_HOLD_CTRL);
        }
    }
    private static void partStats(List<Component> lines, ToolPartItem part, MaterialVariantId material, IMaterialStats stats) {
        lines.add(part.withMaterial(material).getHoverName().copy().withStyle(ChatFormatting.UNDERLINE)
            .withStyle(style -> style.withColor(MaterialTooltipCache.getColor(material))));
        lines.addAll(stats.getLocalizedInfo());
    }
    private static void part(List<Component> lines, String part, MaterialVariantId material, TooltipFlag flag) {
        if (material.equals(IMaterial.UNKNOWN_ID)) return;
        lines.add(Component.translatable("tooltip.tinkers_crossbow.receiver." + part).withStyle(ChatFormatting.GRAY)
            .append(MaterialTooltipCache.getDisplayName(material).copy().withStyle(style -> style.withColor(MaterialTooltipCache.getColor(material)))));
        if (flag.isAdvanced()) lines.add(Component.literal(material.toString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
