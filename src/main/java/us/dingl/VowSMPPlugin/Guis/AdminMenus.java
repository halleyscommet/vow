package us.dingl.VowSMPPlugin.Guis;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.Listeners.ResourcePackListener;
import us.dingl.VowSMPPlugin.Listeners.ResourcePackListener.Pack;
import us.dingl.VowSMPPlugin.Listeners.ResourcePackListener.Source;
import us.dingl.VowSMPPlugin.Mythics.MythicManager;
import us.dingl.VowSMPPlugin.Mythics.MythicManager.Mythic;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;
import us.dingl.paperGUI.api.PaperGUIApi;
import us.dingl.paperGUI.api.menu.Button;
import us.dingl.paperGUI.api.menu.ClickContext;
import us.dingl.paperGUI.api.menu.ClickKind;
import us.dingl.paperGUI.api.menu.Menu;
import us.dingl.paperGUI.api.menu.MenuContext;
import us.dingl.paperGUI.api.menu.Pagination;
import us.dingl.paperGUI.api.menu.Slots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// the /vow admin panel, built on PaperGUI. Menus are built once and reused - everything shown is read
/// when the item renders, and list menus redraw every second so changes made elsewhere show up.
@SuppressWarnings("UnstableApiUsage")
public final class AdminMenus {

    private static final int WRAP_WIDTH = 40;
    /// list menus redraw this often (ticks)
    private static final int REFRESH_TICKS = 20;
    /// session variable holding the UUID of the player being edited
    private static final String TARGET = "target";

    private static final String LIST_PATTERN_BOTTOM = "<###B###>";

    /// feedback sounds, played only to whoever clicked
    private enum UiSound {
        CLICK(Sound.UI_BUTTON_CLICK, 1f),
        PAGE(Sound.ITEM_BOOK_PAGE_TURN, 1f),
        ON(Sound.BLOCK_NOTE_BLOCK_PLING, 2f),
        OFF(Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f),
        UP(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f),
        DOWN(Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f),
        SUCCESS(Sound.ENTITY_PLAYER_LEVELUP, 1.5f),
        REMOVE(Sound.ENTITY_ITEM_BREAK, 1f),
        ERROR(Sound.ENTITY_VILLAGER_NO, 1f);

        private final Sound sound;
        private final float pitch;

        UiSound(Sound sound, float pitch) {
            this.sound = sound;
            this.pitch = pitch;
        }

        void play(Player player) {
            player.playSound(player, sound, SoundCategory.MASTER, 0.6f, pitch);
        }
    }

    private final VowSMPPlugin plugin;
    private final Menu main;
    private final Menu players;
    private final Menu player;
    private final Menu vowPicker;
    private final Menu vows;
    private final Menu mythics;

    public AdminMenus(VowSMPPlugin plugin) {
        this.plugin = plugin;
        this.vowPicker = buildVowPicker();
        this.player = buildPlayer();
        this.players = buildPlayers();
        this.vows = buildVows();
        this.mythics = buildMythics();
        this.main = buildMain();
    }

    public void openMain(Player viewer) {
        main.open(viewer);
    }

    // main panel

