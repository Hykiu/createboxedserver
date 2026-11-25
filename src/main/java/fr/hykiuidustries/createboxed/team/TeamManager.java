package fr.hykiuidustries.createboxed.team;

import fr.hykiuidustries.createboxed.plot.PlotManager;
import fr.hykiuidustries.createboxed.plot.PlotProgressListener;
import fr.hykiuidustries.createboxed.plot.data.PlotData;
import fr.hykiuidustries.createboxed.plot.data.PlotEntry;
import fr.hykiuidustries.createboxed.plot.data.PlotStatus;
import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import fr.hykiuidustries.createboxed.team.data.Team;
import fr.hykiuidustries.createboxed.team.data.TeamMember;
import fr.hykiuidustries.createboxed.team.data.TeamRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Owns team membership and the user-facing slot lifecycle. All mutations are performed
 * on the server thread, so two simultaneous /slot calls can only create one slot.
 */
@NullMarked
public final class TeamManager implements PlotProgressListener {
    private record Invite(
        int teamId,
        long expiresAtMillis
    ) {
    }

    private record DeleteConfirmation(
        int teamId,
        long expiresAtMillis
    ) {
    }

    private static final long DELETE_CONFIRM_MILLIS = 30_000L;
    private static final UUID CONSOLE_UUID = new UUID(0L, 0L);

    private static @Nullable TeamManager instance;

    private final Logger logger;
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, DeleteConfirmation> deletions = new HashMap<>();
    private final Set<Integer> pendingTeams = new LinkedHashSet<>();
    private final Set<Integer> disbandingTeams = new HashSet<>();
    private @Nullable ServerLevel overworld;
    private @Nullable PlotData data;

    public TeamManager(Logger logger) {
        this.logger = logger;
    }

    public static @Nullable TeamManager get() {
        return instance;
    }

    /**
     * Forces the pending plot queue to advance; used after tests settle persisted work.
     */
    public synchronized void drainPendingPlots() {
        drainQueue();
    }

    public void start(ServerLevel level) {
        instance = this;
        this.overworld = level;
        this.data = PlotData.get(level);
        PlotManager.setListener(this);
        enqueueTeamsWithoutPlots();
        drainQueue();
    }

    public void stop() {
        if (instance == this) {
            instance = null;
        }
        invites.clear();
        deletions.clear();
        pendingTeams.clear();
        disbandingTeams.clear();
        PlotManager.setListener(null);
        this.overworld = null;
        this.data = null;
    }

    /**
     * Gives a first-time player a solo team and refreshes their known name.
     */
    public void ensureTeam(ServerPlayer player) {
        PlotData storage = data();
        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();
        Team existing = storage.teamOf(uuid).orElse(null);
        if (existing == null) {
            Team team = createSoloTeam(storage, uuid, name);
            player.sendSystemMessage(Component.translatable(
                "createboxed.team.created",
                team.name()
            ));
            return;
        }
        if (existing.updateKnownName(uuid, name)) {
            storage.setDirty();
        }
    }

    /**
     * /slot: go to the team's slot, or create it first. Returns an error component or null.
     */
    public synchronized @Nullable Component goOrCreate(ServerPlayer player) {
        PlotData storage = data();
        Team team = requireTeam(storage, player);
        PlotEntry plot = findPlot(storage, team.slotIndex());
        if (plot != null) {
            if (plot.status() == PlotStatus.DONE) {
                teleport(player, plot);
                player.sendSystemMessage(Component.translatable(
                    "createboxed.slot.ready",
                    plot.index()
                ));
                return null;
            }
            return Component.translatable(
                "createboxed.slot.generating",
                plot.index(),
                plot.nextChunk(),
                PlotGeometry.CHUNKS_PER_PLOT
            );
        }

        if (PlotManager.get() == null) {
            return Component.translatable("createboxed.slot.server_only");
        }
        if (queuePlot(team)) {
            return null;
        }
        return Component.translatable("createboxed.slot.queued", queuePosition(team.id()));
    }

