package fr.hykiuidustries.createboxed.team.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.hykiuidustries.createboxed.CreateBoxed;
import fr.hykiuidustries.createboxed.plot.data.PlotData;
import fr.hykiuidustries.createboxed.plot.data.PlotEntry;
import fr.hykiuidustries.createboxed.plot.data.PlotStatus;
import fr.hykiuidustries.createboxed.plot.gametest.PlotGameTests;
import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import fr.hykiuidustries.createboxed.plot.geometry.PlotSpawn;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * In-world checks for team membership and the /slot command.
 */
@GameTestHolder(CreateBoxed.MOD_ID)
@SuppressWarnings({"unused", "DuplicatedCode", "DataFlowIssue"}) // GameTest methods are discovered by the framework.
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
            team.slotIndex() == null,
            "A new team must not get a slot before /slot (slot=" + team.slotIndex()
                + ", plots=" + data.plots().size() + ")"
        );
        helper.assertTrue(
            data.plots().isEmpty(),
            "No slot may be persisted before the player runs /slot"
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
        startPlot(helper, player);

        Integer slotIndex = team.slotIndex();
        helper.assertTrue(slotIndex != null, "/slot did not start a slot");
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
        BlockPos expected = Objects.requireNonNull(
            created.spawnPoint(),
            "The teleport must save a valid spawn point"
        );
        helper.assertTrue(PlotSpawn.isValid(level, expected), "The saved spawn point must be valid");
        helper.assertTrue(
            player.blockPosition().equals(expected),
            "Owner was not teleported to the saved spawn point"
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
        startPlot(helper, player);
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
            execute(helper, player, "slot") == 1,
            "A teamless /slot must start a new slot"
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
        startPlot(helper, owner);
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
        BlockPos expected = Objects.requireNonNull(
            plot.spawnPoint(),
            "The completed plot must have a saved spawn point"
        );
        helper.assertTrue(
            visitor.blockPosition().equals(expected),
            "Visitor was not teleported to the saved spawn point"
        );
        helper.assertTrue(
            execute(helper, visitor, "slot visit " + team.name()) == 1,
            "visiting a slot you are already in must be allowed"
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
        startPlot(helper, target);
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
            executeDenied(helper, target) == 0,
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
        startPlot(helper, owner);
        PlotEntry plot = Objects.requireNonNull(
            data.plots()
                .stream()
                .filter(entry -> entry.teamId() == team.id())
                .findFirst()
                .orElse(null)
        );
        finishPlot(helper, plot);
        BlockPos expected = Objects.requireNonNull(
            plot.spawnPoint(),
            "The completed plot must have a saved spawn point"
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

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void leaderSetsSpawnAndTeleportUsesItTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer player = createTestPlayer(helper, "spawn-leader");
        Team team = data.teamOf(player.getUUID()).orElseThrow();
        startPlot(helper, player);
        PlotEntry plot = plotOf(data, team);
        finishPlot(helper, plot);

        BlockPos desired = player.blockPosition();
        helper.assertTrue(
            PlotSpawn.isValid(level, desired),
            "Setup: the default teleport point must be valid"
        );
        helper.assertTrue(
            execute(helper, player, "slot setspawn") == 1,
            "the leader must be able to set the spawn point"
        );
        helper.assertTrue(
            desired.equals(plot.spawnPoint()),
            "The chosen spawn point must be persisted on the plot"
        );

        BlockPos away = new BlockPos(desired.getX() + 100, desired.getY(), desired.getZ());
        movePlayer(player, level, away);
        helper.assertTrue(
            !player.blockPosition().equals(desired),
            "Setup: the player must be moved away from the spawn point"
        );
        helper.assertTrue(execute(helper, player, "slot") == 1, "/slot failed");
        helper.assertTrue(
            desired.equals(player.blockPosition()),
            "The teleport must use the spawn point set by the leader"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void nonLeaderCannotSetSpawnTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer leader = createTestPlayer(helper, "spawn-team-leader");
        ServerPlayer guest = createTestPlayer(helper, "spawn-team-guest");
        makeTeamless(data, guest);
        Team team = data.teamOf(leader.getUUID()).orElseThrow();
        startPlot(helper, leader);
        PlotEntry plot = plotOf(data, team);
        finishPlot(helper, plot);

        helper.assertTrue(
            executeAdmin(helper, "slot admin team invite " + team.name() + " spawn-team-guest")
                == 1,
            "admin invite failed"
        );
        helper.assertTrue(
            execute(helper, guest, "slot team accept") == 1,
            "the guest could not join the team"
        );
        BlockPos before = plot.spawnPoint();
        helper.assertTrue(
            execute(helper, guest, "slot setspawn") == 0,
            "a non-leader must not be able to set the spawn point"
        );
        helper.assertTrue(
            Objects.equals(before, plot.spawnPoint()),
            "A refused setspawn must not change the saved point"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void adminSetsSpawnAtCurrentPositionTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer owner = createTestPlayer(helper, "spawn-admin-owner");
        Team team = data.teamOf(owner.getUUID()).orElseThrow();
        startPlot(helper, owner);
        PlotEntry plot = plotOf(data, team);
        finishPlot(helper, plot);
        BlockPos desired = owner.blockPosition();

        ServerPlayer admin = createTestPlayer(helper, "spawn-admin");
        movePlayer(admin, level, desired);
        helper.assertTrue(
            executeWithPermission(
                helper, admin, "slot admin setspawn " + team.name()
            ) == 1,
            "the admin must be able to set a spawn point at their own position"
        );
        helper.assertTrue(
            desired.equals(plot.spawnPoint()),
            "The admin's current position must be saved"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 200)
    public static void adminSetsSpawnWithCoordinatesTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer owner = createTestPlayer(helper, "spawn-coords-owner");
        Team team = data.teamOf(owner.getUUID()).orElseThrow();
        startPlot(helper, owner);
        PlotEntry plot = plotOf(data, team);
        finishPlot(helper, plot);
        BlockPos desired = owner.blockPosition();

        helper.assertTrue(
            executeAdmin(helper, "slot admin setspawn " + team.name()
                                + " " + desired.getX()
                                + " " + desired.getY()
                                + " " + desired.getZ()) == 1,
            "the admin must be able to set a spawn point with explicit coordinates"
        );
        helper.assertTrue(
            desired.equals(plot.spawnPoint()),
            "The explicit coordinates must be saved"
        );
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 20000)
    public static void adjustedSpawnIsSavedAndUsedTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer owner = createTestPlayer(helper, "spawn-adjust-owner");
        Team team = data.teamOf(owner.getUUID()).orElseThrow();
        startPlot(helper, owner);
        PlotEntry plot = plotOf(data, team);
        finishPlot(helper, plot);
        BlockPos desired = owner.blockPosition();

        // Remove the saved column and build one valid neighbour, so resolution has a clear
        // fallback. Original states are restored below to keep other GameTests deterministic.
        Map<BlockPos, BlockState> original = new HashMap<>();
        for (int y = PlotGeometry.MIN_Y; y <= desired.getY(); y++) {
            BlockPos target = new BlockPos(desired.getX(), y, desired.getZ());
            original.put(target, level.getBlockState(target));
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos neighbour = desired.east();
        for (BlockPos target : new BlockPos[]{
            neighbour.below(), neighbour, neighbour.above()
        }) {
            original.put(target, level.getBlockState(target));
        }
        level.setBlock(neighbour.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(neighbour, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(neighbour.above(), Blocks.AIR.defaultBlockState(), 3);

        plot.spawnPoint(desired);
        data.setDirty();
        helper.assertTrue(
            !PlotSpawn.isValid(level, desired),
            "Setup: the saved point must be invalid"
        );
        helper.assertTrue(
            PlotSpawn.isValid(level, neighbour),
            "Setup: the neighbour must be a valid standing point"
        );

        movePlayer(owner, level, new BlockPos(desired.getX() + 100, desired.getY(), desired.getZ()));
        helper.assertTrue(execute(helper, owner, "slot") == 1, "/slot failed");

        BlockPos saved = plot.spawnPoint();
        helper.assertTrue(
            saved != null
                && !saved.equals(desired)
                && PlotSpawn.isValid(level, saved)
                && Math.max(Math.abs(saved.getX() - desired.getX()),
                    Math.abs(saved.getZ() - desired.getZ())) <= 1,
            "The invalid point must be replaced by a valid neighbour (got " + saved + ")"
        );
        helper.assertTrue(saved.equals(owner.blockPosition()), "The player must land on the new point");
        original.forEach((pos, state) -> level.setBlock(pos, state, 3));
        helper.succeed();
    }

    @GameTest(template = "empty",
        timeoutTicks = 20000)
    public static void destroyedSpawnCreatesBedrockPlatformTest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlotGameTests.clearActiveWork(level);
        PlotData data = PlotData.get(level);

        ServerPlayer owner = createTestPlayer(helper, "spawn-platform-owner");
        Team team = data.teamOf(owner.getUUID()).orElseThrow();
        startPlot(helper, owner);
        PlotEntry plot = plotOf(data, team);
        finishPlot(helper, plot);

        // Clear every column the nearby search covers so the fallback builds a platform.
        // Original states are restored afterwards to keep other GameTests deterministic.
        BlockPos buried = owner.blockPosition();
        Map<BlockPos, BlockState> original = new HashMap<>();
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                for (int y = PlotGeometry.MIN_Y; y <= buried.getY(); y++) {
                    BlockPos target = buried.offset(dx, y - buried.getY(), dz);
                    original.put(target, level.getBlockState(target));
                    level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        helper.assertTrue(
            !PlotSpawn.isValid(level, buried),
            "Setup: the buried point must be invalid"
        );
        plot.spawnPoint(buried);
        data.setDirty();

        movePlayer(
            owner, level,
            new BlockPos(buried.getX() + 100, buried.getY(), buried.getZ())
        );
        helper.assertTrue(execute(helper, owner, "slot") == 1, "/slot failed");

        BlockPos expected = new BlockPos(buried.getX(), PlotGeometry.MIN_Y + 1, buried.getZ());
        helper.assertTrue(
            expected.equals(plot.spawnPoint()),
            "The platform position must be persisted (saved=" + plot.spawnPoint() + ")"
        );
        helper.assertTrue(
            expected.equals(owner.blockPosition()),
            "The player must be teleported onto the platform"
        );
        helper.assertTrue(
            level.getBlockState(expected.below()).is(Blocks.BEDROCK),
            "A bedrock platform must be created when no valid block remains"
        );
        original.forEach((pos, state) -> level.setBlock(pos, state, 3));
        helper.succeed();
    }

    private static PlotEntry plotOf(PlotData data, Team team) {
        return Objects.requireNonNull(
            data.plots()
                .stream()
                .filter(plot -> plot.teamId() == team.id())
                .findFirst()
                .orElse(null),
            "The team must own a persisted plot"
        );
    }

    private static void movePlayer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.teleportTo(
            level,
            pos.getX() + 0.5,
            pos.getY(),
            pos.getZ() + 0.5,
            player.getYRot(),
            player.getXRot()
        );
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

    private static int executeDenied(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = new CommandSourceStack(
            player, player.position(), player.getRotationVector(), level,
            0, "test", player.getDisplayName(), level.getServer(), player
        );
        try {
            return level.getServer().getCommands().getDispatcher()
                .execute("slot admin list", source);
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

    private static void startPlot(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(
            execute(helper, player, "slot") == 1,
            "/slot must start the team's plot"
        );
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
