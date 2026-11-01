package us.dingl.VowSMPPlugin.Items.Abilities;

import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.List;
import java.util.Map;

/// something special an item can do, like never being used up or having a cooldown. Admins put abilities
/// on any item from the item builder (/vow admin item), so nothing about the item itself is hardcoded -
/// the ability lives on the item, along with its settings.
///
/// To add one: extend this, register it in VowSMPPlugin.registerAbilities, done. It shows up in the builder.
///
/// Every ability is also registered as a Listener. Those handlers fire for every item though, so check
/// the item with {@link #on(ItemStack)} (or {@link #settings(ItemStack)} if you need its settings).
public abstract class ItemAbility implements Listener {

    protected final VowSMPPlugin plugin;

    protected ItemAbility(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    /// unique lowercase id, stored on items. Don't change it once items have the ability.
    public abstract String id();

    public abstract String name();

    public abstract String description();

    /// item shown for this ability in the builder
    public Material icon() {
        return Material.NETHER_STAR;
    }

    /// what admins fill in when adding the ability. Empty (the default) means there's nothing to set.
    public List<AbilitySetting> settings() {
        return List.of();
    }

    /// the ability was just added to the item, or its settings changed. Set up any item components it
    /// needs here. {@code settings} has every setting, already checked, and can be added to for values the
    /// ability wants to keep that admins don't set (like an id) - whatever is in it afterwards is saved.
    public void onApply(ItemStack item, Map<String, String> settings) {}

    /// the ability was just taken off the item - undo whatever onApply did
    public void onRemove(ItemStack item) {}

    /// true if this item has this ability
    protected boolean on(ItemStack item) {
        return plugin.getAbilityManager().has(item, this);
    }

    /// this item's settings for this ability, or null if it doesn't have it
    protected Map<String, String> settings(ItemStack item) {
        return plugin.getAbilityManager().getSettings(item, this);
    }
}
