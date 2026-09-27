package us.dingl.VowSMPPlugin.Gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiInstance instance = holder.getInstance();

        event.setCancelled(true); // default: nothing is allowed

        int rawSlot = event.getRawSlot();

        if (rawSlot >= 0 && rawSlot < instance.getSize()) {
            // click landed inside the GUI itself
            instance.getButton(rawSlot).ifPresentOrElse(
                    button -> button.getOnClick().onClick(player, event.getClick()),
                    () -> {
                        GuiZone zone = instance.getZoneForSlot(rawSlot).orElse(null);
                        SlotPermission permission = zone != null ? zone.permission() : SlotPermission.NONE;
                        if (isActionAllowed(event.getAction(), permission)) {
                            event.setCancelled(false);
                        }
                    }
            );
        } else {
            // click landed in the player's own inventory (bottom half).
            // Shift-clicks and double-click "collect to cursor" can both pull items out of / into the GUI.
            InventoryAction action = event.getAction();
            if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY
                    && action != InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(false);
            }
        }

        // defensive resync one tick later regardless of outcome
        Bukkit.getScheduler().runTask(GuiManager.getPlugin(), instance::resyncButtons);
    }

    private boolean isActionAllowed(InventoryAction action, SlotPermission permission) {
        if (permission == SlotPermission.NONE) return false;
        if (permission == SlotPermission.FULL) return true;

        boolean removesFromSlot = switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 COLLECT_TO_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD,
                 MOVE_TO_OTHER_INVENTORY, SWAP_WITH_CURSOR,
                 DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> false;
        };

        boolean insertsIntoSlot = switch (action) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP -> true;
            default -> false;
        };

        return switch (permission) {
            case INSERT_ONLY -> insertsIntoSlot && !removesFromSlot;
            case EXTRACT_ONLY -> removesFromSlot && !insertsIntoSlot;
            default -> false; // NONE/FULL already handled above
        };
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        GuiInstance instance = holder.getInstance();

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 0 || rawSlot >= instance.getSize()) continue;

            if (instance.getButton(rawSlot).isPresent()) {
                event.setCancelled(true);
                return;
            }

            GuiZone zone = instance.getZoneForSlot(rawSlot).orElse(null);
            SlotPermission permission = zone != null ? zone.permission() : SlotPermission.NONE;
            if (permission != SlotPermission.FULL && permission != SlotPermission.INSERT_ONLY) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder
                && event.getPlayer() instanceof Player player) {
            holder.getInstance().handleClose(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // InventoryCloseEvent doesn't reliably fire on disconnect - handle it explicitly
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holder) {
            holder.getInstance().handleClose(event.getPlayer());
        }
    }
}