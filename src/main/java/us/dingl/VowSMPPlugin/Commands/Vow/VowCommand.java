package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import us.dingl.VowSMPPlugin.Commands.Admin.AdminCommand;
import us.dingl.VowSMPPlugin.Commands.LivesCommand;
import us.dingl.VowSMPPlugin.Commands.WithdrawCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.ArrayList;
import java.util.List;

public class VowCommand {

    private final List<SubCommand> subCommands = new ArrayList<>();

    public VowCommand(VowSMPPlugin plugin) {
        subCommands.add(new LivesCommand(plugin));
        subCommands.add(new WithdrawCommand(plugin));
        subCommands.add(new AreaMineSubCommand(plugin));
        subCommands.add(new ResistanceSubCommand(plugin));
        subCommands.add(new TrustSubCommand(plugin));
        subCommands.add(new MythicsSubCommand(plugin));
        // everything admin-only lives under here
        subCommands.add(new AdminCommand(plugin));
        // future subcommands go here
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("vow");

        List<CommandNode<CommandSourceStack>> nodes = new ArrayList<>();
        for (SubCommand subCommand : subCommands) {
            CommandNode<CommandSourceStack> node = subCommand.build().build();
            nodes.add(node);
            root.then(node);
        }

        // running /vow with no arguments prints usage, listing only what the sender can run
        root.executes(ctx -> {
            List<String> names = nodes.stream()
                    .filter(node -> node.canUse(ctx.getSource()))
                    .map(CommandNode::getName)
                    .toList();
            ctx.getSource().getSender().sendMessage("Usage: /vow <" + String.join("|", names) + ">");
            return Command.SINGLE_SUCCESS;
        });

        return root.build();
    }
}
