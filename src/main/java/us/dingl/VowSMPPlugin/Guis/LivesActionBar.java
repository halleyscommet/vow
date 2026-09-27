package us.dingl.VowSMPPlugin.Guis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.UUID;

public final class LivesActionBar {

    // how often everyone gets refreshed
    private static final long INTERVAL_TICKS = 2 * 20L;

    private final VowSMPPlugin plugin;
    private BukkitTask task;

    public LivesActionBar(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /// flips the setting for this player and returns the new state
    public boolean toggle(Player player) {
        boolean enabled = !plugin.isPlayerLivesShown(player.getUniqueId());
        setEnabled(player, enabled);
        return enabled;
    }

    public void setEnabled(Player player, boolean enabled) {
        plugin.setPlayerLivesShown(player.getUniqueId(), enabled);
        if (enabled) {
            show(player);
        } else {
            player.sendActionBar(Component.empty()); // clear it right away
        }
    }

    /// refreshes one player now (no-op if they're offline or have it turned off)
    public void update(UUID id) {
        if (!plugin.isPlayerLivesShown(id)) return;
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            show(player);
        }
    }

    public void updateAll() {
        for (UUID id : plugin.getPlayerLivesShown()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                show(player);
            }
        }
    }

    private void show(Player player) {
        int lives = plugin.getPlayerLives(player.getUniqueId());
        NamedTextColor color = lives <= 1 ? NamedTextColor.RED
                : lives == 2 ? NamedTextColor.YELLOW
                : NamedTextColor.GREEN;
        player.sendActionBar(Component.text("Lives: " + lives + "/" + VowSMPPlugin.MAX_LIVES, color));
    }
}