    /**
     * /slot delete: the first call arms a 30 second confirmation, the second one starts clearing.
     */
    public synchronized @Nullable Component delete(ServerPlayer player) {
        PlotData storage = data();
        Team team = requireTeam(storage, player);
        if (!isLeader(team, player)) {
            return Component.translatable("createboxed.team.not_leader");
        }
        PlotEntry plot = findPlot(storage, team.slotIndex());
        if (plot == null) {
            return Component.translatable("createboxed.slot.none");
        }
        if (plot.status() != PlotStatus.DONE) {
            return Component.translatable("createboxed.slot.busy");
        }
        return beginDeletion(player.getUUID(), player::sendSystemMessage, team, plot);
    }

    private synchronized @Nullable Component beginDeletion(
        UUID requester,
        Consumer<Component> messages,
        Team team,
        PlotEntry plot
    ) {
        DeleteConfirmation pending = deletions.get(requester);
        long now = System.currentTimeMillis();
        if (pending == null || pending.teamId() != team.id() || now > pending.expiresAtMillis()) {
            deletions.put(
                requester,
                new DeleteConfirmation(team.id(), now + DELETE_CONFIRM_MILLIS)
            );
            messages.accept(Component.translatable(
                "createboxed.slot.delete_confirm", DELETE_CONFIRM_MILLIS / 1000L, plot.index()
            ));
            return null;
        }
        deletions.remove(requester);

        PlotManager manager = PlotManager.get();
        if (manager == null) {
            return Component.translatable("createboxed.slot.server_only");
        }
        PlotEntry deleting = manager.deletePlot(team.id());
        if (deleting == null) {
            return Component.translatable("createboxed.slot.busy");
        }
        teleportPlayersOutOf(deleting);
        messages.accept(Component.translatable(
            "createboxed.slot.delete_started", deleting.index(), PlotGeometry.CHUNKS_PER_PLOT
        ));
        return null;
    }

    /**
     * /slot visit <team>: teleports to another team's finished slot when visits are enabled.
     */
    public @Nullable Component visit(ServerPlayer player, String teamName) {
        if (!TeamsModule.allowVisitors()) {
            return Component.translatable("createboxed.slot.visits_disabled");
        }
        PlotData storage = data();
        Team target = storage.teams().stream()
            .filter(team -> team.name().equalsIgnoreCase(teamName))
            .findFirst()
            .orElse(null);
        if (target == null) {
            return Component.translatable("createboxed.team.not_found", teamName);
        }
        PlotEntry plot = findPlot(storage, target.slotIndex());
        if (plot == null) {
            return Component.translatable("createboxed.slot.no_slot", target.name());
        }
        if (plot.status() != PlotStatus.DONE) {
            return Component.translatable("createboxed.slot.not_ready", target.name());
        }
        if (isInside(player, plot)) {
            return Component.translatable("createboxed.slot.already_inside", target.name());
        }
        teleport(player, plot);
        player.sendSystemMessage(Component.translatable(
            "createboxed.slot.visited", target.name(), plot.index()
        ));
        return null;
    }

    /**
     * Suggestions for /slot visit <team>.
     */
    public List<String> visitableTeamNames() {
        if (!TeamsModule.allowVisitors()) {
            return List.of();
        }
        PlotData storage = data();
        List<String> names = new ArrayList<>();
        for (Team team : storage.teams()) {
            PlotEntry plot = findPlot(storage, team.slotIndex());
            if (plot != null && plot.status() == PlotStatus.DONE) {
                names.add(team.name());
            }
        }
        return names;
    }

    public @Nullable Component invite(ServerPlayer player, String targetName) {
        PlotData storage = data();
        Team team = requireTeam(storage, player);
        if (!isLeader(team, player)) {
            return Component.translatable("createboxed.team.not_leader");
        }
        ServerPlayer target = server().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            return Component.translatable("createboxed.team.player_not_found", targetName);
        }
        if (target.getUUID().equals(player.getUUID())) {
            return Component.translatable("createboxed.team.invite_self");
        }
        Team targetTeam = storage.teamOf(target.getUUID()).orElse(null);
        if (targetTeam != null && targetTeam.id() == team.id()) {
            return Component.translatable("createboxed.team.already_member", targetName);
        }
        long expiry = System.currentTimeMillis() + TeamsModule.inviteExpirySeconds() * 1000L;
        invites.put(target.getUUID(), new Invite(team.id(), expiry));

