package mcmod.tinkerscrossbow.stats;

import mcmod.tinkerscrossbow.TinkersCrossbow;
import slimeknights.tconstruct.library.tools.stat.FloatToolStat;
import slimeknights.tconstruct.library.tools.stat.ToolStatId;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

public final class CrossbowStats {
    public static final FloatToolStat FIRE_RATE = ToolStats.register(new FloatToolStat(
        new ToolStatId(TinkersCrossbow.MOD_ID, "fire_rate"), 0xFF85C5F0, 1, 0.01f, 1000));
    public static final FloatToolStat RELOAD_SPEED = ToolStats.register(new FloatToolStat(
        new ToolStatId(TinkersCrossbow.MOD_ID, "reload_speed"), 0xFF83D1AE, 1, 0.01f, 1000));
    public static final FloatToolStat MAGAZINE_SIZE = ToolStats.register(new FloatToolStat(
        new ToolStatId(TinkersCrossbow.MOD_ID, "magazine_size"), 0xFFE6BD64, 3, 1, 256));
    public static void init() {}
    private CrossbowStats() {}
}
