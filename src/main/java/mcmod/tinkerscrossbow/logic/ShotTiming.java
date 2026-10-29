package mcmod.tinkerscrossbow.logic;

/** Fractional deadlines retain sub-tick remainders instead of rounding every shot to the same tick count. */
public final class ShotTiming {
    public static double interval(double baseSeconds, double speedMultiplier) {
        return Math.max(0.05, baseSeconds / Math.max(0.01, speedMultiplier));
    }
    public static double nextDeadline(double previous, double now, double periodTicks) {
        return (previous > now - periodTicks ? previous : now) + periodTicks;
    }
    private ShotTiming() {}
}
