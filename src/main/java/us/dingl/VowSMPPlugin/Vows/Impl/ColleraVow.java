package us.dingl.VowSMPPlugin.Vows.Impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.SoulboundItemVow;

import java.util.Arrays;

/// gives a Sharpness I, Breach IV diamond sword that stays in his inventory when he dies.
/// His helmet and boots can't go above Protection II - anything higher gets downgraded while he wears it.
/// He also can't get strength from any source.
public class ColleraVow extends SoulboundItemVow {

    private static final int MAX_PROTECTION = 2;
    private static final Component SWORD_NAME = Component.text("Murasama").color(NamedTextColor.RED);

    public ColleraVow(VowSMPPlugin plugin) {
        super(plugin, "collera_sword");
    }

    @Override
    public String id() {
        return "collera";
    }

    @Override
    public String name() {
        return "Collera vow";
    }

    @Override
    public String description() {
        return "Gives a Sharpness I, Breach IV diamond sword that you keep when you die and can't drop or store. "
                + "Your helmet and boots can't go above Protection II, and you can't get strength.";
    }

    @Override
    public Material icon() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    protected ItemStack buildItem() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> {
            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
            // unsafe because breach normally only goes on maces
            meta.addEnchant(Enchantment.BREACH, 4, true);
            meta.setUnbreakable(true);
            meta.customName(SWORD_NAME);
        });
        return sword;
    }

    @Override
    public void onGain(Player player) {
        super.onGain(player);
        player.removePotionEffect(PotionEffectType.STRENGTH);
    }

    @Override
    public void tick(Player player, int lives) {
        // checking every second catches every way armor gets on (equipping, anvils, dispensers...)
        capProtection(player, EquipmentSlot.HEAD);
        capProtection(player, EquipmentSlot.FEET);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // the sword used to be breakable with no name, so upgrade any old one (keeps its enchants and tag)
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (isSoulbound(item) && !item.getItemMeta().isUnbreakable()) {
                item.editMeta(meta -> {
                    meta.setUnbreakable(true);
                    meta.customName(SWORD_NAME);
                });
                player.getInventory().setItem(slot, item);
            }
        }

        if (!has(player)) return;

        // Collera used to be a different vow, so anyone who had it before the sword was added gets it now
        boolean hasSword = Arrays.stream(player.getInventory().getContents()).anyMatch(this::isSoulbound);
        if (!hasSword) {
            onGain(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrinkStrength(PlayerItemConsumeEvent event) {
        if (!givesStrength(event.getItem()) || !has(event.getPlayer())) return;

        // he keeps the potion instead of wasting it
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("You can't use strength", NamedTextColor.RED));
    }

    /// catches every other way to get it too (splash and lingering potions, beacons, commands...)
    @EventHandler(ignoreCancelled = true)
    public void onStrength(EntityPotionEffectEvent event) {
        PotionEffect effect = event.getNewEffect();
        if (effect == null || !effect.getType().equals(PotionEffectType.STRENGTH)) return;
        if (event.getEntity() instanceof Player player && has(player)) {
            event.setCancelled(true);
        }
    }

    private boolean givesStrength(ItemStack item) {
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        if (meta.hasCustomEffect(PotionEffectType.STRENGTH)) return true;
        PotionType base = meta.getBasePotionType();
        return base != null && base.getPotionEffects().stream()
                .anyMatch(effect -> effect.getType().equals(PotionEffectType.STRENGTH));
    }

    /// only lowers protection that's too high - armor without it is left alone
    private void capProtection(Player player, EquipmentSlot slot) {
        ItemStack item = player.getInventory().getItem(slot);
        if (item.getEnchantmentLevel(Enchantment.PROTECTION) <= MAX_PROTECTION) return;

        item.editMeta(meta -> meta.addEnchant(Enchantment.PROTECTION, MAX_PROTECTION, true));
        player.getInventory().setItem(slot, item);
    }
}
