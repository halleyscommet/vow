package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public interface SubCommand {
    LiteralArgumentBuilder<CommandSourceStack> build();
}
