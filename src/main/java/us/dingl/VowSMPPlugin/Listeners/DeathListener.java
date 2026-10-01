package us.dingl.VowSMPPlugin.Listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import us.dingl.VowSMPPlugin.Items.LifeItem;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.UUID;

public class DeathListener implements Listener {

    private final VowSMPPlugin plugin;

    public DeathListener(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Player killer = player.getKiller();

        if (plugin.getLeaderboardEnabled()) {
            if (plugin.addPlayerToKilled(id)) {
                Bukkit.broadcast(Component.text(player.getName() + " has died for the first time! o7",
                        NamedTextColor.RED, TextDecoration.BOLD));
            }
        }

        // Collera invitees have no lives to lose or drop - dying just ends their invite
        if (plugin.isInvited(id)) {
            plugin.removeInvite(id);
            // kicking mid-death is messy, so wait a tick
            Bukkit.getScheduler().runTask(plugin, () -> player.kick(
                    Component.text("You died, so your invite is over.", NamedTextColor.RED)));
            return;
        }

        int before = plugin.getPlayerLives(id);
        int toLose = plugin.getVowManager().livesLostOnDeath(player, before);
        int remaining = plugin.updatePlayerLifeCounter(id, -toLose);
        if (remaining <= 0) {
            Bukkit.broadcast(Component.text(player.getName() + " has zero lives!",
                    NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }

        // only hand out lives the victim actually lost - otherwise killing someone
        // at 0 lives over and over would farm lives
        int lost = before - remaining;
        if (lost <= 0) return;

        // natural causes (or killing themselves): the lives drop where they died instead of vanishing
        if (killer == null || killer.equals(player)) {
            player.getWorld().dropItemNaturally(player.getLocation(), LifeItem.create(lost));
            return;
        }

        // killer takes as many as they can hold (none if they're at 0), the rest drop
        UUID killerId = killer.getUniqueId();
        int killerLives = plugin.getPlayerLives(killerId);
        int given = killerLives > 0 ? Math.min(lost, VowSMPPlugin.MAX_LIVES - killerLives) : 0;

        if (given > 0) {
            plugin.updatePlayerLifeCounter(killerId, given);
        }
        if (lost > given) {
            player.getWorld().dropItemNaturally(player.getLocation(), LifeItem.create(lost - given));
        }
    }
}
