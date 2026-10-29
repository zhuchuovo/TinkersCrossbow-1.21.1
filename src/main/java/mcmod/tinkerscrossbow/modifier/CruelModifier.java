package mcmod.tinkerscrossbow.modifier;

import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.stat.FloatToolStat;
import mcmod.tinkerscrossbow.stats.CrossbowStats;

/**
 * 残忍 (manyullyn receiver): hitting a target above {@link #HEALTH} of its health gives the shooter {@link #BONUS} fire rate
 * per level for {@link #DURATION} ticks. The timer lives on the shooter (server side); the crossbow reads it through its
 * fire rate.
 */
public final class CruelModifier extends Modifier implements ProjectileHitModifierHook, ConditionalStatModifierHook {
    public static final float HEALTH = 0.8f;
    /** Fire rate bonus per level. */
    public static final float BONUS = 0.75f;
    public static final int DURATION = 40;
    private static final String UNTIL = "tinkers_crossbow:cruel_until";

    @Override protected void registerHooks(Builder hookBuilder) {
        hookBuilder.addHook(this, ModifierHooks.PROJECTILE_HIT, ModifierHooks.CONDITIONAL_STAT);
    }

    public static boolean active(LivingEntity shooter) {
        return shooter.level().getGameTime() < shooter.getPersistentData().getLong(UNTIL);
    }

    @Override public boolean onProjectileHitEntity(ModifierNBT modifiers, ModDataNBT persistentData, ModifierEntry modifier,
        Projectile projectile, EntityHitResult hit, @Nullable LivingEntity attacker, @Nullable LivingEntity target, boolean notBlocked) {
        // Runs before the arrow deals its damage, so this is the health the arrow found.
        if (attacker != null && target != null && notBlocked && !projectile.level().isClientSide
            && target.getHealth() > HEALTH * target.getMaxHealth()) {
            attacker.getPersistentData().putLong(UNTIL, attacker.level().getGameTime() + DURATION);
        }
        return false;
    }
    @Override public float modifyStat(IToolStackView tool, ModifierEntry modifier, LivingEntity living, FloatToolStat stat,
        float baseValue, float multiplier) {
        return stat == CrossbowStats.FIRE_RATE && active(living) ? baseValue * (1 + BONUS * modifier.getEffectiveLevel()) : baseValue;
    }
}
