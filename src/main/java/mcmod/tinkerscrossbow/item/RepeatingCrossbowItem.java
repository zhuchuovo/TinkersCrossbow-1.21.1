package mcmod.tinkerscrossbow.item;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.tconstruct.library.client.materials.MaterialTooltipCache;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.BowAmmoModifierHook;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ModifiableArrowItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.stats.GripMaterialStats;
import slimeknights.tconstruct.library.utils.ItemStackUtil;
import mcmod.tinkerscrossbow.TinkersCrossbow;
import mcmod.tinkerscrossbow.logic.ShotTiming;
import mcmod.tinkerscrossbow.modifier.CriticalStrikeModifier;
import mcmod.tinkerscrossbow.modifier.PureModifier;
import mcmod.tinkerscrossbow.modifier.StoneboundModifier;
import mcmod.tinkerscrossbow.stats.CrossbowStats;
import mcmod.tinkerscrossbow.stats.RepeatingPartsModule;

public final class RepeatingCrossbowItem extends ModifiableCrossbowItem {
    public static final ResourceLocation MAGAZINE = TinkersCrossbow.id("magazine");
    public static final ResourceLocation RELOADING = TinkersCrossbow.id("reloading");
    public static final ResourceLocation RELOAD_STARTED = TinkersCrossbow.id("reload_started");
    /** Server game time the reload last advanced; a gap means the crossbow stopped ticking in the main hand. */
    public static final ResourceLocation RELOAD_TICK = TinkersCrossbow.id("reload_tick");
    public static final ResourceLocation NEXT_SHOT = TinkersCrossbow.id("next_shot");
    public static final String PANEL_DAMAGE = "tinkers_crossbow:panel_damage";
    /** Marks the extra arrows of 纯粹. */
    public static final String EXTRA_ARROW = "tinkers_crossbow:extra_arrow";

    public RepeatingCrossbowItem(Properties properties, ToolDefinition definition) { super(properties, definition, ARROW_ONLY); }
    @Override public Predicate<ItemStack> getAllSupportedProjectiles() { return ARROW_ONLY; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public boolean useOnRelease(ItemStack stack) { return false; }
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level,
        net.minecraft.core.BlockPos pos, Player player) { return false; }

