package mcmod.tinkerscrossbow.stats;

import java.util.List;
import net.minecraft.network.chat.Component;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import slimeknights.mantle.data.loadable.primitive.FloatLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.materials.stats.IRepairableMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatType;
import slimeknights.tconstruct.library.tools.stat.IToolStat;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * Receiver magazine (弹舱): magazine capacity, reload speed and durability, which may be negative (bone). Its fire rate and damage are percentages
 * (0.1 = +10%, -0.05 = -5%) summed with the other percentage bonuses on the crossbow.
 */
public record ReceiverMagazineStats(int durability, int magazineSize, float reloadSpeed, float fireRate, float damage)
    implements IRepairableMaterialStats {
    public static final MaterialStatsId ID = new MaterialStatsId(TinkersCrossbow.id("receiver_magazine"));
    public static final ReceiverMagazineStats DEFAULT = new ReceiverMagazineStats(30, 3, 1, 0, 0);
    public static final MaterialStatType<ReceiverMagazineStats> TYPE = new MaterialStatType<>(ID, DEFAULT, RecordLoadable.create(
        IntLoadable.ANY_SHORT.requiredField("durability", ReceiverMagazineStats::durability),
        IntLoadable.FROM_ONE.requiredField("magazine_size", ReceiverMagazineStats::magazineSize),
        FloatLoadable.FROM_ZERO.requiredField("reload_speed", ReceiverMagazineStats::reloadSpeed),
        FloatLoadable.ANY.defaultField("fire_rate", 0f, true, ReceiverMagazineStats::fireRate),
        FloatLoadable.ANY.defaultField("damage", 0f, true, ReceiverMagazineStats::damage), ReceiverMagazineStats::new));
    private static final String FIRE_RATE_KEY = "tool_stat.tinkers_crossbow.magazine_fire_rate";
    private static final String DAMAGE_KEY = "tool_stat.tinkers_crossbow.magazine_damage";

    @Override public MaterialStatType<?> getType() { return TYPE; }
    @Override public List<Component> getLocalizedInfo() {
        return List.of(ToolStats.DURABILITY.formatValue((float) durability), CrossbowStats.MAGAZINE_SIZE.formatValue((float) magazineSize),
            CrossbowStats.RELOAD_SPEED.formatValue(reloadSpeed), IToolStat.formatColoredPercentBoost(FIRE_RATE_KEY, fireRate),
            IToolStat.formatColoredPercentBoost(DAMAGE_KEY, damage));
    }
    @Override public List<Component> getLocalizedDescriptions() {
        return List.of(ToolStats.DURABILITY.getDescription(), CrossbowStats.MAGAZINE_SIZE.getDescription(),
            CrossbowStats.RELOAD_SPEED.getDescription(), Component.translatable(FIRE_RATE_KEY + ".description"),
            Component.translatable(DAMAGE_KEY + ".description"));
    }
    @Override public void apply(ModifierStatsBuilder builder, float scale) {
        ToolStats.DURABILITY.add(builder, durability * scale);
        CrossbowStats.MAGAZINE_SIZE.update(builder, (float) magazineSize);
        CrossbowStats.RELOAD_SPEED.update(builder, reloadSpeed);
        CrossbowStats.FIRE_RATE.percent(builder, fireRate);
        ToolStats.PROJECTILE_DAMAGE.percent(builder, damage);
    }
}
