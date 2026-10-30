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
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/** Receiver body (机体): the base projectile damage and fire rate, plus durability. */
public record ReceiverBodyStats(int durability, float damage, float fireRate) implements IRepairableMaterialStats {
    public static final MaterialStatsId ID = new MaterialStatsId(TinkersCrossbow.id("receiver_body"));
    public static final ReceiverBodyStats DEFAULT = new ReceiverBodyStats(45, 4, 0.75f);
    public static final MaterialStatType<ReceiverBodyStats> TYPE = new MaterialStatType<>(ID, DEFAULT, RecordLoadable.create(
        IntLoadable.FROM_ONE.requiredField("durability", ReceiverBodyStats::durability),
        FloatLoadable.FROM_ZERO.requiredField("damage", ReceiverBodyStats::damage),
        FloatLoadable.FROM_ZERO.requiredField("fire_rate", ReceiverBodyStats::fireRate), ReceiverBodyStats::new));

    @Override public MaterialStatType<?> getType() { return TYPE; }
    @Override public List<Component> getLocalizedInfo() {
        return List.of(ToolStats.DURABILITY.formatValue((float) durability), ToolStats.PROJECTILE_DAMAGE.formatValue(damage),
            CrossbowStats.FIRE_RATE.formatValue(fireRate));
    }
    @Override public List<Component> getLocalizedDescriptions() {
        return List.of(ToolStats.DURABILITY.getDescription(), ToolStats.PROJECTILE_DAMAGE.getDescription(),
            CrossbowStats.FIRE_RATE.getDescription());
    }
    @Override public void apply(ModifierStatsBuilder builder, float scale) {
        ToolStats.DURABILITY.update(builder, durability * scale);
        ToolStats.PROJECTILE_DAMAGE.update(builder, damage * scale);
        CrossbowStats.FIRE_RATE.update(builder, fireRate);
    }
}
