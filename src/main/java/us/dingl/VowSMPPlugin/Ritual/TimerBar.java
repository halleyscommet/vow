package us.dingl.VowSMPPlugin.Ritual;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// A countdown shown as a boss bar to everyone online. The bar drains as time runs out.
public final class TimerBar implements Listener {

    private final VowSMPPlugin plugin;

    private BossBar bar;
    private BukkitTask task;
    private String label;
    private int totalSeconds;
    private int remaining;
    private Runnable onFinish;

    public TimerBar(VowSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isRunning() {
        return task != null;
    }

    /// Starts (or restarts) the countdown. onFinish runs once when it hits zero, and may be null.
    public void start(String label, int seconds, Runnable onFinish) {
        stop();

        this.label = label;
        this.totalSeconds = Math.max(1, seconds);
        this.remaining = this.totalSeconds;
        this.onFinish = onFinish;

        bar = BossBar.bossBar(title(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bar);
            plugin.getLogger().info(player.getName());
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /// Cancels the countdown and hides the bar. Safe to call when nothing is running.
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
            bar = null;
        }
    }

    private void tick() {
        remaining--;

        if (remaining <= 0) {
            Runnable done = onFinish;
            stop(); // stop first, so onFinish can safely start a new timer
            if (done != null) done.run();
            return;
        }

        bar.name(title());
        bar.progress(Math.clamp((float) remaining / totalSeconds, 0f, 1f));
        bar.color(BossBar.Color.RED);
    }

    private Component title() {
        return Component.text(label + " - " + String.format("%d:%02d", remaining / 60, remaining % 60));
    }

    // people who join mid-countdown should see it too
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bar != null) {
            event.getPlayer().showBossBar(bar);
        }
    }
}
