package mcmod.tinkerscrossbow.modifier;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import mcmod.tinkerscrossbow.stats.CrossbowStats;

/**
 * 石缚 (rock receiver): the fire rate rises as durability falls, up to {@link #BONUS} per level when nearly broken. The
 * crossbow applies it with its fire rate, like the Tinkers stonebound does for mining speed.
 */
public final class StoneboundModifier extends Modifier implements TooltipModifierHook {
    /** Fire rate bonus per level at no durability left. */
    public static final float BONUS = 0.1f;

    @Override protected void registerHooks(Builder hookBuilder) {
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    public static float bonus(IToolStackView tool, float level) {
        int max = tool.getStats().getInt(ToolStats.DURABILITY);
        if (level <= 0 || max <= 0) return 0;
        return BONUS * level * (1 - Math.min(max, tool.getCurrentDurability()) / (float) max);
    }
    /** Fire rate bonus of a tool from its stonebound level. */
    public static float bonus(IToolStackView tool) {
        return bonus(tool, tool.getModifier(TinkersCrossbow.STONEBOUND.getId()).getEffectiveLevel());
    }

    @Override public void addTooltip(IToolStackView tool, ModifierEntry modifier, @Nullable Player player, List<Component> tooltip,
        TooltipKey key, TooltipFlag flag) {
        TooltipModifierHook.addPercentBoost(this, TooltipModifierHook.statName(this, CrossbowStats.FIRE_RATE),
            bonus(tool, modifier.getEffectiveLevel()), tooltip);
    }
}
