package us.dingl.VowSMPPlugin.Vows.Impl;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

/// permanent Haste II. In exchange, she can't pick up cobwebs, can't use enchanted shields, and every death costs 2 lives.
public class AuroraVow extends Vow {

    private static final int LIVES_LOST = 2;

    public AuroraVow(VowSMPPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "aurora";
    }

    @Override
    public String name() {
        return "Aurora vow";
    }

    @Override
    public String description() {
        return "You have permanent Haste II, but you can't pick up cobwebs, can't use enchanted shields, "
                + "and every death costs " + LIVES_LOST + " lives.";
    }

    @Override
    public Material icon() {
        return Material.GOLDEN_PICKAXE;
    }

    @Override
    public void tick(Player player, int lives) {
        // amplifier 1 = Haste II. 40 ticks so it never runs out between 20-tick refreshes
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 1, true, false, true));
    }

    @Override
    public int livesLostOnDeath(Player player, int livesBefore, int lost) {
        return LIVES_LOST;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && has(player)
                && event.getItem().getItemStack().getType() == Material.COBWEB) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onUseShield(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SHIELD || item.getEnchantments().isEmpty()) return;

        // only stops raising the shield - she can still open doors and chests with it in hand
        if (has(event.getPlayer())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }
}
