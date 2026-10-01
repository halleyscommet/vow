package us.dingl.VowSMPPlugin.Vows;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VowManager {

    private final VowSMPPlugin plugin;
    private final Map<String, Vow> vows = new LinkedHashMap<>();
    private final Map<UUID, Vow> assigned = new ConcurrentHashMap<>();
    private BukkitTask tickTask;

    public VowManager(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Vow vow) {
        if (vows.putIfAbsent(vow.id(), vow) != null) {
            throw new IllegalArgumentException("Duplicate vow id: " + vow.id());
        }
        plugin.getServer().getPluginManager().registerEvents(vow, plugin);
    }

    /// call after every vow is registered - assignments pointing at unknown vows are skipped
    public void start() {
        load();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
    }

    public Collection<Vow> getVows() {
        return Collections.unmodifiableCollection(vows.values());
    }

    public Vow getVowById(String id) {
        return vows.get(id.toLowerCase(Locale.ROOT));
    }

    /// null if the player has no vow
    public Vow getVow(UUID id) {
        return assigned.get(id);
    }

    /// everyone (online or not) who has this vow
    public List<UUID> getHolders(Vow vow) {
        return assigned.entrySet().stream()
                .filter(e -> e.getValue() == vow)
                .map(Map.Entry::getKey)
                .toList();
    }

    /// pass null to clear the player's vow
    public void setVow(UUID id, Vow vow) {
        Vow old = vow == null ? assigned.remove(id) : assigned.put(id, vow);
        if (old == vow) return;

        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            if (old != null) old.onLose(player);
            if (vow != null) vow.onGain(player);
            // some commands are only visible with certain vows (like /vow 3x3)
            player.updateCommands();
        }

        plugin.getConfig().set(ConfigKey.PLAYER_VOWS.getPath() + "." + id, vow == null ? null : vow.id());
        plugin.saveConfig();
    }

    /// how many lives this death should cost, after the player's vow gets a say. Never negative.
    public int livesLostOnDeath(Player player, int livesBefore) {
        Vow vow = assigned.get(player.getUniqueId());
        int lost = vow == null ? 1 : vow.livesLostOnDeath(player, livesBefore, 1);
        return Math.max(0, lost);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Vow vow = assigned.get(player.getUniqueId());
            if (vow != null) {
                vow.tick(player, plugin.getPlayerLives(player.getUniqueId()));
            }
        }
    }

    private void load() {
        assigned.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKey.PLAYER_VOWS.getPath());
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String vowId = section.getString(key, "");
            Vow vow = getVowById(vowId);
            if (vow == null) {
                plugin.getLogger().warning("Ignoring unknown vow '" + vowId + "' for " + key);
                continue;
            }
            try {
                assigned.put(UUID.fromString(key), vow);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid UUID in player-vows: " + key);
            }
        }
    }
}
