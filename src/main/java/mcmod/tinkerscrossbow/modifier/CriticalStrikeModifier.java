package mcmod.tinkerscrossbow.modifier;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import mcmod.tinkerscrossbow.TinkersCrossbow;

/**
 * 致命一击 (copper receiver): every arrow loaded adds {@link #BONUS} damage per level to the next shot. The crossbow counts
 * the loaded arrows in the tool data and spends them on the next shot, which plays a critical hit sound.
 */
public final class CriticalStrikeModifier extends Modifier implements TooltipModifierHook {
    /** Damage bonus per loaded arrow and per level. */
    public static final float BONUS = 0.1f;
    /** Arrows loaded since the last shot. */
    public static final ResourceLocation CHARGE = TinkersCrossbow.id("critical_charge");

    @Override protected void registerHooks(Builder hookBuilder) {
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    public static float bonus(int charge, float level) { return BONUS * level * charge; }
    /** Counts an arrow loaded into a crossbow with this modifier. */
    public static void charge(IToolStackView tool) {
        if (tool.getModifierLevel(TinkersCrossbow.CRITICAL_STRIKE.getId()) <= 0) return;
        ModDataNBT data = tool.getPersistentData();
        data.putInt(CHARGE, data.getInt(CHARGE) + 1);
    }
    /** Damage bonus of the shot being fired; the count starts over. */
    public static float consume(IToolStackView tool) {
        ModDataNBT data = tool.getPersistentData();
        int charge = data.getInt(CHARGE);
        data.remove(CHARGE);
        return bonus(charge, tool.getModifier(TinkersCrossbow.CRITICAL_STRIKE.getId()).getEffectiveLevel());
    }

    @Override public void addTooltip(IToolStackView tool, ModifierEntry modifier, @Nullable Player player, List<Component> tooltip,
        TooltipKey key, TooltipFlag flag) {
        TooltipModifierHook.addPercentBoost(this, TooltipModifierHook.statName(this, ToolStats.PROJECTILE_DAMAGE),
            bonus(tool.getPersistentData().getInt(CHARGE), modifier.getEffectiveLevel()), tooltip);
    }
}
