package fr.hykiuidustries.createboxed.plot;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Keeps /plot as a full alias of /slot by redirecting to the same command node,
 * the way Minecraft registers /tell and /w for /msg. The redirect alone is not
 * enough for the bare command: Brigadier only follows a redirect when there is
 * another token to parse, so the alias also reuses the root command.
 */
public final class PlotCommand {
    private PlotCommand() {
    }

    public static void registerAlias(
        CommandDispatcher<CommandSourceStack> dispatcher,
        LiteralCommandNode<CommandSourceStack> slot
    ) {
        Command<CommandSourceStack> command = Objects.requireNonNull(slot.getCommand());
        dispatcher.register(Commands.literal("plot").executes(command).redirect(slot));
    }
}
