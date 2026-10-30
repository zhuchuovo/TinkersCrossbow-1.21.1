package mcmod.tinkerscrossbow.logic;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;

public final class CrossbowEvents {
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CrossbowEvents::logout);
        MinecraftForge.EVENT_BUS.addListener(CrossbowEvents::attack);
        MinecraftForge.EVENT_BUS.addListener(CrossbowEvents::leftClickBlock);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CrossbowEvents::incomingAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CrossbowEvents::incomingDamage);
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
    /**
     * 1.20.1 checks the target's invulnerability right after this event and before the hit is applied, so clearing the
     * timer here is what lets the extra arrows of 纯粹 land: they fly with the shot and would otherwise be swallowed by
     * the invulnerability its other arrows just gave.
     */
    private static void incomingAttack(LivingAttackEvent event) {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow
            && arrow.getPersistentData().getBoolean(RepeatingCrossbowItem.EXTRA_ARROW)) {
            event.getEntity().invulnerableTime = 0;
        }
    }
    private static void incomingDamage(LivingHurtEvent event) {
        var direct = event.getSource().getDirectEntity();
        if (direct instanceof AbstractArrow arrow && arrow.getPersistentData().contains(RepeatingCrossbowItem.PANEL_DAMAGE)) {
            event.setAmount(arrow.getPersistentData().getFloat(RepeatingCrossbowItem.PANEL_DAMAGE));
            LivingEntity target = event.getEntity();
            // The hit already reset the timer to 20; the crossbow leaves half of it.
            target.invulnerableTime = halfInvulnerability(target.invulnerableTime);
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
