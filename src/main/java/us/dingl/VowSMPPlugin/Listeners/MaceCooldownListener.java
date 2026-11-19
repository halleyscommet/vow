package us.dingl.VowSMPPlugin.Listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// every mace hit on an entity puts maces on cooldown for a while (the vanilla item cooldown, so the client
/// shows it), and mace hits are blocked until it wears off. Mythic maces skip all of this.
/// The length is in config (seconds, 0 turns it off) and set with /vow admin mace cooldown or the admin panel.
public class MaceCooldownListener implements Listener {

    public static final int MAX_SECONDS = 3600;

    private final VowSMPPlugin plugin;
    /// copy of the config, read on every hit
    private int seconds;

    public MaceCooldownListener(VowSMPPlugin plugin) {
        this.plugin = plugin;
        this.seconds = Math.clamp(plugin.getConfig().getInt(ConfigKey.MACE_COOLDOWN.getPath(), 0), 0, MAX_SECONDS);
    }

    public int getSeconds() {
        return seconds;
    }

    /// clamped to 0..MAX_SECONDS, returns what it was set to
    public int setSeconds(int value) {
        seconds = Math.clamp(value, 0, MAX_SECONDS);
        plugin.getConfig().set(ConfigKey.MACE_COOLDOWN.getPath(), seconds);
        plugin.saveConfig();
        return seconds;
    }

    /// still cooling down - no hit
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        ItemStack mace = cooledMace(event);
        if (mace == null) return;
        Player player = (Player) event.getDamager();
        if (player.getCooldown(mace) > 0) {
            event.setCancelled(true);
        }
    }

    /// the hit went through - start the cooldown
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitLanded(EntityDamageByEntityEvent event) {
        ItemStack mace = cooledMace(event);
        if (mace == null) return;
        ((Player) event.getDamager()).setCooldown(mace, seconds * 20);
    }

    /// the mace in the attacker's hand if this is a melee hit with a non-mythic mace and the cooldown is on
    private ItemStack cooledMace(EntityDamageByEntityEvent event) {
        if (seconds <= 0) return null;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return null;
        if (!(event.getDamager() instanceof Player player)) return null;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.MACE) return null;
        if (plugin.getMythicManager().getMythic(item) != null) return null;
        return item;
    }
}
