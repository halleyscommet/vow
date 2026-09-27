package us.dingl.VowSMPPlugin.Gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class GuiHolder implements InventoryHolder {

    private final GuiInstance instance;
    private Inventory inventory;

    GuiHolder(GuiInstance instance) {
        this.instance = instance;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public GuiInstance getInstance() {
        return instance;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}