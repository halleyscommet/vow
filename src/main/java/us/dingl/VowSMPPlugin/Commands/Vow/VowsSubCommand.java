package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;
import us.dingl.VowSMPPlugin.Vows.VowManager;

import java.util.List;
import java.util.UUID;

public class VowsSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public VowsSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("vows")
                .requires(CommandUtil.permission(Permissions.VOWS_LIST))
                .executes(this::list)
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("set")
                        .requires(CommandUtil.permission(Permissions.VOWS_MANAGE))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("vow", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            plugin.getVowManager().getVows().forEach(v -> builder.suggest(v.id()));
                                            return builder.buildFuture();
                                        })
                                        .executes(this::set))))
                .then(Commands.literal("clear")
                        .requires(CommandUtil.permission(Permissions.VOWS_MANAGE))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(this::clear)));
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

    private int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Player target = CommandUtil.getPlayer(ctx, "player");
        String vowId = StringArgumentType.getString(ctx, "vow");

        Vow vow = plugin.getVowManager().getVowById(vowId);
        if (vow == null) {
            sender.sendMessage(Component.text("Unknown vow: " + vowId, NamedTextColor.RED));
            return 0;
        }
        plugin.getVowManager().setVow(target.getUniqueId(), vow);
        sender.sendMessage(target.getName() + " now has " + vow.name());
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player target = CommandUtil.getPlayer(ctx, "player");
        plugin.getVowManager().setVow(target.getUniqueId(), null);
        ctx.getSource().getSender().sendMessage(target.getName() + " no longer has a vow");
        return Command.SINGLE_SUCCESS;
    }
}
