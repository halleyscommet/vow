package us.dingl.VowSMPPlugin.Vows.Impl;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.SoulboundItemVow;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// gives a Sharpness I, Breach IV diamond sword that stays in his inventory when he dies.
/// Right clicking with it shoots a wind charge on a 5 second cooldown.
public class ColleraVow extends SoulboundItemVow {

    private static final int COOLDOWN_TICKS = 5 * 20;

    /// the sword's own cooldown group, so the cooldown shows on it without touching other diamond swords
    private final NamespacedKey cooldownGroup;
    /// server tick his wind charge is ready again. Item cooldowns reset on relog, so track it here too.
    private final Map<UUID, Integer> readyAt = new HashMap<>();

    public ColleraVow(VowSMPPlugin plugin) {
        super(plugin, "collera_sword");
        cooldownGroup = new NamespacedKey(plugin, "collera_wind_charge");
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
                + "Right click with it to shoot a wind charge (5 second cooldown).";
    }

    @Override
    public Material icon() {
        return Material.WIND_CHARGE;
    }

    @Override
    protected ItemStack buildItem() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> {
            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
            // unsafe because breach normally only goes on maces
            meta.addEnchant(Enchantment.BREACH, 4, true);
            UseCooldownComponent cooldown = meta.getUseCooldown();
            cooldown.setCooldownSeconds(COOLDOWN_TICKS / 20f);
            cooldown.setCooldownGroup(cooldownGroup);
            meta.setUseCooldown(cooldown);
        });
        return sword;
    }

    @Override
    public void onLose(Player player) {
        super.onLose(player);
        readyAt.remove(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!has(player)) return;

        // Collera used to be a different vow, so anyone who had it before the sword was added gets it now
        boolean hasSword = Arrays.stream(player.getInventory().getContents()).anyMatch(this::isSoulbound);
        if (!hasSword) {
            onGain(player);
        }

        // put the cooldown back on the sword after a relog
        int remaining = remainingCooldown(player);
        if (remaining > 0) {
            player.setCooldown(cooldownGroup, remaining);
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isSoulbound(event.getItem()) || !has(player)) return;

        // let him open doors and chests with the sword out, unless he's sneaking like vanilla
        Block block = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && block != null && block.getType().isInteractable() && !player.isSneaking()) {
            return;
        }

        if (remainingCooldown(player) > 0) return;

        player.launchProjectile(WindCharge.class);
        player.getWorld().playSound(player, Sound.ENTITY_WIND_CHARGE_THROW, 0.5f, 0.4f);
        readyAt.put(player.getUniqueId(), Bukkit.getCurrentTick() + COOLDOWN_TICKS);
        player.setCooldown(cooldownGroup, COOLDOWN_TICKS);
        player.swingHand(event.getHand());
    }

    private int remainingCooldown(Player player) {
        Integer ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0 : ready - Bukkit.getCurrentTick();
    }
}
