package mcmod.tinkerscrossbow.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;

public record ReloadPayload() implements CustomPacketPayload {
    public static final ReloadPayload INSTANCE = new ReloadPayload();
    public static final Type<ReloadPayload> TYPE = new Type<>(TinkersCrossbow.id("reload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) RepeatingCrossbowItem.startReload(player);
        });
    }
}
