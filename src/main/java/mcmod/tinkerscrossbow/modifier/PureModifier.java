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
import mcmod.tinkerscrossbow.TinkersCrossbow;

/**
 * 纯粹 (amethyst bronze receiver): every shot fires one extra arrow per level, dealing {@link #DAMAGE} of the shot's damage,
 * and every arrow loaded costs {@link #AMMO_COST} arrows. The shot and its extra arrows fly in a tight pattern: two one
 * above the other, three in a triangle, {@link #SPREAD} degrees from the aim. The crossbow loads and fires them; see
 * RepeatingCrossbowItem.
 */
public final class PureModifier extends Modifier implements TooltipModifierHook {
    /** Damage of an extra arrow, relative to the shot. */
    public static final float DAMAGE = 0.5f;
    /** Arrows taken from the inventory for each arrow loaded. */
    public static final int AMMO_COST = 3;
    /**
     * Degrees from the aim to each arrow of the pattern. At 30 blocks the triangle's side arrows land 0.23 blocks from
     * the aim, inside a zombie.
     */
    public static final float SPREAD = 0.5f;

    @Override protected void registerHooks(Builder hookBuilder) {
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    public static boolean has(IToolStackView tool) { return tool.getModifierLevel(TinkersCrossbow.PURE.getId()) > 0; }

    /**
     * Offset of arrow {@code slot} of a pattern of {@code total}, as {yaw, pitch} in degrees (right and up): the arrows sit
     * on a circle around the aim starting at the top, so two are one above the other and three form a triangle.
     */
    public static float[] offset(int slot, int total) {
        if (total <= 1) return new float[]{0, 0};
        double angle = Math.PI / 2 + 2 * Math.PI * slot / total;
        return new float[]{(float)(SPREAD * Math.cos(angle)), (float)(SPREAD * Math.sin(angle))};
    }

    @Override public void addTooltip(IToolStackView tool, ModifierEntry modifier, @Nullable Player player, List<Component> tooltip,
        TooltipKey key, TooltipFlag flag) {
        TooltipModifierHook.addFlatBoost(this, Component.translatable(getTranslationKey() + ".arrows"), modifier.getLevel(), tooltip);
    }
}
