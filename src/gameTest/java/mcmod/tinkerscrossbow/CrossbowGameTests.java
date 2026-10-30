package mcmod.tinkerscrossbow;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.FloatToolStat;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.TinkerToolParts;
import slimeknights.tconstruct.tools.TinkerTools;
import mcmod.tinkerscrossbow.item.ReceiverItem;
import mcmod.tinkerscrossbow.item.RepeatingCrossbowItem;
import mcmod.tinkerscrossbow.logic.CrossbowEvents;
import mcmod.tinkerscrossbow.logic.ShotTiming;
import mcmod.tinkerscrossbow.modifier.CriticalStrikeModifier;
import mcmod.tinkerscrossbow.modifier.CruelModifier;
import mcmod.tinkerscrossbow.modifier.PureModifier;
import mcmod.tinkerscrossbow.modifier.StoneboundModifier;
import mcmod.tinkerscrossbow.recipe.ReceiverAssemblyRecipe;
import mcmod.tinkerscrossbow.recipe.RepeatingCrossbowRecipe;
import mcmod.tinkerscrossbow.stats.CrossbowStats;
import mcmod.tinkerscrossbow.stats.ReceiverBodyStats;
import mcmod.tinkerscrossbow.stats.ReceiverMagazineStats;

/** Development-only source set; these classes and test structures are not shipped in the mod JAR. */
@GameTestHolder(TinkersCrossbow.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrossbowGameTests {
    /** Wood limb and grip, string bowstring: neutral draw speed and velocity. */
    private static ItemStack crossbow(String body, String magazine) {
        return ToolBuildHandler.buildItemFromMaterials(TinkersCrossbow.REPEATING_CROSSBOW.get(), MaterialNBT.of(
            MaterialVariant.of(MaterialVariantId.parse("tconstruct:wood")),
            MaterialVariant.of(MaterialVariantId.parse("tconstruct:wood")),
            MaterialVariant.of(MaterialVariantId.parse("tconstruct:string")),
            MaterialVariant.of(MaterialVariantId.parse("tconstruct:" + body)),
            MaterialVariant.of(MaterialVariantId.parse("tconstruct:" + magazine))));
    }
    private static ItemStack stoneCrossbow() { return crossbow("rock", "rock"); }
    private static void close(GameTestHelper helper, double actual, double expected, String name) {
        helper.assertTrue(Math.abs(actual - expected) < 1e-5, name + ": expected " + expected + ", got " + actual);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stone_panel_and_all_receivers(GameTestHelper helper) {
        ToolStack tool = ToolStack.from(stoneCrossbow());
        close(helper, tool.getStats().get(ToolStats.DURABILITY), 195, "body + magazine + limb + grip durability");
        close(helper, RepeatingCrossbowItem.panelDamage(tool, null), 4, "stone damage");
        close(helper, RepeatingCrossbowItem.fireRate(tool, null), 0.75, "stone fire rate");
        close(helper, RepeatingCrossbowItem.shotSeconds(tool, null), 0.5 / 0.75, "stone shot seconds");
        close(helper, RepeatingCrossbowItem.reloadSeconds(tool, null), 1, "stone reload seconds");
        helper.assertTrue(RepeatingCrossbowItem.capacity(tool) == 3, "stone capacity must be 3");
        helper.assertTrue(tool.getFreeSlots(SlotType.UPGRADE) == 2, "new crossbow must have 2 real upgrade slots");
        helper.assertTrue(tool.getFreeSlots(SlotType.ABILITY) == 1, "new crossbow must have 1 real ability slot");
        close(helper, ShotTiming.interval(1.2, 1.1), 1.2 / 1.1, "+10% draw speed");
        close(helper, ShotTiming.interval(1, 0.9), 1 / 0.9, "-10% draw speed");
        close(helper, ShotTiming.interval(0.5, 1), 0.5, "fire rate 1");
        close(helper, ShotTiming.interval(0.5, 2), 0.25, "fire rate 2");
        close(helper, ShotTiming.interval(1, 2), 0.5, "reload speed 2");
        var contributions = new java.util.ArrayList<Component>();
        RepeatingCrossbowItem.addMaterialInformation(tool, contributions, TooltipFlag.NORMAL);
        helper.assertTrue(contributions.stream().filter(line -> line.getString().contains("60")).count() == 2,
            "Ctrl must show both limb and grip base durability");
        for (String name : TinkersCrossbow.RECEIVER_MATERIALS) {
            var id = MaterialVariantId.parse("tconstruct:" + name).getId();
            helper.assertTrue(TinkersCrossbow.RECEIVER_BODY.get().canUseMaterial(id), "missing body material " + name);
            helper.assertTrue(TinkersCrossbow.RECEIVER_MAGAZINE.get().canUseMaterial(id), "missing magazine material " + name);
        }
        // Tinkers shows durability in the default tooltip and the bar only for damageable stacks.
        ItemStack stack = stoneCrossbow();
        helper.assertTrue(stack.isDamageableItem(), "the crossbow must be damageable like the Tinkers launchers");
        helper.assertTrue(stack.getMaxDamage() == 195, "vanilla max damage must follow the tool durability, got " + stack.getMaxDamage());
        helper.succeed();
    }

    private static void stat(GameTestHelper helper, ToolStack tool, FloatToolStat stat, double expected, String name) {
        close(helper, tool.getStats().get(stat), expected, name);
    }
    /** Receiver stats before traits: cobalt's lightweight, for one, also raises draw speed and velocity. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void body_and_magazine_stats(GameTestHelper helper) {
        ToolStack cobalt = ToolStack.from(crossbow("cobalt", "cobalt"));
        stat(helper, cobalt, ToolStats.PROJECTILE_DAMAGE, 6, "cobalt damage");
        stat(helper, cobalt, CrossbowStats.FIRE_RATE, 1.5 * 1.2, "cobalt fire rate with lightweight II");
        stat(helper, cobalt, CrossbowStats.RELOAD_SPEED, 1.3, "cobalt reload speed");
        close(helper, cobalt.getStats().get(ToolStats.DURABILITY), 360 + 240 + 120, "cobalt durability");
        helper.assertTrue(RepeatingCrossbowItem.capacity(cobalt) == 5, "cobalt magazine holds 5");
        // Rose gold magazine: -5% damage, +5% fire rate; the Tinkers enhanced adds an upgrade slot per level.
        ToolStack roseGold = ToolStack.from(crossbow("rose_gold", "rose_gold"));
        stat(helper, roseGold, ToolStats.PROJECTILE_DAMAGE, 4 * 0.95, "rose gold damage");
        stat(helper, roseGold, CrossbowStats.FIRE_RATE, 1.8 * 1.05, "rose gold fire rate");
        helper.assertTrue(roseGold.getModifierLevel(new ModifierId("tconstruct", "enhanced")) == 2, "rose gold gives enhanced II");
        helper.assertTrue(RepeatingCrossbowItem.capacity(roseGold) == 5, "rose gold magazine holds 5");
        helper.assertTrue(roseGold.getFreeSlots(SlotType.UPGRADE) == 4, "enhanced II adds two upgrade slots, got " + roseGold.getFreeSlots(SlotType.UPGRADE));
        helper.assertTrue(ToolStack.from(crossbow("rose_gold", "iron")).getFreeSlots(SlotType.UPGRADE) == 3, "a rose gold body adds one upgrade slot");
        stat(helper, ToolStack.from(crossbow("iron", "iron")), CrossbowStats.FIRE_RATE, 1, "iron fire rate");
        stat(helper, ToolStack.from(crossbow("iron", "iron")), ToolStats.PROJECTILE_DAMAGE, 6, "iron damage");
        String[] materials = {"rock", "flint", "bone", "copper", "amethyst_bronze", "manyullyn"};
        double[] damage = {4, 4 * 1.05, 5 * 1.05, 5 * 0.95, 7 * 1.1, 9};
        int[] capacity = {3, 2, 3, 3, 3, 9};
        ToolStack manyullyn = ToolStack.from(crossbow("manyullyn", "manyullyn"));
        stat(helper, manyullyn, CrossbowStats.FIRE_RATE, 0.9 * 0.9, "manyullyn fire rate -10%");
        stat(helper, manyullyn, CrossbowStats.RELOAD_SPEED, 0.8, "manyullyn reload speed");
        close(helper, manyullyn.getStats().get(ToolStats.DURABILITY), 1025 + 800 + 120, "manyullyn durability");
        close(helper, ToolStack.from(crossbow("bone", "bone")).getStats().get(ToolStats.DURABILITY), 60 - 15 + 120, "a bone magazine lowers durability");
        for (int i = 0; i < materials.length; i++) {
            ToolStack tool = ToolStack.from(crossbow(materials[i], materials[i]));
            stat(helper, tool, ToolStats.PROJECTILE_DAMAGE, damage[i], materials[i] + " damage");
            helper.assertTrue(RepeatingCrossbowItem.capacity(tool) == capacity[i], materials[i] + " magazine holds " + capacity[i]);
        }
        // Copper magazine: capacity and reload of its own, +5% fire rate and -5% damage on the cobalt body.
        ToolStack mixed = ToolStack.from(crossbow("cobalt", "copper"));
        stat(helper, mixed, CrossbowStats.FIRE_RATE, 1.5 * (1 + 0.05 + 0.1), "magazine percentage fire rate and lightweight I");
        stat(helper, mixed, ToolStats.PROJECTILE_DAMAGE, 6 * 0.95, "magazine percentage damage");
        stat(helper, mixed, CrossbowStats.RELOAD_SPEED, 1.05, "magazine reload speed");
        helper.assertTrue(RepeatingCrossbowItem.capacity(mixed) == 3, "the magazine sets the capacity");
        close(helper, mixed.getStats().get(ToolStats.DURABILITY), 360 + 70 + 120, "body + magazine durability");
        // Both receiver materials give traits: amethyst bronze adds pure to each part.
        var pure = TinkersCrossbow.PURE.getId();
        helper.assertTrue(ToolStack.from(crossbow("amethyst_bronze", "amethyst_bronze")).getModifierLevel(pure) == 2,
            "an amethyst bronze body and magazine give pure II");
        helper.assertTrue(ToolStack.from(crossbow("amethyst_bronze", "iron")).getModifierLevel(pure) == 1,
            "an amethyst bronze body alone gives pure I");
        helper.assertTrue(ToolStack.from(crossbow("cobalt", "iron")).getModifierLevel(pure) == 0, "other receivers have no pure");
        helper.succeed();
    }

    private static final class Inputs implements IMutableTinkerStationContainer {
        private final java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
        private ItemStack center = ItemStack.EMPTY;
        Inputs(int count, int amount) {
            for (int i = 0; i < count; i++) stacks.add(i == 0 ? new ItemStack(Items.COBBLESTONE, amount) : ItemStack.EMPTY);
        }
        /** The given stacks in the first input slots, the rest empty. */
        static Inputs of(int count, ItemStack... inputs) {
            Inputs result = new Inputs(count, 0);
            for (int i = 0; i < count; i++) result.stacks.set(i, i < inputs.length ? inputs[i] : ItemStack.EMPTY);
            return result;
        }
        Inputs withCenter(ItemStack stack) { center = stack; return this; }
        @Override public ItemStack getTinkerableStack() { return center; }
        @Override public int getInputCount() { return stacks.size(); }
        @Override public ItemStack getInput(int index) { return stacks.get(index); }
        @Override public MaterialRecipe getInputMaterial(int index) { return null; }
        @Override public void setInput(int index, ItemStack stack) { stacks.set(index, stack); }
        @Override public void giveItem(ItemStack stack) { throw new AssertionError("Unexpected recipe leftover " + stack); }
    }
    /** Bodies and magazines come from the part builder (stone, flint) or casting (metals), like the Tinkers parts. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void part_builder_and_casting_recipes(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var access = helper.getLevel().registryAccess();
        var goldCasts = TagKey.create(Registries.ITEM, new ResourceLocation("tconstruct", "casts/gold"));
        var sandCasts = TagKey.create(Registries.ITEM, new ResourceLocation("tconstruct", "casts/sand"));
        for (var part : List.of(TinkersCrossbow.RECEIVER_BODY, TinkersCrossbow.RECEIVER_MAGAZINE)) {
            String name = part.getId().getPath();
            var builder = manager.byKey(TinkersCrossbow.id("parts/builder/" + name));
            helper.assertTrue(builder.isPresent() && builder.get() instanceof slimeknights.tconstruct.library.recipe.partbuilder.PartRecipe recipe
                && recipe.getCost() == 8 && recipe.getResultItem(access).is(part.get()), "part builder recipe for " + name);
            for (String recipe : List.of("parts/casting/" + name + "_gold_cast", "parts/casting/" + name + "_sand_cast",
                "parts/casting/" + name + "_composite", "casts/gold/" + name, "casts/sand/molding/" + name,
                "casts/sand/builder_block/" + name, "casts/red_sand/molding/" + name, "casts/red_sand/builder_cast/" + name)) {
                helper.assertTrue(manager.byKey(TinkersCrossbow.id(recipe)).isPresent(), "missing recipe " + recipe);
            }
            helper.assertTrue(slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingLookup.getItemCost((slimeknights.tconstruct.library.tools.part.IMaterialItem) part.get()) == 8,
                "casting " + name + " costs 8 ingots");
        }
        helper.assertTrue(new ItemStack(TinkersCrossbow.BODY_CASTS.get(0).get()).is(goldCasts), "gold casts melt back like the Tinkers ones");
        helper.assertTrue(new ItemStack(TinkersCrossbow.MAGAZINE_CASTS.get(1).get()).is(sandCasts), "sand casts are Tinkers sand casts");
        helper.assertTrue(manager.byKey(TinkersCrossbow.id("receiver_body/iron")).isEmpty(), "the anvil no longer makes bodies");
        helper.succeed();
    }

    private static MaterialVariantId id(String material) { return MaterialVariantId.parse("tconstruct:" + material); }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void receiver_assembly_and_crossbow_building(GameTestHelper helper) {
        var level = helper.getLevel();
        var access = level.registryAccess();
        ItemStack body = TinkersCrossbow.RECEIVER_BODY.get().withMaterial(id("cobalt"));
        ItemStack magazine = TinkersCrossbow.RECEIVER_MAGAZINE.get().withMaterial(id("copper"));
        // Body + magazine -> receiver, in any slots, also on the three-slot station.
        var assembly = new ReceiverAssemblyRecipe(TinkersCrossbow.id("test_receiver"));
        helper.assertTrue(assembly.matches(Inputs.of(3, magazine, body), level), "a body and a magazine make a receiver");
        helper.assertTrue(!assembly.matches(Inputs.of(3, body, body.copy()), level), "two bodies make nothing");
        helper.assertTrue(!assembly.matches(Inputs.of(3, body, magazine, body.copy()), level), "extra parts make nothing");
        ItemStack receiver = assembly.getValidatedResult(Inputs.of(3, body, magazine), access).getResult().getStack();
        helper.assertTrue(receiver.is(TinkersCrossbow.RECEIVER.get()), "the result is a receiver");
        helper.assertTrue(ReceiverItem.material(receiver, 0).equals(id("cobalt"))
            && ReceiverItem.material(receiver, 1).equals(id("copper")), "the receiver keeps both materials");
        Inputs consumed = Inputs.of(3, body.copy(), magazine.copy());
        assembly.updateInputs(null, consumed, true);
        helper.assertTrue(consumed.isEmpty(), "assembly consumes the body and the magazine");
        // Limb + grip + bowstring + receiver -> crossbow with five materials.
        var building = new RepeatingCrossbowRecipe(TinkersCrossbow.id("test_crossbow"));
        ItemStack limb = TinkerToolParts.bowLimb.get().withMaterial(id("wood"));
        ItemStack grip = TinkerToolParts.bowGrip.get().withMaterial(id("wood"));
        ItemStack string = TinkerToolParts.bowstring.get().withMaterial(id("string"));
        helper.assertTrue(building.matches(Inputs.of(5, receiver, string, limb, grip), level), "the anvil builds the crossbow");
        helper.assertTrue(!building.matches(Inputs.of(3, limb, grip, string), level), "the station cannot hold four parts");
        helper.assertTrue(!building.matches(Inputs.of(5, limb, grip, string, body, magazine), level), "loose bodies and magazines do not build it");
        helper.assertTrue(!building.matches(Inputs.of(5, limb, grip, string, new ItemStack(TinkersCrossbow.RECEIVER.get())), level),
            "an empty receiver does not build it");
        ItemStack crossbow = building.getValidatedResult(Inputs.of(5, limb, grip, string, receiver), access).getResult().getStack();
        var materials = ToolStack.from(crossbow).getMaterials();
        helper.assertTrue(materials.size() == 5 && materials.get(3).getVariant().equals(id("cobalt")) && materials.get(4).getVariant().equals(id("copper")),
            "the crossbow takes the receiver materials, got " + materials);
        // A receiver swaps both receiver materials of a crossbow.
        ItemStack iron = TinkersCrossbow.RECEIVER.get().withMaterials(id("iron"), id("rose_gold"));
        Inputs swap = Inputs.of(5, iron).withCenter(crossbow);
        helper.assertTrue(building.matches(swap, level), "a receiver replaces the receiver of a crossbow");
        var swapped = ToolStack.from(building.getValidatedResult(swap, access).getResult().getStack()).getMaterials();
        helper.assertTrue(swapped.get(0).getVariant().equals(id("wood")) && swapped.get(3).getVariant().equals(id("iron"))
            && swapped.get(4).getVariant().equals(id("rose_gold")), "swapping keeps the other parts, got " + swapped);
        helper.assertTrue(!building.matches(Inputs.of(5, iron, limb).withCenter(crossbow), level), "swap one receiver at a time");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fractional_cadence_and_damage(GameTestHelper helper) {
        double next = 0;
        int shots = 0;
        for (int tick = 0; tick <= 2460; tick++) {
            if (tick + 1e-6 >= next) { next = ShotTiming.nextDeadline(next, tick, 24.6); shots++; }
        }
        helper.assertTrue(shots == 101, "1.23 s cadence must retain fractions over 100 intervals; got " + shots);
        var cow = helper.spawn(EntityType.COW, 2, 2, 2);
        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        arrow.getPersistentData().putFloat(RepeatingCrossbowItem.PANEL_DAMAGE, 5.5f);
        float before = cow.getHealth();
        cow.hurt(helper.getLevel().damageSources().arrow(arrow, null), 17);
        close(helper, before - cow.getHealth(), 5.5, "fractional damage must match the panel rather than vanilla speed damage");
        // Vanilla hits leave 20 ticks, ignoring weaker hits for 10; crossbow hits leave 15, ignoring them for 5.
        helper.assertTrue(cow.invulnerableTime == 15, "crossbow hits must halve invulnerability, got " + cow.invulnerableTime);
        helper.assertTrue(CrossbowEvents.halfInvulnerability(20) == 15 && CrossbowEvents.halfInvulnerability(10) == 10,
            "half invulnerability");
        cow.discard();
        helper.succeed();
    }

    /** Rock, copper and amethyst bronze receivers carry their own traits, one level per part. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void receiver_traits(GameTestHelper helper) {
        helper.assertTrue(ToolStack.from(stoneCrossbow()).getModifierLevel(TinkersCrossbow.STONEBOUND.getId()) == 2, "stone gives stonebound II");
        helper.assertTrue(ToolStack.from(crossbow("copper", "copper")).getModifierLevel(TinkersCrossbow.CRITICAL_STRIKE.getId()) == 2,
            "copper gives critical strike II");
        helper.assertTrue(ToolStack.from(crossbow("copper", "iron")).getModifierLevel(TinkersCrossbow.CRITICAL_STRIKE.getId()) == 1,
            "a copper body alone gives critical strike I");
        // Stonebound: +10% fire rate per level at no durability, in proportion to the durability lost.
        ToolStack stone = ToolStack.from(stoneCrossbow());
        close(helper, RepeatingCrossbowItem.fireRate(stone, null), 0.75, "stonebound adds nothing at full durability");
        stone.setDamage(39);
        close(helper, StoneboundModifier.bonus(stone), 0.04, "a fifth of the durability lost gives 2 x 10% x 1/5");
        close(helper, RepeatingCrossbowItem.fireRate(stone, null), 0.75 * 1.04, "stonebound raises the fire rate");
        // Critical strike: every arrow loaded adds 10% per level to the next shot, then starts over.
        ToolStack copper = ToolStack.from(crossbow("copper", "copper"));
        for (int i = 0; i < 3; i++) CriticalStrikeModifier.charge(copper);
        close(helper, CriticalStrikeModifier.consume(copper), 0.6, "three arrows at level II give +60%");
        close(helper, CriticalStrikeModifier.consume(copper), 0, "the bonus is spent by the shot");
        ToolStack iron = ToolStack.from(crossbow("iron", "iron"));
        CriticalStrikeModifier.charge(iron);
        helper.assertTrue(iron.getPersistentData().getInt(CriticalStrikeModifier.CHARGE) == 0, "only critical strike counts arrows");
        // Pure: two arrows one above the other, three in a triangle, all close enough to hit a zombie at 30 blocks.
        float[] top = PureModifier.offset(0, 2), bottom = PureModifier.offset(1, 2);
        close(helper, top[0], 0, "two arrows: the first above the aim");
        close(helper, top[1], PureModifier.SPREAD, "two arrows: the first above the aim");
        close(helper, bottom[0], 0, "two arrows: the second below the aim");
        close(helper, bottom[1], -PureModifier.SPREAD, "two arrows: the second below the aim");
        float[][] triangle = {PureModifier.offset(0, 3), PureModifier.offset(1, 3), PureModifier.offset(2, 3)};
        close(helper, triangle[0][1], PureModifier.SPREAD, "three arrows: the top of the triangle");
        helper.assertTrue(triangle[1][0] < 0 && triangle[2][0] > 0 && triangle[1][1] < 0 && triangle[2][1] < 0, "three arrows: a triangle");
        for (float[] offset : triangle) {
            helper.assertTrue(30 * Math.tan(Math.toRadians(Math.abs(offset[0]))) < 0.3 && 30 * Math.tan(Math.toRadians(Math.abs(offset[1]))) < 0.9,
                "a pattern arrow must stay within a zombie at 30 blocks");
        }
        helper.succeed();
    }

    /** A shot with pure I and a critical strike charge: one extra arrow at half damage, the shot raised by the charge. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pure_and_critical_shot(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PureTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(position.getX(), position.getY(), position.getZ());
        ItemStack crossbow = crossbow("amethyst_bronze", "copper");
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        ToolStack tool = ToolStack.from(crossbow);
        helper.assertTrue(tool.getModifierLevel(TinkersCrossbow.PURE.getId()) == 1, "an amethyst bronze body gives pure I");
        var magazine = new ListTag();
        for (int i = 0; i < 2; i++) {
            magazine.add(new ItemStack(Items.ARROW).save(new CompoundTag()));
            CriticalStrikeModifier.charge(tool);
        }
        tool.getPersistentData().put(RepeatingCrossbowItem.MAGAZINE, magazine);
        float panel = RepeatingCrossbowItem.panelDamage(tool, player);
        TinkersCrossbow.REPEATING_CROSSBOW.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        var arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(4));
        helper.assertTrue(arrows.size() == 2, "pure I fires one extra arrow, got " + arrows.size());
        AbstractArrow main = arrows.stream().filter(a -> !a.getPersistentData().getBoolean(RepeatingCrossbowItem.EXTRA_ARROW)).findFirst().orElseThrow();
        AbstractArrow extra = arrows.stream().filter(a -> a.getPersistentData().getBoolean(RepeatingCrossbowItem.EXTRA_ARROW)).findFirst().orElseThrow();
        // Two loaded arrows with critical strike I: +20%.
        helper.assertTrue(Math.abs(main.getPersistentData().getFloat(RepeatingCrossbowItem.PANEL_DAMAGE) - panel * 1.2) < 1e-4,
            "the shot deals the panel damage +20%, got " + main.getPersistentData().getFloat(RepeatingCrossbowItem.PANEL_DAMAGE) + " of " + panel);
        helper.assertTrue(Math.abs(extra.getPersistentData().getFloat(RepeatingCrossbowItem.PANEL_DAMAGE) - panel * 1.2 * 0.5) < 1e-4,
            "the extra arrow deals half of the shot");
        helper.assertTrue(main.isCritArrow() && main.pickup == AbstractArrow.Pickup.ALLOWED, "the critical shot is a normal arrow with a critical trail");
        helper.assertTrue(extra.pickup == AbstractArrow.Pickup.CREATIVE_ONLY, "extra arrows cannot be picked up");
        // The pair flies one above the other, 2 x SPREAD apart, plus at most the random spread of each arrow.
        double apart = Math.toDegrees(Math.acos(Math.min(1, main.getDeltaMovement().normalize().dot(extra.getDeltaMovement().normalize()))));
        double noise = Math.toDegrees(2 * Math.sqrt(3) * 0.0172275 * ModifierUtil.getInaccuracy(tool, player));
        helper.assertTrue(Math.abs(apart - 2 * PureModifier.SPREAD) <= noise + 0.01, "the pair must be " + 2 * PureModifier.SPREAD + " degrees apart, got " + apart + " (noise " + noise + ")");
        ToolStack after = ToolStack.from(crossbow);
        helper.assertTrue(RepeatingCrossbowItem.loaded(after) == 1, "the extra arrow costs no ammo");
        helper.assertTrue(after.getPersistentData().getInt(CriticalStrikeModifier.CHARGE) == 0, "the shot spends the charge");
        // An extra arrow hits through the invulnerability the shot just gave, and leaves the timer as it was.
        var cow = helper.spawn(EntityType.COW, 2, 2, 2);
        Arrow first = EntityType.ARROW.create(helper.getLevel()), second = EntityType.ARROW.create(helper.getLevel());
        first.getPersistentData().putFloat(RepeatingCrossbowItem.PANEL_DAMAGE, 3);
        second.getPersistentData().putFloat(RepeatingCrossbowItem.PANEL_DAMAGE, 1.5f);
        second.getPersistentData().putBoolean(RepeatingCrossbowItem.EXTRA_ARROW, true);
        float before = cow.getHealth();
        cow.hurt(helper.getLevel().damageSources().arrow(first, null), 3);
        cow.hurt(helper.getLevel().damageSources().arrow(second, null), 1.5f);
        close(helper, before - cow.getHealth(), 4.5, "the extra arrow lands during the invulnerability");
        helper.assertTrue(cow.invulnerableTime == 15, "the extra arrow keeps the timer, got " + cow.invulnerableTime);
        arrows.forEach(AbstractArrow::discard);
        cow.discard();
        helper.succeed();
    }

    /** 纯粹 loads each arrow for three: three arrows from the inventory per arrow loaded, the last one taking what is left. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void pure_triple_ammo(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PureAmmoTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(position.getX(), position.getY(), position.getZ());
        // Amethyst bronze body: pure I; iron magazine: 5 arrows, reload 1.1, 18.2 ticks per arrow.
        ItemStack crossbow = crossbow("amethyst_bronze", "iron");
        var inventory = player.getInventory();
        inventory.selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        ItemStack arrows = new ItemStack(Items.ARROW, 10);
        inventory.setItem(1, arrows);
        RepeatingCrossbowItem.startReload(player);
        final int[] elapsed = {0};
        int[][] expected = {{30, 1, 7}, {45, 2, 4}, {60, 3, 1}, {75, 4, 0}};
        helper.onEachTick(() -> {
            int tick = elapsed[0]++;
            inventory.tick();
            for (int[] check : expected) {
                if (tick != check[0]) continue;
                int loaded = RepeatingCrossbowItem.loaded(ToolStack.from(crossbow));
                helper.assertTrue(loaded == check[1] && arrows.getCount() == check[2],
                    "tick " + tick + ": expected " + check[1] + " loaded and " + check[2] + " left, got " + loaded + " and " + arrows.getCount());
            }
            if (tick == 100) {
                helper.assertTrue(!reloading(crossbow) && RepeatingCrossbowItem.loaded(ToolStack.from(crossbow)) == 4, "the reload stops without ammo");
                helper.succeed();
            }
        });
    }

    /** 乘胜: every third consecutive hit loads one arrow per level; a miss ends the streak, pure's extra arrows do not count. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pursuit_auto_load(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PursuitTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        ItemStack crossbow = crossbow("bone", "bone");
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        ItemStack arrows = new ItemStack(Items.ARROW, 5);
        player.getInventory().setItem(1, arrows);
        helper.assertTrue(ToolStack.from(crossbow).getModifierLevel(TinkersCrossbow.PURSUIT.getId()) == 2, "bone gives pursuit II");
        var cow = helper.spawn(EntityType.COW, 2, 2, 2);
        var pursuit = TinkersCrossbow.PURSUIT.get();
        var entry = new ModifierEntry(TinkersCrossbow.PURSUIT.getId(), 2);
        Runnable hit = () -> pursuit.onProjectileHitEntity(ModifierNBT.EMPTY, null, entry, EntityType.ARROW.create(helper.getLevel()),
            new EntityHitResult(cow), player, cow, true);
        java.util.function.IntSupplier loaded = () -> RepeatingCrossbowItem.loaded(ToolStack.from(crossbow));
        hit.run();
        hit.run();
        helper.assertTrue(loaded.getAsInt() == 0, "two hits load nothing");
        hit.run();
        helper.assertTrue(loaded.getAsInt() == 2 && arrows.getCount() == 3, "the third hit loads two arrows at pursuit II, got "
            + loaded.getAsInt() + " loaded, " + arrows.getCount() + " left");
        hit.run();
        hit.run();
        pursuit.onProjectileHitsBlock(ModifierNBT.EMPTY, null, entry, EntityType.ARROW.create(helper.getLevel()),
            BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO), player);
        hit.run();
        helper.assertTrue(loaded.getAsInt() == 2, "hitting a block ends the streak");
        for (int i = 0; i < 3; i++) {
            Arrow extra = EntityType.ARROW.create(helper.getLevel());
            extra.getPersistentData().putBoolean(RepeatingCrossbowItem.EXTRA_ARROW, true);
            pursuit.onProjectileHitEntity(ModifierNBT.EMPTY, null, entry, extra, new EntityHitResult(cow), player, cow, true);
        }
        helper.assertTrue(loaded.getAsInt() == 2, "extra arrows do not count");
        hit.run();
        hit.run();
        helper.assertTrue(loaded.getAsInt() == 3 && arrows.getCount() == 2, "a magazine of 3 takes only one more");
        cow.discard();
        helper.succeed();
    }

    /** 残忍: hitting a target above 80% health gives +75% fire rate per level for 2 seconds. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void cruel_fire_rate(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CruelTest"));
        ToolStack tool = ToolStack.from(crossbow("manyullyn", "manyullyn"));
        helper.assertTrue(tool.getModifierLevel(TinkersCrossbow.CRUEL.getId()) == 2, "manyullyn gives cruel II");
        double base = RepeatingCrossbowItem.fireRate(tool, player);
        close(helper, base, 0.81, "manyullyn fire rate");
        var cow = helper.spawn(EntityType.COW, 2, 2, 2);
        var cruel = TinkersCrossbow.CRUEL.get();
        var entry = new ModifierEntry(TinkersCrossbow.CRUEL.getId(), 2);
        Runnable hit = () -> cruel.onProjectileHitEntity(ModifierNBT.EMPTY, null, entry, EntityType.ARROW.create(helper.getLevel()),
            new EntityHitResult(cow), player, cow, true);
        cow.setHealth(cow.getMaxHealth() * 0.8f);
        hit.run();
        close(helper, RepeatingCrossbowItem.fireRate(tool, player), base, "a target at 80% gives nothing");
        cow.setHealth(cow.getMaxHealth());
        hit.run();
        close(helper, RepeatingCrossbowItem.fireRate(tool, player), base * (1 + 2 * 0.75), "cruel II: +150% fire rate");
        helper.runAfterDelay(CruelModifier.DURATION + 1, () -> {
            close(helper, RepeatingCrossbowItem.fireRate(tool, player), base, "the bonus ends after 2 seconds");
            cow.discard();
            helper.succeed();
        });
    }

    /**
     * Arrows add only their damage above 2, and Tinkers arrows multiply the shot by their velocity. The bonus is saved when
     * the arrow is loaded, so the panel shows it, and the shot deals it.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tinkers_arrow_damage(GameTestHelper helper) {
        // The examples: 2.5 damage adds 0.5; velocity 1.2 adds 20%, 0.8 takes 20%; the crossbow velocity scales the bonus too.
        close(helper, RepeatingCrossbowItem.arrowShot(4, 1, 0.5f, 1), 4.5, "a 2.5 damage arrow adds 0.5");
        close(helper, RepeatingCrossbowItem.arrowShot(4, 1, 0, 1.2f), 4 * 1.2, "velocity 1.2: +20%");
        close(helper, RepeatingCrossbowItem.arrowShot(4, 1, 0, 0.8f), 4 * 0.8, "velocity 0.8: -20%");
        close(helper, RepeatingCrossbowItem.arrowShot(4, 1.2f, 0.5f, 1), 4.5 * 1.2, "the crossbow velocity scales the bonus");
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ArrowTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(position.getX(), position.getY(), position.getZ());
        ItemStack crossbow = stoneCrossbow();
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        // A Tinkers arrow with power I: 3 damage, 1 above a vanilla arrow.
        ToolStack arrowTool = ToolStack.from(ToolBuildHandler.buildItemFromMaterials(TinkerTools.arrow.get(),
            MaterialNBT.of(MaterialVariant.of(id("flint")), MaterialVariant.of(id("wood")), MaterialVariant.of(id("feather")))));
        float plainDamage = arrowTool.getStats().get(ToolStats.PROJECTILE_DAMAGE);
        arrowTool.addModifier(new ModifierId("tconstruct", "power"), 1);
        ItemStack ammo = arrowTool.createStack();
        float arrowDamage = arrowTool.getStats().get(ToolStats.PROJECTILE_DAMAGE), arrowVelocity = arrowTool.getStats().get(ToolStats.VELOCITY);
        helper.assertTrue(arrowDamage > 2, "power raises the arrow above 2, got " + arrowDamage + " from " + plainDamage);
        var bonus = RepeatingCrossbowItem.arrowBonus(ammo, player, crossbow);
        close(helper, bonus.damage(), arrowDamage - 2, "only the damage above 2 counts");
        close(helper, bonus.velocity(), arrowVelocity, "the arrow velocity");
        helper.assertTrue(RepeatingCrossbowItem.arrowBonus(new ItemStack(Items.ARROW), player, crossbow).equals(RepeatingCrossbowItem.ArrowBonus.NONE),
            "a vanilla arrow adds nothing");
        // Loading it raises the panel; firing deals the panel.
        close(helper, RepeatingCrossbowItem.panelDamage(ToolStack.from(crossbow), player), 4, "stone panel without arrows");
        player.getInventory().setItem(1, ammo);
        helper.assertTrue(RepeatingCrossbowItem.autoLoad(player, 1) == 1, "the Tinkers arrow loads");
        float panel = RepeatingCrossbowItem.panelDamage(ToolStack.from(crossbow), player);
        close(helper, panel, (4 + arrowDamage - 2) * arrowVelocity, "the loaded arrow shows on the panel");
        TinkersCrossbow.REPEATING_CROSSBOW.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        var arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(4));
        helper.assertTrue(arrows.size() == 1, "one shot, got " + arrows.size());
        close(helper, arrows.get(0).getPersistentData().getFloat(RepeatingCrossbowItem.PANEL_DAMAGE), panel, "the shot deals the panel");
        close(helper, RepeatingCrossbowItem.panelDamage(ToolStack.from(crossbow), player), 4, "an empty magazine shows the crossbow alone");
        arrows.forEach(AbstractArrow::discard);
        helper.succeed();
    }

    /** 扩容: +1 magazine size per level and an upgrade slot each, from iron and a chest at the anvil. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void expansion_upgrade(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        helper.assertTrue(manager.byKey(TinkersCrossbow.id("modifiers/expansion")).filter(recipe -> recipe
            instanceof slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe).isPresent(), "expansion recipe");
        helper.assertTrue(manager.byKey(TinkersCrossbow.id("modifiers/salvage/expansion")).isPresent(), "expansion salvage");
        ToolStack tool = ToolStack.from(stoneCrossbow());
        helper.assertTrue(RepeatingCrossbowItem.capacity(tool) == 3, "a stone magazine holds 3");
        tool.addModifier(TinkersCrossbow.EXPANSION, 5);
        helper.assertTrue(RepeatingCrossbowItem.capacity(tool) == 8, "expansion V adds 5, got " + RepeatingCrossbowItem.capacity(tool));
        helper.succeed();
    }

    /** The receiver tooltip totals: durability added, damage and fire rate raised by the magazine percentages. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void receiver_total_stats(GameTestHelper helper) {
        var registry = MaterialRegistry.getInstance();
        ReceiverBodyStats body = registry.<ReceiverBodyStats>getMaterialStats(id("cobalt").getId(), ReceiverBodyStats.ID).orElseThrow();
        ReceiverMagazineStats magazine = registry.<ReceiverMagazineStats>getMaterialStats(id("copper").getId(), ReceiverMagazineStats.ID).orElseThrow();
        var total = ReceiverItem.combined(body, magazine, ReceiverItem.traits(id("cobalt"), id("copper")));
        close(helper, total.getStat(ToolStats.DURABILITY), 430, "cobalt body 360 + copper magazine 70");
        close(helper, total.getStat(ToolStats.PROJECTILE_DAMAGE), 6 * 0.95, "damage 6 -5%");
        close(helper, total.getStat(CrossbowStats.FIRE_RATE), 1.5 * 1.15, "fire rate 1.5 +5% and lightweight I");
        close(helper, total.getStat(CrossbowStats.MAGAZINE_SIZE), 3, "copper magazine size");
        close(helper, total.getStat(CrossbowStats.RELOAD_SPEED), 1.05, "copper reload speed");
        var traits = ReceiverItem.traits(id("copper"), id("copper")).getModifiers();
        helper.assertTrue(traits.size() == 1 && traits.get(0).getId().equals(TinkersCrossbow.CRITICAL_STRIKE.getId()) && traits.get(0).getLevel() == 2,
            "a copper receiver lists critical strike II, got " + traits);
        // Trait stat changes count: lightweight II on a cobalt receiver.
        var cobaltMagazine = registry.<ReceiverMagazineStats>getMaterialStats(id("cobalt").getId(), ReceiverMagazineStats.ID).orElseThrow();
        close(helper, ReceiverItem.combined(body, cobaltMagazine, ReceiverItem.traits(id("cobalt"), id("cobalt")))
            .getStat(CrossbowStats.FIRE_RATE), 1.5 * 1.2, "the receiver totals include lightweight");
        helper.succeed();
    }

    private static boolean reloading(ItemStack crossbow) {
        return ToolStack.from(crossbow).getPersistentData().getBoolean(RepeatingCrossbowItem.RELOADING);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sequential_reload_shoot_and_interrupt(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CrossbowTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(position.getX(), position.getY(), position.getZ());
        // Iron body: fire rate 1, 0.5 s per shot; stone magazine: 3 arrows, 1 s per arrow.
        ItemStack crossbow = crossbow("iron", "rock");
        var inventory = player.getInventory();
        inventory.selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        ItemStack arrows = new ItemStack(Items.TIPPED_ARROW, 5);
        PotionUtils.setPotion(arrows, Potions.POISON);
        inventory.setItem(1, arrows);
        var item = TinkersCrossbow.REPEATING_CROSSBOW.get();
        RepeatingCrossbowItem.startReload(player);
        final int[] elapsed = {0};
        helper.onEachTick(() -> {
            // Game ticks since startReload: the first callback runs in the same tick the test started.
            int tick = elapsed[0]++;
            // Fake players are not ticked by the server; tick the inventory like Player#aiStep. 93-95 and 98-101
            // simulate the crossbow leaving the inventory mid-reload.
            if ((tick < 93 || tick > 95) && (tick < 98 || tick > 101)) inventory.tick();
            ToolStack tool = ToolStack.from(crossbow);
            if (tick == 19) helper.assertTrue(RepeatingCrossbowItem.loaded(tool) == 0, "first arrow must wait one full second");
            if (tick == 20) {
                helper.assertTrue(RepeatingCrossbowItem.loaded(tool) == 1, "exactly one arrow at 20 ticks");
                helper.assertTrue(arrows.getCount() == 4, "consume only completed arrow");
                var saved = tool.getPersistentData().getList(RepeatingCrossbowItem.MAGAZINE, Tag.TAG_COMPOUND).getCompound(0);
                ItemStack restored = ItemStack.of(saved);
                helper.assertTrue(ItemStack.isSameItemSameTags(restored, arrows), "stored arrow must preserve its potion data");
            }
            if (tick == 40) helper.assertTrue(RepeatingCrossbowItem.loaded(tool) == 2, "second arrow at 40 ticks");
            if (tick == 60) {
                helper.assertTrue(RepeatingCrossbowItem.loaded(tool) == 3, "full magazine at 60 ticks");
                helper.assertTrue(!reloading(crossbow), "a full magazine ends the reload");
                helper.assertTrue(arrows.getCount() == 2, "three loaded arrows cost exactly three inventory arrows");
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                helper.assertTrue(RepeatingCrossbowItem.loaded(ToolStack.from(crossbow)) == 2, "right click immediately fires first arrow");
            }
            if (tick > 60 && tick <= 80) item.onUseTick(helper.getLevel(), player, crossbow, 72000 - tick);
            if (tick == 69) helper.assertTrue(RepeatingCrossbowItem.loaded(ToolStack.from(crossbow)) == 2, "cooldown must block early shots");
            if (tick == 70) helper.assertTrue(RepeatingCrossbowItem.loaded(ToolStack.from(crossbow)) == 1, "second shot after 0.5 seconds");
            if (tick == 80) {
                helper.assertTrue(RepeatingCrossbowItem.loaded(ToolStack.from(crossbow)) == 0, "third shot after another 0.5 seconds");
                helper.assertTrue(ToolStack.from(crossbow).getCurrentDurability() == 327, "one durability per fired arrow");
                player.stopUsingItem();
                RepeatingCrossbowItem.startReload(player);
                helper.assertTrue(reloading(crossbow), "left click starts a reload");
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                helper.assertTrue(reloading(crossbow), "right click with an empty magazine must not cancel the reload");
            }
            if (tick == 90) {
                helper.assertTrue(reloading(crossbow), "reload continues while held");
                inventory.selected = 2;
                inventory.tick();
                helper.assertTrue(arrows.getCount() == 2, "interrupting a partial reload must not consume an arrow");
                helper.assertTrue(!reloading(crossbow), "switching items must stop reload");
            }
            if (tick == 91) {
                inventory.selected = 0;
                RepeatingCrossbowItem.startReload(player);
                helper.assertTrue(reloading(crossbow), "reload restarts after switching back");
            }
            if (tick == 96) {
                helper.assertTrue(!reloading(crossbow), "a reload that stopped ticking must not resume");
                helper.assertTrue(arrows.getCount() == 2 && RepeatingCrossbowItem.loaded(tool) == 0, "a stale reload loads nothing");
                RepeatingCrossbowItem.startReload(player);
            }
            if (tick == 101) {
                // The crossbow is back before its first tick: the leftover flag must not block a new reload.
                RepeatingCrossbowItem.startReload(player);
                helper.assertTrue(RepeatingCrossbowItem.reloadProgress(ToolStack.from(crossbow), player, helper.getLevel().getGameTime()) == 0,
                    "a stale reload flag must restart the reload");
            }
            if (tick == 102) {
                helper.assertTrue(reloading(crossbow), "the restarted reload keeps running");
                helper.succeed();
            }
        });
    }
}
