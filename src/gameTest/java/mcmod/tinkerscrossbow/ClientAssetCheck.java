package mcmod.tinkerscrossbow;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.lwjgl.glfw.GLFW;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

/** Uses its own development client and game directory; never runs in the packaged mod. */
@EventBusSubscriber(modid = TinkersCrossbow.MOD_ID, value = Dist.CLIENT)
public final class ClientAssetCheck {
    private static boolean checked;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("tinkers_crossbow.asset_check") || checked) return;
        Minecraft mc = Minecraft.getInstance();
        // A first launch can open the accessibility onboarding screen before the title screen.
        if (mc.screen == null || mc.getOverlay() != null) return;
        checked = true;
        StringBuilder report = new StringBuilder();
        try {
            var materials = new ArrayList<>(TinkersCrossbow.RECEIVER_MATERIALS);
            materials.add("unknown");
            for (var part : java.util.List.of(TinkersCrossbow.RECEIVER_BODY, TinkersCrossbow.RECEIVER_MAGAZINE)) {
                String prefix = "tinkers_crossbow:item/tool/parts/" + part.getId().getPath() + "_";
                for (String material : materials) {
                    var stack = part.get().withMaterialForDisplay(MaterialVariantId.parse("tconstruct:" + material));
                    var model = mc.getItemRenderer().getModel(stack, null, null, 0);
                    int count = 0;
                    var sprites = new java.util.TreeSet<String>();
                    for (var pass : model.getRenderPasses(stack, false)) {
                        for (var quad : pass.getQuads(null, null, RandomSource.create(1), ModelData.EMPTY, null)) {
                            String name = quad.getSprite().contents().name().toString();
                            if (!name.startsWith(prefix)) throw new AssertionError(part.getId() + " " + material + " has incorrect sprite " + name);
                            sprites.add(name);
                            count++;
                        }
                    }
                    if (count == 0) throw new AssertionError(part.getId() + " " + material + " has no geometry");
                    report.append(part.getId().getPath()).append(' ').append(material).append(": ").append(count).append(" quads, ")
                        .append(sprites).append('\n');
                }
                // Station slot icon.
                var pattern = mc.getModelManager().getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                    .getSprite(TinkersCrossbow.id("gui/tinker_pattern/" + part.getId().getPath()));
                if (pattern.contents().name().getPath().equals("missingno")) throw new AssertionError("Missing slot pattern for " + part.getId());
            }
            // The receiver reuses the crossbow layers: its fixed frame plus the body and magazine sprites.
            for (String material : materials) {
                var stack = TinkersCrossbow.RECEIVER.get().withMaterials(MaterialVariantId.parse("tconstruct:" + material),
                    MaterialVariantId.parse("tconstruct:" + material));
                var model = mc.getItemRenderer().getModel(stack, null, null, 0);
                var sprites = new java.util.TreeSet<String>();
                for (var pass : model.getRenderPasses(stack, false)) {
                    for (var quad : pass.getQuads(null, null, RandomSource.create(1), ModelData.EMPTY, null)) {
                        sprites.add(quad.getSprite().contents().name().toString());
                    }
                }
                String suffix = "_tconstruct_" + (material.equals("rock") ? "rock_stone" : material);
                for (String layer : new String[]{"receiver_fixed", "body" + suffix, "magazine" + suffix}) {
                    if (!sprites.contains("tinkers_crossbow:item/tool/repeating_crossbow/" + layer))
                        throw new AssertionError("receiver " + material + " is missing " + layer + ", has " + sprites);
                }
                report.append("receiver ").append(material).append(": ").append(sprites).append('\n');
            }
            // Part casts draw their own sprite.
            for (var cast : java.util.stream.Stream.concat(TinkersCrossbow.BODY_CASTS.stream(), TinkersCrossbow.MAGAZINE_CASTS.stream()).toList()) {
                var stack = new net.minecraft.world.item.ItemStack(cast.get());
                String path = cast.getId().getPath();
                String part = path.substring(0, path.indexOf("_", "receiver_".length()));
                String kind = path.substring(part.length() + 1);
                String expected = "tinkers_crossbow:item/" + kind + "/" + part;
                for (var quad : mc.getItemRenderer().getModel(stack, null, null, 0).getQuads(null, null, RandomSource.create(1))) {
                    if (!quad.getSprite().contents().name().toString().equals(expected))
                        throw new AssertionError(path + " has sprite " + quad.getSprite().contents().name() + ", expected " + expected);
                }
                if (!Language.getInstance().has("item.tinkers_crossbow." + path)) throw new AssertionError("Missing name of " + path);
            }
            for (String pattern : new String[]{"receiver_body", "receiver_magazine"}) {
                if (!Language.getInstance().has("pattern.tinkers_crossbow." + pattern)) throw new AssertionError("Missing pattern name " + pattern);
            }
            var receiverPattern = mc.getModelManager().getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                .getSprite(TinkersCrossbow.id("gui/tinker_pattern/receiver"));
            if (receiverPattern.contents().name().getPath().equals("missingno")) throw new AssertionError("Missing receiver slot pattern");
            for (String key : new String[]{"tool_stat.tinkers_crossbow.fire_rate", "tool_stat.tinkers_crossbow.reload_speed",
                "tool_stat.tinkers_crossbow.magazine_size", "tool_stat.tinkers_crossbow.magazine_fire_rate",
                "tool_stat.tinkers_crossbow.magazine_damage", "item.tinkers_crossbow.repeating_crossbow.description",
                "item.tinkers_crossbow.receiver.description", "item.tinkers_crossbow.receiver_body.description",
                "item.tinkers_crossbow.receiver_magazine.description",
                "modifier.tinkers_crossbow.pure", "modifier.tinkers_crossbow.pure.description", "modifier.tinkers_crossbow.pure.arrows",
                "modifier.tinkers_crossbow.critical_strike", "modifier.tinkers_crossbow.critical_strike.description",
                "modifier.tinkers_crossbow.critical_strike.projectile_damage", "modifier.tinkers_crossbow.stonebound",
                "modifier.tinkers_crossbow.stonebound.description", "modifier.tinkers_crossbow.stonebound.fire_rate",
                "modifier.tinkers_crossbow.expansion", "modifier.tinkers_crossbow.expansion.description",
                "tooltip.tinkers_crossbow.receiver.stats", "modifier.tinkers_crossbow.lightweight",
                "modifier.tinkers_crossbow.lightweight.description", "modifier.tinkers_crossbow.pursuit",
                "modifier.tinkers_crossbow.pursuit.description", "modifier.tinkers_crossbow.cruel",
                "modifier.tinkers_crossbow.cruel.description"}) {
                if (!Language.getInstance().has(key)) throw new AssertionError("Missing translation " + key);
            }
            try (var reader = mc.getResourceManager().openAsReader(TinkersCrossbow.id("models/item/repeating_crossbow/loaded.json"))) {
                if (JsonParser.parseReader(reader).getAsJsonObject().has("ammo")) throw new AssertionError("Loaded model still displays ammo");
            }
            report.append("CROSSBOW_CLIENT_ASSETS_OK\n");
        } catch (Throwable failure) {
            report.append("CROSSBOW_CLIENT_ASSETS_FAILED: ").append(failure).append('\n');
            failure.printStackTrace();
        }
        try { Files.writeString(Path.of("asset-check.txt"), report); } catch (Exception failure) { failure.printStackTrace(); }
        System.out.print(report);
        mc.stop();
    }
    @EventBusSubscriber(modid = TinkersCrossbow.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            if (Boolean.getBoolean("tinkers_crossbow.asset_check")) {
                event.enqueueWork(() -> GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));
            }
        }
    }
}
