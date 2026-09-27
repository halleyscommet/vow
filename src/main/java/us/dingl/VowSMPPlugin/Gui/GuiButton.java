package us.dingl.VowSMPPlugin.Gui;

import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public class GuiButton {

    private final int slot;
    private final Supplier<ItemStack> itemSupplier;
    private final ClickHandler onClick;

    public GuiButton(int slot, Supplier<ItemStack> itemSupplier, ClickHandler onClick) {
        this.slot = slot;
        this.itemSupplier = itemSupplier;
        this.onClick = onClick;
    }

    public int getSlot() { return slot; }
    public ClickHandler getOnClick() { return onClick; }

    /** Called on placement and again on every resync - re-reads current state. */
    public ItemStack renderItem() {
        return itemSupplier.get();
    }
}