package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.Listeners.ResourcePackListener;
import us.dingl.VowSMPPlugin.Listeners.ResourcePackListener.Pack;
import us.dingl.VowSMPPlugin.Listeners.ResourcePackListener.Source;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.Locale;

/// /vow admin pack [release|config] - shows or switches where the resource pack comes from
public class PackAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public PackAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pack").executes(this::status);
        for (Source source : Source.values()) {
            root.then(Commands.literal(source.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                plugin.getResourcePackListener().setSource(source);
                return status(ctx);
            }));
        }
        return root;
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ResourcePackListener packs = plugin.getResourcePackListener();
        Pack pack = packs.currentPack();

        sender.sendMessage("Resource pack source: " + packs.getSource().name().toLowerCase(Locale.ROOT));
        if (pack.problem() != null) {
            sender.sendMessage(Component.text("Nothing is being sent: " + pack.problem(), NamedTextColor.RED));
        } else {
            sender.sendMessage("Sending: " + pack.url());
        }
        return Command.SINGLE_SUCCESS;
    }
}
