package fr.hykiuidustries.createboxed.team;

import fr.hykiuidustries.createboxed.Keys.Lang;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.hykiuidustries.createboxed.plot.PlotCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * /slot (go-or-create) and its team subcommands. /plot is registered as an alias.
 */
@NullMarked
public final class SlotCommand {
    private SlotCommand() {
    }

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher,
        TeamManager manager
    ) {
        LiteralCommandNode<CommandSourceStack> slot = dispatcher.register(root("slot", manager));
        PlotCommand.registerAlias(dispatcher, slot);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> root(
        String name,
        TeamManager manager
    ) {
        return Commands.literal(name)
            .executes(context -> {
                ServerPlayer player = player(context);
                return player == null
                       ? fail(
                           context.getSource(),
                           Component.translatable(Lang.SLOT_PLAYER_ONLY)
                       )
                       : report(context.getSource(), manager.goOrCreate(player));
            })
            .then(Commands.literal("team")
                .then(Commands.literal("invite")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> withPlayer(
                            context, source -> manager.invite(
                                source, StringArgumentType.getString(context, "player")
                            )
                        ))
                    )
                )
                .then(Commands.literal("accept")
                    .executes(context -> withPlayer(context, manager::accept))
                )
                .then(Commands.literal("leave")
                    .executes(context -> withPlayer(context, manager::leave))
                )
                .then(Commands.literal("kick")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> withPlayer(
                            context, source -> manager.kick(
                                source, StringArgumentType.getString(context, "player")
                            )
                        ))
                    )
                )
                .then(Commands.literal("rename")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> withPlayer(
                            context, source -> manager.rename(
                                source, StringArgumentType.getString(context, "name")
                            )
                        ))
                    )
                )
                .then(Commands.literal("info")
                    .executes(context -> {
                        ServerPlayer player = player(context);
                        if (player == null) {
                            return fail(
                                context.getSource(),
                                Component.translatable(Lang.SLOT_PLAYER_ONLY)
                            );
                        }
                        manager.info(player);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("delete")
                .executes(context -> withPlayer(context, manager::delete))
            )
            .then(Commands.literal("setspawn")
                .executes(context -> withPlayer(context, manager::setSpawn))
            )
            .then(Commands.literal("visit")
                .then(Commands.argument("team", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        for (String teamName : manager.visitableTeamNames()) {
                            builder.suggest(teamName);
                        }
                        return builder.buildFuture();
                    })
                    .executes(context -> withPlayer(
                        context, source -> manager.visit(
                            source, StringArgumentType.getString(context, "team")
                        )
                    ))
                )
            )
            .then(Commands.literal("admin")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> {
                    manager.adminList(context.getSource());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("list")
                    .executes(context -> {
                        manager.adminList(context.getSource());
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(Commands.literal("info")
                    .then(adminTeamArgument(manager)
                        .executes(context -> report(
                            context.getSource(), manager.adminInfo(
                                context.getSource(), adminTeam(context)
                            )
                        ))
                    )
                )
                .then(Commands.literal("tp")
                    .then(adminTeamArgument(manager)
                        .executes(context -> report(
                            context.getSource(), manager.adminTeleport(
                                context.getSource(), adminTeam(context)
                            )
                        ))
                    )
                )
                .then(Commands.literal("setspawn")
                    .then(adminTeamArgument(manager)
                        .executes(context -> report(
                            context.getSource(), manager.adminSetSpawn(
                                context.getSource(), adminTeam(context), null
                            )
                        ))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                    .executes(context -> report(
                                        context.getSource(), manager.adminSetSpawn(
                                            context.getSource(), adminTeam(context),
                                            new BlockPos(
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "y"),
                                                IntegerArgumentType.getInteger(context, "z")
                                            )
                                        )
                                    ))
                                )
                            )
                        )
                    )
                )
                .then(Commands.literal("visit")
                    .then(adminPlayerArgument(manager)
                        .then(adminTeamArgument(manager)
                            .executes(context -> report(
                                context.getSource(), manager.adminVisit(
                                    context.getSource(), adminPlayer(context), adminTeam(context)
                                )
                            ))
                        )
                    )
                )
                .then(Commands.literal("delete")
                    .then(adminTeamArgument(manager)
                        .executes(context -> report(
                            context.getSource(), manager.adminDelete(
                                context.getSource(), adminTeam(context)
                            )
                        ))
                    )
                )
                .then(Commands.literal("team")
                    .then(Commands.literal("invite")
                        .then(adminTeamArgument(manager)
                            .then(adminPlayerArgument(manager)
                                .executes(context -> report(
                                    context.getSource(), manager.adminInvite(
                                        context.getSource(),
                                        adminTeam(context),
                                        adminPlayer(context)
                                    )
                                ))
                            )
                        )
                    )
                    .then(Commands.literal("kick")
                        .then(adminTeamArgument(manager)
                            .then(adminPlayerArgument(manager)
                                .executes(context -> report(
                                    context.getSource(), manager.adminKick(
                                        context.getSource(),
                                        adminTeam(context),
                                        adminPlayer(context)
                                    )
                                ))
                            )
                        )
                    )
                    .then(Commands.literal("leader")
                        .then(adminTeamArgument(manager)
                            .then(adminPlayerArgument(manager)
                                .executes(context -> report(
                                    context.getSource(), manager.adminLeader(
                                        context.getSource(),
                                        adminTeam(context),
                                        adminPlayer(context)
                                    )
                                ))
                            )
                        )
                    )
                    .then(Commands.literal("rename")
                        .then(adminTeamArgument(manager)
                            .then(Commands.argument("newName", StringArgumentType.word())
                                .executes(context -> report(
                                    context.getSource(), manager.adminRename(
                                        context.getSource(), adminTeam(context),
                                        StringArgumentType.getString(context, "newName")
                                    )
                                ))
                            )
                        )
                    )
                )
            );
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> adminTeamArgument(
        TeamManager manager
    ) {
        return Commands.argument("team", StringArgumentType.word())
            .suggests((context, builder) -> suggest(builder, manager.adminTeamNames()));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> adminPlayerArgument(
        TeamManager manager
    ) {
        return Commands.argument("player", StringArgumentType.word())
            .suggests((context, builder) -> suggest(builder, manager.adminPlayerNames()));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
        SuggestionsBuilder builder,
        Iterable<String> values
    ) {
        for (String value : values) {
            builder.suggest(value);
        }
        return builder.buildFuture();
    }

    private static String adminTeam(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "team");
    }

    private static String adminPlayer(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "player");
    }

    private static int withPlayer(
        CommandContext<CommandSourceStack> context,
        java.util.function.Function<ServerPlayer, @Nullable Component> action
    ) {
        ServerPlayer player = player(context);
        if (player == null) {
            return fail(
                context.getSource(),
                Component.translatable(Lang.SLOT_PLAYER_ONLY)
            );
        }
        return report(context.getSource(), action.apply(player));
    }

    private static int report(CommandSourceStack source, @Nullable Component error) {
        return error == null ? Command.SINGLE_SUCCESS : fail(source, error);
    }

    private static int fail(CommandSourceStack source, Component error) {
        source.sendFailure(error);
        return 0;
    }

    private static @Nullable ServerPlayer player(CommandContext<CommandSourceStack> context) {
        Entity entity = context.getSource().getEntity();
        return entity instanceof ServerPlayer player ? player : null;
    }
}
