package mcmod.tinkerscrossbow.modifier;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;

/**
 * 乘胜 (bone receiver): every {@link #HITS} consecutive hits on an enemy load one arrow per level from the inventory into
 * the crossbow in the main hand. An arrow hitting a block, a shield or a pause of {@link #TIMEOUT} ticks ends the streak.
 * The extra arrows of 纯粹 neither count nor end it. The streak lives on the shooter, so it carries between shots.
 */
public final class PursuitModifier extends Modifier implements ProjectileHitModifierHook {
    public static final int HITS = 3;
    public static final int TIMEOUT = 60;
    private static final String STREAK = "tinkers_crossbow:pursuit_streak";
    private static final String LAST_HIT = "tinkers_crossbow:pursuit_last_hit";

    @Override protected void registerHooks(Builder hookBuilder) {
        hookBuilder.addHook(this, ModifierHooks.PROJECTILE_HIT);
    }

    /** Consecutive hits the shooter has landed since the last load, 0 once the streak timed out. */
    public static int streak(LivingEntity shooter) {
        CompoundTag data = shooter.getPersistentData();
        return shooter.level().getGameTime() - data.getLong(LAST_HIT) > TIMEOUT ? 0 : data.getInt(STREAK);
    }
    private static void reset(LivingEntity shooter) {
        shooter.getPersistentData().remove(STREAK);
        shooter.getPersistentData().remove(LAST_HIT);
    }
    private static boolean extra(Projectile projectile) {
        return projectile.getPersistentData().getBoolean(RepeatingCrossbowItem.EXTRA_ARROW);
    }

    @Override public boolean onProjectileHitEntity(ModifierNBT modifiers, ModDataNBT persistentData, ModifierEntry modifier,
        Projectile projectile, EntityHitResult hit, @Nullable LivingEntity attacker, @Nullable LivingEntity target, boolean notBlocked) {
        if (attacker == null || target == null || projectile.level().isClientSide || extra(projectile)) return false;
        if (!notBlocked) { reset(attacker); return false; }
        int streak = streak(attacker) + 1;
        if (streak >= HITS) {
            streak = 0;
            if (attacker instanceof Player player) {
                var crossbow = player.getMainHandItem();
                if (crossbow.getItem() instanceof RepeatingCrossbowItem) RepeatingCrossbowItem.autoLoad(player,
                    slimeknights.tconstruct.library.tools.nbt.ToolStack.from(crossbow).getModifierLevel(TinkersCrossbow.PURSUIT.getId()));
            }
        }
        CompoundTag data = attacker.getPersistentData();
        data.putInt(STREAK, streak);
        data.putLong(LAST_HIT, attacker.level().getGameTime());
        return false;
    }
    @Override public boolean onProjectileHitsBlock(ModifierNBT modifiers, ModDataNBT persistentData, ModifierEntry modifier,
        Projectile projectile, BlockHitResult hit, @Nullable LivingEntity owner) {
        if (owner != null && !projectile.level().isClientSide && !extra(projectile)) reset(owner);
        return false;
    }
}
