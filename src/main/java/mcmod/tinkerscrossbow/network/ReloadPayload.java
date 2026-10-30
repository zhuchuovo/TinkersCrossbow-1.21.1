package mcmod.tinkerscrossbow.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;

/** Empty request: the client pressed left click with the crossbow in hand and wants a reload to start. */
public final class ReloadPayload {
    public static final ReloadPayload INSTANCE = new ReloadPayload();

    private ReloadPayload() {}

    public static void encode(ReloadPayload payload, FriendlyByteBuf buffer) {}

    public static ReloadPayload decode(FriendlyByteBuf buffer) { return INSTANCE; }

    public static void handle(ReloadPayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> RepeatingCrossbowItem.startReload(player));
        context.setPacketHandled(true);
    }
}
