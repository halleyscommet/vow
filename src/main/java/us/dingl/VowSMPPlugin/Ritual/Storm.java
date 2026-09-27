package us.dingl.VowSMPPlugin.Ritual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import us.dingl.VowSMPPlugin.Items.RitualItem;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class Storm {

    private static final String DISPLAY_ID = "vow_storm";

    // storm shape
    private static final double RADIUS = 105.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;
    private static final float VISUAL_SCALE = (float) (RADIUS * 2);
    private static final float HEIGHT_SCALE = 400f;
    private static final int GROW_TICKS = 3 * 20;

    // ritual
    private static final String RITUAL_TITLE = "Resurrection Ritual";
    private static final int RITUAL_SECONDS = 10 * 60;
    private static final int REWARD_SCATTER = 100; // how far from the altar the reward teleport can land
    private static final Set<Material> UNSAFE_GROUND =
            Set.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    // lightning: ticks between strikes (random in this range) and how far from a player a bolt can land
    private static final int LIGHTNING_MIN_DELAY = 60;
    private static final int LIGHTNING_MAX_DELAY = 100;
    private static final double STRIKE_MIN_DISTANCE = 25;
    private static final double STRIKE_MAX_DISTANCE = 75;

    // time lock
    private static final long TIME_LOCK_VALUE = 6000L; // noon
    private static final int TIME_CHECK_TICKS = 5;

    private final VowSMPPlugin plugin;
    private final NamespacedKey idKey;
    private final StormBiome biome;
    private final TimerBar timerBar;
    private final FakeLightning lightning = new FakeLightning();
    private final Altar altar;

    // per-storm state, all cleared in stop()
    private UUID confinedPlayerId;
    private Location center;
    private ItemDisplay display;
    private BukkitTask tickTask;
    private SwordPlunge sword; // the sword in the altar, from the intro animation until stop()
    private UUID pendingInitiatorId; // who started it, only while the sword animation is still playing
    private boolean refundCurtana;   // give the Curtana back if it's cancelled during the animation
    private int tickCount;
    private int nextStrikeTick;
    private final Set<UUID> timeLocked = new HashSet<>();

    public Storm(VowSMPPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "display_id");
        this.biome = new StormBiome(plugin);
        this.timerBar = new TimerBar(plugin);
        this.altar = new Altar(plugin);
    }

    // public API

    public boolean isActive() {
        return confinedPlayerId != null;
    }

    public UUID getConfinedPlayerId() {
        return confinedPlayerId;
    }

    public Location getCenter() {
        return center;
    }

    public double getRadius() {
        return RADIUS;
    }

    /// true for the player the ritual is for, including during the sword animation before the storm begins
    public boolean isInitiator(UUID id) {
        return id.equals(confinedPlayerId) || id.equals(pendingInitiatorId);
    }

    /// plays the sword animation, then starts the storm. returns false if one is already running or starting.
    /// refundCurtana: the caller took a Curtana for this, so hand it back if it's stopped before the storm begins
    public boolean start(Player initiator, boolean refundCurtana) {
        if (isActive() || sword != null) return false;

        this.pendingInitiatorId = initiator.getUniqueId();
        this.refundCurtana = refundCurtana;
        sword = SwordPlunge.play(plugin, altar.location(), () -> {
            pendingInitiatorId = null;
            begin(initiator);
        });
        return true;
    }

    private void begin(Player initiator) {
        World world = Bukkit.getWorlds().getFirst();
        Location location = new Location(world, 0, world.getHighestBlockYAt(0, 0), 0); // loads the chunk
        Chunk chunk = location.getChunk();

        removeStrayDisplays(chunk);          // leftovers from a crash / older versions
        chunk.addPluginChunkTicket(plugin);  // keep it loaded for the whole ritual

        // from here on stop() knows how to undo everything, so any failure just calls it
        center = location;
        confinedPlayerId = initiator.getUniqueId();

        try {
            ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS);
            stack.editMeta(meta -> meta.setItemModel(new NamespacedKey("vow", "storm")));

            ItemDisplay spawned = world.spawn(location, ItemDisplay.class, entity -> {
                entity.setItemStack(stack);
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setViewRange(16f);
                entity.setInterpolationDelay(0);
                entity.setInterpolationDuration(0);
                entity.setTransformationMatrix(new Matrix4f().scale(1f, HEIGHT_SCALE, 1f));
                entity.setPersistent(false); // never saved, so a crash can't leave it behind
                entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, DISPLAY_ID);
            });
            display = spawned;

            biome.enable(world, location, RADIUS);

            // wait a couple of ticks so the client sees the small state first, then interpolates to the big one
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!spawned.isValid()) return;
                spawned.setInterpolationDelay(0);
                spawned.setInterpolationDuration(GROW_TICKS);
                spawned.setTransformationMatrix(new Matrix4f().scale(VISUAL_SCALE, HEIGHT_SCALE, VISUAL_SCALE));
            }, 2);

            Bukkit.broadcast(Component.text("A revival for ").append(Component.text(initiator.getName()).color(NamedTextColor.DARK_RED).decorate(TextDecoration.ITALIC, TextDecoration.BOLD)).append(Component.text(" has been initiated!")));
            for (Player player : Bukkit.getOnlinePlayers()) {
                Component mainTitle = Component.text(initiator.getName()).color(NamedTextColor.DARK_RED).decorate(TextDecoration.ITALIC, TextDecoration.BOLD);
                Component subtitle = Component.text("has started a revival!");

                Title.Times times = Title.DEFAULT_TIMES;

                Title title = Title.title(mainTitle, subtitle, times);
                player.showTitle(title);
            }

            altar.setModel(new NamespacedKey("vow", "altar_sword"));

            timerBar.start(RITUAL_TITLE, RITUAL_SECONDS, () -> {
                stop(); // first, so the confinement doesn't block the reward teleport
                reward(initiator);
            });

            tickCount = 0;
            // wait until the storm wall has finished growing before the strikes start
            nextStrikeTick = GROW_TICKS + randomDelay();
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
        } catch (RuntimeException e) {
            stop(); // don't leave a half-started storm behind
            throw e;
        }
    }

    public void stop() {
        if (sword != null) {
            sword.remove();
            sword = null;
        }
        if (pendingInitiatorId != null) {
            // stopped during the animation, before the storm began
            if (refundCurtana) {
                giveBackCurtana(pendingInitiatorId);
            }
            pendingInitiatorId = null;
        }
        if (!isActive()) return;

        Location loc = center;
        center = null;
        confinedPlayerId = null;

        altar.setModel(new NamespacedKey("vow", "altar_empty"));

        try {
            timerBar.stop();
            if (tickTask != null) {
                tickTask.cancel();
                tickTask = null;
            }
            releaseTimeLock();
            biome.disable();
        } finally {
            // runs even if something above threw
            if (display != null) {
                display.remove();
                display = null;
            }
            loc.getChunk().removePluginChunkTicket(plugin);
        }
    }

    /// ends the ritual for good (the player died): no reward, and no Curtana refund even mid-animation
    public void fail() {
        refundCurtana = false;
        stop();
    }

    public void shutdown() {
        stop();
        biome.close();
    }

    public void reward(Player player) {
        plugin.updatePlayerLifeCounter(player.getUniqueId(), 3);
        player.sendMessage(Component.text("Congrats! You now have 3 lives!", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text("The revival for ").append(Component.text(player.getName()).color(NamedTextColor.DARK_GREEN).decorate(TextDecoration.ITALIC, TextDecoration.BOLD)).append(Component.text(" has been finished!")));

        Location tpTo = player.getRespawnLocation();
        if (tpTo == null) {
            tpTo = randomSafeSpot(player.getLocation());
        }
        player.teleport(tpTo);
    }

    /// a random spot within REWARD_SCATTER blocks, standing on solid ground (no water, lava, cactus...).
    /// falls back to world spawn if nothing safe turns up
    private static Location randomSafeSpot(Location around) {
        World world = around.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < 10; attempt++) {
            int x = around.getBlockX() + random.nextInt(-REWARD_SCATTER, REWARD_SCATTER + 1);
            int z = around.getBlockZ() + random.nextInt(-REWARD_SCATTER, REWARD_SCATTER + 1);
            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);

            if (ground.getType().isSolid() && !UNSAFE_GROUND.contains(ground.getType())) {
                return ground.getLocation().add(0.5, 1, 0.5);
            }
        }
        return world.getSpawnLocation();
    }

    private void giveBackCurtana(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.give(RitualItem.create(1));
        } else {
            // above the altar, since the altar itself is inside a barrier block
            Location drop = altar.location().add(0, 1, 0);
            drop.getWorld().dropItemNaturally(drop, RitualItem.create(1));
        }
    }

    // tick loop

    private void tick() {
        if (center == null) return;

        int t = tickCount++;
        boolean timeDue = t % TIME_CHECK_TICKS == 0;
        boolean strikeDue = t >= nextStrikeTick;
        if (!timeDue && !strikeDue) return;

        List<Player> inside = playersInside(); // one scan shared by both effects
        if (timeDue) updateTimeLock(inside);
        if (strikeDue) tryStrike(inside);
    }

    // lightning

    private void tryStrike(List<Player> inside) {
        if (inside.isEmpty()) return; // nothing to show it to, retry next tick

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Player anchor = inside.get(random.nextInt(inside.size()));

        double angle = random.nextDouble(Math.TAU);
        double distance = random.nextDouble(STRIKE_MIN_DISTANCE, STRIKE_MAX_DISTANCE);
        double x = anchor.getX() + Math.cos(angle) * distance;
        double z = anchor.getZ() + Math.sin(angle) * distance;

        // landed outside the storm: leave the schedule alone so it just retries next tick
        if (!isInside(x, z)) return;

        World world = center.getWorld();
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        // don't force-load or generate chunks just for a visual effect
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) return;

        double y = world.getHighestBlockYAt(blockX, blockZ) + 1;

        nextStrikeTick = tickCount + randomDelay();
        for (Player player : inside) {
            lightning.summon(player, x, y, z);
        }
    }

    private static int randomDelay() {
        return ThreadLocalRandom.current().nextInt(LIGHTNING_MIN_DELAY, LIGHTNING_MAX_DELAY + 1);
    }

    // time lock

    private void updateTimeLock(List<Player> inside) {
        Set<UUID> insideNow = new HashSet<>();
        for (Player player : inside) {
            UUID id = player.getUniqueId();
            insideNow.add(id);
            if (timeLocked.add(id)) {
                // just entered: absolute time (false = don't let it advance)
                player.setPlayerTime(TIME_LOCK_VALUE, false);
                player.setPlayerWeather(WeatherType.CLEAR);
            }
        }

        // anyone we locked earlier who isn't inside anymore gets their normal time back
        Iterator<UUID> it = timeLocked.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (insideNow.contains(id)) continue;

            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.resetPlayerTime();
            }
            it.remove();
        }
    }

    private void releaseTimeLock() {
        for (UUID id : timeLocked) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.resetPlayerTime();
                player.resetPlayerWeather();
            }
        }
        timeLocked.clear();
    }

    // helpers

    private boolean isInside(double x, double z) {
        double dx = x - center.getX();
        double dz = z - center.getZ();
        return dx * dx + dz * dz <= RADIUS_SQ;
    }

    private List<Player> playersInside() {
        List<Player> result = new ArrayList<>();
        for (Player player : center.getWorld().getPlayers()) {
            if (isInside(player.getX(), player.getZ())) {
                result.add(player);
            }
        }
        return result;
    }

    private void removeStrayDisplays(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ItemDisplay d && DISPLAY_ID.equals(d.getPersistentDataContainer().get(idKey, PersistentDataType.STRING))) {
                d.remove();
            }
        }
    }
}
