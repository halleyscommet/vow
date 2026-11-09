package us.dingl.VowSMPPlugin.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Guis.GearMenu;

/// /gear - opens GearMenu. Anyone not on its list doesn't see the command at all.
public final class GearCommand {

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("gear")
                .requires(source -> source.getSender() instanceof Player player && GearMenu.isAllowed(player))
                .executes(ctx -> {
                    GearMenu.open((Player) ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                });
    }
}
