package us.dingl.VowSMPPlugin.Items.Abilities;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.*;

/// every registered ability, and reading/writing them on items. An item's abilities live in its own data,
/// so they travel with it - copies, chests, other players, restarts:
///   vowsmpplugin:abilities -> { vowsmpplugin:<ability id> -> { vowsmpplugin:<setting key> -> "value" } }
public class AbilityManager {

    private final VowSMPPlugin plugin;
    private final NamespacedKey rootKey;
    private final Map<String, ItemAbility> abilities = new LinkedHashMap<>();

    public AbilityManager(VowSMPPlugin plugin) {
        this.plugin = plugin;
        this.rootKey = new NamespacedKey(plugin, "abilities");
    }

    public void register(ItemAbility ability) {
        if (abilities.putIfAbsent(ability.id(), ability) != null) {
            throw new IllegalArgumentException("Duplicate ability id: " + ability.id());
        }
        plugin.getServer().getPluginManager().registerEvents(ability, plugin);
    }

    public Collection<ItemAbility> getAbilities() {
        return Collections.unmodifiableCollection(abilities.values());
    }

    public ItemAbility getAbility(String id) {
        return abilities.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean has(ItemStack item, ItemAbility ability) {
        PersistentDataContainer root = root(item);
        return root != null && root.has(key(ability.id()), PersistentDataType.TAG_CONTAINER);
    }

    /// every registered ability on the item, in registration order. Unknown ones (from a removed
    /// ability) are skipped.
    public List<ItemAbility> getAbilities(ItemStack item) {
        PersistentDataContainer root = root(item);
        if (root == null) return List.of();
        return abilities.values().stream()
                .filter(a -> root.has(key(a.id()), PersistentDataType.TAG_CONTAINER))
                .toList();
    }

    /// the item's settings for this ability, or null if it doesn't have it
    public Map<String, String> getSettings(ItemStack item, ItemAbility ability) {
        PersistentDataContainer root = root(item);
        if (root == null) return null;
        PersistentDataContainer data = root.get(key(ability.id()), PersistentDataType.TAG_CONTAINER);
        if (data == null) return null;

        Map<String, String> settings = new LinkedHashMap<>();
        for (NamespacedKey k : data.getKeys()) {
            String value = data.get(k, PersistentDataType.STRING);
            if (value != null) settings.put(k.getKey(), value);
        }
        return settings;
    }

    /// adds the ability (or updates its settings). {@code settings} must already be checked.
    /// Edits the stack in place, so set it back into the inventory afterwards.
    public void apply(ItemStack item, ItemAbility ability, Map<String, String> settings) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> existing = getSettings(item, ability);
        if (existing != null) values.putAll(existing); // keeps values the ability stored for itself
        values.putAll(settings);
        ability.onApply(item, values);

        item.editPersistentDataContainer(pdc -> {
            PersistentDataContainer root = pdc.getOrDefault(rootKey, PersistentDataType.TAG_CONTAINER,
                    pdc.getAdapterContext().newPersistentDataContainer());
            PersistentDataContainer data = root.getAdapterContext().newPersistentDataContainer();
            values.forEach((k, v) -> data.set(key(k), PersistentDataType.STRING, v));
            root.set(key(ability.id()), PersistentDataType.TAG_CONTAINER, data);
            pdc.set(rootKey, PersistentDataType.TAG_CONTAINER, root);
        });
    }

    /// takes the ability off. Edits the stack in place, so set it back into the inventory afterwards.
    public void remove(ItemStack item, ItemAbility ability) {
        if (!has(item, ability)) return;
        ability.onRemove(item);
        item.editPersistentDataContainer(pdc -> {
            PersistentDataContainer root = pdc.get(rootKey, PersistentDataType.TAG_CONTAINER);
            if (root == null) return;
            root.remove(key(ability.id()));
            if (root.isEmpty()) {
                pdc.remove(rootKey);
            } else {
                pdc.set(rootKey, PersistentDataType.TAG_CONTAINER, root);
            }
        });
    }

    private PersistentDataContainer root(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        return item.getPersistentDataContainer().get(rootKey, PersistentDataType.TAG_CONTAINER);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }
}
