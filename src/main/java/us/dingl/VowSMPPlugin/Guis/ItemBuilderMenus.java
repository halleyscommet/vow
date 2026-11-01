package us.dingl.VowSMPPlugin.Guis;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.Guis.AdminMenus.UiSound;
import us.dingl.VowSMPPlugin.Items.Abilities.AbilityManager;
import us.dingl.VowSMPPlugin.Items.Abilities.AbilitySetting;
import us.dingl.VowSMPPlugin.Items.Abilities.ItemAbility;
import us.dingl.VowSMPPlugin.Mythics.MythicManager;
import us.dingl.VowSMPPlugin.Mythics.MythicManager.Mythic;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.paperGUI.api.PaperGUIApi;
import us.dingl.paperGUI.api.menu.Button;
import us.dingl.paperGUI.api.menu.ClickContext;
import us.dingl.paperGUI.api.menu.ClickKind;
import us.dingl.paperGUI.api.menu.Menu;
import us.dingl.paperGUI.api.menu.MenuContext;
import us.dingl.paperGUI.api.menu.Pagination;
import us.dingl.paperGUI.api.menu.Slots;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static us.dingl.VowSMPPlugin.Guis.AdminMenus.filler;
import static us.dingl.VowSMPPlugin.Guis.AdminMenus.item;
import static us.dingl.VowSMPPlugin.Guis.AdminMenus.text;
import static us.dingl.VowSMPPlugin.Guis.AdminMenus.ui;
import static us.dingl.VowSMPPlugin.Guis.AdminMenus.wrap;

/// the item builder (/vow admin item): edits the item in your hand, live. Text (type, name, lore,
/// enchantments, ability settings) is typed into dialog forms; toggles are clicks. From here an item can
/// get any registered ability and be made a mythic, so special items don't need any code of their own.
///
/// The item stays in your inventory the whole time - the menus just remember which slot it's in.
@SuppressWarnings("UnstableApiUsage")
public final class ItemBuilderMenus {

