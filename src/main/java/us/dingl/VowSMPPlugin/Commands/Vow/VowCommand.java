package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.ArrayList;
import java.util.List;

public class VowCommand {

    private final List<SubCommand> subCommands = new ArrayList<>();

    public VowCommand(VowSMPPlugin plugin) {
        subCommands.add(new LeaderboardSubCommand(plugin));
        subCommands.add(new GuiSubCommand(plugin));
        subCommands.add(new StatsSubCommand(plugin));
        subCommands.add(new RitualSubCommand(plugin));
        subCommands.add(new AdminSubCommand(plugin));
        // future subcommands go here
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        List<String> names = new ArrayList<>();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("vow");

        for (SubCommand subCommand : subCommands) {
            LiteralArgumentBuilder<CommandSourceStack> node = subCommand.build();
            names.add(node.getLiteral());
            root.then(node);
        }

        // running /vow with no arguments prints usage
        root.executes(ctx -> {
            ctx.getSource().getSender().sendMessage("Usage: /vow <" + String.join("|", names) + ">");
            return Command.SINGLE_SUCCESS;
        });

        return root.build();
    }
}
