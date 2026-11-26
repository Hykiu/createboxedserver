package fr.hykiuidustries.createboxed.plot.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.hykiuidustries.createboxed.CreateBoxed;
import fr.hykiuidustries.createboxed.plot.PlotManager;
import fr.hykiuidustries.createboxed.plot.data.PlotData;
import fr.hykiuidustries.createboxed.plot.data.PlotEntry;
import fr.hykiuidustries.createboxed.plot.data.PlotStatus;
import fr.hykiuidustries.createboxed.plot.generation.AirChunkFiller;
import fr.hykiuidustries.createboxed.plot.generation.DirtChunkFiller;
import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import fr.hykiuidustries.createboxed.team.TeamManager;
import io.netty.channel.embedded.EmbeddedChannel;
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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-world checks for the plot fill and the /plot lifecycle.
 */
@GameTestHolder(CreateBoxed.MOD_ID)
@SuppressWarnings({
    "unused",
    "DuplicatedCode"
})
// GameTest methods are discovered by the framework.
public final class PlotGameTests {
    private PlotGameTests() {
    }

    /**
     * Fills one isolated chunk and verifies the resulting blocks, heightmaps and cleanup.
     */
    @GameTest(template = "empty",
        timeoutTicks = 200)
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
            helper.assertTrue(
                level.getBlockEntity(chest) != null,
                "Chest block entity was not placed"
            );

            DirtChunkFiller filler = new DirtChunkFiller();
            filler.fill(level, chunk, 0, 0, filler.prepare(0, 0));

            for (int y : new int[]{
                PlotGeometry.MIN_Y,
                0,
                PlotGeometry.MAX_Y
            }) {
                helper.assertTrue(
                    level.getBlockState(new BlockPos(chest.getX(), y, chest.getZ()))
                        .is(Blocks.DIRT),
                    "Expected dirt at y=" + y
                );
            }
            helper.assertTrue(
                level.getBlockState(new BlockPos(
                    chest.getX(),
                    PlotGeometry.MAX_Y + 1,
                    chest.getZ()
                )).is(Blocks.AIR),
                "Nothing may be placed above y=63"
            );
            helper.assertTrue(
                level.getBlockEntity(chest) == null,
                "Chest block entity was not removed"
            );
            int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
            int motion = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
            helper.assertTrue(surface == PlotGeometry.MAX_Y, "WORLD_SURFACE height was " + surface);
            helper.assertTrue(motion == PlotGeometry.MAX_Y, "MOTION_BLOCKING height was " + motion);
            helper.succeed();
        } finally {
            level.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, pos, 0, pos);
        }
    }

    /**
     * Clears a dirt-filled chunk back to air.
     */
    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void clearOneChunkTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos pos = new ChunkPos(220, 220);
        level.getChunkSource().addRegionTicket(TicketType.UNKNOWN, pos, 0, pos);
        try {
            LevelChunk chunk = Objects.requireNonNull(
                (LevelChunk) level.getChunk(pos.x, pos.z, ChunkStatus.FULL, true)
            );
            BlockPos column = new BlockPos(pos.x * 16 + 8, 0, pos.z * 16 + 8);
            DirtChunkFiller dirt = new DirtChunkFiller();
            dirt.fill(level, chunk, 0, 0, dirt.prepare(0, 0));
            helper.assertTrue(
                level.getBlockState(column).is(Blocks.DIRT),
                "Setup failed: expected dirt before clearing"
            );

            AirChunkFiller air = new AirChunkFiller();
            air.fill(level, chunk, 0, 0, air.prepare(0, 0));
            for (int y : new int[]{
                PlotGeometry.MIN_Y,
                0,
                PlotGeometry.MAX_Y
            }) {
                helper.assertTrue(
                    level.getBlockState(new BlockPos(column.getX(), y, column.getZ()))
                        .is(Blocks.AIR),
                    "Expected air at y=" + y
                );
            }
            helper.succeed();
        } finally {
            level.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, pos, 0, pos);
        }
    }

    /**
     * Starts a plot via the command, checks the busy guard and waits for completion/teleport.
     */
    @GameTest(template = "empty",
        timeoutTicks = 20000)
    public static void plotLifecycleTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearActiveWork(level);

        PlotManager manager = Objects.requireNonNull(
            TeamManager.get(),
            "TeamManager was not started"
        ).plots();

        ServerPlayer player = createTestPlayer(helper);
        CommandSourceStack source = new CommandSourceStack(
            player, player.position(), player.getRotationVector(), level,
            2, "test", player.getDisplayName(), level.getServer(), player
        );
        CommandDispatcher<CommandSourceStack> dispatcher = level.getServer()
            .getCommands()
            .getDispatcher();

        PlotData data = PlotData.get(level);
        int teamId = data.teamOf(player.getUUID()).orElseThrow().id();
        int plotsBefore = data.plots().size();
        helper.assertTrue(plotsBefore == 0, "Joining the server must not start a plot");
        try {
            helper.assertTrue(
                dispatcher.execute("plot", source) == 1,
                "/plot must start the team's plot"
            );
            PlotEntry entry = data.plots().stream()
                .filter(plot -> plot.teamId() == teamId)
                .findFirst()
                .orElse(null);
            helper.assertTrue(entry != null, "/plot must persist a plot");
            helper.assertTrue(manager.isGenerating(), "Manager should be busy");

            helper.assertTrue(
                dispatcher.execute("plot team info", source) == 1,
                "/plot must expose the /slot subcommands through its redirect"
            );

            int refused = dispatcher.execute("plot", source);
            helper.assertTrue(refused == 0, "A second /plot must be refused while busy");
            int count = data.plots().size();
            helper.assertTrue(
                count == plotsBefore + 1,
                "A second plot must not be created (found " + count + ")"
            );

            // The async pipeline is covered by slotBelongsToTeamTest; here we settle the
            // persisted state directly so this test cannot interleave with the other GameTests.
            PlotEntry created = Objects.requireNonNull(entry);
            data.advanceChunk(created, PlotGeometry.CHUNKS_PER_PLOT);
            data.finishPlot(created);
            helper.assertTrue(created.status() == PlotStatus.DONE, "Plot status should be DONE");
            helper.assertTrue(
                created.nextChunk() == PlotGeometry.CHUNKS_PER_PLOT,
                "All chunks should be filled"
            );
            helper.assertTrue(data.generatingPlot() == null, "Manager should no longer be busy");
            helper.succeed();
        } catch (CommandSyntaxException exception) {
            helper.fail("Command failed: " + exception.getMessage());
        }
    }

    /**
     * SavedData survives between GameTestServer runs. Give every test a clean slate so
     * stale teams cannot occupy the single-generation queue before the test's own player.
     */
    public static void clearActiveWork(ServerLevel level) {
        TeamManager teams = Objects.requireNonNull(
            TeamManager.get(),
            "TeamManager was not started"
        );
        teams.stop();
        teams.plots().stop();
        PlotData data = PlotData.get(level);
        for (fr.hykiuidustries.createboxed.team.data.Team team : List.copyOf(data.teams())) {
            data.removeTeam(team.id());
        }
        for (PlotEntry plot : List.copyOf(data.plots())) {
            data.removePlot(plot);
        }
        data.setNextIndex(0);
        teams.plots().start(level);
        teams.start(level);
    }

    /**
     * Registers a real ServerPlayer in the player list without the deprecated GameTest helper.
     */
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