    private Menu buildMain() {
        return Menu.builder("vow_admin")
                .rows(3)
                .title("Vow Admin")
                .pattern(
                        "#########",
                        "#PVM#LTS#",
                        "#######RU")
                .bind('#', filler())
                .bind('P', Button.of(item(Material.PLAYER_HEAD, "<gold><b>Players",
                        "<gray>Lives, vows and immortals"), ctx -> navigate(ctx, players)))
                .bind('V', Button.of(item(Material.ENCHANTED_BOOK, "<light_purple><b>Vows",
                        "<gray>Every vow and who has it"), ctx -> navigate(ctx, vows)))
                .bind('M', Button.of(item(Material.NETHER_STAR, "<yellow><b>Mythics",
                        "<gray>Who holds each mythic"), ctx -> navigate(ctx, mythics)))
                .bind('L', Button.builder()
                        .item(ctx -> plugin.getLeaderboardEnabled()
                                ? item(Material.REDSTONE, "<dark_red><b>Disable Leaderboard")
                                : item(Material.EMERALD, "<dark_green><b>Enable Leaderboard"))
                        .onClick(ctx -> {
                            (plugin.toggleLeaderboard() ? UiSound.ON : UiSound.OFF).play(ctx.player());
                            ctx.refresh();
                        })
                        .build())
                .bind('T', Button.builder()
                        .item(ctx -> {
                            Location pos = plugin.getLeaderboardLocation();
                            String coords = pos == null ? "unknown"
                                    : pos.getBlockX() + " " + pos.getBlockY() + " " + pos.getBlockZ();
                            return item(Material.PAPER, "<white>Move Leaderboard Here",
                                    "<gray>Currently at " + coords,
                                    "<gray>Moves it above the block you're looking at");
                        })
                        .onClick(ctx -> {
                            Player viewer = ctx.player();
                            Block target = viewer.getTargetBlockExact(5);
                            Location location = target != null ? target.getLocation() : viewer.getLocation();
                            // +1 so the board floats above the block
                            plugin.setLeaderboardPosition(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
                            UiSound.SUCCESS.play(viewer);
                            ctx.refresh();
                        })
                        .build())
                .bind('S', Button.builder()
                        .item(ctx -> plugin.getStorm().isActive()
                                ? item(Material.LIGHTNING_ROD, "<dark_red><b>Stop Storm")
                                : item(Material.LIGHTNING_ROD, "<dark_green><b>Start Storm",
                                        "<gray>Starts a ritual storm where you're standing"))
                        .onClick(ctx -> {
                            if (plugin.getStorm().isActive()) {
                                plugin.getStorm().stop();
                                UiSound.OFF.play(ctx.player());
                            } else {
                                (plugin.getStorm().start(ctx.player(), false) ? UiSound.ON : UiSound.ERROR).play(ctx.player());
                            }
                            ctx.refresh();
                        })
                        .build())
                .bind('R', Button.builder()
                        .item(ctx -> {
                            ResourcePackListener packs = plugin.getResourcePackListener();
                            Pack pack = packs.currentPack();
                            boolean release = packs.getSource() == Source.RELEASE;
                            List<String> lore = new ArrayList<>();
                            lore.add(release ? "<gray>Sending the pack from this version's release"
                                    : "<gray>Sending resource-pack.url from config");
                            if (pack.problem() != null) lore.add("<red>Nothing is being sent: " + pack.problem());
                            lore.add("<gray>Click to switch to " + (release ? "config" : "release"));
                            lore.add("<gray>(resends it to everyone online)");
                            return item(Material.PAINTING, "<gold><b>Resource Pack: " + (release ? "Release" : "Config"),
                                    lore.toArray(String[]::new));
                        })
                        .onClick(ctx -> {
                            ResourcePackListener packs = plugin.getResourcePackListener();
                            Source next = packs.getSource() == Source.RELEASE ? Source.CONFIG : Source.RELEASE;
                            packs.setSource(next);
                            (packs.currentPack().problem() != null ? UiSound.ERROR : UiSound.SUCCESS).play(ctx.player());
                            ctx.refresh();
                        })
                        .build())
                .bind('U', Button.of(ctx -> item(Material.RECOVERY_COMPASS, "<aqua><b>Check for Updates",
                        "<gray>Current version: " + plugin.getPluginMeta().getVersion(),
                        "<gray>Downloads the latest release,",
                        "<gray>then restart to apply it"), ctx -> {
                    // chat is hidden behind the menu, so close it to show the progress
                    UiSound.CLICK.play(ctx.player());
                    ctx.close();
                    plugin.getUpdater().update(ctx.player());
                }))
                .build();
    }

    // players

    private Menu buildPlayers() {
        return listMenu("vow_admin_players", "Players", Pagination.builder("players")
                .slots(Slots.range(0, 44))
                .items(ctx -> plugin.getLoggedPlayers().stream()
                        .sorted(Comparator.comparing(id -> plugin.getPlayerName(id).toLowerCase()))
                        .map(this::playerButton)
                        .toList())
                .build());
    }

    private Button playerButton(UUID id) {
        return Button.builder()
                .item(ctx -> playerHead(id, "<gray>Left click: <green>+1 life", "<gray>Right click: <red>-1 life",
                        "<gray>Shift click: <white>more"))
                .on(ClickKind.LEFT, ctx -> changeLives(ctx, id, 1))
                .on(ClickKind.RIGHT, ctx -> changeLives(ctx, id, -1))
                .on(ClickKind.SHIFT_LEFT, ctx -> openPlayer(ctx, id))
                .on(ClickKind.SHIFT_RIGHT, ctx -> openPlayer(ctx, id))
                .build();
    }

    private Menu buildPlayer() {
        return Menu.builder("vow_admin_player")
                .rows(3)
                .title(ctx -> Component.text(plugin.getPlayerName(target(ctx))))
                .updateInterval(REFRESH_TICKS)
                .pattern(
                        "#########",
                        "#H#-+#VI#",
                        "B########")
                .bind('#', filler())
                .bind('B', backButton())
                .bind('H', Button.of(ctx -> playerHead(target(ctx)), ctx -> { }))
                .bind('-', Button.of(ui("ui_minus", item(Material.RED_CONCRETE, "<red><b>-1 Life")),
                        ctx -> changeLives(ctx, target(ctx), -1)))
                .bind('+', Button.of(ui("ui_plus", item(Material.LIME_CONCRETE, "<green><b>+1 Life")),
                        ctx -> changeLives(ctx, target(ctx), 1)))
                .bind('V', Button.of(ctx -> {
                    Vow vow = plugin.getVowManager().getVow(target(ctx));
                    return vow == null
                            ? item(Material.BOOK, "<gray>No vow", "<gray>Click to give a vow")
                            : item(vow.icon(), "<gold><b>" + vow.name(), "<gray>Click to change");
                }, ctx -> {
                    UiSound.CLICK.play(ctx.player());
                    PaperGUIApi.get().open(ctx.player(), vowPicker, Map.of(TARGET, target(ctx)));
                }))
                .bind('I', Button.builder()
                        .item(ctx -> isImmortal(target(ctx))
                                ? item(Material.TOTEM_OF_UNDYING, "<green><b>Immortal",
                                        "<gray>Hasn't died yet", "<gray>Click to mark as dead")
                                : item(Material.SKELETON_SKULL, "<red><b>Has Died",
                                        "<gray>Click to make immortal again"))
                        .onClick(ctx -> {
                            UUID id = target(ctx);
                            if (isImmortal(id)) {
                                plugin.addPlayerToKilled(id);
                                UiSound.REMOVE.play(ctx.player());
                            } else {
                                plugin.revivePlayer(id);
                                UiSound.SUCCESS.play(ctx.player());
                            }
                            ctx.refresh();
                        })
                        .build())
                .build();
    }

    private Menu buildVowPicker() {
        return listMenu("vow_admin_vow_picker", "Pick a Vow", Pagination.builder("vows")
                .slots(Slots.range(0, 44))
                .items(ctx -> {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Button.of(item(Material.BARRIER, "<red><b>No Vow", "<gray>Clears their vow"),
                            click -> pickVow(click, null)));
                    for (Vow vow : plugin.getVowManager().getVows()) {
                        buttons.add(Button.of(vowItem(vow), click -> pickVow(click, vow)));
                    }
                    return buttons;
                })
                .build());
    }

