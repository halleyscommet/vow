package us.dingl.VowSMPPlugin;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import us.dingl.VowSMPPlugin.Commands.LivesCommand;
import us.dingl.VowSMPPlugin.Commands.Vow.VowCommand;
import us.dingl.VowSMPPlugin.Commands.WithdrawCommand;
import us.dingl.VowSMPPlugin.Gui.GuiManager;
import us.dingl.VowSMPPlugin.Guis.HeadCache;
import us.dingl.VowSMPPlugin.Guis.LivesActionBar;
import us.dingl.VowSMPPlugin.Items.LifeItem;
import us.dingl.VowSMPPlugin.Items.RitualItem;
import us.dingl.VowSMPPlugin.Leaderboard.Leaderboard;
import us.dingl.VowSMPPlugin.Listeners.ClaimLifeListener;
import us.dingl.VowSMPPlugin.Listeners.DeathListener;
import us.dingl.VowSMPPlugin.Listeners.LoginListener;
import us.dingl.VowSMPPlugin.Listeners.ResourcePackListener;
import us.dingl.VowSMPPlugin.Listeners.Ritual.AltarClickListener;
import us.dingl.VowSMPPlugin.Listeners.Ritual.StormConfinementListener;
import us.dingl.VowSMPPlugin.Ritual.Altar;
import us.dingl.VowSMPPlugin.Ritual.Storm;
import us.dingl.VowSMPPlugin.Update.Updater;
import us.dingl.VowSMPPlugin.Vows.Impl.CharlotteVow;
import us.dingl.VowSMPPlugin.Vows.Impl.ColleraVow;
import us.dingl.VowSMPPlugin.Vows.Impl.GoodwyllVow;
import us.dingl.VowSMPPlugin.Vows.Impl.HaleyVow;
import us.dingl.VowSMPPlugin.Vows.Impl.HarperVow;
import us.dingl.VowSMPPlugin.Vows.VowManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VowSMPPlugin extends JavaPlugin {

    public static final int DEFAULT_LIVES = 5;
    public static final int MAX_LIVES = 7;

    private Leaderboard leaderboard;
    private Storm storm;
    private VowManager vowManager;
    private Updater updater;

    private HeadCache headCache;

    private final Set<UUID> livesShown = ConcurrentHashMap.newKeySet();
    private LivesActionBar livesActionBar;

    @Override
    public void onEnable() {
        // copies resources/config.yml on first run. getConfig() also falls back to
        // the jar's copy for any key missing from the server's file.
        saveDefaultConfig();
        loadLivesShown();
        livesActionBar = new LivesActionBar(this);
        livesActionBar.start();

        Permissions.register(getServer().getPluginManager());

        leaderboard = new Leaderboard(this);
        storm = new Storm(this);
        LifeItem.init(this);
        RitualItem.init(this);

        vowManager = new VowManager(this);
        registerVows();
        vowManager.start();

        updater = new Updater(this, getFile());

        registerCommands();
        registerListeners();
        GuiManager.init(this);

        headCache = new HeadCache(this);
        headCache.start();

        // worlds might not exist yet during onEnable, so spawn the leaderboard next tick
        getServer().getScheduler().runTask(this, leaderboard::refresh);

        // register recipe
        NamespacedKey curtanaKey = new NamespacedKey(this, "curtana");
        ItemStack curtanaItem = RitualItem.create(1);
        ShapedRecipe curtanaRecipe = new ShapedRecipe(curtanaKey, curtanaItem);

        curtanaRecipe.shape(
                "ALN",
                "LWL",
                "NLA"
        );
        curtanaRecipe.setIngredient('A', Material.ENCHANTED_GOLDEN_APPLE);
        curtanaRecipe.setIngredient('L', LifeItem.create(1));
        curtanaRecipe.setIngredient('N', Material.NETHERITE_INGOT);
        curtanaRecipe.setIngredient('W', Material.WAXED_WEATHERED_CUT_COPPER_STAIRS);

        getServer().addRecipe(curtanaRecipe);

        // make altar if it doesn't exist
        new Altar(this).spawn();

        getLogger().info("Vow SMP Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (storm != null) {
            storm.shutdown();
        }
        if (vowManager != null) {
            vowManager.stop();
        }
        if (headCache != null) {
            headCache.stop();
        }
        if (livesActionBar != null) {
            livesActionBar.stop();
        }
        getLogger().info("Vow SMP Plugin has been disabled!");
    }

    public HeadCache getHeadCache() {
        return headCache;
    }

    public Storm getStorm() {
        return storm;
    }

    public VowManager getVowManager() {
        return vowManager;
    }

    public Updater getUpdater() {
        return updater;
    }

    public LivesActionBar getLivesActionBar() {
        return livesActionBar;
    }

    // leaderboard
    public boolean getLeaderboardEnabled() {
        return getConfig().getBoolean(ConfigKey.LEADERBOARD_ENABLED.getPath(), false);
    }

    public void setLeaderboardEnabled(boolean enabled) {
        getConfig().set(ConfigKey.LEADERBOARD_ENABLED.getPath(), enabled);
        saveConfig();
    }

    /// flips the leaderboard on/off and spawns/removes the display. Returns the new state.
    public boolean toggleLeaderboard() {
        boolean enabled = !getLeaderboardEnabled();
        setLeaderboardEnabled(enabled);
        if (enabled) {
            leaderboard.refresh();
        } else {
            leaderboard.hide();
        }
        return enabled;
    }

    /// the block position stored in config, or world spawn if none was ever set.
    /// null only if no worlds are loaded.
    public Location getLeaderboardLocation() {
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorlds().getFirst();
        FileConfiguration config = getConfig();

        if (!config.isSet(ConfigKey.LEADERBOARD_X.getPath())
                || !config.isSet(ConfigKey.LEADERBOARD_Y.getPath())
                || !config.isSet(ConfigKey.LEADERBOARD_Z.getPath())) {
            return world.getSpawnLocation();
        }

        int x = config.getInt(ConfigKey.LEADERBOARD_X.getPath());
        int y = Math.clamp(config.getInt(ConfigKey.LEADERBOARD_Y.getPath()), world.getMinHeight(), world.getMaxHeight());
        int z = config.getInt(ConfigKey.LEADERBOARD_Z.getPath());
        return new Location(world, x, y, z);
    }

    /// saves the new position (one disk write) and moves the display if it's enabled.
    public void setLeaderboardPosition(int x, int y, int z) {
        if (getLeaderboardEnabled()) {
            leaderboard.hide(); // config still points at the OLD spot, so its chunk gets loaded and cleaned
        }

        getConfig().set(ConfigKey.LEADERBOARD_X.getPath(), x);
        getConfig().set(ConfigKey.LEADERBOARD_Y.getPath(), y);
        getConfig().set(ConfigKey.LEADERBOARD_Z.getPath(), z);
        saveConfig();

        leaderboard.refresh();
    }

    // players / lives

    public String getPlayerName(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString();
    }

    public List<UUID> getLoggedPlayers() {
        return readUuidList(ConfigKey.LOGGED_PLAYERS);
    }

    public List<UUID> getKilledPlayers() {
        return readUuidList(ConfigKey.KILLED_PLAYERS);
    }

    public List<UUID> getPlayersNotYetKilled() {
        Set<UUID> killed = new HashSet<>(getKilledPlayers());
        return getLoggedPlayers().stream().filter(id -> !killed.contains(id)).toList();
    }

    public void addPlayerToLog(UUID id) {
        String uuid = id.toString();
        boolean changed = false;

        List<String> logged = new ArrayList<>(getConfig().getStringList(ConfigKey.LOGGED_PLAYERS.getPath()));
        if (!logged.contains(uuid)) {
            logged.add(uuid);
            getConfig().set(ConfigKey.LOGGED_PLAYERS.getPath(), logged);
            changed = true;
        }

        String livesPath = ConfigKey.PLAYER_LIVES.getPath() + "." + uuid;
        if (!getConfig().isSet(livesPath)) {
            getConfig().set(livesPath, DEFAULT_LIVES);
            changed = true;
        }

        if (changed) {
            saveConfig();
            leaderboard.refresh();
        }
    }

    /// returns true if this was the player's FIRST death
    public boolean addPlayerToKilled(UUID id) {
        String uuid = id.toString();
        List<String> killed = new ArrayList<>(getConfig().getStringList(ConfigKey.KILLED_PLAYERS.getPath()));
        if (killed.contains(uuid)) {
            return false;
        }

        killed.add(uuid);
        getConfig().set(ConfigKey.KILLED_PLAYERS.getPath(), killed);
        saveConfig();
        leaderboard.refresh();
        return true;
    }

    private void loadLivesShown() {
        livesShown.clear();
        livesShown.addAll(readUuidList(ConfigKey.LIVES_SHOWN)); // skips invalid UUIDs with a warning
    }

    public boolean isPlayerLivesShown(UUID id) {
        return livesShown.contains(id);
    }

    /// read-only live view, no copying or parsing
    public Set<UUID> getPlayerLivesShown() {
        return Collections.unmodifiableSet(livesShown);
    }

    public void setPlayerLivesShown(UUID id, boolean enabled) {
        boolean changed = enabled ? livesShown.add(id) : livesShown.remove(id);
        if (!changed) return; // nothing to write

        getConfig().set(ConfigKey.LIVES_SHOWN.getPath(),
                livesShown.stream().map(UUID::toString).toList());
        saveConfig();
    }

    public int getPlayerLives(UUID id) {
        return getConfig().getInt(ConfigKey.PLAYER_LIVES.getPath() + "." + id, 0);
    }

    public Map<UUID, Integer> getPlayerLives() {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        var section = getConfig().getConfigurationSection(ConfigKey.PLAYER_LIVES.getPath());
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            try {
                result.put(UUID.fromString(key), section.getInt(key));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Ignoring invalid UUID in player-lives: " + key);
            }
        }
        return result;
    }

    /// applies the change, clamped to 0<->MAX_LIVES, and returns the new life count.
    /// a missing entry counts as 0, same as getPlayerLives - new players get DEFAULT_LIVES in addPlayerToLog.
    public int updatePlayerLifeCounter(UUID id, int change) {
        String path = ConfigKey.PLAYER_LIVES.getPath() + "." + id;
        int current = getConfig().getInt(path, 0);
        int updated = Math.clamp((long) current + change, 0, MAX_LIVES);

        if (updated != current || !getConfig().isSet(path)) {
            getConfig().set(path, updated);
            saveConfig();
        }

        if (updated != current && livesActionBar != null) {
            livesActionBar.update(id);
        }

        return updated;
    }

    // collera invites

    /// true if this player only got on the server through a Collera invite
    public boolean isInvited(UUID id) {
        return getConfig().isSet(ConfigKey.INVITES.getPath() + "." + id);
    }

    /// whitelists them with 0 lives, so they can't claim or drop any. Uses up a forgiveness.
    public void addInvite(UUID invitee, UUID inviter) {
        getConfig().set(ConfigKey.INVITES.getPath() + "." + invitee, inviter.toString());
        setUuidListEntry(ConfigKey.FORGIVEN_INVITES, invitee, false);
        getConfig().set(ConfigKey.PLAYER_LIVES.getPath() + "." + invitee, 0); // addPlayerToLog won't overwrite it
        saveConfig();
        Bukkit.getOfflinePlayer(invitee).setWhitelisted(true);
    }

    /// unwhitelists them and forgets the invite
    public void removeInvite(UUID invitee) {
        getConfig().set(ConfigKey.INVITES.getPath() + "." + invitee, null);
        saveConfig();
        Bukkit.getOfflinePlayer(invitee).setWhitelisted(false);
    }

    /// true if an admin forgave them, so they can be invited even though they've played before
    public boolean isInviteForgiven(UUID id) {
        return readUuidList(ConfigKey.FORGIVEN_INVITES).contains(id);
    }

    /// lets them be invited once more, even though they've played before. Lasts until they're invited.
    public void forgiveInvite(UUID id) {
        setUuidListEntry(ConfigKey.FORGIVEN_INVITES, id, true);
        saveConfig();
    }

    /// adds or removes one uuid from a config list. Doesn't save.
    private void setUuidListEntry(ConfigKey key, UUID id, boolean present) {
        List<String> list = new ArrayList<>(getConfig().getStringList(key.getPath()));
        boolean changed = present ? !list.contains(id.toString()) && list.add(id.toString()) : list.remove(id.toString());
        if (changed) {
            getConfig().set(key.getPath(), list);
        }
    }

    private List<UUID> readUuidList(ConfigKey key) {
        List<UUID> result = new ArrayList<>();
        for (String s : getConfig().getStringList(key.getPath())) {
            try {
                result.add(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Ignoring invalid UUID in " + key.getPath() + ": " + s);
            }
        }
        return result;
    }

    // registration

    private void registerVows() {
        vowManager.register(new GoodwyllVow(this));
        vowManager.register(new CharlotteVow(this));
        vowManager.register(new ColleraVow(this));
        vowManager.register(new HaleyVow(this));
        vowManager.register(new HarperVow(this));
        // new vows go here
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(new VowCommand(this).build(), "Vow SMP commands");
            registrar.register(new WithdrawCommand(this).build().build(), "Withdraw lives as items");
            registrar.register(new LivesCommand(this).build().build(), "Get lives or show an actionbar");
        });
    }

    private void registerListeners() {
        registerListeners(
                new LoginListener(this),
                new DeathListener(this),
                new ResourcePackListener(this),
                new StormConfinementListener(this, storm),
                new ClaimLifeListener(this),
                //new CombatLogListener(),
                new AltarClickListener(this)
        );
    }

    private void registerListeners(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}