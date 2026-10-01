package us.dingl.VowSMPPlugin.Vows;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// base class for every vow. Override only the hooks you need - everything defaults to "do nothing".
///
/// Every vow is also registered as a Listener, so you can add @EventHandler methods for anything
/// the hooks don't cover. Those fire for EVERY player though, so guard them with {@link #has(Player)}.
public abstract class Vow implements Listener {

    protected final VowSMPPlugin plugin;

    protected Vow(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    /// unique lowercase id, used in config and commands. Don't change it once players have the vow.
    public abstract String id();

    public abstract String name();

    public abstract String description();

    /// item shown for this vow in the vows menu
    public Material icon() {
        return Material.ENCHANTED_BOOK;
    }

    /// the player just got this vow (only called if they're online at the time)
    public void onGain(Player player) {}

    /// the player just lost this vow (only called if they're online at the time)
    public void onLose(Player player) {}

    /// runs once a second for every online player with this vow. Good for passive effects -
    /// give potion effects a slightly longer duration than a second so they don't blink off.
    public void tick(Player player, int lives) {}

    /// how many lives the player loses on this death. {@code lost} is the default (1).
    /// Whatever is lost goes to the killer, or drops as life items if they can't take it.
    public int livesLostOnDeath(Player player, int livesBefore, int lost) {
        return lost;
    }

    /// shown in the player's action bar (next to their lives if they show them). null for nothing.
    /// Call {@code plugin.getLivesActionBar().update(id)} when it changes so it shows right away.
    public Component actionBarStatus(Player player) {
        return null;
    }

    /// true if this player currently has this vow - use it to guard @EventHandler methods
    protected boolean has(Player player) {
        return plugin.getVowManager().getVow(player.getUniqueId()) == this;
    }
}
