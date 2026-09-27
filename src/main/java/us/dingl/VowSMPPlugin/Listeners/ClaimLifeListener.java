package us.dingl.VowSMPPlugin.Listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.Items.LifeItem;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class ClaimLifeListener implements Listener {

    private final VowSMPPlugin plugin;

    public ClaimLifeListener(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLifeClaim(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!LifeItem.isLife(item)) return;

        event.setCancelled(true);

        int current = plugin.getPlayerLives(player.getUniqueId());
        if (current >= VowSMPPlugin.MAX_LIVES) {
            player.sendMessage(Component.text("You already have the maximum amount of lives (" + VowSMPPlugin.MAX_LIVES + ")!", NamedTextColor.RED));
            return;
        }

        if (current <= 0) {
            player.sendMessage(Component.text("You cannot claim lives while at 0 lives!", NamedTextColor.RED));
            return;
        }

        int toClaim = Math.min(item.getAmount(), VowSMPPlugin.MAX_LIVES - current);
        plugin.updatePlayerLifeCounter(player.getUniqueId(), toClaim);
        item.setAmount(item.getAmount() - toClaim);

        Component actionBarText = Component.text("Claimed " + toClaim + ((toClaim > 1) ? " lives!" : " life!")).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);

        player.sendActionBar(actionBarText);
    }
}