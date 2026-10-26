package us.dingl.VowSMPPlugin.Vows.Impl;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.Items.LifeItem;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.SoulboundItemVow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// taking the vow drops him to 0 lives and destroys every life item he has, and every death after costs 2 lives.
/// The vow stays dormant until he finishes a resurrection ritual. Then he gets a soulbound smite item:
/// right click strikes the nearest player he's aiming at within 15 blocks with lightning for exactly 3 hearts
/// (through armor) plus 1 second of Blindness and Darkness, on a 3 minute cooldown. Players he trusts with
/// /vow trust are never targeted.
public class GuruVow extends SoulboundItemVow {

    private static final int LIVES_LOST = 2;
    private static final double RANGE = 15.0;
    /// how far off his crosshair a player can be and still get hit
    private static final double AIM_TOLERANCE = 0.75;
    /// 3 hearts
    private static final double DAMAGE = 6.0;
    private static final int EFFECT_TICKS = 20;
    private static final int COOLDOWN_TICKS = 3 * 60 * 20;
    /// how often the action bar counts down
    private static final long COOLDOWN_DISPLAY_TICKS = 2L;

    /// vow:smite from the bundled datapack - ignores armor, protection and shields, and never scales with difficulty
    private static final NamespacedKey SMITE_KEY = new NamespacedKey("vow", "smite");

    private static final Component ITEM_NAME = Component.text("Smite", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false);

    /// set on the player once his ritual is done. Kept in his own data so it survives restarts.
    private final NamespacedKey awakenedKey;
    private final NamespacedKey cooldownGroup;
    private final DamageType smiteDamage;
    /// server tick his smite is ready again. Item cooldowns reset on relog, so track it here too.
    private final Map<UUID, Integer> readyAt = new HashMap<>();
    /// guru -> players he trusts. A copy of the config so targeting never touches it - see loadTrusted().
    /// Concurrent because tab completion reads it off the main thread.
    private final Map<UUID, Set<UUID>> trusted = new ConcurrentHashMap<>();

    public GuruVow(VowSMPPlugin plugin) {
        super(plugin, "guru_smite");
        awakenedKey = new NamespacedKey(plugin, "guru_awakened");
        cooldownGroup = new NamespacedKey(plugin, "guru_smite");

        DamageType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).get(SMITE_KEY);
        if (type == null) {
            // datapack didn't load. magic also ignores armor and doesn't scale, but protection reduces it
            plugin.getLogger().warning("Damage type " + SMITE_KEY + " is missing, Guru vow smite will use magic");
            type = DamageType.MAGIC;
        }
        smiteDamage = type;

        loadTrusted();

