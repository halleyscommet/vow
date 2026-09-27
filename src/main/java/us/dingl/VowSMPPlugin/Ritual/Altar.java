package us.dingl.VowSMPPlugin.Ritual;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public final class Altar {

    private static final String DISPLAY_ID = "vow_altar";
    private static final NamespacedKey EMPTY_MODEL = new NamespacedKey("vow", "altar_empty");

    private final NamespacedKey idKey;

    public Altar(VowSMPPlugin plugin) {
        this.idKey = new NamespacedKey(plugin, "display_id");
    }

    /// call once from onEnable; safe to call on every startup, won't duplicate
    public void spawn() {
        Location location = location();
        Chunk chunk = location.getChunk(); // loads it
        placeBarrier(location);

        ItemDisplay existing = find(chunk);
        if (existing != null) {
            // no ritual can be running at startup, so undo a sword left behind by a crash mid-ritual
            setModel(existing, EMPTY_MODEL);
            return;
        }

        Location hitboxLocation = location.clone().subtract(0, 0.25, 0);
        World world = location.getWorld();

        ItemStack stack = new ItemStack(Material.PAPER);
        stack.editMeta(meta -> meta.setItemModel(EMPTY_MODEL));

        world.spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(stack);
            entity.setPersistent(true); // survives restarts, unlike Storm's display
            entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, DISPLAY_ID);
        });

        world.spawn(hitboxLocation, Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(1.5f);
            hitbox.setInteractionHeight(1.5f);
            hitbox.setResponsive(true);
            hitbox.setPersistent(true);

            hitbox.getPersistentDataContainer().set(
                    idKey,
                    PersistentDataType.STRING,
                    DISPLAY_ID
            );
        });
    }

    /// where the altar display sits. uses the existing display if there is one, so the barrier
    /// (or anything built on top) can't shift it; otherwise centered on 0, 0 just above the highest block
    public Location location() {
        World world = Bukkit.getWorlds().getFirst();
        ItemDisplay existing = find(world.getChunkAt(0, 0));
        if (existing != null) {
            return existing.getLocation();
        }
        return new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1.5, 0.5);
    }

    public void setModel(NamespacedKey modelKey) {
        ItemDisplay display = find(location().getChunk());
        if (display != null) {
            setModel(display, modelKey);
        }
    }

    /// keeps players from walking into the altar
    private static void placeBarrier(Location location) {
        Block block = location.getBlock();
        if (block.getType() != Material.BARRIER) {
            block.setType(Material.BARRIER);
        }
    }

    private static void setModel(ItemDisplay display, NamespacedKey modelKey) {
        ItemStack stack = display.getItemStack();
        stack.editMeta(meta -> meta.setItemModel(modelKey));
        display.setItemStack(stack);
    }

    private ItemDisplay find(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ItemDisplay d && DISPLAY_ID.equals(d.getPersistentDataContainer().get(idKey, PersistentDataType.STRING))) {
                return d;
            }
        }
        return null;
    }
}
