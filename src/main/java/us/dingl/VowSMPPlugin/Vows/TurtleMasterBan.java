package us.dingl.VowSMPPlugin.Vows;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.Set;

/// players with a vow that implements {@link Banned} can't drink, throw or shoot Turtle Master,
/// and other people's splash and lingering Turtle Master potions and tipped arrows don't affect them.
public final class TurtleMasterBan implements Listener {

    /// marker for vows that come with the ban
    public interface Banned {}

    private static final Set<PotionType> TURTLE_MASTER =
            Set.of(PotionType.TURTLE_MASTER, PotionType.LONG_TURTLE_MASTER, PotionType.STRONG_TURTLE_MASTER);

    private final VowSMPPlugin plugin;

    public TurtleMasterBan(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isBanned(LivingEntity entity) {
        return entity instanceof Player player && plugin.getVowManager().getVows(player.getUniqueId()).stream()
                .anyMatch(vow -> vow instanceof Banned);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (isTurtleMaster(event.getItem()) && isBanned(event.getPlayer())) {
            // they keep the potion instead of wasting it
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    /// splash and lingering potions
    @EventHandler(ignoreCancelled = true)
    public void onThrow(PlayerLaunchProjectileEvent event) {
        if (isTurtleMaster(event.getItemStack()) && isBanned(event.getPlayer())) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    /// tipped arrows, from bows and crossbows
    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (isTurtleMaster(event.getConsumable()) && isBanned(event.getEntity())) {
            event.setConsumeItem(false);
            event.setCancelled(true);
            denied((Player) event.getEntity());
        }
    }

    /// someone else's splash potion - everyone else in the splash still gets it
    @EventHandler(ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!isTurtleMaster(event.getPotion().getItem())) return;
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (isBanned(entity)) event.setIntensity(entity, 0);
        }
    }

    /// standing in someone else's lingering cloud
    @EventHandler(ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        if (TURTLE_MASTER.contains(event.getEntity().getBasePotionType())) {
            event.getAffectedEntities().removeIf(this::isBanned);
        }
    }

    /// someone else's tipped arrow - it still hits and does damage, it just loses the effect.
    /// Fires before the arrow lands, so the effect is never applied.
    @EventHandler(ignoreCancelled = true)
    public void onArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow && TURTLE_MASTER.contains(arrow.getBasePotionType())
                && event.getHitEntity() instanceof LivingEntity hit && isBanned(hit)) {
            arrow.setBasePotionType(null);
        }
    }

    private static void denied(Player player) {
        player.sendActionBar(Component.text("You can't use Turtle Master", NamedTextColor.RED));
    }

    private static boolean isTurtleMaster(ItemStack item) {
        return item != null && item.getItemMeta() instanceof PotionMeta meta
                && TURTLE_MASTER.contains(meta.getBasePotionType());
    }
}
