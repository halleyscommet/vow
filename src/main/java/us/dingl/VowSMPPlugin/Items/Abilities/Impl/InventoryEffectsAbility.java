package us.dingl.VowSMPPlugin.Items.Abilities.Impl;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import us.dingl.VowSMPPlugin.Items.Abilities.ItemEffects;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.Map;

/// like Held Effects, but the item works from anywhere in the player's inventory - hotbar, main slots,
/// armor or off hand. Not from inside shulker boxes or bundles, or their ender chest.
public class InventoryEffectsAbility extends HeldEffectsAbility {

    public InventoryEffectsAbility(VowSMPPlugin plugin, ItemEffects effects) {
        super(plugin, effects);
    }

    @Override
    public String id() {
        return "inventory_effects";
    }

    @Override
    public String name() {
        return "Inventory Effects";
    }

    @Override
    public String description() {
        return "Gives potion effects while it's anywhere in the player's inventory.";
    }

    @Override
    public Material icon() {
        return Material.SPLASH_POTION;
    }

    @Override
    void addEffectsFor(Player player, Map<PotionEffectType, Integer> wanted) {
        for (ItemStack item : player.getInventory().getContents()) {
            addEffects(item, wanted);
        }
    }
}
