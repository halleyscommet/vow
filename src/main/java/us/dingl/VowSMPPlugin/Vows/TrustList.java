package us.dingl.VowSMPPlugin.Vows;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// who each holder of a targeting vow has trusted, so their abilities skip them. Saved in config under
/// its own key per vow. Edited with /vow trust (see {@link TrustingVow}).
///
/// Targeting reads an in-memory copy, never the config. The copy is rebuilt from config on enable and
/// before every /vow command, so hand edits to the config are picked up too.
public final class TrustList implements Listener {

    private final VowSMPPlugin plugin;
    private final ConfigKey key;
    /// holder -> players they trust. Concurrent because tab completion reads it off the main thread.
    private final Map<UUID, Set<UUID>> trusted = new ConcurrentHashMap<>();

    public TrustList(VowSMPPlugin plugin, ConfigKey key) {
        this.plugin = plugin;
        this.key = key;
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /// read-only live view of who this holder trusts
    public Set<UUID> getTrusted(UUID holder) {
        Set<UUID> set = trusted.get(holder);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public boolean isTrusted(Player holder, Player other) {
        Set<UUID> set = trusted.get(holder.getUniqueId());
        return set != null && set.contains(other.getUniqueId());
    }

    /// updates the set and writes it to config. Returns false if nothing changed.
    public boolean setTrusted(UUID holder, UUID other, boolean trust) {
        Set<UUID> set = trusted.computeIfAbsent(holder, id -> ConcurrentHashMap.newKeySet());
        boolean changed = trust ? set.add(other) : set.remove(other);
        if (!changed) {
            if (set.isEmpty()) trusted.remove(holder);
            return false;
        }

        String path = key.getPath() + "." + holder;
        if (set.isEmpty()) {
            trusted.remove(holder);
            plugin.getConfig().set(path, null);
        } else {
            plugin.getConfig().set(path, set.stream().map(UUID::toString).toList());
        }
        plugin.saveConfig();
        return true;
    }

    private void load() {
        trusted.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(key.getPath());
        if (section == null) return;

        for (String holderKey : section.getKeys(false)) {
            UUID holder = parseUuid(holderKey);
            if (holder == null) continue;

            Set<UUID> set = ConcurrentHashMap.newKeySet();
            for (String otherKey : section.getStringList(holderKey)) {
                UUID other = parseUuid(otherKey);
                if (other != null) set.add(other);
            }
            if (!set.isEmpty()) trusted.put(holder, set);
        }
    }

    private UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ignoring invalid UUID in " + key.getPath() + ": " + s);
            return null;
        }
    }

    // both fire before the command runs, so /vow trust itself sees the fresh sets
    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isVowCommand(event.getMessage().substring(1))) load();
    }

    @EventHandler(ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (isVowCommand(event.getCommand())) load();
    }

    /// "vow ..." or "vowsmpplugin:vow ..."
    private static boolean isVowCommand(String command) {
        String label = command.stripLeading().split(" ", 2)[0];
        label = label.substring(label.indexOf(':') + 1);
        return label.equalsIgnoreCase("vow");
    }
}