    /// session variable: inventory slot of the item being edited
    private static final String SLOT = "builder_slot";
    private static final int REFRESH_TICKS = 20;
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(15))
            .build();

    private final VowSMPPlugin plugin;
    private final Menu builder;
    private final Menu abilities;

    ItemBuilderMenus(VowSMPPlugin plugin, AdminMenus admin) {
        this.plugin = plugin;
        this.builder = buildBuilder();
        this.abilities = buildAbilities(admin);
    }

    /// opens the builder on whatever they're holding. Empty hand starts a new item.
    public void open(Player player) {
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack held = player.getInventory().getItem(slot);
        if (held == null || held.isEmpty()) {
            player.getInventory().setItem(slot, new ItemStack(Material.PAPER));
            player.sendMessage(Component.text("Started a new item in your hand. Change its type under Name & Lore.",
                    NamedTextColor.YELLOW));
        }
        open(player, builder, slot);
    }

    private void open(Player player, Menu menu, int slot) {
        PaperGUIApi.get().open(player, menu, Map.of(SLOT, slot));
    }

    // main builder

    private Menu buildBuilder() {
        return Menu.builder("vow_item_builder")
                .rows(4)
                .title("Item Builder")
                .updateInterval(REFRESH_TICKS)
                .pattern(
                        "#########",
                        "#T#E#P#A#",
                        "#G#U#H#M#",
                        "B########")
                .bind('#', filler())
                .bind('B', Button.of(ui("ui_back", item(Material.OAK_DOOR, "<white>Back")), ctx -> {
                    UiSound.CLICK.play(ctx.player());
                    plugin.getAdminMenus().openMain(ctx.player());
                }))
                .bind('P', Button.of(this::preview, ctx -> { }))
                .bind('T', Button.of(item(Material.NAME_TAG, "<gold><b>Name & Lore",
                        "<gray>Item type, name and lore", "<gray>Click to edit"), this::openTextDialog))
                .bind('E', Button.of(this::enchantsItem, this::openEnchantDialog))
                .bind('A', Button.of(this::abilitiesItem, ctx -> {
                    UiSound.CLICK.play(ctx.player());
                    open(ctx.player(), abilities, slot(ctx));
                }))
                .bind('G', Button.of(this::glintItem, ctx -> edit(ctx, item -> {
                    // default -> forced on -> forced off -> default
                    item.editMeta(meta -> {
                        Boolean current = meta.hasEnchantmentGlintOverride() ? meta.getEnchantmentGlintOverride() : null;
                        meta.setEnchantmentGlintOverride(current == null ? Boolean.TRUE : current ? Boolean.FALSE : null);
                    });
                    return item;
                })))
                .bind('U', Button.of(this::unbreakableItem, ctx -> edit(ctx, item -> {
                    item.editMeta(meta -> meta.setUnbreakable(!meta.isUnbreakable()));
                    return item;
                })))
                .bind('H', Button.of(this::hideDetailsItem, ctx -> edit(ctx, item -> {
                    if (detailsHidden(item)) {
                        item.resetData(DataComponentTypes.TOOLTIP_DISPLAY);
                    } else {
                        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                                .addHiddenComponents(DataComponentTypes.ENCHANTMENTS,
                                        DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE)
                                .build());
                    }
                    return item;
                })))
                .bind('M', Button.of(this::mythicItem, this::makeMythic))
                .build();
    }

    private ItemStack preview(MenuContext ctx) {
        ItemStack held = held(ctx);
        return held == null ? missingItem() : held.clone();
    }

    private ItemStack enchantsItem(MenuContext ctx) {
        ItemStack held = held(ctx);
        List<String> lore = new ArrayList<>();
        if (held != null && !held.getEnchantments().isEmpty()) {
            held.getEnchantments().forEach((e, level) -> lore.add("<white> - " + enchantName(e) + " " + level));
        } else {
            lore.add("<dark_gray>None");
        }
        lore.add("<gray>Click to edit");
        return item(Material.ENCHANTED_BOOK, "<light_purple><b>Enchantments", lore.toArray(String[]::new));
    }

    private ItemStack abilitiesItem(MenuContext ctx) {
        ItemStack held = held(ctx);
        List<ItemAbility> on = held == null ? List.of() : plugin.getAbilityManager().getAbilities(held);
        List<String> lore = new ArrayList<>();
        if (on.isEmpty()) {
            lore.add("<dark_gray>None");
        } else {
            for (ItemAbility ability : on) lore.add("<white> - " + ability.name());
        }
        lore.add("<gray>Click to add or change");
        return item(Material.NETHER_STAR, "<aqua><b>Abilities", lore.toArray(String[]::new));
    }

    private ItemStack glintItem(MenuContext ctx) {
        ItemStack held = held(ctx);
        Boolean glint = held != null && held.getItemMeta().hasEnchantmentGlintOverride()
                ? held.getItemMeta().getEnchantmentGlintOverride() : null;
        String state = glint == null ? "<gray>Default" : glint ? "<green>Always" : "<red>Never";
        ItemStack item = item(Material.GLOWSTONE_DUST, "<yellow><b>Glint: " + state, "<gray>Click to cycle");
        if (Boolean.TRUE.equals(glint)) item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }

    private ItemStack unbreakableItem(MenuContext ctx) {
        ItemStack held = held(ctx);
        boolean on = held != null && held.getItemMeta().isUnbreakable();
        return item(on ? Material.OBSIDIAN : Material.COBBLESTONE,
                "<yellow><b>Unbreakable: " + (on ? "<green>Yes" : "<red>No"), "<gray>Click to toggle");
    }

    private ItemStack hideDetailsItem(MenuContext ctx) {
        ItemStack held = held(ctx);
        boolean hidden = held != null && detailsHidden(held);
        return item(hidden ? Material.ENDER_EYE : Material.ENDER_PEARL,
                "<yellow><b>Hide Details: " + (hidden ? "<green>Yes" : "<red>No"),
                "<gray>Hides enchantments, attributes", "<gray>and unbreakable from the tooltip",
                "<gray>Click to toggle");
    }

    private ItemStack mythicItem(MenuContext ctx) {
        ItemStack held = held(ctx);
        Mythic mythic = held == null ? null : plugin.getMythicManager().getMythic(held);
        if (mythic != null) {
            ItemStack item = item(Material.NETHER_STAR, "<gold><b>Mythic", "<gray>This is already a mythic",
                    "<dark_gray>" + mythic.id());
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            return item;
        }
        return item(Material.GOLD_BLOCK, "<yellow><b>Make Mythic", "<gray>Turns it into a one-of-a-kind",
                "<gray>mythic that's tracked by holder", "<gray>Click to make it mythic");
    }

    private void makeMythic(ClickContext ctx) {
        Player player = ctx.player();
        ItemStack held = held(ctx);
        MythicManager mythics = plugin.getMythicManager();
        if (held == null || mythics.getMythic(held) != null) {
            UiSound.ERROR.play(player);
            return;
        }
        // the id goes on the whole stack, so a stack would be several "one-of-a-kind" items
        if (held.getAmount() > 1) {
            UiSound.ERROR.play(player);
            player.sendMessage(Component.text("A mythic has to be a single item, not a stack.", NamedTextColor.RED));
            return;
        }

        Mythic mythic = mythics.makeMythic(held, player);
        player.getInventory().setItem(slot(ctx), held);
        UiSound.SUCCESS.play(player);
        player.sendMessage(Component.text("Made ", NamedTextColor.YELLOW).append(mythic.name())
                .append(Component.text(" a mythic.", NamedTextColor.YELLOW)));
        ctx.refresh();
    }

    // abilities

    private Menu buildAbilities(AdminMenus admin) {
        return admin.listMenu("vow_item_abilities", "Abilities", Pagination.builder("abilities")
                        .slots(Slots.range(0, 44))
                        .items(ctx -> plugin.getAbilityManager().getAbilities().stream()
                                .map(this::abilityButton)
                                .toList())
                        .build())
                .toBuilder()
                // back goes to the builder even after a dialog, which loses the menu history
                .bind('B', Button.builder()
                        .item(ui("ui_back", item(Material.OAK_DOOR, "<white>Back")))
                        .onClick(ctx -> {
                            UiSound.CLICK.play(ctx.player());
                            open(ctx.player(), builder, slot(ctx));
                        })
                        .priority(1)
                        .build())
                .build();
    }

    private Button abilityButton(ItemAbility ability) {
        return Button.builder()
                .item(ctx -> abilityItem(ctx, ability))
                .on(ClickKind.LEFT, ctx -> addOrEditAbility(ctx, ability))
                .on(ClickKind.SHIFT_LEFT, ctx -> addOrEditAbility(ctx, ability))
                .on(ClickKind.RIGHT, ctx -> removeAbility(ctx, ability))
                .on(ClickKind.SHIFT_RIGHT, ctx -> removeAbility(ctx, ability))
                .build();
    }

    private ItemStack abilityItem(MenuContext ctx, ItemAbility ability) {
        ItemStack held = held(ctx);
        Map<String, String> settings = held == null ? null : plugin.getAbilityManager().getSettings(held, ability);
        boolean on = settings != null;

        List<String> lore = new ArrayList<>();
        for (String line : wrap(ability.description())) lore.add("<gray>" + line);
        lore.add("");
        if (on) {
            lore.add("<green>On this item");
            for (AbilitySetting setting : ability.settings()) {
                String value = settings.get(setting.key());
                if (value != null) {
                    lore.add("<gray>" + setting.label() + ": <white>" + setting.type().display(value));
                }
            }
            lore.add("");
            if (!ability.settings().isEmpty()) lore.add("<gray>Left click: <white>change settings");
            lore.add("<gray>Right click: <red>remove");
        } else {
            lore.add("<dark_gray>Not on this item");
            lore.add("<gray>Click to add");
        }

        ItemStack item = item(ability.icon(), (on ? "<green><b>" : "<gold><b>") + ability.name(),
                lore.toArray(String[]::new));
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(on));
        return item;
    }

    private void addOrEditAbility(ClickContext ctx, ItemAbility ability) {
        Player player = ctx.player();
        int slot = slot(ctx);
        ItemStack held = held(ctx);
        if (held == null) {
            UiSound.ERROR.play(player);
            return;
        }

        if (ability.settings().isEmpty()) {
            if (plugin.getAbilityManager().has(held, ability)) {
                UiSound.ERROR.play(player); // nothing to change - right click removes it
                return;
            }
            edit(ctx, item -> {
                plugin.getAbilityManager().apply(item, ability, Map.of());
                return item;
            });
            UiSound.SUCCESS.play(player);
            return;
        }

        UiSound.CLICK.play(player);
        ctx.close();
        openSettingsDialog(player, slot, ability);
    }

    private void removeAbility(ClickContext ctx, ItemAbility ability) {
        ItemStack held = held(ctx);
        if (held == null || !plugin.getAbilityManager().has(held, ability)) {
            UiSound.ERROR.play(ctx.player());
            return;
        }
        edit(ctx, item -> {
            plugin.getAbilityManager().remove(item, ability);
            return item;
        });
        UiSound.REMOVE.play(ctx.player());
    }

    private void openSettingsDialog(Player player, int slot, ItemAbility ability) {
        ItemStack held = player.getInventory().getItem(slot);
        Map<String, String> current = held == null ? null : plugin.getAbilityManager().getSettings(held, ability);

        List<DialogInput> inputs = new ArrayList<>();
        for (AbilitySetting setting : ability.settings()) {
            String stored = current != null && current.containsKey(setting.key())
                    ? current.get(setting.key()) : setting.defaultValue();
            inputs.add(DialogInput.text(setting.key(), Component.text(setting.label() + " (" + setting.type().hint() + ")"))
                    .initial(setting.type().display(stored))
                    .maxLength(256)
                    .width(300)
                    .build());
        }

        showDialog(player, ability.name(), List.of(DialogBody.plainMessage(Component.text(ability.description()))), inputs,
                view -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (AbilitySetting setting : ability.settings()) {
                        String value = setting.type().check(view.getText(setting.key()));
                        if (value == null) {
                            UiSound.ERROR.play(player);
                            player.sendMessage(Component.text(setting.label() + " should be " + setting.type().hint()
                                    + ". Nothing was changed.", NamedTextColor.RED));
                            open(player, abilities, slot);
                            return;
                        }
                        values.put(setting.key(), value);
                    }
                    if (edit(player, slot, item -> {
                        plugin.getAbilityManager().apply(item, ability, values);
                        return item;
                    })) {
                        UiSound.SUCCESS.play(player);
                    }
                    open(player, abilities, slot);
                },
                () -> open(player, abilities, slot));
    }

    // name, lore and type

    private void openTextDialog(ClickContext ctx) {
        Player player = ctx.player();
        int slot = slot(ctx);
        ItemStack held = held(ctx);
        if (held == null) {
            UiSound.ERROR.play(player);
            return;
        }
        UiSound.CLICK.play(player);
        ctx.close();

        String name = held.getItemMeta().hasCustomName() ? toMiniMessage(held.getItemMeta().customName()) : "";
        List<String> loreLines = new ArrayList<>();
        List<Component> lore = held.lore();
        if (lore != null) {
            for (Component line : lore) {
                if (!MythicManager.isMarker(line)) loreLines.add(toMiniMessage(line));
            }
        }

        List<DialogInput> inputs = List.of(
                DialogInput.text("type", Component.text("Item type (like golden_apple)"))
                        .initial(held.getType().getKey().getKey())
                        .maxLength(64)
                        .width(300)
                        .build(),
                DialogInput.text("name", Component.text("Name (blank for the normal name)"))
                        .initial(name)
                        .maxLength(512)
                        .width(300)
                        .build(),
                DialogInput.text("lore", Component.text("Lore (one line per line)"))
                        .initial(String.join("\n", loreLines))
                        .maxLength(4096)
                        .width(300)
                        .multiline(TextDialogInput.MultilineOptions.create(16, 120))
                        .build());

        showDialog(player, "Name & Lore", List.of(DialogBody.plainMessage(Component.text(
                        "Name and lore use MiniMessage: <gold>, <bold>, <#ff8800>, <gradient:red:blue> and so on. "
                                + "Text isn't italic unless you add <italic>."))), inputs,
                view -> {
                    Material type = Material.matchMaterial(view.getText("type").trim());
                    if (type == null || !type.isItem() || type.isAir()) {
                        UiSound.ERROR.play(player);
                        player.sendMessage(Component.text("There's no item called " + view.getText("type")
                                + " - kept the old type.", NamedTextColor.RED));
                        type = null;
                    }
                    Material newType = type;
                    edit(player, slot, item -> applyText(item, newType, view));
                    UiSound.SUCCESS.play(player);
                    open(player, builder, slot);
                },
                () -> open(player, builder, slot));
    }

    private ItemStack applyText(ItemStack item, Material type, DialogResponseView view) {
        String name = view.getText("name").trim();
        List<String> lines = new ArrayList<>(Arrays.asList(view.getText("lore").split("\n", -1)));
        while (!lines.isEmpty() && lines.getLast().isBlank()) lines.removeLast();

        item.editMeta(meta -> {
            meta.customName(name.isEmpty() ? null : text(name));
            meta.lore(lines.isEmpty() ? null : lines.stream().map(AdminMenus::text).toList());
        });

        if (type != null && type != item.getType()) {
            item = item.withType(type);
            // set the abilities up again in case their item components didn't carry over
            AbilityManager manager = plugin.getAbilityManager();
            for (ItemAbility ability : manager.getAbilities(item)) {
                manager.apply(item, ability, manager.getSettings(item, ability));
            }
        }

        // the "✦ Mythic" line was hidden from the form, so put it back
        if (plugin.getMythicManager().getMythic(item) != null) {
            plugin.getMythicManager().mark(item);
        }
        return item;
    }

    // enchantments

    private void openEnchantDialog(ClickContext ctx) {
        Player player = ctx.player();
        int slot = slot(ctx);
        ItemStack held = held(ctx);
        if (held == null) {
            UiSound.ERROR.play(player);
            return;
        }
        UiSound.CLICK.play(player);
        ctx.close();

        List<String> current = new ArrayList<>();
        held.getEnchantments().forEach((e, level) -> current.add(enchantName(e) + " " + level));

        List<DialogInput> inputs = List.of(DialogInput.text("enchants", Component.text("Enchantments"))
                .initial(String.join("\n", current))
                .maxLength(2048)
                .width(300)
                .multiline(TextDialogInput.MultilineOptions.create(16, 120))
                .build());

        showDialog(player, "Enchantments", List.of(DialogBody.plainMessage(Component.text(
                        "One per line, name then level, like \"sharpness 10\". Any level works, even past the "
                                + "normal max, on any item. Leave it empty to remove them all."))), inputs,
                view -> {
                    Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
                    List<String> problems = new ArrayList<>();
                    Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

                    for (String line : view.getText("enchants").split("\n")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length == 0 || parts[0].isEmpty()) continue;

                        NamespacedKey key = NamespacedKey.fromString(parts[0].toLowerCase());
                        Enchantment enchantment = key == null ? null : registry.get(key);
                        if (enchantment == null) {
                            problems.add("unknown enchantment \"" + parts[0] + "\"");
                            continue;
                        }
                        int level = 1;
                        if (parts.length > 1) {
                            try {
                                level = Integer.parseInt(parts[1]);
                            } catch (NumberFormatException e) {
                                level = -1;
                            }
                        }
                        if (level < 1 || level > 255) {
                            problems.add("bad level for " + parts[0] + " (1-255)");
                            continue;
                        }
                        enchants.put(enchantment, level);
                    }

                    if (!problems.isEmpty()) {
                        UiSound.ERROR.play(player);
                        player.sendMessage(Component.text("Nothing was changed: " + String.join(", ", problems) + ".",
                                NamedTextColor.RED));
                    } else if (edit(player, slot, item -> {
                        item.editMeta(meta -> {
                            meta.removeEnchantments();
                            enchants.forEach((e, level) -> meta.addEnchant(e, level, true));
                        });
                        return item;
                    })) {
                        UiSound.SUCCESS.play(player);
                    }
                    open(player, builder, slot);
                },
                () -> open(player, builder, slot));
    }

    // helpers

    /// a form with Save and Cancel. Both run on the main thread; closing it with escape does nothing.
    private void showDialog(Player player, String title, List<DialogBody> body, List<DialogInput> inputs,
                            Consumer<DialogResponseView> onSave, Runnable onCancel) {
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(text("<green>Save"))
                                .action(DialogAction.customClick((view, audience) -> sync(() -> onSave.accept(view)), ONCE))
                                .build(),
                        ActionButton.builder(text("<red>Cancel"))
                                .action(DialogAction.customClick((view, audience) -> sync(onCancel), ONCE))
                                .build())));
        player.showDialog(dialog);
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static int slot(MenuContext ctx) {
        return (Integer) ctx.session().var(SLOT);
    }

    /// the item being edited, or null if it's gone
    private static ItemStack held(MenuContext ctx) {
        ItemStack item = ctx.player().getInventory().getItem(slot(ctx));
        return item == null || item.isEmpty() ? null : item;
    }

    private void edit(ClickContext ctx, UnaryOperator<ItemStack> change) {
        if (edit(ctx.player(), slot(ctx), change)) {
            UiSound.CLICK.play(ctx.player());
        } else {
            UiSound.ERROR.play(ctx.player());
        }
        ctx.refresh();
    }

    /// changes the item in that slot and puts it back. False if the slot is empty now.
    private static boolean edit(Player player, int slot, UnaryOperator<ItemStack> change) {
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.isEmpty()) {
            player.sendMessage(Component.text("The item you were editing isn't in that slot any more.",
                    NamedTextColor.RED));
            return false;
        }
        player.getInventory().setItem(slot, change.apply(item));
        return true;
    }

    private static ItemStack missingItem() {
        return item(Material.BARRIER, "<red><b>Item missing",
                "<gray>The item you were editing", "<gray>isn't in that slot any more");
    }

    private static boolean detailsHidden(ItemStack item) {
        TooltipDisplay display = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        return display != null && !display.hiddenComponents().isEmpty();
    }

    /// "sharpness" for vanilla enchantments, the full key for anything else
    private static String enchantName(Enchantment enchantment) {
        NamespacedKey key = enchantment.getKey();
        return key.getNamespace().equals(NamespacedKey.MINECRAFT) ? key.getKey() : key.toString();
    }

    /// back into the MiniMessage an admin would type. Lines made by the builder start with italics turned
    /// off, which is the default anyway, so that's left out.
    private static String toMiniMessage(Component component) {
        if (component == null) return "";
        String mini = MiniMessage.miniMessage().serialize(component);
        for (String prefix : List.of("<!italic>", "<italic:false>", "<!i>", "<i:false>")) {
            if (mini.startsWith(prefix)) return mini.substring(prefix.length());
        }
        return mini;
    }
}
