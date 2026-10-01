package us.dingl.VowSMPPlugin.Vows.Impl;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.SoulboundItemVow;

/// gives an unbreakable Knockback III diamond sword that stays in her inventory when she dies.
/// The sword can't be dropped or stored anywhere (chests, bundles, item frames...) so it never leaves her inventory.
public class CharlotteVow extends SoulboundItemVow {

    public CharlotteVow(VowSMPPlugin plugin) {
        super(plugin, "charlotte_sword");
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
    protected ItemStack buildItem() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> {
            // unsafe because knockback normally caps at II
            meta.addEnchant(Enchantment.KNOCKBACK, 3, true);
            meta.setUnbreakable(true);
        });
        return sword;
    }
}
