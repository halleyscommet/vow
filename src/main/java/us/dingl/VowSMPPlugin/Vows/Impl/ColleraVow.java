package us.dingl.VowSMPPlugin.Vows.Impl;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.SoulboundItemVow;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// gives a Sharpness I, Breach IV diamond sword that stays in his inventory when he dies.
/// His wind charges have a 5 second cooldown instead of vanilla's half second.
public class ColleraVow extends SoulboundItemVow {

    private static final int COOLDOWN_TICKS = 5 * 20;

    /// server tick his wind charges are ready again. Item cooldowns reset on relog, so track it here too.
    private final Map<UUID, Integer> readyAt = new HashMap<>();

    public ColleraVow(VowSMPPlugin plugin) {
        super(plugin, "collera_sword");
    }

    @Override
    public String id() {
        return "collera";
    }

    @Override
    public String name() {
        return "Collera vow";
    }

    @Override
    public String description() {
        return "Gives a Sharpness I, Breach IV diamond sword that you keep when you die and can't drop or store. "
                + "Your wind charges have a 5 second cooldown.";
    }

    @Override
    public Material icon() {
        return Material.WIND_CHARGE;
    }

    @Override
    protected ItemStack buildItem() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> {
            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
            // unsafe because breach normally only goes on maces
            meta.addEnchant(Enchantment.BREACH, 4, true);
        });
        return sword;
    }

    @Override
    public void onLose(Player player) {
        super.onLose(player);
        // back to vanilla's cooldown (anything left is at most 5 seconds anyway)
        if (readyAt.remove(player.getUniqueId()) != null) {
            player.setCooldown(Material.WIND_CHARGE, 0);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!has(player)) return;

        // Collera used to be a different vow, so anyone who had it before the sword was added gets it now
        boolean hasSword = Arrays.stream(player.getInventory().getContents()).anyMatch(this::isSoulbound);
        if (!hasSword) {
            onGain(player);
        }

        // put the cooldown back on his wind charges after a relog
        int remaining = remainingCooldown(player);
        if (remaining > 0) {
            player.setCooldown(Material.WIND_CHARGE, remaining);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThrow(PlayerLaunchProjectileEvent event) {
        if (!(event.getProjectile() instanceof WindCharge)) return;
        Player player = event.getPlayer();
        if (!has(player)) return;

        // the item cooldown already blocks throws, this is a backup for anything that resets it
        int remaining = remainingCooldown(player);
        if (remaining > 0) {
            event.setCancelled(true);
            player.setCooldown(Material.WIND_CHARGE, remaining);
            return;
        }

        readyAt.put(player.getUniqueId(), Bukkit.getCurrentTick() + COOLDOWN_TICKS);
        // vanilla sets its half second cooldown right after this event, so ours goes on next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            int left = remainingCooldown(player);
            if (left > 0 && player.isOnline() && has(player)) {
                player.setCooldown(Material.WIND_CHARGE, left);
            }
        });
    }

    private int remainingCooldown(Player player) {
        Integer ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0 : ready - Bukkit.getCurrentTick();
    }
}
