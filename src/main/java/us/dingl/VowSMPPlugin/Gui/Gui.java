package us.dingl.VowSMPPlugin.Gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Gui {

    private final GuiInstance instance;

    private Gui(Component title, int rows) {
        this.instance = new GuiInstance(title, rows);
    }

    public static Gui builder(Component title, int rows) {
        return new Gui(title, rows);
    }

    public Gui button(int slot, Supplier<ItemStack> itemSupplier, ClickHandler onClick) {
        instance.addButton(new GuiButton(slot, itemSupplier, onClick));
        return this;
    }

    public Gui button(int slot, StateBinding<?> binding, ClickHandler onClick) {
        return button(slot, binding.asSupplier(), onClick);
    }

    public Gui button(int slot, ItemStack item, ClickHandler onClick) {
        return button(slot, () -> item, onClick);
    }

    public Gui filler(int slot, ItemStack item) {
        return button(slot, item, (player, clickType) -> {
        });
    }

    public Gui filler(Set<Integer> slots, ItemStack item) {
        for (int slot : slots) {
            filler(slot, item);
        }
        return this;
    }

    public Gui zone(String id, SlotPermission permission, int... slots) {
        Set<Integer> slotSet = new HashSet<>();
        for (int slot : slots) slotSet.add(slot);
        instance.addZone(new GuiZone(id, slotSet, permission));
        return this;
    }

    public Gui zone(String id, SlotPermission permission, Set<Integer> slots) {
        instance.addZone(new GuiZone(id, slots, permission));
        return this;
    }

    public Gui onClose(Consumer<Player> onClose) {
        instance.setOnClose(onClose);
        return this;
    }

    public GuiInstance build() {
        return instance;
    }
}