    public static int loaded(IToolStackView tool) { return tool.getPersistentData().getList(MAGAZINE, Tag.TAG_COMPOUND).size(); }
    public static int capacity(IToolStackView tool) { return Math.max(1, Math.min(256, Math.round(tool.getStats().get(CrossbowStats.MAGAZINE_SIZE)))); }
    private static float modified(IToolStackView tool, LivingEntity entity, slimeknights.tconstruct.library.tools.stat.FloatToolStat stat) {
        return entity == null ? tool.getStats().get(stat) : ConditionalStatModifierHook.getModifiedStat(tool, entity, stat);
    }
    private static double decimal(float value) {
        // Data-pack stats use floats. Recover their decimal value before scheduling (1.2f must mean exactly 24 ticks).
        return Double.parseDouble(Float.toString(value));
    }
    public static double shotSeconds(IToolStackView tool, LivingEntity entity) {
        return ShotTiming.interval(0.5, fireRate(tool, entity));
    }
    public static double reloadSeconds(IToolStackView tool, LivingEntity entity) {
        return ShotTiming.interval(1, reloadSpeed(tool, entity));
    }
    public static double fireRate(IToolStackView tool, LivingEntity entity) {
        double rate = decimal(modified(tool, entity, CrossbowStats.FIRE_RATE)) * decimal(modified(tool, entity, ToolStats.DRAW_SPEED));
        float stonebound = StoneboundModifier.bonus(tool);
        return stonebound > 0 ? rate * (1 + stonebound) : rate;
    }
    public static double reloadSpeed(IToolStackView tool, LivingEntity entity) {
        return decimal(modified(tool, entity, CrossbowStats.RELOAD_SPEED)) * decimal(modified(tool, entity, ToolStats.DRAW_SPEED));
    }
    /**
     * What a loaded arrow adds to its shot: the damage above a vanilla arrow's 2 (nothing below), and for Tinkers arrows
     * their velocity, which multiplies the shot like on a Tinkers bow. Worked out when the arrow is loaded and saved with
     * it in the magazine, so the panel shows the next shot.
     */
    public record ArrowBonus(float damage, float velocity) {
        public static final ArrowBonus NONE = new ArrowBonus(0, 1);
        private static final String DAMAGE = "tinkers_crossbow:arrow_damage", VELOCITY = "tinkers_crossbow:arrow_velocity";
        /** Adds the bonus to a saved magazine entry; the item codec ignores the extra keys. */
        public void write(CompoundTag entry) {
            entry.putFloat(DAMAGE, damage);
            entry.putFloat(VELOCITY, velocity);
        }
        @Nullable public static ArrowBonus read(CompoundTag entry) {
            return entry.contains(VELOCITY) ? new ArrowBonus(entry.getFloat(DAMAGE), entry.getFloat(VELOCITY)) : null;
        }
    }
    public static ArrowBonus arrowBonus(ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
        if (ammo.getItem() instanceof ModifiableArrowItem) {
            ToolStack arrow = ToolStack.from(ammo);
            return new ArrowBonus(Math.max(0, ConditionalStatModifierHook.getModifiedStat(arrow, shooter, ToolStats.PROJECTILE_DAMAGE) - 2),
                ConditionalStatModifierHook.getModifiedStat(arrow, shooter, ToolStats.VELOCITY));
        }
        ArrowItem item = ammo.getItem() instanceof ArrowItem arrowItem ? arrowItem : (ArrowItem) Items.ARROW;
        double damage = item.createArrow(shooter.level(), ammo.copyWithCount(1), shooter, weapon).getBaseDamage();
        return new ArrowBonus((float) Math.max(0, damage - 2), 1);
    }
    /** Damage of a shot before 致命一击: (crossbow damage + arrow bonus) x crossbow velocity x arrow velocity. */
    public static float shotDamage(IToolStackView tool, LivingEntity entity, ArrowBonus arrow) {
        return arrowShot(modified(tool, entity, ToolStats.PROJECTILE_DAMAGE), modified(tool, entity, ToolStats.VELOCITY),
            arrow.damage(), arrow.velocity());
    }
    public static float arrowShot(float damage, float velocity, float arrowDamage, float arrowVelocity) {
        return Math.max(0, (damage + arrowDamage) * velocity * arrowVelocity);
    }
    /** The panel shows the next shot: the crossbow with the first loaded arrow. */
    public static float panelDamage(IToolStackView tool, LivingEntity entity) {
        ListTag queue = tool.getPersistentData().getList(MAGAZINE, Tag.TAG_COMPOUND);
        ArrowBonus next = queue.isEmpty() ? null : ArrowBonus.read(queue.getCompound(0));
        return shotDamage(tool, entity, next == null ? ArrowBonus.NONE : next);
    }
    public static void putDouble(ModDataNBT data, ResourceLocation key, double value) {
        data.put(key, DoubleTag.valueOf(value));
    }
    public static double getDouble(IToolStackView tool, ResourceLocation key) {
        return tool.getPersistentData().get(key, CompoundTag::getDouble);
    }
    public static void sync(ItemStack stack) {
        CompoundTag tag = ItemStackUtil.getTag(stack);
        if (tag != null) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static void publishAmmo(IToolStackView tool) {
        ModDataNBT data = tool.getPersistentData();
        ListTag queue = data.getList(MAGAZINE, Tag.TAG_COMPOUND);
        if (queue.isEmpty()) data.remove(KEY_CROSSBOW_AMMO);
        else data.put(KEY_CROSSBOW_AMMO, queue.getCompound(0).copy());
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken()) return InteractionResultHolder.fail(stack);
        if (loaded(tool) == 0) {
            // Nothing to fire, so a reload in progress keeps going.
            if (!level.isClientSide && !tool.getPersistentData().getBoolean(RELOADING))
                player.displayClientMessage(Component.translatable("message.tinkers_crossbow.empty"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            stopReload(stack);
            fire(stack, player, hand);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide && entity instanceof Player player) {
            if (ToolStack.from(stack).isBroken() || loaded(ToolStack.from(stack)) == 0) { player.stopUsingItem(); return; }
            fire(stack, player, player.getUsedItemHand());
        }
    }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {}

    /** Left click: load one arrow at a time until the magazine is full or the ammo runs out. */
    public static void startReload(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RepeatingCrossbowItem item) || player.isSpectator() || !player.isAlive()) return;
        ToolStack tool = ToolStack.from(stack);
        ModDataNBT data = tool.getPersistentData();
        double now = player.level().getGameTime();
        if (data.getBoolean(RELOADING) && !stale(tool, now) || tool.isBroken() || loaded(tool) >= capacity(tool)) return;
        if (BowAmmoModifierHook.getAmmo(tool, stack, player, item.getAllSupportedProjectiles()).isEmpty()) {
            player.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
            return;
        }
        player.stopUsingItem();
        data.putBoolean(RELOADING, true);
        putDouble(data, RELOAD_STARTED, now);
        putDouble(data, RELOAD_TICK, now);
        sync(stack);
        player.level().playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.PLAYERS, 0.7f, 1);
    }
    /** Ends a reload. Loaded arrows stay in the magazine; the unfinished arrow is not consumed. */
    public static void stopReload(ItemStack stack) {
        ModDataNBT data = ToolStack.from(stack).getPersistentData();
        if (!data.contains(RELOADING)) return;
        clearReload(data);
        sync(stack);
    }
    /** The reload has not advanced for over a tick: the crossbow stopped ticking in the main hand (left the inventory). */
    private static boolean stale(IToolStackView tool, double now) {
        return now - getDouble(tool, RELOAD_TICK) > 1.5;
    }
    private static void clearReload(ModDataNBT data) {
        data.remove(RELOADING);
        data.remove(RELOAD_STARTED);
        data.remove(RELOAD_TICK);
    }
    /** Fraction of the arrow currently being loaded, for the HUD; -1 when not reloading. */
    public static double reloadProgress(IToolStackView tool, LivingEntity entity, double gameTime) {
        if (!tool.getPersistentData().getBoolean(RELOADING)) return -1;
        double elapsed = gameTime - getDouble(tool, RELOAD_STARTED);
        return Math.max(0, Math.min(1, elapsed / (20 * reloadSeconds(tool, entity))));
    }

    // The reload lives in the tool data; the server advances it while the crossbow ticks in the main hand.
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide && entity instanceof Player player) {
            upgradeReceiver(stack);
            tickReload(stack, player);
        }
    }
    /** Crossbows from 1.0.2 and earlier have one receiver material; it becomes both the body and the magazine. */
    private static void upgradeReceiver(ItemStack stack) {
        ToolStack tool = ToolStack.from(stack);
        var materials = tool.getMaterials();
        if (materials.size() != RepeatingPartsModule.MAGAZINE) return;
        tool.replaceMaterial(RepeatingPartsModule.MAGAZINE, materials.get(RepeatingPartsModule.BODY));
        sync(stack);
    }
    private static void tickReload(ItemStack stack, Player player) {
        ToolStack tool = ToolStack.from(stack);
        ModDataNBT data = tool.getPersistentData();
        if (!data.getBoolean(RELOADING)) return;
        Level level = player.level();
        double now = level.getGameTime();
        // Switching items, firing, dying, or the crossbow leaving the inventory interrupt it.
        if (player.getMainHandItem() != stack || !player.isAlive() || player.isSpectator() || player.isUsingItem()
            || tool.isBroken() || loaded(tool) >= capacity(tool) || stale(tool, now)) {
            stopReload(stack);
            return;
        }
        putDouble(data, RELOAD_TICK, now);
        double period = 20 * reloadSeconds(tool, player);
        double started = getDouble(tool, RELOAD_STARTED);
        if (now - started + 1.0e-9 < period) return;
        if (!loadArrow(tool, stack, player)) {
            stopReload(stack);
            return;
        }
        // Keep the fractional remainder so the next arrow starts on schedule.
        if (loaded(tool) >= capacity(tool)) clearReload(data);
        else putDouble(data, RELOAD_STARTED, started + period);
        sync(stack);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.65f, 1.1f);
    }
    /** Takes an arrow from the inventory into the magazine; false without ammo. The caller syncs the stack. */
    private static boolean loadArrow(ToolStack tool, ItemStack stack, Player player) {
        var projectiles = ((RepeatingCrossbowItem) stack.getItem()).getAllSupportedProjectiles();
        ItemStack ammo = BowAmmoModifierHook.consumeAmmo(tool, stack, player, player, projectiles);
        if (ammo.isEmpty()) return false;
        // 纯粹 pays for its extra arrows when loading: the loaded arrow takes AMMO_COST arrows, or what is left.
        if (PureModifier.has(tool)) BowAmmoModifierHook.consumeAmmo(tool, stack, player, player, projectiles,
            BowAmmoModifierHook.getDesiredProjectiles(tool) * (PureModifier.AMMO_COST - 1));
        ModDataNBT data = tool.getPersistentData();
        ListTag queue = data.getList(MAGAZINE, Tag.TAG_COMPOUND).copy();
        CompoundTag entry = (CompoundTag) ammo.save(player.level().registryAccess());
        arrowBonus(ammo, player, stack).write(entry);
        queue.add(entry);
        data.put(MAGAZINE, queue);
        publishAmmo(tool);
        CriticalStrikeModifier.charge(tool);
        return true;
    }
    /** 乘胜: loads up to {@code count} arrows into the crossbow in the main hand at once, without the reload time. */
    public static int autoLoad(Player player, int count) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RepeatingCrossbowItem) || player.isSpectator()) return 0;
        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken()) return 0;
        int added = 0;
        while (added < count && loaded(tool) < capacity(tool) && loadArrow(tool, stack, player)) added++;
        if (added > 0) {
            sync(stack);
            player.level().playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
        }
        return added;
    }

    private static void fire(ItemStack stack, Player player, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide || player.isSpectator()) return;
        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken()) return;
        ModDataNBT data = tool.getPersistentData();
        double now = level.getGameTime();
        double previous = getDouble(tool, NEXT_SHOT);
        if (now + 1.0e-6 < previous) return;
        ListTag queue = data.getList(MAGAZINE, Tag.TAG_COMPOUND).copy();
        if (queue.isEmpty()) return;
        ItemStack ammo = ItemStack.parseOptional(level.registryAccess(), queue.getCompound(0));
        ArrowBonus shot = ArrowBonus.read(queue.getCompound(0));
        if (ammo.isEmpty()) { queue.remove(0); data.put(MAGAZINE, queue); publishAmmo(tool); sync(stack); return; }
        float velocity = Math.max(0.01f, modified(tool, player, ToolStats.VELOCITY));
        float inaccuracy = ModifierUtil.getInaccuracy(tool, player);
        int count = Math.min(ammo.getCount(), 64);
        // 纯粹 adds arrows beside the shot; 致命一击 spends the arrows loaded since the last shot.
        int extra = tool.getModifierLevel(TinkersCrossbow.PURE.getId());
        float critical = CriticalStrikeModifier.consume(tool);
        // Arrows loaded by older versions have no saved bonus.
        if (shot == null) shot = arrowBonus(ammo, player, stack);
        for (int i = 0; i < count + extra; i++) {
            boolean bonus = i >= count;
            ItemStack singleAmmo = ammo.copyWithCount(1);
            ArrowItem item = singleAmmo.getItem() instanceof ArrowItem arrowItem ? arrowItem : (ArrowItem) Items.ARROW;
            AbstractArrow arrow = item.createArrow(level, singleAmmo, player, stack);
            float damage = shotDamage(tool, player, shot) * (1 + critical);
            // Multishot arrows fan out 10 degrees apart; 纯粹 puts its extra arrows in a tight pattern with the middle one.
            int fan = bonus ? count / 2 : i;
            Vec3 up = player.getUpVector(1);
            Vector3f direction = player.getViewVector(1).toVector3f().rotate(new Quaternionf().setAngleAxis(
                Math.toRadians((fan - (count - 1) / 2f) * 10), up.x, up.y, up.z));
            int slot = bonus ? i - count + 1 : i == count / 2 ? 0 : -1;
            if (extra > 0 && slot >= 0) {
                float[] offset = PureModifier.offset(slot, extra + 1);
                Vector3f vertical = up.toVector3f(), right = new Vector3f(direction).cross(vertical);
                direction.add(right.mul((float) Math.tan(Math.toRadians(offset[0]))))
                    .add(vertical.mul((float) Math.tan(Math.toRadians(offset[1])))).normalize();
            }
            arrow.shoot(direction.x, direction.y, direction.z, 3f * velocity, inaccuracy);
            // The critical trail is only visual: the hit deals the panel damage below.
            arrow.setCritArrow(critical > 0);
            arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
            arrow.setBaseDamage(damage / Math.max(0.01, arrow.getDeltaMovement().length()));
            var modifiers = tool.getModifiers();
            EntityModifierCapability.getCapability(arrow).addModifiers(modifiers);
            ModDataNBT projectileData = PersistentDataCapability.getOrWarn(arrow);
            for (ModifierEntry modifier : modifiers.getModifiers()) {
                modifier.getHook(ModifierHooks.PROJECTILE_LAUNCH).onProjectileLaunch(tool, modifier, player, singleAmmo,
                    arrow, arrow, projectileData, i == count / 2);
            }
            if (bonus) {
                arrow.setBaseDamage(arrow.getBaseDamage() * PureModifier.DAMAGE);
                arrow.getPersistentData().putBoolean(EXTRA_ARROW, true);
            }
            // Preserve fractional panel damage before armor, without a second velocity multiplier or random critical damage.
            arrow.getPersistentData().putFloat(PANEL_DAMAGE, (float)(arrow.getBaseDamage() * arrow.getDeltaMovement().length()));
            // Extra arrows were paid for when loading; only the shot can be picked up.
            if (bonus || player.getAbilities().instabuild) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            level.addFreshEntity(arrow);
        }
        queue.remove(0);
        data.put(MAGAZINE, queue);
        putDouble(data, NEXT_SHOT, ShotTiming.nextDeadline(previous, now, shotSeconds(tool, player) * 20));
        publishAmmo(tool);
        ToolDamageUtil.damageAnimated(tool, count, player, hand);
        sync(stack);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1, 1);
        if (critical > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1, 0.9f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        if (player instanceof ServerPlayer server) {
            CriteriaTriggers.SHOT_CROSSBOW.trigger(server, stack);
            server.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
    }

    public static String number(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    @Override public List<Component> getStatInformation(IToolStackView tool, Player player, List<Component> lines,
        TooltipKey key, TooltipFlag flag) {
        lines.add(statLine("durability", tool.getCurrentDurability() + " / " + tool.getStats().getInt(ToolStats.DURABILITY), ChatFormatting.DARK_GREEN));
        lines.add(statLine("draw_speed", number(modified(tool, player, ToolStats.DRAW_SPEED)), ChatFormatting.DARK_PURPLE));
        lines.add(statLine("velocity", number(modified(tool, player, ToolStats.VELOCITY)), ChatFormatting.AQUA));
        lines.add(statLine("damage", number(panelDamage(tool, player)), ChatFormatting.RED));
        lines.add(statLine("fire_rate", number(fireRate(tool, player)), ChatFormatting.DARK_PURPLE));
        lines.add(statLine("magazine", loaded(tool) + " / " + capacity(tool), ChatFormatting.GOLD));
        lines.add(statLine("reload_speed", number(reloadSpeed(tool, player)), ChatFormatting.AQUA));
        lines.add(statLine("accuracy", number(modified(tool, player, ToolStats.ACCURACY)), ChatFormatting.DARK_PURPLE));
        double baseDamage = player == null ? 1 : player.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
        lines.add(statLine("melee_damage", number(modified(tool, player, ToolStats.ATTACK_DAMAGE) + baseDamage), ChatFormatting.RED));
        lines.add(statLine("melee_speed", number(modified(tool, player, ToolStats.ATTACK_SPEED)), ChatFormatting.DARK_PURPLE));
        lines.add(statLine("upgrades", tool.getFreeSlots(SlotType.UPGRADE), ChatFormatting.YELLOW));
        lines.add(statLine("abilities", tool.getFreeSlots(SlotType.ABILITY), ChatFormatting.LIGHT_PURPLE));
        for (ModifierEntry modifier : tool.getModifierList()) {
            modifier.getHook(ModifierHooks.TOOLTIP).addTooltip(tool, modifier, player, lines, key, flag);
        }
        return lines;
    }
    private static Component statLine(String stat, Object value, ChatFormatting color) {
        return Component.translatable("tooltip.tinkers_crossbow." + stat + "_label").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value.toString()).withStyle(color));
    }
    /** Show the same part contributions used by RepeatingPartsModule, including absolute grip durability. */
    public static void addMaterialInformation(IToolStackView tool, List<Component> lines, TooltipFlag flag) {
        var materials = tool.getMaterials();
        var parts = ToolPartsHook.parts(tool.getDefinition());
        if (materials.size() < parts.size()) return;
        for (int i = 0; i < parts.size(); i++) {
            var material = materials.get(i).getVariant();
            lines.add(parts.get(i).withMaterial(material).getHoverName().copy().withStyle(ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withColor(MaterialTooltipCache.getColor(material))));
            if (flag.isAdvanced()) lines.add(Component.literal(material.toString()).withStyle(ChatFormatting.DARK_GRAY));
            if (i == 1) {
                lines.add(ToolStats.DURABILITY.formatValue((float)RepeatingPartsModule.gripDurability(material.getId())));
                MaterialRegistry.getInstance().getMaterialStats(material.getId(), GripMaterialStats.ID).ifPresent(grip -> {
                        // Its percentage durability is replaced by the absolute material durability above.
                        lines.addAll(grip.getLocalizedInfo().subList(1, 3));
                    });
            } else {
                MaterialRegistry.getInstance().getMaterialStats(material.getId(),
                    parts.get(i).getStatType()).ifPresent(stats -> lines.addAll(stats.getLocalizedInfo()));
            }
            if (i < parts.size() - 1) lines.add(Component.empty());
        }
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (SafeClientAccess.getTooltipKey() == TooltipKey.CONTROL && getToolDefinition().isDataLoaded()
            && !ToolStack.from(stack).getMaterials().isEmpty()) {
            addMaterialInformation(ToolStack.from(stack), lines, flag);
        } else {
            super.appendHoverText(stack, context, lines, flag);
        }
    }
}