        // the lives bar only refreshes every 2 seconds, so count the cooldown down ourselves
        Bukkit.getScheduler().runTaskTimer(plugin, this::cooldownTick, COOLDOWN_DISPLAY_TICKS, COOLDOWN_DISPLAY_TICKS);
    }

    @Override
    public String id() {
        return "guru";
    }

    @Override
    public String name() {
        return "Guru vow";
    }

    @Override
    public String description() {
        return "Give up every life you have and drop to 0. Complete a resurrection ritual to unlock Smite: "
                + "right click to strike the player you're aiming at (15 blocks) with lightning for 3 hearts "
                + "and 1 second of Blindness and Darkness (3 minute cooldown). Every death costs " + LIVES_LOST + " lives.";
    }

    @Override
    public Material icon() {
        return Material.LIGHTNING_ROD;
    }

    @Override
    protected ItemStack buildItem() {
        // a stick that looks like a lightning rod, so it can't be placed
        ItemStack item = new ItemStack(Material.STICK);
        item.editMeta(meta -> {
            meta.customName(ITEM_NAME);
            meta.setItemModel(NamespacedKey.minecraft("lightning_rod"));
            meta.setEnchantmentGlintOverride(true);
            UseCooldownComponent cooldown = meta.getUseCooldown();
            cooldown.setCooldownSeconds(COOLDOWN_TICKS / 20f);
            cooldown.setCooldownGroup(cooldownGroup);
            meta.setUseCooldown(cooldown);
        });
        return item;
    }

    @Override
    public void onGain(Player player) {
        // no item yet - that comes with the ritual
        player.getPersistentDataContainer().remove(awakenedKey);

        UUID id = player.getUniqueId();
        int lives = plugin.getPlayerLives(id);
        plugin.updatePlayerLifeCounter(id, -lives);

        int items = removeLifeItems(player.getInventory()) + removeLifeItems(player.getEnderChest());
        ItemStack cursor = player.getItemOnCursor();
        if (LifeItem.isLife(cursor)) {
            items += cursor.getAmount();
            player.setItemOnCursor(null);
        }

        player.sendMessage(Component.text("You gave up " + lives + (lives == 1 ? " life" : " lives")
                + (items > 0 ? " and " + items + " life " + (items == 1 ? "item" : "items") : "")
                + ". Complete a resurrection ritual to awaken your vow.", NamedTextColor.GOLD));
    }

    @Override
    public void onLose(Player player) {
        super.onLose(player);
        player.getPersistentDataContainer().remove(awakenedKey);
        readyAt.remove(player.getUniqueId());
    }

    @Override
    public void onRitualComplete(Player player) {
        if (isAwakened(player)) return;

        player.getPersistentDataContainer().set(awakenedKey, PersistentDataType.BYTE, (byte) 1);
        super.onGain(player); // gives the smite item
        player.sendMessage(Component.text("Your vow has awakened. Right click with Smite to strike the player you're aiming at.",
                NamedTextColor.YELLOW));
        plugin.getLivesActionBar().update(player.getUniqueId());
    }

    @Override
    public int livesLostOnDeath(Player player, int livesBefore, int lost) {
        return LIVES_LOST;
    }

    @Override
    public Component actionBarStatus(Player player) {
        if (!isAwakened(player)) return null;

        Component label = Component.text("Smite: ", NamedTextColor.GRAY);
        int remaining = remainingCooldown(player);
        if (remaining <= 0) {
            return label.append(Component.text("READY", NamedTextColor.GREEN));
        }
        int seconds = (remaining + 19) / 20;
        return label.append(Component.text(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60), NamedTextColor.RED));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // put the cooldown back on the item after a relog
        Player player = event.getPlayer();
        int remaining = remainingCooldown(player);
        if (remaining > 0 && has(player)) {
            player.setCooldown(cooldownGroup, remaining);
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isSoulbound(event.getItem()) || !has(player) || !isAwakened(player)) return;

        // let him open doors and chests with it out, unless he's sneaking like vanilla
        Block block = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && block != null && block.getType().isInteractable() && !player.isSneaking()) {
            return;
        }

        if (remainingCooldown(player) > 0) return;

        Player target = findTarget(player);
        if (target == null) {
            player.sendActionBar(Component.text("No one in range", NamedTextColor.RED));
            return;
        }

        smite(player, target);
        readyAt.put(player.getUniqueId(), Bukkit.getCurrentTick() + COOLDOWN_TICKS);
        player.setCooldown(cooldownGroup, COOLDOWN_TICKS);
        player.swingHand(event.getHand());
        plugin.getLivesActionBar().update(player.getUniqueId());
    }

    /// the closest player whose (slightly padded) hitbox is under his crosshair, within range and in sight
    private Player findTarget(Player guru) {
        Vector eye = guru.getEyeLocation().toVector();
        Vector direction = guru.getEyeLocation().getDirection();

        Player closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player other : guru.getWorld().getPlayers()) {
            if (other.equals(guru) || other.isDead() || isTrusted(guru, other)
                    || other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE) {
                continue;
            }

            BoundingBox box = other.getBoundingBox().expand(AIM_TOLERANCE);
            RayTraceResult hit = box.rayTrace(eye, direction, RANGE);
            if (hit == null) continue;

            double distance = hit.getHitPosition().distance(eye);
            if (distance < closestDistance && guru.hasLineOfSight(other)) {
                closest = other;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private void smite(Player guru, Player target) {
        target.getWorld().strikeLightningEffect(target.getLocation());

        // a hit just before shouldn't eat the damage through invulnerability frames
        target.setNoDamageTicks(0);
        target.damage(DAMAGE, DamageSource.builder(smiteDamage)
                .withCausingEntity(guru)
                .withDirectEntity(guru)
                .build());

        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, EFFECT_TICKS, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, EFFECT_TICKS, 0));
    }

    // trust

    /// read-only live view of who this guru trusts
    public Set<UUID> getTrusted(UUID guru) {
        Set<UUID> set = trusted.get(guru);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public boolean isTrusted(Player guru, Player other) {
        Set<UUID> set = trusted.get(guru.getUniqueId());
        return set != null && set.contains(other.getUniqueId());
    }

    /// updates the set and writes it to config. Returns false if nothing changed.
    public boolean setTrusted(UUID guru, UUID other, boolean trust) {
        Set<UUID> set = trusted.computeIfAbsent(guru, id -> ConcurrentHashMap.newKeySet());
        boolean changed = trust ? set.add(other) : set.remove(other);
        if (!changed) return false;

        String path = ConfigKey.GURU_TRUSTED.getPath() + "." + guru;
        if (set.isEmpty()) {
            trusted.remove(guru);
            plugin.getConfig().set(path, null);
        } else {
            plugin.getConfig().set(path, set.stream().map(UUID::toString).toList());
        }
        plugin.saveConfig();
        return true;
    }

    /// rebuilds the sets from config. Runs on enable and before every /vow command.
    private void loadTrusted() {
        trusted.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKey.GURU_TRUSTED.getPath());
        if (section == null) return;

        for (String guruKey : section.getKeys(false)) {
            UUID guru = parseUuid(guruKey);
            if (guru == null) continue;

            Set<UUID> set = ConcurrentHashMap.newKeySet();
            for (String otherKey : section.getStringList(guruKey)) {
                UUID other = parseUuid(otherKey);
                if (other != null) set.add(other);
            }
            if (!set.isEmpty()) trusted.put(guru, set);
        }
    }

    private UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ignoring invalid UUID in " + ConfigKey.GURU_TRUSTED.getPath() + ": " + s);
            return null;
        }
    }

    // both fire before the command runs, so /vow trust itself sees the fresh sets
    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isVowCommand(event.getMessage().substring(1))) loadTrusted();
    }

    @EventHandler(ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (isVowCommand(event.getCommand())) loadTrusted();
    }

    /// "vow ..." or "vowsmpplugin:vow ..."
    private static boolean isVowCommand(String command) {
        String label = command.stripLeading().split(" ", 2)[0];
        label = label.substring(label.indexOf(':') + 1);
        return label.equalsIgnoreCase("vow");
    }

    private boolean isAwakened(Player player) {
        return player.getPersistentDataContainer().has(awakenedKey, PersistentDataType.BYTE);
    }

    private int remainingCooldown(Player player) {
        Integer ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0 : ready - Bukkit.getCurrentTick();
    }

    private void cooldownTick() {
        int now = Bukkit.getCurrentTick();
        // keep each player in the map for one more refresh after it runs out, so the bar flips to READY right away
        readyAt.entrySet().removeIf(entry -> {
            plugin.getLivesActionBar().update(entry.getKey());
            return entry.getValue() <= now;
        });
    }

    /// removes life items from the inventory, including inside shulker boxes and bundles. Returns how many.
    private static int removeLifeItems(Inventory inventory) {
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null) continue;

            if (LifeItem.isLife(item)) {
                removed += item.getAmount();
                inventory.setItem(slot, null);
                continue;
            }

            int nested = removeNestedLifeItems(item);
            if (nested > 0) {
                removed += nested;
                inventory.setItem(slot, item);
            }
        }
        return removed;
    }

    /// strips life items out of a shulker box or bundle, editing the stack in place
    private static int removeNestedLifeItems(ItemStack container) {
        if (!container.hasItemMeta()) return 0;

        if (container.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof ShulkerBox box) {
            int removed = removeLifeItems(box.getInventory());
            if (removed > 0) {
                meta.setBlockState(box);
                container.setItemMeta(meta);
            }
            return removed;
        }

        if (container.getItemMeta() instanceof BundleMeta meta) {
            int removed = 0;
            List<ItemStack> kept = new ArrayList<>();
            for (ItemStack item : meta.getItems()) {
                if (LifeItem.isLife(item)) {
                    removed += item.getAmount();
                } else {
                    removed += removeNestedLifeItems(item);
                    kept.add(item);
                }
            }
            if (removed > 0) {
                meta.setItems(kept);
                container.setItemMeta(meta);
            }
            return removed;
        }
        return 0;
    }
}
