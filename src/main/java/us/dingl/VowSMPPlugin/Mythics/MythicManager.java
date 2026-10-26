package us.dingl.VowSMPPlugin.Mythics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/// mythic items: one-of-a-kind items tagged with their own uuid. Every mythic and its holder is kept in
/// memory and mirrored to config. Holders are found by scanning online inventories once a second, so the
/// holder is whoever had it last - putting it in a chest doesn't change that, someone else taking it does.
public class MythicManager {

    private static final long SCAN_TICKS = 20L;

    public static final class Mythic {
        private final UUID id;
        private Component name;
        private UUID holder;

        private Mythic(UUID id, Component name, UUID holder) {
            this.id = id;
            this.name = name;
            this.holder = holder;
        }

        public UUID id() {
            return id;
        }

        public Component name() {
            return name;
        }

        /// null if nobody has picked it up since it became mythic
        public UUID holder() {
            return holder;
        }
    }

    private final VowSMPPlugin plugin;
    private final NamespacedKey idKey;
    /// insertion order = the order they were made, which is how /mythics lists them
    private final Map<UUID, Mythic> mythics = new LinkedHashMap<>();

    public MythicManager(VowSMPPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "mythic_id");
        load();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, SCAN_TICKS, SCAN_TICKS);
    }

    public Collection<Mythic> getMythics() {
        return Collections.unmodifiableCollection(mythics.values());
    }

    /// the mythic's uuid, or null if this isn't a mythic
    public UUID getId(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        String id = item.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// tags the item with a fresh uuid and starts tracking it, with {@code holder} holding it.
    /// Edits the stack in place, so set it back into the inventory afterwards.
    public Mythic makeMythic(ItemStack item, Player holder) {
        UUID id = UUID.randomUUID();
        item.editPersistentDataContainer(pdc -> pdc.set(idKey, PersistentDataType.STRING, id.toString()));

        Mythic mythic = new Mythic(id, item.effectiveName(), holder.getUniqueId());
        mythics.put(id, mythic);
        save(mythic);
        return mythic;
    }

    private void scan() {
        if (mythics.isEmpty()) return;

        // who has each mythic right now. A duplicated copy could show up on two players - if the current
        // holder is one of them they keep it, so the copies can't keep stealing it back and forth
        Map<UUID, Player> foundOn = new HashMap<>();
        Map<UUID, ItemStack> foundItem = new HashMap<>();
        Set<UUID> heldByHolder = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            forEachMythic(player, (id, item) -> {
                Mythic mythic = mythics.get(id);
                if (mythic == null) return; // tagged item we no longer track
                if (player.getUniqueId().equals(mythic.holder)) {
                    heldByHolder.add(id);
                } else if (!foundOn.containsKey(id)) {
                    foundOn.put(id, player);
                    foundItem.put(id, item);
                }
            });
        }

        foundOn.forEach((id, player) -> {
            if (heldByHolder.contains(id)) return;
            transfer(mythics.get(id), player, foundItem.get(id));
        });
    }

    private void transfer(Mythic mythic, Player newHolder, ItemStack item) {
        UUID oldHolder = mythic.holder;
        mythic.holder = newHolder.getUniqueId();
        mythic.name = item.effectiveName(); // keep up with anvil renames
        save(mythic);

        Component message = Component.text(newHolder.getName(), NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" now holds ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(mythic.name.decoration(TextDecoration.BOLD, false))
                .append(Component.text(oldHolder != null ? " (from " + plugin.getPlayerName(oldHolder) + ")" : "",
                        NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false));
        Bukkit.broadcast(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    /// every mythic in their inventory, armor, offhand and cursor, including inside shulker boxes and bundles
    private void forEachMythic(Player player, BiConsumer<UUID, ItemStack> action) {
        for (ItemStack item : player.getInventory().getContents()) {
            visit(item, action);
        }
        visit(player.getItemOnCursor(), action);
    }

    private void visit(ItemStack item, BiConsumer<UUID, ItemStack> action) {
        if (item == null || item.isEmpty()) return;

        UUID id = getId(item);
        if (id != null) {
            action.accept(id, item);
            return;
        }

        // getItemMeta copies, so only look inside things that can hold items
        if (!item.hasItemMeta()) return;
        if (item.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) visit(inner, action);
        } else if (item.getItemMeta() instanceof BundleMeta meta) {
            for (ItemStack inner : meta.getItems()) visit(inner, action);
        }
    }

    // config: mythics.<uuid>.name (MiniMessage) and mythics.<uuid>.holder

    private void load() {
        mythics.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKey.MYTHICS.getPath());
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Component name = MiniMessage.miniMessage().deserialize(section.getString(key + ".name", "Unknown"));
                String holder = section.getString(key + ".holder");
                mythics.put(id, new Mythic(id, name, holder != null ? UUID.fromString(holder) : null));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid mythic in " + ConfigKey.MYTHICS.getPath() + ": " + key);
            }
        }
    }

    private void save(Mythic mythic) {
        String path = ConfigKey.MYTHICS.getPath() + "." + mythic.id;
        plugin.getConfig().set(path + ".name", MiniMessage.miniMessage().serialize(mythic.name));
        plugin.getConfig().set(path + ".holder", mythic.holder != null ? mythic.holder.toString() : null);
        plugin.saveConfig();
    }
}
