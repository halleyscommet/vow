package us.dingl.VowSMPPlugin.Gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;

public class GuiInstance {

    private final int size;
    private final Inventory inventory;
    private final GuiHolder holder;

    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private final List<GuiZone> zones = new ArrayList<>();
    private final Map<Integer, GuiZone> zoneBySlot = new HashMap<>();

    private Consumer<Player> onClose;
    private boolean closed = false;

    GuiInstance(Component title, int rows) {
        this.size = rows * 9;
        this.holder = new GuiHolder(this);
        this.inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
    }

    void addButton(GuiButton button) {
        buttons.put(button.getSlot(), button);
        inventory.setItem(button.getSlot(), button.renderItem());
    }


    void addZone(GuiZone zone) {
        zones.add(zone);
        for (int slot : zone.slots()) {
            zoneBySlot.put(slot, zone);
        }
    }

    void setOnClose(Consumer<Player> onClose) {
        this.onClose = onClose;
    }

    public Optional<GuiButton> getButton(int slot) {
        return Optional.ofNullable(buttons.get(slot));
    }

    public Optional<GuiZone> getZoneForSlot(int slot) {
        return Optional.ofNullable(zoneBySlot.get(slot));
    }

    public int getSize() {
        return size;
    }

    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Re-asserts every button's item. Cheap insurance against any client-side desync.
     */
    public void resyncButtons() {
        for (GuiButton button : buttons.values()) {
            inventory.setItem(button.getSlot(), button.renderItem());
        }
    }

    void handleClose(Player player) {
        if (closed) return; // guard against double-fire (close event + quit event both landing)
        closed = true;
        if (onClose != null) {
            onClose.accept(player);
        }
    }

    public void open(Player player) {
        clearCursorSafely(player);
        closed = false;
        player.openInventory(inventory);
    }

    /**
     * Returns whatever's on the player's cursor to their inventory (or drops it) before opening a GUI.
     */
    private static void clearCursorSafely(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType() == Material.AIR) return;

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(cursor);
        leftover.values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item));
        player.setItemOnCursor(null);
    }
}