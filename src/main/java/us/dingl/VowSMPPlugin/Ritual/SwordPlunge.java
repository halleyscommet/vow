package us.dingl.VowSMPPlugin.Ritual;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import us.dingl.VowSMPPlugin.Items.RitualItem;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// the ritual sword rising over the altar and plunging into it, played right before the storm starts.
/// the sword stays stuck in the altar until remove() is called
final class SwordPlunge {

    private static final int SPAWN_DELAY = 2;   // lets the client see the start state before interpolating
    private static final int APPEAR_TICKS = 8;  // grows in and drifts up
    private static final int HOVER_TICKS = 14;   // hangs for a moment
    private static final int PLUNGE_TICKS = 4;  // drops into the altar
    private static final int SETTLE_TICKS = 8;  // pause after impact before the storm starts

    // shape, relative to the altar display's position
    private static final float SCALE = 1.5f;
    private static final float APPEAR_Y = 2.5f;
    private static final float HOVER_Y = 3.2f;
    private static final float REST_Y = 0.6f; // tweak to line up with the sword in the altar_sword model

    // under the NONE transform the sword tip starts out pointing up-left; this turns it tip-down
    private static final Quaternionf TIP_DOWN = new Quaternionf().rotateZ((float) Math.toRadians(135));

    private final Location altar;
    private final ItemDisplay display;
    private BukkitTask task; // not final: the timer lambda cancels it
    private int tick;

    private SwordPlunge(VowSMPPlugin plugin, Location altar, Runnable onFinish) {
        this.altar = altar;
        this.display = altar.getWorld().spawn(altar, ItemDisplay.class, entity -> {
            entity.setItemStack(RitualItem.create(1));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setPersistent(false); // never saved, so a crash can't leave it behind
            entity.setTransformation(transform(APPEAR_Y, 0f));
        });

        int appearAt = SPAWN_DELAY;
        int plungeAt = appearAt + APPEAR_TICKS + HOVER_TICKS;
        int impactAt = plungeAt + PLUNGE_TICKS;
        int endAt = impactAt + SETTLE_TICKS;

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int t = tick++;
            if (t == appearAt) {
                animate(APPEAR_TICKS, HOVER_Y, SCALE);
                sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.8f);
            } else if (t == plungeAt) {
                animate(PLUNGE_TICKS, REST_Y, SCALE);
                sound(Sound.ITEM_TRIDENT_THROW, 1f, 0.6f);
            } else if (t == impactAt) {
                impact();
            } else if (t >= endAt) {
                this.task.cancel(); // the display stays in the altar
                onFinish.run();
            }
        }, 0L, 1L);
    }

    /// spawns the sword and starts the animation; onFinish runs once it has landed and settled
    static SwordPlunge play(VowSMPPlugin plugin, Location altar, Runnable onFinish) {
        return new SwordPlunge(plugin, altar, onFinish);
    }

    /// removes the sword, stopping the animation first (without running onFinish) if it's still playing
    void remove() {
        task.cancel();
        display.remove();
    }

    private void animate(int ticks, float y, float scale) {
        if (!display.isValid()) return;
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transform(y, scale));
    }

    private void impact() {
        World world = altar.getWorld();
        Location hit = altar.clone().add(0, REST_Y, 0);
        sound(Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
        sound(Sound.ITEM_TRIDENT_HIT, 1f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, hit, 1);
        world.spawnParticle(Particle.ELECTRIC_SPARK, hit, 40, 0.3, 0.3, 0.3, 0.4);
        world.spawnParticle(Particle.ENCHANTED_HIT, hit, 25, 0.5, 0.2, 0.5, 0.3);
    }

    private void sound(Sound sound, float volume, float pitch) {
        altar.getWorld().playSound(altar, sound, volume, pitch);
    }

    private static Transformation transform(float y, float scale) {
        return new Transformation(
                new Vector3f(0f, y, 0f),
                new Quaternionf(TIP_DOWN),
                new Vector3f(scale),
                new Quaternionf()
        );
    }
}
