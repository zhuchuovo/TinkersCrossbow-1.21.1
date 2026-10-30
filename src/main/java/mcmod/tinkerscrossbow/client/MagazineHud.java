package mcmod.tinkerscrossbow.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import slimeknights.tconstruct.library.modifiers.hook.ranged.BowAmmoModifierHook;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;

/**
 * Magazine beside the crosshair: one bolt per slot, stacked from the bottom (the bottom bolt fires next).
 * The slot above the stack fills while an arrow loads; empty slots turn red when no ammo is left to load.
 * At most six bolts long: larger magazines squeeze their bolts together into that length. It shows when an arrow is
 * fired or loaded, while a reload runs and on a reload click, and fades out after {@link #HOLD} ms without them.
 */
public final class MagazineHud implements IGuiOverlay {
    /** 1.20.1 has no GUI sprite atlas, so the sprites are blitted straight from their texture files. */
    private static final ResourceLocation BOLT = texture("bolt");
    private static final ResourceLocation SPECTRAL = texture("bolt_spectral");
    private static final ResourceLocation FLETCHING = texture("bolt_fletching");
    private static final ResourceLocation SLOT = texture("slot");
    private static final ResourceLocation UNAVAILABLE = texture("slot_unavailable");
    private static final ResourceLocation PROGRESS = texture("progress");
    /** Sprites are 12x5: a 10x3 bolt inside a 1px outline. Outlines of neighbouring bolts touch. */
    private static final int WIDTH = 12, HEIGHT = 5, BOLT_LENGTH = WIDTH - 2;
    /** Six bolts: more slots than that overlap within the same length. */
    private static final int MAX_LENGTH = 6 * HEIGHT, MAX_SLOTS = 256;
    /** Gap from the crosshair centre to the bolts. */
    private static final int OFFSET = 16;
    private static final float OPACITY = 0.75f;
    /** Fully shown for HOLD ms after the last shot, load or reload click, then fading out over FADE ms. */
    private static final long HOLD = 1000, FADE = 1000;
    /** Hotbar slot and loaded count last seen, to notice shots and loads; -1 when no crossbow is held. */
    private static int watchedSlot = -1, watchedLoaded;
    private static long lastActivity = Long.MIN_VALUE / 2;
    /** Parsed magazine, reused while the synced list is unchanged. */
    private static ListTag cachedMagazine;
    private static List<ItemStack> cachedAmmo = List.of();

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(TinkersCrossbow.MOD_ID, "textures/gui/sprites/hud/magazine/" + name + ".png");
    }

    /** Shows the magazine again, as a shot or a load does. */
    public static void wake() { lastActivity = Util.getMillis(); }
    /**
     * GUI distance between squeezed bolts: the largest whole number of screen pixels that keeps them within six bolts.
     * With more bolts than screen pixels, they share the length evenly.
     */
    public static double evenStep(int slots, double guiScale) {
        double pixels = Math.floor((MAX_LENGTH - HEIGHT) * guiScale / (slots - 1));
        return pixels >= 1 ? pixels / guiScale : (MAX_LENGTH - HEIGHT) / (double) (slots - 1);
    }
    /** Opacity factor {@code elapsed} ms after the last activity. */
    public static float visibility(long elapsed) {
        return elapsed <= HOLD ? 1 : Math.max(0, 1 - (elapsed - HOLD) / (float) FADE);
    }

    @Override public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || player.isSpectator()) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RepeatingCrossbowItem item)) { watchedSlot = -1; return; }
        ToolStack tool = ToolStack.from(stack);
        List<ItemStack> ammo = ammo(tool.getPersistentData().getList(RepeatingCrossbowItem.MAGAZINE, Tag.TAG_COMPOUND));
        int loaded = ammo.size();
        int slots = Math.min(Math.max(RepeatingCrossbowItem.capacity(tool), loaded), MAX_SLOTS);
        double progress = RepeatingCrossbowItem.reloadProgress(tool, player, mc.level.getGameTime() + partialTick);
        // Picking up a crossbow does not show it; a shot or a loaded arrow does.
        int selected = player.getInventory().selected;
        if (selected != watchedSlot) {
            watchedSlot = selected;
            watchedLoaded = loaded;
        } else if (loaded != watchedLoaded) {
            watchedLoaded = loaded;
            wake();
        }
        if (progress >= 0) wake();
        float alpha = OPACITY * visibility(Util.getMillis() - lastActivity);
        if (alpha <= 0) return;
        boolean starved = loaded < slots && progress < 0 && !tool.isBroken()
            && BowAmmoModifierHook.getAmmo(tool, stack, player, item.getAllSupportedProjectiles()).isEmpty();

        // Up to six slots sit one bolt apart; more share the length of six, overlapping. The gap is a whole number of
        // screen pixels so every gap is the same; rounding each bolt to GUI pixels made them alternate.
        double scale = mc.getWindow().getGuiScale();
        double step = slots <= 6 ? HEIGHT : evenStep(slots, scale);
        double length = (slots - 1) * step + HEIGHT;
        int x = graphics.guiWidth() / 2 + OFFSET;
        double top = Math.round((graphics.guiHeight() - length) / 2 * scale) / scale;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1, 1, 1, alpha);
        // From the top down, so each bolt draws over the one above it: the bottom bolt fires next.
        for (int i = slots - 1; i >= 0; i--) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, top + (slots - 1 - i) * step, 0);
            if (i < loaded) {
                drawBolt(graphics, ammo.get(i), alpha);
            } else {
                blit(graphics, starved ? UNAVAILABLE : SLOT, 0, 0, WIDTH, HEIGHT);
                if (i == loaded && progress > 0) {
                    blit(graphics, PROGRESS, 0, 0, 1 + (int) Math.round(progress * BOLT_LENGTH), HEIGHT);
                }
            }
            graphics.pose().popPose();
        }
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** Draws {@code width} pixels of a 12x5 sprite at the current pose origin. */
    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        graphics.blit(texture, x, y, 0, 0f, 0f, width, height, WIDTH, HEIGHT);
    }

    private static void drawBolt(GuiGraphics graphics, ItemStack ammo, float alpha) {
        blit(graphics, ammo.is(Items.SPECTRAL_ARROW) ? SPECTRAL : BOLT, 0, 0, WIDTH, HEIGHT);
        if (!PotionUtils.getMobEffects(ammo).isEmpty()) {
            int color = PotionUtils.getColor(ammo);
            graphics.setColor((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, alpha);
            blit(graphics, FLETCHING, 0, 0, WIDTH, HEIGHT);
            graphics.setColor(1, 1, 1, alpha);
        }
    }

    private static List<ItemStack> ammo(ListTag magazine) {
        if (!magazine.equals(cachedMagazine)) {
            List<ItemStack> parsed = new ArrayList<>(magazine.size());
            for (int i = 0; i < magazine.size(); i++) parsed.add(ItemStack.of(magazine.getCompound(i)));
            cachedMagazine = magazine.copy();
            cachedAmmo = parsed;
        }
        return cachedAmmo;
    }
}
