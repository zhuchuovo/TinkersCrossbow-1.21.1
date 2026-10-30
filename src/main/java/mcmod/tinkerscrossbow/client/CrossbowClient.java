package mcmod.tinkerscrossbow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;
import mcmod.tinkerscrossbow.network.CrossbowNetwork;
import mcmod.tinkerscrossbow.network.ReloadPayload;

@Mod.EventBusSubscriber(modid = TinkersCrossbow.MOD_ID, value = Dist.CLIENT)
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
        CrossbowNetwork.sendToServer(ReloadPayload.INSTANCE);
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
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.screen == null && mc.options.keyAttack.isDown();
        if (down && !attackWasDown && holdingCrossbow()) requestReload();
        if (!down) requested = false;
        attackWasDown = down;
    }

    @Mod.EventBusSubscriber(modid = TinkersCrossbow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> ItemProperties.register(TinkersCrossbow.REPEATING_CROSSBOW.get(), TinkersCrossbow.id("loaded"),
                (stack, level, entity, seed) -> RepeatingCrossbowItem.loaded(ToolStack.from(stack)) > 0 ? 1 : 0));
        }
        @SubscribeEvent public static void layers(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "magazine", new MagazineHud());
        }
    }
    private CrossbowClient() {}
}
