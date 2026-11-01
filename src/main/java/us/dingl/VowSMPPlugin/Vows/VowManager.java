package us.dingl.VowSMPPlugin.Vows;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/// tracks which vows each player has. A player can hold several: everyone gets one slot, and every
/// life count listed under extra-vow-lives in the config unlocks another while they have at least that many.
/// Dropping below one takes away their newest vows until they fit again.
public class VowManager implements Listener {

    public enum AddResult { ADDED, ALREADY_HAS, NO_FREE_SLOT }

    private final VowSMPPlugin plugin;
    private final Map<String, Vow> vows = new LinkedHashMap<>();
    /// oldest first - when slots run out, vows go from the end
    private final Map<UUID, List<Vow>> assigned = new ConcurrentHashMap<>();
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
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        // after a reload people might be holding more vows than their lives allow
        Bukkit.getOnlinePlayers().forEach(p -> enforceSlots(p.getUniqueId()));
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

    /// every vow the player has, oldest first. Empty if none.
    public List<Vow> getVows(UUID id) {
        return assigned.getOrDefault(id, List.of());
    }

    public boolean hasVow(UUID id, Vow vow) {
        return getVows(id).contains(vow);
    }

    /// the player's vow of this type, or null if they don't have one - for vow-specific commands
    public <T extends Vow> T getVow(UUID id, Class<T> type) {
        for (Vow vow : getVows(id)) {
            if (type.isInstance(vow)) return type.cast(vow);
        }
        return null;
    }

    /// everyone (online or not) who has this vow
    public List<UUID> getHolders(Vow vow) {
        return assigned.entrySet().stream()
                .filter(e -> e.getValue().contains(vow))
                .map(Map.Entry::getKey)
                .toList();
    }

    /// how many vows this player can hold right now
    public int getSlots(UUID id) {
        int lives = plugin.getPlayerLives(id);
        int slots = 1;
        for (int needed : plugin.getConfig().getIntegerList(ConfigKey.EXTRA_VOW_LIVES.getPath())) {
            if (lives >= needed) slots++;
        }
        return slots;
    }

    /// the fewest lives that unlock another slot past the ones they have now, or -1 if there are no more
    public int livesForNextSlot(UUID id) {
        int lives = plugin.getPlayerLives(id);
        return plugin.getConfig().getIntegerList(ConfigKey.EXTRA_VOW_LIVES.getPath()).stream()
                .filter(needed -> needed > lives)
                .min(Integer::compare)
                .orElse(-1);
    }

    /// gives the player another vow, if they have a free slot
    public AddResult addVow(UUID id, Vow vow) {
        List<Vow> current = getVows(id);
        if (current.contains(vow)) return AddResult.ALREADY_HAS;
        if (current.size() >= getSlots(id)) return AddResult.NO_FREE_SLOT;

        List<Vow> updated = new ArrayList<>(current);
        updated.add(vow);
        apply(id, updated);
        return AddResult.ADDED;
    }

    /// takes one vow away. False if they didn't have it.
    public boolean removeVow(UUID id, Vow vow) {
        List<Vow> updated = new ArrayList<>(getVows(id));
        if (!updated.remove(vow)) return false;
        apply(id, updated);
        return true;
    }

    /// replaces all of the player's vows with just this one. Pass null to clear them all.
    public void setVow(UUID id, Vow vow) {
        apply(id, vow == null ? List.of() : List.of(vow));
    }

    /// takes away the player's newest vows until they fit in their slots. Only runs while they're
    /// online and alive, so onLose can clean up properly - otherwise it waits for them to join or respawn.
    public void enforceSlots(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || player.isDead()) return;

        List<Vow> current = getVows(id);
        int slots = getSlots(id);
        if (current.size() <= slots) return;

        List<Vow> kept = current.subList(0, slots);
        List<Vow> lost = current.subList(slots, current.size());
        for (Vow vow : lost) {
            player.sendMessage(Component.text("You no longer have enough lives to hold " + vow.name() + ".",
                    NamedTextColor.RED));
        }
        apply(id, List.copyOf(kept));
    }

    /// how many lives this death should cost. Each vow answers on its own and the harshest one wins,
    /// so the order they were gained in doesn't matter. Never negative.
    public int livesLostOnDeath(Player player, int livesBefore) {
        List<Vow> held = getVows(player.getUniqueId());
        if (held.isEmpty()) return 1;

        int lost = Integer.MIN_VALUE;
        for (Vow vow : held) {
            lost = Math.max(lost, vow.livesLostOnDeath(player, livesBefore, 1));
        }
        return Math.max(0, lost);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        enforceSlots(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // a tick later, once they're alive again and their kept items are back
        UUID id = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> enforceSlots(id));
    }

    private void apply(UUID id, List<Vow> updated) {
        List<Vow> old = getVows(id);
        if (old.equals(updated)) return;

        if (updated.isEmpty()) {
            assigned.remove(id);
        } else {
            assigned.put(id, List.copyOf(updated));
        }

        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            for (Vow vow : old) {
                if (!updated.contains(vow)) vow.onLose(player);
            }
            for (Vow vow : updated) {
                if (!old.contains(vow)) vow.onGain(player);
            }
            // some commands are only visible with certain vows (like /vow 3x3)
            player.updateCommands();
            if (plugin.getLivesActionBar() != null) plugin.getLivesActionBar().update(id);
        }

        plugin.getConfig().set(ConfigKey.PLAYER_VOWS.getPath() + "." + id,
                updated.isEmpty() ? null : updated.stream().map(Vow::id).toList());
        plugin.saveConfig();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<Vow> held = getVows(player.getUniqueId());
            if (held.isEmpty()) continue;

            int lives = plugin.getPlayerLives(player.getUniqueId());
            for (Vow vow : held) {
                vow.tick(player, lives);
            }
        }
    }

    private void load() {
        assigned.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKey.PLAYER_VOWS.getPath());
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid UUID in player-vows: " + key);
                continue;
            }

            // older configs stored a single vow id instead of a list
            List<String> ids = section.isList(key) ? section.getStringList(key) : List.of(section.getString(key, ""));
            List<Vow> held = new ArrayList<>();
            for (String vowId : ids) {
                Vow vow = getVowById(vowId);
                if (vow == null) {
                    plugin.getLogger().warning("Ignoring unknown vow '" + vowId + "' for " + key);
                } else if (!held.contains(vow)) {
                    held.add(vow);
                }
            }
            if (!held.isEmpty()) {
                assigned.put(id, List.copyOf(held));
            }
        }
    }
}