    private void pickVow(ClickContext ctx, Vow vow) {
        UUID id = target(ctx);
        plugin.getVowManager().setVow(id, vow);
        UiSound.SUCCESS.play(ctx.player());
        ctx.player().sendMessage(plugin.getPlayerName(id) + (vow == null ? " no longer has a vow" : " now has " + vow.name()));
        ctx.back();
    }

    // vows

    private Menu buildVows() {
        return listMenu("vow_admin_vows", "Vows", Pagination.builder("vows")
                .slots(Slots.range(0, 44))
                .items(ctx -> plugin.getVowManager().getVows().stream()
                        .map(vow -> Button.of(c -> vowItem(vow), c -> { }))
                        .toList())
                .build());
    }

    private ItemStack vowItem(Vow vow) {
        List<String> lore = new ArrayList<>();
        for (String line : wrap(vow.description())) lore.add("<gray>" + line);
        lore.add("");
        lore.add("<dark_gray>ID: " + vow.id());

        List<UUID> holders = plugin.getVowManager().getHolders(vow);
        if (holders.isEmpty()) {
            lore.add("<dark_gray>Nobody has this vow");
        } else {
            lore.add("<green>Held by:");
            for (UUID id : holders) lore.add("<white> - " + plugin.getPlayerName(id));
        }
        return item(vow.icon(), "<gold><b>" + vow.name(), lore.toArray(String[]::new));
    }

