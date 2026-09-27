package us.dingl.VowSMPPlugin.Listeners.Ritual;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import us.dingl.VowSMPPlugin.Items.RitualItem;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class AltarClickListener implements Listener {

    private final VowSMPPlugin plugin;
    private final NamespacedKey idKey;

    public AltarClickListener(VowSMPPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "display_id");
    }

    private final RitualItem item = new RitualItem();

    @EventHandler
    public void onAltarClick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction hitbox)) {
            return;
        }

        String id = hitbox.getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING);

        if (!"vow_altar".equals(id)) {
            return;
        }

        // fires once per hand; both hands are checked below, so only handle the main-hand one
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();

        EquipmentSlot slot;
        if (item.isRitualItem(player.getInventory().getItemInMainHand())) {
            slot = EquipmentSlot.HAND;
        } else if (item.isRitualItem(player.getInventory().getItemInOffHand())) {
            slot = EquipmentSlot.OFF_HAND;
        } else {
            return;
        }

        event.setCancelled(true);

        if (plugin.getPlayerLives(player.getUniqueId()) != 0) {
            player.sendMessage(Component.text("You already have 1 or more lives, you cannot start a ritual."));
            return;
        }

        // only take the sword if the ritual actually starts
        if (!plugin.getStorm().start(player, true)) {
            player.sendMessage(Component.text("A ritual is already in progress."));
            return;
        }
        player.getInventory().setItem(slot, new ItemStack(Material.AIR));
    }
}
