package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.Mythics.MythicManager;
import us.dingl.VowSMPPlugin.Mythics.MythicManager.Mythic;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.UUID;

/// /vow admin makemythic - turns the held item into a tracked mythic
/// /vow admin removemythic [id] - stops tracking the held mythic, or the one with that uuid
public class AdminSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public AdminSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin")
                .requires(CommandUtil.permission(Permissions.MYTHICS_MANAGE))
                .then(Commands.literal("makemythic").executes(this::makeMythic))
                .then(Commands.literal("removemythic")
                        .executes(this::removeHeldMythic)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    // name as the tooltip, since nobody remembers uuids
                                    for (Mythic mythic : plugin.getMythicManager().getMythics()) {
                                        builder.suggest(mythic.id().toString(),
                                                MessageComponentSerializer.message().serialize(mythic.name()));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(this::removeMythicById)));
    }

    private int makeMythic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = CommandUtil.requirePlayer(ctx);
        MythicManager mythics = plugin.getMythicManager();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.isEmpty()) {
            player.sendMessage(Component.text("Hold the item you want to make mythic.", NamedTextColor.RED));
            return 0;
        }
        // the uuid goes on the whole stack, so a stack would be several "one-of-a-kind" items
        if (item.getAmount() > 1) {
            player.sendMessage(Component.text("Hold a single item, not a stack.", NamedTextColor.RED));
            return 0;
        }
        // a leftover tag from a removed mythic is fine - makeMythic gives it a new uuid
        if (mythics.getMythic(item) != null) {
            player.sendMessage(Component.text("That's already a mythic.", NamedTextColor.RED));
            return 0;
        }

        Mythic mythic = mythics.makeMythic(item, player);
        player.getInventory().setItemInMainHand(item);
        player.sendMessage(Component.text("Made ", NamedTextColor.YELLOW)
                .append(mythic.name())
                .append(Component.text(" a mythic (" + mythic.id() + ").", NamedTextColor.YELLOW)));
        return Command.SINGLE_SUCCESS;
    }

    private int removeHeldMythic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = CommandUtil.requirePlayer(ctx);
        ItemStack item = player.getInventory().getItemInMainHand();

        Mythic mythic = plugin.getMythicManager().getMythic(item);
        if (mythic == null) {
            player.sendMessage(Component.text("You're not holding a mythic. Use /vow admin removemythic <id> instead.",
                    NamedTextColor.RED));
            return 0;
        }

        plugin.getMythicManager().removeMythic(mythic, item);
        player.getInventory().setItemInMainHand(item);
        sendRemoved(player, mythic);
        return Command.SINGLE_SUCCESS;
    }

    private int removeMythicById(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String input = StringArgumentType.getString(ctx, "id");

        Mythic mythic = null;
        try {
            mythic = plugin.getMythicManager().getMythic(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            // not a uuid, falls through to the error below
        }
        if (mythic == null) {
            sender.sendMessage(Component.text("No mythic with id " + input, NamedTextColor.RED));
            return 0;
        }

        // strip the tag too if they happen to be holding it
        ItemStack held = sender instanceof Player player ? player.getInventory().getItemInMainHand() : null;
        plugin.getMythicManager().removeMythic(mythic, held);
        if (held != null) ((Player) sender).getInventory().setItemInMainHand(held);
        sendRemoved(sender, mythic);
        return Command.SINGLE_SUCCESS;
    }

    private void sendRemoved(CommandSender sender, Mythic mythic) {
        sender.sendMessage(Component.text("Removed ", NamedTextColor.YELLOW)
                .append(mythic.name())
                .append(Component.text(" from the mythics. It's a normal item again.", NamedTextColor.YELLOW)));
    }
}
