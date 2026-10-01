package us.dingl.VowSMPPlugin.Vows;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.Iterator;

/// a vow that comes with one soulbound item: given on gain, removed on loss, kept on death,
/// and it can't be dropped or stored anywhere (chests, bundles, item frames...) so it never leaves her inventory.
public abstract class SoulboundItemVow extends Vow {

    private final NamespacedKey itemKey;

    /// {@code itemKey} tags the item in its persistent data - don't change it once players have the item
    protected SoulboundItemVow(VowSMPPlugin plugin, String itemKey) {
        super(plugin);
        this.itemKey = new NamespacedKey(plugin, itemKey);
    }

    /// build the item. It gets tagged as soulbound automatically.
    protected abstract ItemStack buildItem();

    @Override
    public void onGain(Player player) {
        player.give(createItem());
    }

    @Override
    public void onLose(Player player) {
        // the item belongs to the vow, so it goes away with it (includes offhand)
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isSoulbound(contents[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!has(event.getPlayer())) return;

        // pull the item out of the drops and put it back in her inventory on respawn
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack item = drops.next();
            if (isSoulbound(item)) {
                drops.remove();
                event.getItemsToKeep().add(item);
            }
        }
    }

    // everything below keeps the item in the player's own inventory. These apply to anyone
    // holding it, not just vow holders, so it can't be passed around either.

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isSoulbound(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // dropping from an open inventory (Q on a slot, or clicking outside the window)
        switch (event.getAction()) {
            case DROP_ALL_CURSOR, DROP_ONE_CURSOR -> {
                if (isSoulbound(cursor)) event.setCancelled(true);
                return;
            }
            case DROP_ALL_SLOT, DROP_ONE_SLOT -> {
                if (isSoulbound(current)) event.setCancelled(true);
                return;
            }
            default -> { }
        }

        // bundles can hold the item, and then the bundle could go anywhere
        if ((isSoulbound(cursor) && isBundle(current)) || (isBundle(cursor) && isSoulbound(current))) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        if (clicked.equals(top)) {
            // putting it into the container: placing from the cursor, number keys, or F for offhand
            boolean placing = isSoulbound(cursor)
                    || (event.getClick() == ClickType.NUMBER_KEY
                        && isSoulbound(player.getInventory().getItem(event.getHotbarButton())))
                    || (event.getClick() == ClickType.SWAP_OFFHAND
                        && isSoulbound(player.getInventory().getItemInOffHand()));
            if (placing) event.setCancelled(true);
        } else if (event.isShiftClick() && isSoulbound(current) && top.getType() != InventoryType.CRAFTING) {
            // shift-click from their inventory into an open container. CRAFTING is the player's
            // own inventory screen, where shift-click only moves it between hotbar and main slots
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isSoulbound(event.getOldCursor())) return;

        // raw slots below the top inventory's size are in the container
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        boolean takesItems = event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof Allay;
        if (takesItems && isSoulbound(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isSoulbound(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !isSoulbound(event.getItem())) return;

        // decorated pots and shelves take the held item on right click, no inventory screen involved
        Material type = block.getType();
        if (type == Material.DECORATED_POT || type.name().endsWith("_SHELF")) {
            event.setCancelled(true);
        }
    }

    private ItemStack createItem() {
        ItemStack item = buildItem();
        item.editMeta(meta -> meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    private boolean isBundle(ItemStack item) {
        return item != null && Tag.ITEMS_BUNDLES.isTagged(item.getType());
    }

    /// true if this is this vow's item (not some other vow's soulbound item)
    protected boolean isSoulbound(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }
}
