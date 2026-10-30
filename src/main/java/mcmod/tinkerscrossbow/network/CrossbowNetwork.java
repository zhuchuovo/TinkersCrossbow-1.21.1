package mcmod.tinkerscrossbow.network;

import java.util.Optional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import mcmod.tinkerscrossbow.TinkersCrossbow;

/** Client to server channel: left clicking the crossbow asks the server to start a reload. */
public final class CrossbowNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(TinkersCrossbow.id("reload"))
        .networkProtocolVersion(() -> PROTOCOL)
        .clientAcceptedVersions(CrossbowNetwork::acceptsVersion)
        .serverAcceptedVersions(CrossbowNetwork::acceptsVersion)
        .simpleChannel();
    private static boolean registered;

    private CrossbowNetwork() {}

    private static boolean acceptsVersion(String version) {
        return PROTOCOL.equals(version) || NetworkRegistry.ABSENT.equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(0, ReloadPayload.class, ReloadPayload::encode, ReloadPayload::decode,
            ReloadPayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToServer(ReloadPayload payload) {
        CHANNEL.sendToServer(payload);
    }
}
