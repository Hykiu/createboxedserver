package fr.hykiuidustries.createboxed.team.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.hykiuidustries.createboxed.CreateBoxed;
import fr.hykiuidustries.createboxed.plot.PlotManager;
import fr.hykiuidustries.createboxed.plot.data.PlotData;
import fr.hykiuidustries.createboxed.plot.data.PlotEntry;
import fr.hykiuidustries.createboxed.plot.data.PlotStatus;
import fr.hykiuidustries.createboxed.plot.gametest.PlotGameTests;
import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import fr.hykiuidustries.createboxed.team.TeamManager;
import fr.hykiuidustries.createboxed.team.data.Team;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-world checks for team membership and the /slot command.
 */
@GameTestHolder(CreateBoxed.MOD_ID)
public final class TeamGameTests {
    private TeamGameTests() {
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void soloTeamOnLoginTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        ServerPlayer player = createTestPlayer(helper, "solo-login");

        PlotData data = PlotData.get(level);
        Team team = data.teamOf(player.getUUID()).orElse(null);
        helper.assertTrue(team != null, "No team was created on login");
        helper.assertTrue(team.size() == 1, "A new player must start alone");
        helper.assertTrue(
            team.leader().equals(player.getUUID()),
            "Solo player must lead their team"
        );
        helper.assertTrue(team.name().length() >= 3, "Solo team name must be valid");
        helper.assertTrue(
            team.slotIndex() != null, "A new team must automatically get a slot (slot="
                + team.slotIndex() + ", plots=" + data.plots().size()
                + ", generating=" + PlotManager.get().isGenerating()
                + ", deleting=" + PlotManager.get().isDeleting() + ")"
        );
        helper.assertTrue(
            data.plots().stream().anyMatch(plot -> plot.teamId() == team.id()),
            "The automatic slot must be persisted"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void inviteAcceptAndLeaveTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = createTestPlayer(helper, "team-leader");
        ServerPlayer guest = createTestPlayer(helper, "team-guest");
        PlotData data = PlotData.get(level);
        makeTeamless(data, guest);

        String newName = "At" + leader.getUUID().toString().replace("-", "").substring(0, 8);
        helper.assertTrue(
            execute(helper, leader, "slot team rename " + newName) == 1,
            "rename failed"
        );
        helper.assertTrue(
            execute(helper, leader, "slot team invite team-guest") == 1,
            "invite failed"
        );
        helper.assertTrue(execute(helper, guest, "slot team accept") == 1, "accept failed");

        Team first = data.teamOf(leader.getUUID()).orElseThrow();
        Team second = data.teamOf(guest.getUUID()).orElseThrow();
        helper.assertTrue(first.id() == second.id(), "Accepted player joined the wrong team");
        helper.assertTrue(first.size() == 2, "Team should have two members");
        helper.assertTrue(first.name().equals(newName), "Rename was not persisted");

        helper.assertTrue(execute(helper, guest, "slot team leave") == 1, "leave failed");
        helper.assertTrue(first.size() == 1, "Leader should remain in the original team");
        helper.assertTrue(
            data.teamOf(guest.getUUID()).orElseThrow().id() != first.id(),
            "Leaving player must get a new solo team"
        );
        helper.assertTrue(first.leader().equals(leader.getUUID()), "Leader did not change");
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void leaderLeavingPromotesMemberTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = createTestPlayer(helper, "promote-leader");
        ServerPlayer guest = createTestPlayer(helper, "promote-guest");
        PlotData data = PlotData.get(level);
        makeTeamless(data, guest);

        helper.assertTrue(
            execute(helper, leader, "slot team invite promote-guest") == 1,
            "invite failed"
        );
        helper.assertTrue(execute(helper, guest, "slot team accept") == 1, "accept failed");

        Team team = data.teamOf(leader.getUUID()).orElseThrow();
        helper.assertTrue(execute(helper, leader, "slot team leave") == 1, "leader leave failed");
        helper.assertTrue(team.size() == 1, "Original team should keep the guest");
        helper.assertTrue(
            team.leader().equals(guest.getUUID()),
            "Longest-standing member must lead"
        );
        helper.assertTrue(
            data.teamOf(leader.getUUID()).orElseThrow().id() != team.id(),
            "Leaving leader must get a new solo team"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void offlineKickCreatesSoloTeamTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = createTestPlayer(helper, "kick-leader");
        ServerPlayer guest = createTestPlayer(helper, "kick-guest");
        PlotData data = PlotData.get(level);
        makeTeamless(data, guest);

        helper.assertTrue(
            execute(helper, leader, "slot team invite kick-guest") == 1,
            "invite failed"
        );
        helper.assertTrue(execute(helper, guest, "slot team accept") == 1, "accept failed");
        level.getServer().getPlayerList().remove(guest);

        helper.assertTrue(
            execute(helper, leader, "slot team kick kick-guest") == 1,
            "offline kick failed"
        );
        Team team = data.teamOf(leader.getUUID()).orElseThrow();
        helper.assertTrue(team.size() == 1, "Kicked player must leave the team");
        Team solo = data.teamOf(guest.getUUID()).orElseThrow();
        helper.assertTrue(solo.id() != team.id(), "Kicked player must get a new solo team");
        helper.assertTrue(
            solo.leader().equals(guest.getUUID()),
            "Kicked player must lead their solo team"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 20000)
    public static void slotBelongsToTeamTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer player = createTestPlayer(helper, "slot-owner");
        Team team = data.teamOf(player.getUUID()).orElseThrow();

        Integer slotIndex = team.slotIndex();
        helper.assertTrue(slotIndex != null, "Login did not automatically start a slot");
        PlotEntry entry = data.plots().stream()
            .filter(plot -> plot.index() == slotIndex)
            .findFirst()
            .orElse(null);
        helper.assertTrue(entry != null, "Slot entry was not persisted");
        helper.assertTrue(entry.teamId() == team.id(), "Slot must be owned by the team");
        helper.assertTrue(entry.status() == PlotStatus.GENERATING, "Slot should be generating");

        helper.assertTrue(
            execute(helper, player, "slot") == 0,
            "A generating slot must not be restarted"
        );

        PlotEntry created = Objects.requireNonNull(entry);
        finishPlot(helper, created);
        helper.assertTrue(created.status() == PlotStatus.DONE, "Team slot did not finish");
        helper.assertTrue(
            execute(helper, player, "slot") == 1,
            "/slot must enter the finished slot"
        );
        BlockPos expected = PlotGeometry.centerBlock(
            created.originX() + PlotGeometry.HALF_CHUNKS,
            created.originZ() + PlotGeometry.HALF_CHUNKS,
            PlotGeometry.MAX_Y + 1
        );
        helper.assertTrue(
            player.blockPosition().equals(expected),
            "Owner was not teleported to slot center"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void deleteDestroysTeamAndReusesLowestIndexTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer player = createTestPlayer(helper, "delete-owner");
        Team team = data.teamOf(player.getUUID()).orElseThrow();
        PlotEntry created = Objects.requireNonNull(
            data.plots()
                .stream()
                .filter(plot -> plot.teamId() == team.id())
                .findFirst()
                .orElse(null)
        );
        int firstIndex = created.index();
        finishPlot(helper, created);

        helper.assertTrue(
            execute(helper, player, "slot delete") == 1,
            "first delete must arm the confirmation"
        );
        helper.assertTrue(data.deletingPlot() == null, "first delete must not start clearing");

        helper.assertTrue(
            execute(helper, player, "slot delete") == 1,
            "second delete must start clearing"
        );
        PlotEntry deleting = Objects.requireNonNull(
            data.deletingPlot(),
            "deletion must be persisted"
        );
        helper.assertTrue(deleting.status() == PlotStatus.DELETING, "status must be DELETING");
        finishDeletion(helper, deleting);

        helper.assertTrue(
            data.findTeam(team.id()).isEmpty(),
            "Deleting the slot must destroy the team"
        );
        helper.assertTrue(
            data.teamOf(player.getUUID()).isEmpty(),
            "Deleted team members must be teamless"
        );

        helper.assertTrue(
            execute(helper, player, "slot") == 0,
            "A teamless /slot must queue generation"
        );
        Team replacement = data.teamOf(player.getUUID()).orElseThrow();
        helper.assertTrue(replacement.id() != team.id(), "The player must receive a new team");
        Objects.requireNonNull(TeamManager.get(), "TeamManager was not started")
            .drainPendingPlots();
        Integer index = replacement.slotIndex();
        helper.assertTrue(
            index != null && index == firstIndex,
            "the lowest free index must be reused"
        );
        PlotEntry again = Objects.requireNonNull(
            data.plots().stream()
                .filter(plot -> plot.teamId() == replacement.id())
                .findFirst()
                .orElse(null)
        );
        finishPlot(helper, again);
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void visitFinishedSlotTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer owner = createTestPlayer(helper, "visit-owner");
        Team team = data.teamOf(owner.getUUID()).orElseThrow();
        PlotEntry plot = Objects.requireNonNull(
            data.plots()
                .stream()
                .filter(entry -> entry.teamId() == team.id())
                .findFirst()
                .orElse(null)
        );
        finishPlot(helper, plot);

        ServerPlayer visitor = createTestPlayer(helper, "visit-guest");
        helper.assertTrue(
            execute(helper, visitor, "slot visit " + team.name()) == 1,
            "visit failed"
        );
        BlockPos expected = PlotGeometry.centerBlock(
            plot.originX() + PlotGeometry.HALF_CHUNKS,
            plot.originZ() + PlotGeometry.HALF_CHUNKS,
            PlotGeometry.MAX_Y + 1
        );
        helper.assertTrue(
            visitor.blockPosition().equals(expected),
            "Visitor was not teleported to the slot"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void adminDeleteDestroysTeamTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer target = createTestPlayer(helper, "admin-delete-target");
        Team team = data.teamOf(target.getUUID()).orElseThrow();
        PlotEntry plot = Objects.requireNonNull(
            data.plots()
                .stream()
                .filter(entry -> entry.teamId() == team.id())
                .findFirst()
                .orElse(null)
        );
        finishPlot(helper, plot);

        helper.assertTrue(executeAdmin(helper, "slot admin list") == 1, "admin list failed");
        helper.assertTrue(
            executeDenied(helper, target, "slot admin list") == 0,
            "a normal player must not access admin commands"
        );
        helper.assertTrue(
            executeAdmin(helper, "slot admin delete " + team.name()) == 1,
            "first admin delete must arm the confirmation"
        );
        helper.assertTrue(
            data.deletingPlot() == null,
            "first admin delete must not start clearing"
        );
        helper.assertTrue(
            executeAdmin(helper, "slot admin delete " + team.name()) == 1,
            "second admin delete must start clearing"
        );
        finishDeletion(helper, Objects.requireNonNull(data.deletingPlot()));

        helper.assertTrue(
            data.findTeam(team.id()).isEmpty(),
            "Admin deletion must destroy the team"
        );
        helper.assertTrue(
            data.teamOf(target.getUUID()).isEmpty(),
            "Admin deletion must leave the player teamless"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void adminManagesMembersTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer leader = createTestPlayer(helper, "admin-leader");
        ServerPlayer guest = createTestPlayer(helper, "admin-guest");
        makeTeamless(data, guest);
        Team team = data.teamOf(leader.getUUID()).orElseThrow();

        helper.assertTrue(
            executeAdmin(helper, "slot admin team invite " + team.name() + " admin-guest") == 1,
            "admin invite failed"
        );
        helper.assertTrue(
            execute(helper, guest, "slot team accept") == 1,
            "admin invite could not be accepted"
        );
        helper.assertTrue(team.size() == 2, "guest did not join the admin-managed team");

        String renamed = "Adm" + leader.getUUID().toString().replace("-", "").substring(0, 8);
        helper.assertTrue(
            executeAdmin(helper, "slot admin team rename " + team.name() + " " + renamed) == 1,
            "admin rename failed"
        );
        helper.assertTrue(team.name().equals(renamed), "admin rename was not persisted");

        helper.assertTrue(
            executeAdmin(helper, "slot admin team leader " + renamed + " admin-guest") == 1,
            "admin leader change failed"
        );
        helper.assertTrue(team.leader().equals(guest.getUUID()), "admin leader was not applied");

        helper.assertTrue(
            executeAdmin(helper, "slot admin team kick " + renamed + " admin-leader") == 1,
            "admin kick failed"
        );
        helper.assertTrue(team.size() == 1, "kicked leader must leave the team");
        helper.assertTrue(
            data.teamOf(leader.getUUID()).orElseThrow().id() != team.id(),
            "admin kick must create a solo team"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void adminVisitAndTeleportTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer owner = createTestPlayer(helper, "admin-visit-owner");
        Team team = data.teamOf(owner.getUUID()).orElseThrow();
        PlotEntry plot = Objects.requireNonNull(
            data.plots()
                .stream()
                .filter(entry -> entry.teamId() == team.id())
                .findFirst()
                .orElse(null)
        );
        finishPlot(helper, plot);
        BlockPos expected = PlotGeometry.centerBlock(
            plot.originX() + PlotGeometry.HALF_CHUNKS,
            plot.originZ() + PlotGeometry.HALF_CHUNKS,
            PlotGeometry.MAX_Y + 1
        );

        ServerPlayer visitor = createTestPlayer(helper, "admin-visit-guest");
        helper.assertTrue(
            executeAdmin(helper, "slot admin visit admin-visit-guest " + team.name()) == 1,
            "forced visit failed"
        );
        helper.assertTrue(
            visitor.blockPosition().equals(expected),
            "forced visit did not teleport the player"
        );

        ServerPlayer admin = createTestPlayer(helper, "admin-tp");
        helper.assertTrue(
            executeWithPermission(helper, admin, "slot admin tp " + team.name()) == 1,
            "admin teleport failed"
        );
        helper.assertTrue(
            admin.blockPosition().equals(expected),
            "admin teleport did not move the admin"
        );
        helper.succeed();
    }

    private static int execute(GameTestHelper helper, ServerPlayer player, String command) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = new CommandSourceStack(
            player, player.position(), player.getRotationVector(), level,
            0, "test", player.getDisplayName(), level.getServer(), player
        );
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException exception) {
            helper.fail("Command failed: " + command + " (" + exception.getMessage() + ")");
            return 0;
        }
    }

    private static int executeDenied(GameTestHelper helper, ServerPlayer player, String command) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = new CommandSourceStack(
            player, player.position(), player.getRotationVector(), level,
            0, "test", player.getDisplayName(), level.getServer(), player
        );
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException exception) {
            return 0;
        }
    }

    private static ServerPlayer createTestPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), name), false
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
        Objects.requireNonNull(TeamManager.get(), "TeamManager was not started")
            .drainPendingPlots();
        return player;
    }

    private static void makeTeamless(PlotData data, ServerPlayer player) {
        Team team = data.teamOf(player.getUUID()).orElse(null);
        if (team == null) {
            return;
        }
        data.removeTeam(team.id());
        for (PlotEntry plot : List.copyOf(data.plots())) {
            if (plot.teamId() == team.id()) {
                data.removePlot(plot);
            }
        }
    }

    private static void finishPlot(GameTestHelper helper, PlotEntry entry) {
        PlotData data = PlotData.get(helper.getLevel());
        data.advanceChunk(entry, PlotGeometry.CHUNKS_PER_PLOT);
        data.finishPlot(entry);
        Objects.requireNonNull(TeamManager.get(), "TeamManager was not started").onComplete(entry);
    }

    private static void finishDeletion(GameTestHelper helper, PlotEntry entry) {
        PlotData data = PlotData.get(helper.getLevel());
        data.advanceChunk(entry, PlotGeometry.CHUNKS_PER_PLOT);
        data.finishDeletion(entry);
        Objects.requireNonNull(TeamManager.get(), "TeamManager was not started").onDeleteComplete(
            entry);
    }

    private static int executeAdmin(GameTestHelper helper, String command) {
        ServerLevel level = helper.getLevel();
        try {
            return level.getServer().getCommands().getDispatcher().execute(
                command, level.getServer().createCommandSourceStack()
            );
        } catch (CommandSyntaxException exception) {
            helper.fail("Admin command failed: " + command + " (" + exception.getMessage() + ")");
            return 0;
        }
    }

    private static int executeWithPermission(
        GameTestHelper helper,
        ServerPlayer player,
        String command
    ) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = new CommandSourceStack(
            player, player.position(), player.getRotationVector(), level,
            2, "test", player.getDisplayName(), level.getServer(), player
        );
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException exception) {
            helper.fail("Command failed: " + command + " (" + exception.getMessage() + ")");
            return 0;
        }
    }
}
