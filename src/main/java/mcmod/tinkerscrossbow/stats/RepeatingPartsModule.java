package mcmod.tinkerscrossbow.stats;

import java.util.List;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.loadable.record.SingletonLoader;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.definition.module.material.PartStatsModule;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;
import slimeknights.tconstruct.tools.stats.GripMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

/**
 * Adds the durability of all durable parts. The receiver is two parts: the body supplies the base projectile damage
 * and fire rate, the magazine the capacity and reload speed plus percentage fire rate and damage.
 */
public final class RepeatingPartsModule extends PartStatsModule {
    public static final RecordLoadable<PartStatsModule> LOADER = new SingletonLoader<>(new RepeatingPartsModule());
    public static final int BODY = 3, MAGAZINE = 4;
    private RepeatingPartsModule() {
        super(List.of(TinkerToolParts.bowLimb.get(), TinkerToolParts.bowGrip.get(), TinkerToolParts.bowstring.get(),
            TinkersCrossbow.RECEIVER_BODY.get(), TinkersCrossbow.RECEIVER_MAGAZINE.get()), new float[]{1, 1, 1, 1, 1}, BODY);
    }
    @Override public RecordLoadable<PartStatsModule> getLoader() { return LOADER; }
    public static int gripDurability(MaterialId material) {
        var registry = MaterialRegistry.getInstance();
        return registry.<HeadMaterialStats>getMaterialStats(material, HeadMaterialStats.ID).map(HeadMaterialStats::durability)
            .orElseGet(() -> registry.<LimbMaterialStats>getMaterialStats(material, LimbMaterialStats.ID).map(LimbMaterialStats::durability).orElse(0));
    }
    @Override public void addToolStats(IToolContext context, ModifierStatsBuilder builder) {
        var materials = context.getMaterials();
        var registry = MaterialRegistry.getInstance();
        ReceiverBodyStats body = registry.getStatsOrDefault(materials.get(BODY).getId(), ReceiverBodyStats.ID);
        (body == null ? ReceiverBodyStats.DEFAULT : body).apply(builder, 1);
        ReceiverMagazineStats magazine = registry.getStatsOrDefault(materials.get(MAGAZINE).getId(), ReceiverMagazineStats.ID);
        (magazine == null ? ReceiverMagazineStats.DEFAULT : magazine).apply(builder, 1);
        LimbMaterialStats limb = registry.getStatsOrDefault(materials.get(0).getId(), LimbMaterialStats.ID);
        if (limb != null) {
            ToolStats.DURABILITY.add(builder, limb.durability());
            ToolStats.DRAW_SPEED.add(builder, limb.drawSpeed());
            ToolStats.VELOCITY.add(builder, limb.velocity());
            ToolStats.ACCURACY.add(builder, limb.accuracy());
        }
        GripMaterialStats grip = registry.getStatsOrDefault(materials.get(1).getId(), GripMaterialStats.ID);
        if (grip != null) {
            ToolStats.DURABILITY.add(builder, gripDurability(materials.get(1).getId()));
            ToolStats.ACCURACY.add(builder, grip.accuracy());
            ToolStats.ATTACK_DAMAGE.update(builder, grip.meleeDamage());
        }
    }
}
