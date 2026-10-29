package mcmod.tinkerscrossbow.logic;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;

public final class CrossbowEvents {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(CrossbowEvents::logout);
        NeoForge.EVENT_BUS.addListener(CrossbowEvents::attack);
        NeoForge.EVENT_BUS.addListener(CrossbowEvents::leftClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CrossbowEvents::incomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CrossbowEvents::extraArrowCooldown);
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (stack.getItem() instanceof RepeatingCrossbowItem) RepeatingCrossbowItem.stopReload(stack);
    }
    private static void attack(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof RepeatingCrossbowItem) event.setCanceled(true);
    }
    private static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof RepeatingCrossbowItem) event.setCanceled(true);
    }
    private static void incomingDamage(LivingIncomingDamageEvent event) {
        var direct = event.getSource().getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow && arrow.getPersistentData().contains(RepeatingCrossbowItem.PANEL_DAMAGE)) {
            event.setAmount(arrow.getPersistentData().getFloat(RepeatingCrossbowItem.PANEL_DAMAGE));
            var container = event.getContainer();
            container.setPostAttackInvulnerabilityTicks(halfInvulnerability(container.getPostAttackInvulnerabilityTicks()));
        }
    }
    /**
     * The extra arrows of 纯粹 fly with the shot, so they would hit during the invulnerability its other arrows just gave.
     * They ignore it and leave the timer as it was. Runs last, so a cancelled hit keeps the timer.
     */
    private static void extraArrowCooldown(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
            && arrow.getPersistentData().getBoolean(RepeatingCrossbowItem.EXTRA_ARROW)) {
            var target = event.getEntity();
            event.getContainer().setPostAttackInvulnerabilityTicks(target.invulnerableTime);
            target.invulnerableTime = 0;
        }
    }
    /**
     * Repeating crossbow hits leave half the usual invulnerability. A target ignores weaker hits while its timer is above
     * 10, so the vanilla 20 ticks block for 10 ticks; 15 blocks for 5.
     */
    public static int halfInvulnerability(int ticks) {
        return ticks <= 10 ? ticks : 10 + (ticks - 10) / 2;
    }
    private CrossbowEvents() {}
}
