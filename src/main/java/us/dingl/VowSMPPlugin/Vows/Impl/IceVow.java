package us.dingl.VowSMPPlugin.Vows.Impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.SoulboundItemVow;

import java.util.List;

/// gives a full set of max enchanted netherite armor (no swift sneak or soul speed) that he keeps when he dies
/// and can't drop or store. The armor can still break, and a broken piece is gone for good - it's never replaced,
/// but he can wear his own armor in its place. He also can't use invisibility or drink potions of infestation.
public class IceVow extends SoulboundItemVow {

    public IceVow(VowSMPPlugin plugin) {
        super(plugin, "ice_armor");
    }

    @Override
    public String id() {
        return "ice";
    }

    @Override
    public String name() {
        return "Ice vow";
    }

    @Override
    public String description() {
        return "Gives max enchanted netherite armor that you keep when you die and can't drop or store. "
                + "When a piece breaks it's gone forever. You can't use invisibility or drink potions of infestation.";
    }

    @Override
    public Material icon() {
        return Material.NETHERITE_CHESTPLATE;
    }

    @Override
    protected List<ItemStack> buildItems() {
        ItemStack helmet = armor(Material.NETHERITE_HELMET);
        helmet.addEnchantment(Enchantment.RESPIRATION, 3);
        helmet.addEnchantment(Enchantment.AQUA_AFFINITY, 1);

        ItemStack boots = armor(Material.NETHERITE_BOOTS);
        boots.addEnchantment(Enchantment.FEATHER_FALLING, 4);
        boots.addEnchantment(Enchantment.DEPTH_STRIDER, 3);

        // no swift sneak on the leggings or soul speed on the boots
        return List.of(helmet, armor(Material.NETHERITE_CHESTPLATE), armor(Material.NETHERITE_LEGGINGS), boots);
    }

    @Override
    protected void giveItem(Player player, ItemStack item) {
        // straight onto him if that slot is free, otherwise into his inventory
        EquipmentSlot slot = item.getType().getEquipmentSlot();
        PlayerInventory inventory = player.getInventory();
        if (inventory.getItem(slot).isEmpty()) {
            inventory.setItem(slot, item);
        } else {
            player.give(item);
        }
    }

    @Override
    public void onGain(Player player) {
        super.onGain(player);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (!has(event.getPlayer())) return;

        String banned;
        if (givesEffect(event.getItem(), PotionEffectType.INVISIBILITY)) {
            banned = "invisibility";
        } else if (givesEffect(event.getItem(), PotionEffectType.INFESTED)) {
            banned = "potions of infestation";
        } else {
            return;
        }

        // he keeps the potion instead of wasting it
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("You can't use " + banned, NamedTextColor.RED));
    }

    /// catches every other way to get invisibility too (splash and lingering potions, commands...).
    /// Infestation is only banned when drunk, so it's left alone here.
    @EventHandler(ignoreCancelled = true)
    public void onInvisibility(EntityPotionEffectEvent event) {
        PotionEffect effect = event.getNewEffect();
        if (effect == null || !effect.getType().equals(PotionEffectType.INVISIBILITY)) return;
        if (event.getEntity() instanceof Player player && has(player)) {
            event.setCancelled(true);
        }
    }

    /// what every piece gets. Thorns is left off since it wears the armor down faster, and broken pieces are gone for good
    private ItemStack armor(Material type) {
        ItemStack item = new ItemStack(type);
        item.addEnchantment(Enchantment.PROTECTION, 4);
        item.addEnchantment(Enchantment.UNBREAKING, 3);
        item.addEnchantment(Enchantment.MENDING, 1);
        return item;
    }

    private boolean givesEffect(ItemStack item, PotionEffectType type) {
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        if (meta.hasCustomEffect(type)) return true;
        PotionType base = meta.getBasePotionType();
        return base != null && base.getPotionEffects().stream()
                .anyMatch(effect -> effect.getType().equals(type));
    }
}