        player.sendSystemMessage(Component.translatable(
            "createboxed.team.invite_sent", targetName, team.name()
        ));
        target.sendSystemMessage(Component.translatable(
            "createboxed.team.invite_received", player.getGameProfile().getName(), team.name()
        ));
        return null;
    }

    public @Nullable Component accept(ServerPlayer player) {
        PlotData storage = data();
        Invite invite = invites.remove(player.getUUID());
        if (invite == null) {
            return Component.translatable("createboxed.team.no_invite");
        }
        if (System.currentTimeMillis() > invite.expiresAtMillis()) {
            return Component.translatable("createboxed.team.invite_expired");
        }
        Team target = storage.findTeam(invite.teamId()).orElse(null);
        if (target == null) {
            return Component.translatable("createboxed.team.invite_expired");
        }
        Team current = storage.teamOf(player.getUUID()).orElse(null);
        if (current != null && current.id() == target.id()) {
            return Component.translatable("createboxed.team.already_member", target.name());
        }
        if (!TeamRules.canAccept(
            current != null && findPlot(storage, current.slotIndex()) != null,
            current == null ? 0 : current.size(),
            target.size(),
            TeamsModule.maxTeamSize()
        )) {
            return Component.translatable("createboxed.team.delete_slot_first");
        }

        TeamMember member = current == null
                            ? new TeamMember(player.getUUID(), player.getGameProfile().getName())
                            : current.findMember(player.getUUID())
                                .orElseGet(() -> new TeamMember(
                                    player.getUUID(),
                                    player.getGameProfile().getName()
                                ));
        if (current != null) {
            current.removeMember(member.uuid());
            if (current.size() == 0) {
                storage.removeTeam(current.id());
            } else {
                storage.setDirty();
            }
        }
        target.addMember(member);
        storage.assign(member.uuid(), target.id());
        player.sendSystemMessage(Component.translatable("createboxed.team.joined", target.name()));
        return null;
    }

    public @Nullable Component leave(ServerPlayer player) {
        PlotData storage = data();
        Team team = requireTeam(storage, player);
        if (team.size() <= 1) {
            return Component.translatable("createboxed.team.leave_solo");
        }
        TeamMember member = team.findMember(player.getUUID())
            .orElseGet(() -> new TeamMember(player.getUUID(), player.getGameProfile().getName()));
        team.removeMember(member.uuid());
        storage.setDirty();
        Team solo = createSoloTeam(storage, member.uuid(), member.name());
        player.sendSystemMessage(Component.translatable(
            "createboxed.team.left", team.name(), solo.name()
        ));
        return null;
    }

    public @Nullable Component kick(ServerPlayer player, String targetName) {
        PlotData storage = data();
        Team team = requireTeam(storage, player);
        if (!isLeader(team, player)) {
            return Component.translatable("createboxed.team.not_leader");
        }
        TeamMember target = team.findMemberByName(targetName).orElse(null);
        if (target == null) {
            return Component.translatable("createboxed.team.member_not_found", targetName);
        }
        if (target.uuid().equals(player.getUUID())) {
            return Component.translatable("createboxed.team.kick_self");
        }
        team.removeMember(target.uuid());
        storage.setDirty();
        Team solo = createSoloTeam(storage, target.uuid(), target.name());
        ServerPlayer online = server().getPlayerList().getPlayer(target.uuid());
        if (online != null) {
            online.sendSystemMessage(Component.translatable(
                "createboxed.team.kicked", team.name(), solo.name()
            ));
        }
        player.sendSystemMessage(Component.translatable(
            "createboxed.team.kick_done", target.name(), solo.name()
        ));
        return null;
    }

    public @Nullable Component rename(ServerPlayer player, String name) {
        PlotData storage = data();
        Team team = requireTeam(storage, player);
        if (!isLeader(team, player)) {
            return Component.translatable("createboxed.team.not_leader");
        }
        if (!TeamRules.isValidName(name)) {
            return Component.translatable("createboxed.team.invalid_name");
        }
        boolean taken = storage.teams().stream()
            .anyMatch(other -> other.id() != team.id() && other.name().equalsIgnoreCase(name));
        if (taken) {
            return Component.translatable("createboxed.team.name_taken", name);
        }
        team.name(name);
        storage.setDirty();
        player.sendSystemMessage(Component.translatable("createboxed.team.renamed", name));
        return null;
    }

    public void info(ServerPlayer player) {
        PlotData storage = data();
        Team team = requireTeam(storage, player);
        String leader = team.findMember(team.leader()).map(TeamMember::name).orElse("?");
        String members = team.members().stream().map(TeamMember::name).collect(Collectors.joining(
            ", "));
        Component status = slotStatus(findPlot(storage, team.slotIndex()));
        player.sendSystemMessage(Component.translatable(
            "createboxed.team.info", team.name(), leader, members, status
        ));
    }

    public void adminList(CommandSourceStack source) {
        PlotData storage = data();
        Consumer<Component> messages = sourceMessages(source);
        if (storage.teams().isEmpty()) {
            messages.accept(Component.translatable("createboxed.admin.no_teams"));
            return;
        }
        messages.accept(Component.translatable(
            "createboxed.admin.list.header",
            storage.teams().size()
        ));
        for (Team team : storage.teams()) {
            messages.accept(teamLine(storage, team));
        }
    }

    public @Nullable Component adminInfo(CommandSourceStack source, String teamName) {
        Team team = findTeamByName(data(), teamName);
        if (team == null) {
            return Component.translatable("createboxed.team.not_found", teamName);
        }
        sourceMessages(source).accept(teamLine(data(), team));
        return null;
    }

    public @Nullable Component adminTeleport(CommandSourceStack source, String teamName) {
        if (!(source.getEntity() instanceof ServerPlayer admin)) {
            return Component.translatable("createboxed.slot.player_only");
        }
        Team team = findTeamByName(data(), teamName);
        if (team == null) {
            return Component.translatable("createboxed.team.not_found", teamName);
        }
        PlotEntry plot = findPlot(data(), team.slotIndex());
        if (plot == null) {
            return Component.translatable("createboxed.slot.no_slot", team.name());
        }
        if (plot.status() != PlotStatus.DONE) {
            return Component.translatable("createboxed.slot.not_ready", team.name());
        }
        teleport(admin, plot);
        source.sendSuccess(
            () -> Component.translatable("createboxed.admin.teleported", team.name(), plot.index()),
            false
        );
        return null;
    }

    public @Nullable Component adminVisit(
        CommandSourceStack source,
        String playerName,
        String teamName
    ) {
        ServerPlayer target = server().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            return Component.translatable("createboxed.team.player_not_found", playerName);
        }
        Team team = findTeamByName(data(), teamName);
        if (team == null) {
            return Component.translatable("createboxed.team.not_found", teamName);
        }
        PlotEntry plot = findPlot(data(), team.slotIndex());
        if (plot == null) {
            return Component.translatable("createboxed.slot.no_slot", team.name());
        }
        if (plot.status() != PlotStatus.DONE) {
            return Component.translatable("createboxed.slot.not_ready", team.name());
        }
        teleport(target, plot);
        source.sendSuccess(
            () -> Component.translatable(
                "createboxed.admin.visit_sent", playerName, team.name(), plot.index()
            ),
            false
        );
        target.sendSystemMessage(Component.translatable(
            "createboxed.admin.visit_target", team.name(), plot.index()
        ));
        return null;
    }

    public @Nullable Component adminDelete(CommandSourceStack source, String teamName) {
        Team team = findTeamByName(data(), teamName);
        if (team == null) {
            return Component.translatable("createboxed.team.not_found", teamName);
        }
        PlotEntry plot = findPlot(data(), team.slotIndex());
        if (plot == null) {
            return Component.translatable("createboxed.slot.no_slot", team.name());
        }
        if (plot.status() != PlotStatus.DONE) {
            return Component.translatable("createboxed.slot.busy");
        }
        return beginDeletion(sourceUuid(source), sourceMessages(source), team, plot);
    }

    public @Nullable Component adminInvite(
        CommandSourceStack source,
        String teamName,
        String playerName
    ) {
        Team team = findTeamByName(data(), teamName);
        if (team == null) {
            return Component.translatable("createboxed.team.not_found", teamName);
        }
        ServerPlayer target = server().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            return Component.translatable("createboxed.team.player_not_found", playerName);
        }
        if (team.hasMember(target.getUUID())) {
            return Component.translatable("createboxed.team.already_member", playerName);
        }
        long expiry = System.currentTimeMillis() + TeamsModule.inviteExpirySeconds() * 1000L;
        invites.put(target.getUUID(), new Invite(team.id(), expiry));
        source.sendSuccess(
            () -> Component.translatable("createboxed.admin.invite_sent", playerName, team.name()),
            false
        );
        target.sendSystemMessage(Component.translatable(
            "createboxed.admin.invite_received", team.name()
        ));
        return null;
    }

    public @Nullable Component adminKick(
        CommandSourceStack source,
        String teamName,
        String playerName
    ) {
        PlotData storage = data();
        Team team = findTeamByName(storage, teamName);
        if (team == null) {
            return Component.translatable("createboxed.team.not_found", teamName);
        }
        TeamMember member = team.findMemberByName(playerName).orElse(null);
        if (member == null) {
            return Component.translatable("createboxed.team.member_not_found", playerName);
        }
        team.removeMember(member.uuid());
        if (team.size() == 0) {
            requestDisband(team);
        } else {
            storage.setDirty();
        }
        Team solo = createSoloTeam(storage, member.uuid(), member.name());
        ServerPlayer online = server().getPlayerList().getPlayer(member.uuid());
        if (online != null) {
            online.sendSystemMessage(Component.translatable(
                "createboxed.team.kicked", team.name(), solo.name()
            ));
        }
        source.sendSuccess(
            () -> Component.translatable(
                "createboxed.admin.kick_done", member.name(), team.name(), solo.name()
            ),
            false
        );
        return null;
    }

    public @Nullable Component adminLeader(
        CommandSourceStack source,
        String teamName,
        String playerName
    ) {
        PlotData storage = data();
        Team team = findTeamByName(storage, teamName);
        if (team == null) {
            return Component.translatable("createboxed.team.not_found", teamName);
        }
        TeamMember member = team.findMemberByName(playerName).orElse(null);
        if (member == null) {
            return Component.translatable("createboxed.team.member_not_found", playerName);
        }
        team.leader(member.uuid());
        storage.setDirty();
        source.sendSuccess(
            () -> Component.translatable(
                "createboxed.admin.leader_changed",
                member.name(),
                team.name()
            ),
            false
        );
        return null;
    }

    public @Nullable Component adminRename(
        CommandSourceStack source,
        String teamName,
        String newName
    ) {
        PlotData storage = data();
        Team team = findTeamByName(storage, teamName);
        if (team == null) {
            return Component.translatable("createboxed.team.not_found", teamName);
        }
        if (!TeamRules.isValidName(newName)) {
            return Component.translatable("createboxed.team.invalid_name");
        }
        boolean taken = storage.teams().stream()
            .anyMatch(other -> other.id() != team.id() && other.name().equalsIgnoreCase(newName));
        if (taken) {
            return Component.translatable("createboxed.team.name_taken", newName);
        }
        String oldName = team.name();
        team.name(newName);
        storage.setDirty();
        source.sendSuccess(
            () -> Component.translatable("createboxed.admin.renamed", oldName, newName),
            false
        );
        return null;
    }

    public List<String> adminTeamNames() {
        return data().teams().stream().map(Team::name).toList();
    }

    public List<String> adminPlayerNames() {
        return server().getPlayerList().getPlayers().stream()
            .map(player -> player.getGameProfile().getName())
            .toList();
    }

    @Override
    public void onProgress(PlotEntry entry, int chunksDone) {
        Team team = data().findTeam(entry.teamId()).orElse(null);
        if (team == null) {
            return;
        }
        Component message = Component.translatable(
            "createboxed.slot.progress", chunksDone, PlotGeometry.CHUNKS_PER_PLOT
        );
        for (TeamMember member : team.members()) {
            ServerPlayer player = server().getPlayerList().getPlayer(member.uuid());
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    @Override
    public synchronized void onComplete(PlotEntry entry) {
        if (disbandingTeams.remove(entry.teamId())) {
            destroyTeam(entry.teamId());
            drainQueue();
            return;
        }
        Team team = data().findTeam(entry.teamId()).orElse(null);
        if (team == null) {
            logger.warn("Slot {} completed without an owning team", entry.index());
            drainQueue();
            return;
        }
        Component message = Component.translatable("createboxed.slot.done", entry.index());
        for (TeamMember member : team.members()) {
            ServerPlayer player = server().getPlayerList().getPlayer(member.uuid());
            if (player == null) {
                continue;
            }
            teleport(player, entry);
            player.sendSystemMessage(message);
        }
        drainQueue();
    }

    @Override
    public synchronized void onDeleteComplete(PlotEntry entry) {
        Team team = data().findTeam(entry.teamId()).orElse(null);
        if (team == null) {
            logger.warn("Slot {} was deleted without an owning team", entry.index());
            drainQueue();
            return;
        }
        Component message = Component.translatable("createboxed.team.disbanded", team.name());
        for (TeamMember member : team.members()) {
            ServerPlayer player = server().getPlayerList().getPlayer(member.uuid());
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
        destroyTeam(team.id());
        drainQueue();
    }

    private static boolean isLeader(Team team, ServerPlayer player) {
        return team.leader().equals(player.getUUID());
    }

    private static Component slotStatus(@Nullable PlotEntry plot) {
        if (plot == null) {
            return Component.translatable("createboxed.slot.status.none");
        }
        return Component.translatable("createboxed.slot.status.index", plot.index());
    }

    private static @Nullable PlotEntry findPlot(PlotData storage, @Nullable Integer index) {
        if (index == null) {
            return null;
        }
        return storage.plots().stream().filter(plot -> plot.index() == index).findFirst().orElse(
            null);
    }

    private static boolean hasPlot(PlotData storage, int teamId) {
        return storage.plots().stream().anyMatch(plot -> plot.teamId() == teamId);
    }

    private static @Nullable Team findTeamByName(PlotData storage, String name) {
        return storage.teams().stream()
            .filter(team -> team.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private static Component teamLine(PlotData storage, Team team) {
        String leader = team.findMember(team.leader()).map(TeamMember::name).orElse("?");
        String members = team.members().stream().map(TeamMember::name).collect(Collectors.joining(
            ", "));
        return Component.translatable(
            "createboxed.admin.list.entry", team.name(), leader, members,
            slotStatus(findPlot(storage, team.slotIndex()))
        );
    }

    private static Consumer<Component> sourceMessages(CommandSourceStack source) {
        return message -> source.sendSuccess(() -> message, false);
    }

    private static UUID sourceUuid(CommandSourceStack source) {
        Entity entity = source.getEntity();
        return entity == null ? CONSOLE_UUID : entity.getUUID();
    }

    private Team requireTeam(PlotData storage, ServerPlayer player) {
        Team team = storage.teamOf(player.getUUID()).orElse(null);
        return team != null ? team : createSoloTeam(
            storage,
            player.getUUID(),
            player.getGameProfile().getName()
        );
    }

    private Team createSoloTeam(PlotData storage, UUID uuid, String knownName) {
        Set<String> used = storage.teams().stream().map(Team::name).collect(Collectors.toSet());
        String name = TeamRules.uniqueName(knownName.toLowerCase(Locale.ROOT), used);
        Team team = Team.solo(storage.allocateTeamId(), name, new TeamMember(uuid, knownName));
        storage.addTeam(team);
        queuePlot(team);
        return team;
    }

    private void enqueueTeamsWithoutPlots() {
        PlotData storage = data();
        for (Team team : storage.teams()) {
            if (team.slotIndex() != null && findPlot(storage, team.slotIndex()) == null) {
                team.slotIndex(null);
                storage.setDirty();
            }
            if (team.slotIndex() == null && !hasPlot(storage, team.id())) {
                pendingTeams.add(team.id());
            }
        }
    }

    /**
     * Returns true when this call actually starts the team's slot.
     */
    private synchronized boolean queuePlot(Team team) {
        if (!hasPlot(data(), team.id())) {
            if (team.slotIndex() != null) {
                team.slotIndex(null);
            }
            pendingTeams.add(team.id());
            data().setDirty();
        }
        drainQueue();
        return team.slotIndex() != null;
    }

    private int queuePosition(int teamId) {
        int position = 1;
        for (int pending : pendingTeams) {
            if (pending == teamId) {
                return position;
            }
            position++;
        }
        return 1;
    }

    private synchronized void drainQueue() {
        PlotManager manager = PlotManager.get();
        if (manager == null) {
            return;
        }
        Iterator<Integer> iterator = pendingTeams.iterator();
        while (iterator.hasNext() && !manager.isBusy()) {
            int teamId = iterator.next();
            iterator.remove();
            Team team = data().findTeam(teamId).orElse(null);
            if (team == null || team.slotIndex() != null || hasPlot(data(), teamId)) {
                continue;
            }
            int preferred = TeamRules.firstFreeIndex(data().usedSlotIndices());
            PlotEntry plot = manager.createPlot(teamId, preferred);
            if (plot == null) {
                pendingTeams.add(teamId);
                break;
            }
            team.slotIndex(plot.index());
            data().setDirty();
            notifyMembers(
                team, Component.translatable(
                    "createboxed.slot.started", plot.index(), PlotGeometry.CHUNKS_PER_PLOT
                )
            );
        }
    }

    private void requestDisband(@Nullable Team team) {
        if (team == null) {
            return;
        }
        pendingTeams.remove(team.id());
        notifyMembers(team, Component.translatable("createboxed.team.disbanded", team.name()));
        PlotEntry plot = findPlot(data(), team.slotIndex());
        if (plot == null) {
            destroyTeam(team.id());
            drainQueue();
            return;
        }
        disbandingTeams.add(team.id());
        if (plot.status() == PlotStatus.DONE) {
            PlotManager manager = PlotManager.get();
            if (manager != null) {
                PlotEntry deleting = manager.deletePlot(team.id());
                if (deleting != null) {
                    teleportPlayersOutOf(deleting);
                }
            }
        }
        drainQueue();
    }

    private void destroyTeam(int teamId) {
        data().removeTeam(teamId);
        pendingTeams.remove(teamId);
        disbandingTeams.remove(teamId);
        invites.values().removeIf(invite -> invite.teamId() == teamId);
        deletions.values().removeIf(deletion -> deletion.teamId() == teamId);
    }

    private void notifyMembers(Team team, Component message) {
        for (TeamMember member : team.members()) {
            ServerPlayer player = server().getPlayerList().getPlayer(member.uuid());
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static boolean isInside(ServerPlayer player, PlotEntry plot) {
        BlockPos pos = player.blockPosition();
        int minX = plot.originX() * 16;
        int minZ = plot.originZ() * 16;
        return pos.getX() >= minX && pos.getX() < minX + PlotGeometry.PLOT_BLOCKS
            && pos.getZ() >= minZ && pos.getZ() < minZ + PlotGeometry.PLOT_BLOCKS
            && pos.getY() >= PlotGeometry.MIN_Y && pos.getY() <= PlotGeometry.MAX_Y + 1;
    }

    private void teleportPlayersOutOf(PlotEntry plot) {
        ServerLevel level = overworld;
        if (level == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(server().getPlayerList().getPlayers())) {
            if (isInside(player, plot)) {
                teleportToSpawn(player, level);
            }
        }
    }

    private static void teleportToSpawn(ServerPlayer player, ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        player.teleportTo(
            level,
            spawn.getX() + 0.5,
            spawn.getY(),
            spawn.getZ() + 0.5,
            level.getSharedSpawnAngle(),
            player.getXRot()
        );
    }

    private void teleport(ServerPlayer player, PlotEntry plot) {
        ServerLevel level = overworld;
        if (level == null) {
            return;
        }
        net.minecraft.core.BlockPos center = PlotGeometry.centerBlock(
            plot.originX() + PlotGeometry.HALF_CHUNKS,
            plot.originZ() + PlotGeometry.HALF_CHUNKS,
            PlotGeometry.MAX_Y + 1
        );
        player.teleportTo(
            level,
            center.getX() + 0.5,
            center.getY(),
            center.getZ() + 0.5,
            player.getYRot(),
            player.getXRot()
        );
    }

    private PlotData data() {
        PlotData storage = data;
        if (storage == null) {
            throw new IllegalStateException("TeamManager was not started");
        }
        return storage;
    }

    private MinecraftServer server() {
        ServerLevel level = overworld;
        if (level == null) {
            throw new IllegalStateException("TeamManager was not started");
        }
        return level.getServer();
    }
}
