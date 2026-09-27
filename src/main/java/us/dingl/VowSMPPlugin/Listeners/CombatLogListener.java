package us.dingl.VowSMPPlugin.Listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// Anyone who hits or gets hit by another player is "in combat" for a bit.
/// Disconnecting while in combat kills them, so logging out can't be used to dodge a death.
public class CombatLogListener implements Listener {

    private static final long TAG_MS = 15_000L;

    private final Map<UUID, Long> taggedUntil = new HashMap<>();

    // MONITOR + ignoreCancelled: only real hits count (not ones blocked by e.g. the resource pack protection)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (!(damager instanceof Player attacker) || attacker.equals(victim)) return;

        tag(victim);
        tag(attacker);
    }

    private void tag(Player player) {
        long now = System.currentTimeMillis();
        Long previous = taggedUntil.put(player.getUniqueId(), now + TAG_MS);
        if (previous == null || previous < now) {
            player.sendMessage(Component.text("You're in combat. Logging out now will kill you.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        taggedUntil.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Long until = taggedUntil.remove(player.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return;

        // kicks (including the server shutting down) and timeouts aren't the player's choice
        if (event.getReason() != PlayerQuitEvent.QuitReason.DISCONNECTED || Bukkit.isStopping()) return;
        if (player.isDead()) return;

        Bukkit.broadcast(Component.text(player.getName() + " logged out during combat!", NamedTextColor.RED));
        player.setHealth(0); // goes through DeathListener like any other death
    }
}
