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

public final class LifeItem {

    private static final Component NAME = Component.text(" LIFE ", NamedTextColor.GREEN)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false);

    private static NamespacedKey key;

    private LifeItem() {}

    public static void init(VowSMPPlugin plugin) {
        key = new NamespacedKey(plugin, "life_item");
    }

    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        item.editMeta(meta -> {
            meta.customName(NAME);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static boolean isLife(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
