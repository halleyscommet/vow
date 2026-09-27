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

        if (plugin.addPlayerToKilled(id)) {
            Bukkit.broadcast(Component.text(player.getName() + " has died for the first time! o7",
                    NamedTextColor.RED, TextDecoration.BOLD));
        }

        int before = plugin.getPlayerLives(id);
        int remaining = plugin.updatePlayerLifeCounter(id, -1);
        if (remaining <= 0) {
            Bukkit.broadcast(Component.text(player.getName() + " has zero lives!",
                    NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }

        // killer gets a life back, but not for killing themselves, and only if the victim
        // actually lost one - otherwise killing someone at 0 lives over and over would farm lives
        if (killer != null && !killer.equals(player) && remaining < before) {
            UUID killerId = killer.getUniqueId();
            int killerLives = plugin.getPlayerLives(killerId);

            if (killerLives > 0) {
                if (killerLives >= VowSMPPlugin.MAX_LIVES) {
                    player.getWorld().dropItemNaturally(player.getLocation(), LifeItem.create(1));
                } else {
                    plugin.updatePlayerLifeCounter(killerId, 1);
                }
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), LifeItem.create(1));
            }
        }
    }
}
