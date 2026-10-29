package mcmod.tinkerscrossbow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;
import mcmod.tinkerscrossbow.network.ReloadPayload;

@EventBusSubscriber(modid = TinkersCrossbow.MOD_ID, value = Dist.CLIENT)
public final class CrossbowClient {
    private static boolean attackWasDown;
    /** One reload request per press of the attack key. */
    private static boolean requested;

    private static boolean holdingCrossbow() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.player.isSpectator() && mc.player.getMainHandItem().getItem() instanceof RepeatingCrossbowItem;
    }
    private static void requestReload() {
        if (requested) return;
        requested = true;
        MagazineHud.wake();
        PacketDistributor.sendToServer(ReloadPayload.INSTANCE);
    }
    /** Left click with the crossbow reloads instead of attacking; this also catches clicks shorter than a tick. */
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && holdingCrossbow()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            requestReload();
        }
    }
    /** Fallback on the raw key state, for clicks that never reach the attack event (attack cooldown, other mods). */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.screen == null && mc.options.keyAttack.isDown();
        if (down && !attackWasDown && holdingCrossbow()) requestReload();
        if (!down) requested = false;
        attackWasDown = down;
    }
    @EventBusSubscriber(modid = TinkersCrossbow.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> ItemProperties.register(TinkersCrossbow.REPEATING_CROSSBOW.get(), TinkersCrossbow.id("loaded"),
                (stack, level, entity, seed) -> RepeatingCrossbowItem.loaded(ToolStack.from(stack)) > 0 ? 1 : 0));
        }
        @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.CROSSHAIR, TinkersCrossbow.id("magazine"), MagazineHud::render);
        }
    }
    private CrossbowClient() {}
}
