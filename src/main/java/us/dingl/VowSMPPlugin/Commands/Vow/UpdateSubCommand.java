package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class UpdateSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public UpdateSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("update")
                .requires(CommandUtil.permission(Permissions.UPDATE))
                .executes(ctx -> {
                    plugin.getUpdater().update(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                });
    }
}
