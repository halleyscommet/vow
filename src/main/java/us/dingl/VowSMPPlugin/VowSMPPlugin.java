package us.dingl.VowSMPPlugin;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.*;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import us.dingl.VowSMPPlugin.Commands.LivesCommand;
import us.dingl.VowSMPPlugin.Commands.Vow.MythicsSubCommand;
import us.dingl.VowSMPPlugin.Commands.Vow.ResistanceSubCommand;
import us.dingl.VowSMPPlugin.Commands.Vow.VowCommand;
import us.dingl.VowSMPPlugin.Commands.WithdrawCommand;
import us.dingl.VowSMPPlugin.Guis.AdminMenus;
import us.dingl.VowSMPPlugin.Guis.HeadCache;
import us.dingl.VowSMPPlugin.Guis.LivesActionBar;
import us.dingl.VowSMPPlugin.Items.LifeItem;
import us.dingl.VowSMPPlugin.Items.RitualItem;
import us.dingl.VowSMPPlugin.Leaderboard.Leaderboard;
import us.dingl.VowSMPPlugin.Mythics.MythicManager;
import us.dingl.VowSMPPlugin.Listeners.ClaimLifeListener;
import us.dingl.VowSMPPlugin.Listeners.DeathListener;
import us.dingl.VowSMPPlugin.Listeners.LoginListener;
import us.dingl.VowSMPPlugin.Listeners.ResourcePackListener;
import us.dingl.VowSMPPlugin.Listeners.Ritual.AltarClickListener;
import us.dingl.VowSMPPlugin.Listeners.Ritual.StormConfinementListener;
import us.dingl.VowSMPPlugin.Ritual.Altar;
import us.dingl.VowSMPPlugin.Ritual.Storm;
import us.dingl.VowSMPPlugin.Update.Updater;
import us.dingl.VowSMPPlugin.Vows.Impl.AuroraVow;
import us.dingl.VowSMPPlugin.Vows.Impl.CharlotteVow;
import us.dingl.VowSMPPlugin.Vows.Impl.ColleraVow;
import us.dingl.VowSMPPlugin.Vows.Impl.GoodwyllVow;
import us.dingl.VowSMPPlugin.Vows.Impl.GuruVow;
import us.dingl.VowSMPPlugin.Vows.Impl.HaleyVow;
import us.dingl.VowSMPPlugin.Vows.Impl.HarperVow;
import us.dingl.VowSMPPlugin.Vows.Impl.HazelVow;
import us.dingl.VowSMPPlugin.Vows.Impl.IceVow;
import us.dingl.VowSMPPlugin.Vows.Impl.KarmaVow;
import us.dingl.VowSMPPlugin.Vows.Impl.TwoLifeDeathVow;
import us.dingl.VowSMPPlugin.Vows.Impl.WhimplyVow;
import us.dingl.VowSMPPlugin.Vows.VowManager;
import us.dingl.paperGUI.api.PaperGUIApi;
import us.dingl.paperGUI.builtin.Builtins;
import us.dingl.paperGUI.internal.GuiManager;
import us.dingl.paperGUI.internal.PaperGUIApiImpl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VowSMPPlugin extends JavaPlugin {

    public static final int DEFAULT_LIVES = 5;
    public static final int MAX_LIVES = 7;

    private Leaderboard leaderboard;
    private Leaderboard killsLeaderboard;
    private Storm storm;
    private VowManager vowManager;
    private MythicManager mythicManager;
    private Updater updater;
    private ResourcePackListener resourcePackListener;

    private GuiManager guiManager;
    private HeadCache headCache;
    private AdminMenus adminMenus;

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

        leaderboard = Leaderboard.immortals(this);
        killsLeaderboard = Leaderboard.kills(this);
        storm = new Storm(this);
        LifeItem.init(this);
        RitualItem.init(this);

        vowManager = new VowManager(this);
        registerVows();
        vowManager.start();

        mythicManager = new MythicManager(this);
        mythicManager.start();

        updater = new Updater(this, getFile());

        registerCommands();
        registerListeners();

        startGuis();
        headCache = new HeadCache(this);
        adminMenus = new AdminMenus(this);
        headCache.start();

        // worlds might not exist yet during onEnable, so spawn the leaderboards next tick
        getServer().getScheduler().runTask(this, () -> {
            leaderboard.refresh();
            killsLeaderboard.refresh();
        });

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
        if (guiManager != null) {
            guiManager.shutdown();
        }
        getLogger().info("Vow SMP Plugin has been disabled!");
    }

    /// PaperGUI is shaded in rather than installed as its own plugin, so boot its menu runtime here -
    /// the same setup its plugin does, minus the /papergui command, web editor and file menus.
    /// The api is registered as a service so PaperGUIApi.get() still finds it.
    private void startGuis() {
        guiManager = new GuiManager(this);
        guiManager.clickCooldown(2);
        PaperGUIApiImpl api = new PaperGUIApiImpl(this, guiManager);
        getServer().getServicesManager().register(PaperGUIApi.class, api, this, ServicePriority.Normal);
        Builtins.register(this, api);
        getServer().getPluginManager().registerEvents(guiManager, this);
        guiManager.start();
    }

    /// config.yml holds every player's lives and vows, so never write it in place - a crash mid-write
    /// would leave it half written. Write a temp file, flush it to disk, then swap it in all at once.
    @Override
    public void saveConfig() {
        Path target = getDataFolder().toPath().resolve("config.yml");
        Path temp = target.resolveSibling("config.yml.tmp");
        ByteBuffer data = ByteBuffer.wrap(getConfig().saveToString().getBytes(StandardCharsets.UTF_8));

        try {
            Files.createDirectories(target.getParent());
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                while (data.hasRemaining()) {
                    channel.write(data);
                }
                channel.force(true); // so a power cut can't swap in a file whose contents never hit the disk
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save " + target, e);
        }
    }

    public AdminMenus getAdminMenus() {
        return adminMenus;
    }

    public HeadCache getHeadCache() {
        return headCache;
    }

    public Storm getStorm() {
        return storm;
    }

    public MythicManager getMythicManager() {
        return mythicManager;
    }

    public VowManager getVowManager() {
        return vowManager;
    }

    public ResourcePackListener getResourcePackListener() {
        return resourcePackListener;
    }

    public Updater getUpdater() {
        return updater;
    }

    public LivesActionBar getLivesActionBar() {
        return livesActionBar;
    }

    public Leaderboard getLeaderboard() {
        return leaderboard;
    }

    public Leaderboard getKillsLeaderboard() {
        return killsLeaderboard;
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

    /// puts a player back on the immortals list. Returns false if they were already on it.
    public boolean revivePlayer(UUID id) {
        boolean wasImmortal = getPlayersNotYetKilled().contains(id);
        addPlayerToLog(id);

        List<String> killed = new ArrayList<>(getConfig().getStringList(ConfigKey.KILLED_PLAYERS.getPath()));
        if (killed.remove(id.toString())) {
            getConfig().set(ConfigKey.KILLED_PLAYERS.getPath(), killed);
            saveConfig();
            leaderboard.refresh();
        }
        return !wasImmortal;
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
        return readUuidIntMap(ConfigKey.PLAYER_LIVES);
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

        // losing lives can lose vow slots
        if (updated < current && vowManager != null) {
            vowManager.enforceSlots(id);
        }

        return updated;
    }

    // kills

    public int getPlayerKills(UUID id) {
        return getConfig().getInt(ConfigKey.PLAYER_KILLS.getPath() + "." + id, 0);
    }

    public Map<UUID, Integer> getPlayerKills() {
        return readUuidIntMap(ConfigKey.PLAYER_KILLS);
    }

    /// sets a player's kill count (never below 0) and updates the kills leaderboard.
    /// returns the new count.
    public int setPlayerKills(UUID id, int kills) {
        int updated = Math.max(0, kills);
        if (updated == getPlayerKills(id)) return updated;

        // 0 kills = no entry, so they drop off the board and out of the file
        getConfig().set(ConfigKey.PLAYER_KILLS.getPath() + "." + id, updated == 0 ? null : updated);
        saveConfig();
        killsLeaderboard.refresh();
        return updated;
    }

    private Map<UUID, Integer> readUuidIntMap(ConfigKey key) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        var section = getConfig().getConfigurationSection(key.getPath());
        if (section == null) {
            return result;
        }
        for (String k : section.getKeys(false)) {
            try {
                result.put(UUID.fromString(k), section.getInt(k));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Ignoring invalid UUID in " + key.getPath() + ": " + k);
            }
        }
        return result;
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
        vowManager.register(new KarmaVow(this));
        vowManager.register(new AuroraVow(this));
        vowManager.register(new GuruVow(this));
        vowManager.register(new IceVow(this));
        vowManager.register(new WhimplyVow(this));
        vowManager.register(new HazelVow(this));
        vowManager.register(new TwoLifeDeathVow(this));
        // new vows go here
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(new VowCommand(this).build(), "Vow SMP commands");
            registrar.register(new WithdrawCommand(this).build().build(), "Withdraw lives as items");
            registrar.register(new LivesCommand(this).build().build(), "Get lives or show an actionbar");
            registrar.register(new ResistanceSubCommand(this).build().build(), "Use the Karma vow's resistance");
            registrar.register(new MythicsSubCommand(this).build().build(), "See who holds each mythic item");
        });
    }

    private void registerListeners() {
        resourcePackListener = new ResourcePackListener(this);
        registerListeners(
                new LoginListener(this),
                new DeathListener(this),
                resourcePackListener,
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