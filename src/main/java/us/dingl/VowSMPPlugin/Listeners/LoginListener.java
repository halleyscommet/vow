package us.dingl.VowSMPPlugin.Listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class LoginListener implements Listener {

    private final VowSMPPlugin plugin;

    public LoginListener(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerLogin(PlayerJoinEvent event) {
        plugin.addPlayerToLog(event.getPlayer().getUniqueId());
        plugin.getLivesActionBar().update(event.getPlayer().getUniqueId());
    }
}
