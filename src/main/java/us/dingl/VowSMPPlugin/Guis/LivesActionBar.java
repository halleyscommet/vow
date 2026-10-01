package us.dingl.VowSMPPlugin.Guis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.UUID;

/// the lives counter (for players who turned it on) plus any status their vow shows, on one line
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
        Component text = build(player);
        // clear it right away when there's nothing left to show
        player.sendActionBar(text != null ? text : Component.empty());
    }

    /// refreshes one player now (no-op if they're offline or have nothing to show)
    public void update(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            show(player);
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player);
        }
    }

    private void show(Player player) {
        // sending nothing (instead of an empty bar) leaves other plugins' action bar messages alone
        Component text = build(player);
        if (text != null) {
            player.sendActionBar(text);
        }
    }

    /// null if there's nothing to show
    private Component build(Player player) {
        UUID id = player.getUniqueId();
        Component lives = null;
        if (plugin.isPlayerLivesShown(id)) {
            int count = plugin.getPlayerLives(id);
            NamedTextColor color = count <= 1 ? NamedTextColor.RED
                    : count == 2 ? NamedTextColor.YELLOW
                    : NamedTextColor.GREEN;
            lives = Component.text("Lives: " + count + "/" + VowSMPPlugin.MAX_LIVES, color);
        }

        Vow vow = plugin.getVowManager().getVow(id);
        Component status = vow == null ? null : vow.actionBarStatus(player);

        if (lives == null) return status;
        if (status == null) return lives;
        return lives.append(Component.text("  |  ", NamedTextColor.DARK_GRAY)).append(status);
    }
}