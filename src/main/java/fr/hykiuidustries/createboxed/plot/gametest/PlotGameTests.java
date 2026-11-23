package fr.hykiuidustries.createboxed.plot.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.hykiuidustries.createboxed.CreateBoxed;
import fr.hykiuidustries.createboxed.plot.PlotManager;
import fr.hykiuidustries.createboxed.plot.data.PlotData;
import fr.hykiuidustries.createboxed.plot.data.PlotEntry;
import fr.hykiuidustries.createboxed.plot.data.PlotStatus;
import fr.hykiuidustries.createboxed.plot.generation.DirtChunkFiller;
import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** In-world checks for the plot fill and the /plot lifecycle. */
@GameTestHolder(CreateBoxed.MODID)
public final class PlotGameTests {
    private PlotGameTests() {
    }

    /** Fills one isolated chunk and verifies the resulting blocks, heightmaps and cleanup. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fillOneChunkTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A chunk far from the GameTest grid so the full fill cannot disturb other tests.
        ChunkPos pos = new ChunkPos(200, 200);
        level.getChunkSource().addRegionTicket(TicketType.UNKNOWN, pos, 0, pos);
        try {
            LevelChunk chunk = Objects.requireNonNull(
                (LevelChunk) level.getChunk(pos.x, pos.z, ChunkStatus.FULL, true)
            );
            BlockPos chest = new BlockPos(pos.x * 16 + 8, 0, pos.z * 16 + 8);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockEntity(chest) != null, "Chest block entity was not placed");

            DirtChunkFiller filler = new DirtChunkFiller();
            filler.fill(level, chunk, 0, 0, filler.prepare(0, 0));

            for (int y : new int[] {PlotGeometry.MIN_Y, 0, PlotGeometry.MAX_Y}) {
                helper.assertTrue(
                    level.getBlockState(new BlockPos(chest.getX(), y, chest.getZ())).is(Blocks.DIRT),
                    "Expected dirt at y=" + y
                );
            }
            helper.assertTrue(
                level.getBlockState(new BlockPos(chest.getX(), PlotGeometry.MAX_Y + 1, chest.getZ())).is(Blocks.AIR),
                "Nothing may be placed above y=63"
            );
            helper.assertTrue(level.getBlockEntity(chest) == null, "Chest block entity was not removed");
            int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
            int motion = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
            helper.assertTrue(surface == PlotGeometry.MAX_Y, "WORLD_SURFACE height was " + surface);
            helper.assertTrue(motion == PlotGeometry.MAX_Y, "MOTION_BLOCKING height was " + motion);
            helper.succeed();
        } finally {
            level.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, pos, 0, pos);
        }
    }

    /** Starts a plot via the command, checks the busy guard and waits for completion/teleport. */
    @GameTest(template = "empty", timeoutTicks = 20000)
    public static void plotLifecycleTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // SavedData can survive between GameTestServer runs: clear any leftover generating plot.
        PlotData leftover = PlotData.get(level);
        if (leftover.generatingPlot() != null) {
            leftover.finishPlot(leftover.generatingPlot());
        }
        PlotManager.stop();
        PlotManager.start(level);

        PlotManager manager = Objects.requireNonNull(PlotManager.get(), "PlotManager was not started");

        ServerPlayer player = createTestPlayer(helper);
        CommandSourceStack source = new CommandSourceStack(
            player, player.position(), player.getRotationVector(), level,
            2, "test", player.getDisplayName(), level.getServer(), player
        );
        CommandDispatcher<CommandSourceStack> dispatcher = level.getServer().getCommands().getDispatcher();

        int plotsBefore = PlotData.get(level).plots().size();
        try {
            int started = dispatcher.execute("plot", source);
            helper.assertTrue(started == 1, "/plot should start generation");
            helper.assertTrue(manager.isGenerating(), "Manager should be busy");

            PlotEntry entry = PlotData.get(level).generatingPlot();
            helper.assertTrue(entry != null, "Generating plot not persisted");

            int refused = dispatcher.execute("plot", source);
            helper.assertTrue(refused == 0, "A second /plot must be refused while busy");
            int count = PlotData.get(level).plots().size();
            helper.assertTrue(count == plotsBefore + 1, "A second plot must not be created (found " + count + ")");

            helper.succeedWhen(() -> {
                PlotData data = PlotData.get(level);
                PlotEntry current = data.generatingPlot();
                if (current != null) {
                    helper.assertTrue(
                        false,
                        "Plot generation did not finish: chunk " + current.nextChunk() + "/"
                            + PlotGeometry.CHUNKS_PER_PLOT + " status=" + current.status()
                    );
                }
                PlotEntry done = data.plots().getLast();
                helper.assertTrue(done.status() == PlotStatus.DONE, "Plot status should be DONE");
                helper.assertTrue(done.nextChunk() == PlotGeometry.CHUNKS_PER_PLOT, "All chunks should be filled");

                BlockPos expected = PlotGeometry.centerBlock(
                    done.originX() + PlotGeometry.HALF_CHUNKS,
                    done.originZ() + PlotGeometry.HALF_CHUNKS,
                    PlotGeometry.MAX_Y + 1
                );
                helper.assertTrue(player.blockPosition().equals(expected), "Creator was not teleported to the plot center");
            });
        } catch (CommandSyntaxException exception) {
            helper.fail("Command failed: " + exception.getMessage());
        }
    }

    /** Registers a real ServerPlayer in the player list without the deprecated GameTest helper. */
    private static ServerPlayer createTestPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "plot-test-player"), false
        );
        ServerPlayer player = new ServerPlayer(
            level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()
        ) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