    // mythics

    private Menu buildMythics() {
        MythicManager manager = plugin.getMythicManager();
        return listMenu("vow_admin_mythics", "Mythics", Pagination.builder("mythics")
                .slots(Slots.range(0, 44))
                .items(ctx -> manager.getMythics().stream()
                        .map(mythic -> Button.builder()
                                .item(c -> mythicItem(mythic))
                                .on(ClickKind.SHIFT_RIGHT, c -> {
                                    // the item itself could be anywhere - strip the tag too if they're holding it
                                    ItemStack held = c.player().getInventory().getItemInMainHand();
                                    manager.removeMythic(mythic, held);
                                    c.player().getInventory().setItemInMainHand(held);
                                    UiSound.REMOVE.play(c.player());
                                    c.player().sendMessage(Component.text("Removed ").append(mythic.name())
                                            .append(Component.text(" from the mythics.")));
                                    c.refresh();
                                })
                                .build())
                        .toList())
                .build())
                .toBuilder()
                .button(46, transferToggle("Chat Broadcast", Material.OAK_SIGN,
                        manager::isBroadcastEnabled, manager::setBroadcastEnabled))
                .button(47, transferToggle("Sound", Material.NOTE_BLOCK,
                        manager::isSoundEnabled, manager::setSoundEnabled))
                .button(51, Button.builder()
                        .item(item(Material.ANVIL, "<yellow><b>Make Held Item Mythic",
                                "<gray>Hold a single item in your main hand"))
                        .priority(1) // above the filler in that slot
                        .onClick(ctx -> {
                            Player viewer = ctx.player();
                            ItemStack held = viewer.getInventory().getItemInMainHand();
                            if (held.isEmpty() || held.getAmount() > 1 || manager.getMythic(held) != null) {
                                viewer.sendMessage(Component.text("Hold a single item that isn't already mythic."));
                                UiSound.ERROR.play(viewer);
                                return;
                            }
                            Mythic mythic = manager.makeMythic(held, viewer);
                            viewer.getInventory().setItemInMainHand(held);
                            UiSound.SUCCESS.play(viewer);
                            viewer.sendMessage(Component.text("Made ").append(mythic.name()).append(Component.text(" a mythic.")));
                            ctx.refresh();
                        })
                        .build())
                .build();
    }

    /// a switch for something that happens when a mythic changes hands
    private Button transferToggle(String label, Material icon, BooleanSupplier get, Consumer<Boolean> set) {
        return Button.builder()
                .item(ctx -> get.getAsBoolean()
                        ? item(icon, "<green><b>" + label + ": On", "<gray>When a mythic changes hands", "<gray>Click to turn off")
                        : item(icon, "<red><b>" + label + ": Off", "<gray>When a mythic changes hands", "<gray>Click to turn on"))
                .priority(1) // above the filler in that slot
                .onClick(ctx -> {
                    boolean enabled = !get.getAsBoolean();
                    set.accept(enabled);
                    (enabled ? UiSound.ON : UiSound.OFF).play(ctx.player());
                    ctx.refresh();
                })
                .build();
    }

