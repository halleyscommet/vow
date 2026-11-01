package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;
import us.dingl.VowSMPPlugin.Vows.VowManager;

import java.util.List;
import java.util.UUID;

public class VowsAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public VowsAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("vows")
                .executes(this::list)
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(vowArgument().executes(this::set))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(vowArgument().executes(this::add))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(vowArgument().executes(this::remove))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(this::clear)));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> vowArgument() {
        return Commands.argument("vow", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    plugin.getVowManager().getVows().forEach(v -> builder.suggest(v.id()));
                    return builder.buildFuture();
                });
    }

    /// null (after telling the sender) if there's no such vow
    private Vow vowArg(CommandContext<CommandSourceStack> ctx) {
        String vowId = StringArgumentType.getString(ctx, "vow");
        Vow vow = plugin.getVowManager().getVowById(vowId);
        if (vow == null) {
            ctx.getSource().getSender().sendMessage(Component.text("Unknown vow: " + vowId, NamedTextColor.RED));
        }
        return vow;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        VowManager vows = plugin.getVowManager();

        sender.sendMessage(Component.text("Vows:", NamedTextColor.GOLD));
        for (Vow vow : vows.getVows()) {
            List<UUID> holders = vows.getHolders(vow);
            String held = holders.isEmpty()
                    ? "nobody"
                    : String.join(", ", holders.stream().map(plugin::getPlayerName).toList());

            sender.sendMessage(Component.text(vow.name(), NamedTextColor.YELLOW)
                    .append(Component.text(" (" + vow.id() + ")", NamedTextColor.DARK_GRAY)));
            sender.sendMessage(Component.text("  " + vow.description(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Held by: " + held, NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    /// replaces all of their vows with this one
    private int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player target = CommandUtil.getPlayer(ctx, "player");
        Vow vow = vowArg(ctx);
        if (vow == null) return 0;

        plugin.getVowManager().setVow(target.getUniqueId(), vow);
        ctx.getSource().getSender().sendMessage(target.getName() + " now has only " + vow.name());
        return Command.SINGLE_SUCCESS;
    }

    /// gives them another vow, if they have a free slot
    private int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Player target = CommandUtil.getPlayer(ctx, "player");
        Vow vow = vowArg(ctx);
        if (vow == null) return 0;

        VowManager vows = plugin.getVowManager();
        UUID id = target.getUniqueId();
        switch (vows.addVow(id, vow)) {
            case ADDED -> {
                sender.sendMessage(target.getName() + " now has " + vow.name());
                return Command.SINGLE_SUCCESS;
            }
            case ALREADY_HAS -> sender.sendMessage(Component.text(target.getName() + " already has " + vow.name(),
                    NamedTextColor.RED));
            case NO_FREE_SLOT -> {
                int next = vows.livesForNextSlot(id);
                sender.sendMessage(Component.text(target.getName() + " has no free vow slots ("
                        + vows.getVows(id).size() + "/" + vows.getSlots(id) + ")"
                        + (next > 0 ? " - they need " + next + " lives for another" : ""), NamedTextColor.RED));
            }
        }
        return 0;
    }

    private int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Player target = CommandUtil.getPlayer(ctx, "player");
        Vow vow = vowArg(ctx);
        if (vow == null) return 0;

        if (!plugin.getVowManager().removeVow(target.getUniqueId(), vow)) {
            sender.sendMessage(Component.text(target.getName() + " doesn't have " + vow.name(), NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(target.getName() + " no longer has " + vow.name());
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player target = CommandUtil.getPlayer(ctx, "player");
        plugin.getVowManager().setVow(target.getUniqueId(), null);
        ctx.getSource().getSender().sendMessage(target.getName() + " no longer has any vows");
        return Command.SINGLE_SUCCESS;
    }
}
