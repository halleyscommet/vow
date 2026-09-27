package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Guis.ConfigMenuGui;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class GuiSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public GuiSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("menu")
                .requires(source -> source.getSender().hasPermission("vow.config"))
                .executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    ConfigMenuGui.create(plugin).open(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