    private ItemStack mythicItem(Mythic mythic) {
        String holder = mythic.holder() == null ? "nobody" : plugin.getPlayerName(mythic.holder());
        ItemStack item = item(Material.NETHER_STAR, "",
                "<gray>Held by: <white>" + holder,
                "<dark_gray>" + mythic.id(),
                "",
                "<gray>Shift right click: <red>remove");
        item.editMeta(meta -> meta.displayName(mythic.name().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        return item;
    }

    // shared pieces

    /// 6 rows: the list fills the top 5, the bottom row has page arrows and a back button
    private Menu listMenu(String id, String title, Pagination pagination) {
        String page = pagination.id();
        return Menu.builder(id)
                .rows(6)
                .title(title)
                .updateInterval(REFRESH_TICKS)
                .pagination(pagination)
                .pattern(
                        "         ",
                        "         ",
                        "         ",
                        "         ",
                        "         ",
                        LIST_PATTERN_BOTTOM)
                .bind('#', filler())
                .bind('B', backButton())
                // the arrows sit on top of filler and only show when there's somewhere to go
                .bind('<', filler())
                .bind('>', filler())
                .bind('<', Button.builder()
                        .item(ui("ui_arrow_left", item(Material.ARROW, "<white>Previous Page")))
                        .viewRequirement(ctx -> ctx.session().hasPreviousPage(page))
                        .onClick(ctx -> {
                            UiSound.PAGE.play(ctx.player());
                            ctx.session().previousPage(page);
                        })
                        .priority(1)
                        .build())
                .bind('>', Button.builder()
                        .item(ui("ui_arrow_right", item(Material.ARROW, "<white>Next Page")))
                        .viewRequirement(ctx -> ctx.session().hasNextPage(page))
                        .onClick(ctx -> {
                            UiSound.PAGE.play(ctx.player());
                            ctx.session().nextPage(page);
                        })
                        .priority(1)
                        .build())
                .build();
    }

    private Button backButton() {
        return Button.of(ui("ui_back", item(Material.OAK_DOOR, "<white>Back")), ctx -> {
            UiSound.CLICK.play(ctx.player());
            ctx.back();
        });
    }

    private static Button filler() {
        ItemStack filler = ui("blank_slot", new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
        filler.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return Button.of(filler);
    }

    /// swaps the item's look for one of the pack's slot-sized UI textures (resourcepack/assets/vow/items).
    /// The real item stays underneath, so it still makes sense if the pack didn't load.
    private static ItemStack ui(String model, ItemStack item) {
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("vow", model)));
        return item;
    }

    private ItemStack playerHead(UUID id, String... extraLore) {
        Vow vow = plugin.getVowManager().getVow(id);
        List<Component> lore = new ArrayList<>();
        lore.add(text("<green>Lives: " + plugin.getPlayerLives(id) + "/" + VowSMPPlugin.MAX_LIVES));
        lore.add(text("<gray>Vow: <white>" + (vow == null ? "none" : vow.name())));
        lore.add(text(isImmortal(id) ? "<gold>Immortal" : "<dark_gray>Has died"));
        if (extraLore.length > 0) {
            lore.add(Component.empty());
            for (String line : extraLore) lore.add(text(line));
        }
        return plugin.getHeadCache().head(id, text("<white>" + plugin.getPlayerName(id)), lore);
    }

    private void changeLives(ClickContext ctx, UUID id, int change) {
        int before = plugin.getPlayerLives(id);
        int after = plugin.updatePlayerLifeCounter(id, change);
        // already at 0 or max
        UiSound sound = after == before ? UiSound.ERROR : change > 0 ? UiSound.UP : UiSound.DOWN;
        sound.play(ctx.player());
        ctx.refresh();
    }

    private void navigate(ClickContext ctx, Menu menu) {
        UiSound.CLICK.play(ctx.player());
        ctx.open(menu);
    }

    private void openPlayer(ClickContext ctx, UUID id) {
        UiSound.CLICK.play(ctx.player());
        PaperGUIApi.get().open(ctx.player(), player, Map.of(TARGET, id));
    }

    private boolean isImmortal(UUID id) {
        return plugin.getPlayersNotYetKilled().contains(id);
    }

    private static UUID target(MenuContext ctx) {
        return (UUID) ctx.session().var(TARGET);
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(text(name));
            if (lore.length > 0) meta.lore(Arrays.stream(lore).map(AdminMenus::text).toList());
        });
        return item;
    }

    /// MiniMessage with italics off, since item names and lore default to italic
    private static Component text(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /// lore doesn't wrap on its own, so long descriptions would run off the screen
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!current.isEmpty() && current.length() + 1 + word.length() > WRAP_WIDTH) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
}
