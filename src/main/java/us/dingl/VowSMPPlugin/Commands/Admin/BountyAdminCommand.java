package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import us.dingl.VowSMPPlugin.Bounty.BountyManager;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.UUID;

/// /vow admin bounty <give|take> <player>, /vow admin bounty list|clear - the event bounty tag
public class BountyAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public BountyAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("bounty")
                .executes(this::list)
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("give").then(playerArg(true)))
                .then(Commands.literal("take").then(playerArg(false)))
                .then(Commands.literal("clear").executes(ctx -> {
                    plugin.getBountyManager().clear();
                    ctx.getSource().getSender().sendMessage(Component.text("Nobody has the bounty now.", NamedTextColor.YELLOW));
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> playerArg(boolean give) {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    BountyManager bounty = plugin.getBountyManager();
                    plugin.getLoggedPlayers().stream()
                            .filter(id -> bounty.has(id) != give) // give suggests non-holders, take suggests holders
                            .forEach(id -> builder.suggest(plugin.getPlayerName(id)));
                    return builder.buildFuture();
                })
                .executes(ctx -> change(ctx, give));
    }

    private int change(CommandContext<CommandSourceStack> ctx, boolean give) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");

        CommandUtil.lookupUuid(plugin, name, id -> {
            if (id == null) {
                sender.sendMessage(Component.text("No player named " + name, NamedTextColor.RED));
                return;
            }
            BountyManager bounty = plugin.getBountyManager();
            String display = plugin.getPlayerName(id);
            if (give) {
                sender.sendMessage(bounty.give(id)
                        ? Component.text(display + " now has the bounty.", NamedTextColor.GOLD)
                        : Component.text(display + " already has the bounty.", NamedTextColor.RED));
            } else {
                sender.sendMessage(bounty.take(id)
                        ? Component.text(display + " no longer has the bounty.", NamedTextColor.YELLOW)
                        : Component.text(display + " doesn't have the bounty.", NamedTextColor.RED));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        var holders = plugin.getBountyManager().getHolders();
        ctx.getSource().getSender().sendMessage(holders.isEmpty()
                ? Component.text("Nobody has the bounty.", NamedTextColor.YELLOW)
                : Component.text("Bounty: " + String.join(", ",
                        holders.stream().map((UUID id) -> plugin.getPlayerName(id)).toList()), NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }
}
