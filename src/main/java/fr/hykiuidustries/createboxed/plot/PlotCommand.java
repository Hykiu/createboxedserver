package fr.hykiuidustries.createboxed.plot;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** The /plot command, restricted to permission level 2. */
public final class PlotCommand {
    private PlotCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("plot")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                PlotManager manager = PlotManager.get();
                if (manager == null) {
                    source.sendFailure(Component.translatable("createboxed.plot.server_only"));
                    return 0;
                }
                Component error = manager.createPlot(source);
                if (error != null) {
                    source.sendFailure(error);
                    return 0;
                }
                return 1;
            })
        );
    }
}
