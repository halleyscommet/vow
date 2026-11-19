package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.Listeners.MaceCooldownListener;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// /vow admin mace cooldown [seconds] - how long maces cool down after a hit (0 = off). Mythic maces are exempt.
public class MaceAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public MaceAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mace")
                .then(Commands.literal("cooldown")
                        .executes(ctx -> {
                            int seconds = plugin.getMaceCooldown().getSeconds();
                            ctx.getSource().getSender().sendMessage(Component.text(seconds > 0
                                    ? "Mace cooldown is " + seconds + "s (mythic maces are exempt)."
                                    : "Mace cooldown is off.", NamedTextColor.YELLOW));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, MaceCooldownListener.MAX_SECONDS))
                                .executes(ctx -> {
                                    int seconds = plugin.getMaceCooldown()
                                            .setSeconds(IntegerArgumentType.getInteger(ctx, "seconds"));
                                    ctx.getSource().getSender().sendMessage(Component.text(seconds > 0
                                            ? "Mace cooldown set to " + seconds + "s."
                                            : "Mace cooldown turned off.", NamedTextColor.YELLOW));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
