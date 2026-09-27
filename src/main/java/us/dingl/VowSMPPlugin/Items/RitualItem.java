package us.dingl.VowSMPPlugin.Items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.ArrayList;
import java.util.List;

public final class RitualItem {

    private static final Component NAME = Component.text("Curtana", NamedTextColor.BLUE)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false);

    private static final List<Component> LORE = new ArrayList<>(
            List.of(
                    Component.text(""),
                    Component.text("Plunge this sword into the ")
                            .append(Component.text("ALTAR").color(NamedTextColor.GOLD)),
                    Component.text("at X:0, Z:0")
            )
    );

    private static NamespacedKey key;

    public static void init(VowSMPPlugin plugin) {
        key = new NamespacedKey(plugin, "ritual_item");
    }

    public RitualItem() {}

    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.IRON_SWORD, amount);
        item.editMeta(meta -> {
            meta.customName(NAME);
            meta.lore(LORE);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public boolean isRitualItem(ItemStack item) {
        if (item == null || item.getType() != Material.IRON_SWORD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
