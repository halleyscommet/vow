package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class UpdateAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public UpdateAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("update")
                .executes(ctx -> {
                    plugin.getUpdater().update(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                });
    }
}
