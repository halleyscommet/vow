package us.dingl.VowSMPPlugin.Vows.Impl;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
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
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.Iterator;

/// gives an unbreakable Knockback III diamond sword that stays in her inventory when she dies.
/// The sword can't be dropped or stored anywhere (chests, bundles, item frames...) so it never leaves her inventory.
public class CharlotteVow extends Vow {

    private final NamespacedKey swordKey;

    public CharlotteVow(VowSMPPlugin plugin) {
        super(plugin);
        swordKey = new NamespacedKey(plugin, "charlotte_sword");
    }

    @Override
    public String id() {
        return "charlotte";
    }

    @Override
    public String name() {
        return "Charlotte vow";
    }

    @Override
    public String description() {
        return "Gives an unbreakable Knockback III diamond sword that you keep when you die and can't drop or store.";
    }

    @Override
    public Material icon() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    public void onGain(Player player) {
        player.give(createSword());
    }

    @Override
    public void onLose(Player player) {
        // the sword belongs to the vow, so it goes away with it (includes offhand)
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isSword(contents[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!has(event.getPlayer())) return;

        // pull the sword out of the drops and put it back in her inventory on respawn
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack item = drops.next();
            if (isSword(item)) {
                drops.remove();
                event.getItemsToKeep().add(item);
            }
        }
    }

    // everything below keeps the sword in the player's own inventory. These apply to anyone
    // holding it, not just vow holders, so it can't be passed around either.

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isSword(event.getItemDrop().getItemStack())) {
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
                if (isSword(cursor)) event.setCancelled(true);
                return;
            }
            case DROP_ALL_SLOT, DROP_ONE_SLOT -> {
                if (isSword(current)) event.setCancelled(true);
                return;
            }
            default -> { }
        }

        // bundles can hold a sword, and then the bundle could go anywhere
        if ((isSword(cursor) && isBundle(current)) || (isBundle(cursor) && isSword(current))) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        if (clicked.equals(top)) {
            // putting it into the container: placing from the cursor, number keys, or F for offhand
            boolean placing = isSword(cursor)
                    || (event.getClick() == ClickType.NUMBER_KEY
                        && isSword(player.getInventory().getItem(event.getHotbarButton())))
                    || (event.getClick() == ClickType.SWAP_OFFHAND
                        && isSword(player.getInventory().getItemInOffHand()));
            if (placing) event.setCancelled(true);
        } else if (event.isShiftClick() && isSword(current) && top.getType() != InventoryType.CRAFTING) {
            // shift-click from their inventory into an open container. CRAFTING is the player's
            // own inventory screen, where shift-click only moves it between hotbar and main slots
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isSword(event.getOldCursor())) return;

        // raw slots below the top inventory's size are in the container
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        boolean takesItems = event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof Allay;
        if (takesItems && isSword(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isSword(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !isSword(event.getItem())) return;

        // decorated pots and shelves take the held item on right click, no inventory screen involved
        Material type = block.getType();
        if (type == Material.DECORATED_POT || type.name().endsWith("_SHELF")) {
            event.setCancelled(true);
        }
    }

    private ItemStack createSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> {
            // unsafe because knockback normally caps at II
            meta.addEnchant(Enchantment.KNOCKBACK, 3, true);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(swordKey, PersistentDataType.BYTE, (byte) 1);
        });
        return sword;
    }

    private boolean isBundle(ItemStack item) {
        return item != null && Tag.ITEMS_BUNDLES.isTagged(item.getType());
    }

    private boolean isSword(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(swordKey, PersistentDataType.BYTE);
    }
}
