package mcmod.tinkerscrossbow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import mcmod.tinkerscrossbow.client.MagazineHud;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;

/**
 * Development-only visual check (runHudCheckClient): creates a flat world, reloads crossbows and saves screenshots
 * of the magazine HUD and the item textures to run-asset-check/screenshots. Never runs in the packaged mod.
 */
@EventBusSubscriber(modid = TinkersCrossbow.MOD_ID, value = Dist.CLIENT)
public final class HudCheck {
    private static final String WORLD = "hudcheck";
    private static int stage, ticks;
    private static final StringBuilder report = new StringBuilder();

    private static ItemStack crossbow(String limb, String grip, String bowstring, String body, String magazine) {
        return ToolBuildHandler.buildItemFromMaterials(TinkersCrossbow.REPEATING_CROSSBOW.get(), MaterialNBT.of(
            material(limb), material(grip), material(bowstring), material(body), material(magazine)));
    }
    private static ItemStack crossbow(String limb, String grip, String bowstring, String receiver) {
        return crossbow(limb, grip, bowstring, receiver, receiver);
    }
    private static MaterialVariant material(String name) { return MaterialVariant.of(MaterialVariantId.parse("tconstruct:" + name)); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("tinkers_crossbow.hud_check")) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            switch (stage) {
                case 0 -> {
                    // Any menu will do: a fresh game directory opens the accessibility onboarding screen first.
                    if (mc.getOverlay() != null || mc.screen == null || mc.level != null) return;
                    mc.options.onboardAccessibility = false;
                    mc.options.pauseOnLostFocus = false;
                    Path saves = mc.getLevelSource().getBaseDir();
                    if (Files.isDirectory(saves.resolve(WORLD))) {
                        try (var files = Files.walk(saves.resolve(WORLD))) {
                            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                        }
                    }
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(0, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        new TitleScreen());
                    next();
                }
                case 1 -> {
                    if (mc.player == null || mc.level == null || mc.screen != null || mc.getSingleplayerServer() == null) { ticks = 0; return; }
                    if (ticks < 60) return;
                    var server = mc.getSingleplayerServer();
                    var uuid = mc.player.getUUID();
                    mc.player.setYRot(0);
                    mc.player.setXRot(20);
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        server.overworld().setDayTime(6000);
                        var inventory = player.getInventory();
                        inventory.clearContent();
                        inventory.setItem(0, crossbow("wood", "wood", "string", "rock"));
                        inventory.setItem(1, crossbow("wood", "wood", "string", "cobalt"));
                        inventory.setItem(2, crossbow("iron", "iron", "string", "iron"));
                        inventory.setItem(3, crossbow("cobalt", "cobalt", "string", "copper"));
                        ItemStack tipped = new ItemStack(Items.TIPPED_ARROW);
                        tipped.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
                        inventory.setItem(9, tipped);
                        inventory.setItem(10, new ItemStack(Items.SPECTRAL_ARROW));
                        inventory.setItem(11, new ItemStack(Items.ARROW, 4));
                        RepeatingCrossbowItem.startReload(player);
                    });
                    next();
                }
                case 2 -> {
                    // 1.5 s into a 1 s per arrow reload: one bolt loaded, the second slot half full.
                    if (ticks < 31) return;
                    shot(mc, "1_reloading");
                    next();
                }
                case 3 -> {
                    // The reload ended over a second ago; show it again for the screenshot.
                    if (ticks == 45) MagazineHud.wake();
                    if (ticks < 50) return;
                    shot(mc, "2_full");
                    mc.player.getInventory().selected = 1;
                    next();
                }
                case 4 -> {
                    if (ticks == 2) {
                        var server = mc.getSingleplayerServer();
                        var uuid = mc.player.getUUID();
                        server.execute(() -> RepeatingCrossbowItem.startReload(server.getPlayerList().getPlayer(uuid)));
                    }
                    // Cobalt holds five; the three arrows left run out, so the empty slots turn red.
                    if (ticks == 75) MagazineHud.wake();
                    if (ticks < 80) return;
                    shot(mc, "3_starved");
                    // Iron magazine with expansion V: ten slots squeezed into the length of six, nine loaded.
                    var server = mc.getSingleplayerServer();
                    var uuid = mc.player.getUUID();
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        ItemStack stack = player.getInventory().getItem(2);
                        ToolStack tool = ToolStack.from(stack);
                        tool.addModifier(TinkersCrossbow.EXPANSION, 5);
                        ItemStack tipped = new ItemStack(Items.TIPPED_ARROW);
                        tipped.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
                        var magazine = new net.minecraft.nbt.ListTag();
                        for (int i = 0; i < 9; i++) {
                            ItemStack arrow = i % 3 == 0 ? tipped : new ItemStack(i % 3 == 1 ? Items.SPECTRAL_ARROW : Items.ARROW);
                            magazine.add(arrow.save(player.registryAccess()));
                        }
                        tool.getPersistentData().put(RepeatingCrossbowItem.MAGAZINE, magazine);
                        RepeatingCrossbowItem.publishAmmo(tool);
                        RepeatingCrossbowItem.sync(stack);
                    });
                    mc.player.getInventory().selected = 2;
                    next();
                }
                case 5 -> {
                    // Switching to it shows nothing until something happens; wake it like a reload click does.
                    if (ticks == 5) MagazineHud.wake();
                    if (ticks == 10) {
                        shot(mc, "4_compressed");
                        // The same at GUI scale 2.
                        mc.options.guiScale().set(2);
                        mc.resizeDisplay();
                    }
                    if (ticks == 15) {
                        shot(mc, "4_compressed_scale2");
                        mc.options.guiScale().set(0);
                        mc.resizeDisplay();
                    }
                    // 1.5 s after the last activity it is half faded, after 2 s gone.
                    if (ticks == 35) shot(mc, "5_fading");
                    if (ticks < 60) return;
                    shot(mc, "6_faded");
                    mc.setScreen(new ItemSheet());
                    next();
                }
                case 6 -> {
                    if (ticks < 10) return;
                    shot(mc, "7_items");
                    next();
                }
                case 7 -> {
                    if (ticks < 40) return;
                    report.append("HUD_CHECK_DONE\n");
                    finish(mc);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            report.append("HUD_CHECK_FAILED: ").append(failure).append('\n');
            failure.printStackTrace();
            finish(mc);
        }
    }

    private static void next() {
        stage++;
        ticks = 0;
    }
    private static void shot(Minecraft mc, String name) {
        if (mc.player != null) {
            ToolStack tool = ToolStack.from(mc.player.getMainHandItem());
            report.append(name).append(": loaded ").append(RepeatingCrossbowItem.loaded(tool)).append('/')
                .append(RepeatingCrossbowItem.capacity(tool)).append(", progress ")
                .append(RepeatingCrossbowItem.reloadProgress(tool, mc.player, mc.level.getGameTime())).append('\n');
        }
        Screenshot.grab(mc.gameDirectory, "hudcheck_" + name + ".png", mc.getMainRenderTarget(), message -> {});
    }
    private static void finish(Minecraft mc) {
        stage = -1;
        try { Files.writeString(Path.of("hud-check.txt"), report); } catch (IOException failure) { failure.printStackTrace(); }
        System.out.print(report);
        mc.stop();
    }

    /** Item icons at 3x: receivers, the default crossbow with each receiver, and other part materials. */
    private static final class ItemSheet extends Screen {
        private final List<ItemStack> items = new ArrayList<>();
        ItemSheet() {
            super(Component.empty());
            for (var part : List.of(TinkersCrossbow.RECEIVER_BODY, TinkersCrossbow.RECEIVER_MAGAZINE)) {
                for (String receiver : TinkersCrossbow.RECEIVER_MATERIALS) {
                    items.add(part.get().withMaterialForDisplay(MaterialVariantId.parse("tconstruct:" + receiver)));
                }
                items.add(part.get().withMaterialForDisplay(MaterialVariantId.parse("tconstruct:unknown")));
            }
            for (String receiver : TinkersCrossbow.RECEIVER_MATERIALS) {
                var id = MaterialVariantId.parse("tconstruct:" + receiver);
                items.add(TinkersCrossbow.RECEIVER.get().withMaterials(id, id));
            }
            items.add(TinkersCrossbow.RECEIVER.get().withMaterials(MaterialVariantId.parse("tconstruct:cobalt"), MaterialVariantId.parse("tconstruct:rose_gold")));
            for (var cast : TinkersCrossbow.BODY_CASTS) items.add(new ItemStack(cast.get()));
            for (var cast : TinkersCrossbow.MAGAZINE_CASTS) items.add(new ItemStack(cast.get()));
            for (String receiver : TinkersCrossbow.RECEIVER_MATERIALS) items.add(crossbow("wood", "wood", "string", receiver));
            items.add(crossbow("iron", "iron", "string", "iron"));
            items.add(crossbow("cobalt", "cobalt", "string", "cobalt"));
            items.add(crossbow("bamboo", "bamboo", "vine", "copper"));
            items.add(crossbow("bone", "bone", "string", "flint"));
            items.add(crossbow("copper", "copper", "string", "rose_gold"));
            items.add(crossbow("manyullyn", "manyullyn", "darkthread", "amethyst_bronze"));
            items.add(crossbow("slimesteel", "slimesteel", "skyslime_vine", "cobalt"));
            items.add(crossbow("wood", "wood", "string", "cobalt", "rose_gold"));
            items.add(crossbow("wood", "wood", "string", "amethyst_bronze", "cobalt"));
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF8B8B8B);
            for (int i = 0; i < items.size(); i++) {
                graphics.pose().pushPose();
                graphics.pose().translate(4 + i % 8 * 52, 4 + i / 8 * 52, 0);
                graphics.pose().scale(3, 3, 1);
                graphics.renderItem(items.get(i), 0, 0);
                graphics.pose().popPose();
            }
        }
    }

    @EventBusSubscriber(modid = TinkersCrossbow.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            if (Boolean.getBoolean("tinkers_crossbow.hud_check")) {
                event.enqueueWork(() -> GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));
            }
        }
    }

    private HudCheck() {}
}
