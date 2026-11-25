package fr.hykiuidustries.createboxed.plot.data;

import fr.hykiuidustries.createboxed.CreateBoxed;
import fr.hykiuidustries.createboxed.team.data.Team;
import fr.hykiuidustries.createboxed.team.data.TeamMember;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Overworld SavedData holding the frozen layout, all plots and all teams.
 */
@Getter
@Accessors(fluent = true)
@NullMarked
public final class PlotData extends SavedData {
    private static final String DATA_NAME = CreateBoxed.MOD_ID + "_plots";
    private static final SavedData.Factory<PlotData> FACTORY = new SavedData.Factory<>(
        PlotData::new,
        PlotData::read
    );
    private final List<PlotEntry> plots = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final Map<UUID, Integer> memberships = new HashMap<>();
    private boolean anchored;
    private int anchorX;
    private int anchorZ;
    private int gap;
    private int nextIndex;
    private int nextTeamId;
    private @Nullable PlotEntry generatingPlot;
    private @Nullable PlotEntry deletingPlot;

    public static PlotData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static PlotData read(CompoundTag tag, HolderLookup.Provider registries) {
        PlotData data = new PlotData();
        data.anchored = tag.getBoolean("anchored");
        data.anchorX = tag.getInt("anchorX");
        data.anchorZ = tag.getInt("anchorZ");
        data.gap = tag.getInt("gap");
        data.nextIndex = tag.getInt("nextIndex");
        data.nextTeamId = tag.getInt("nextTeamId");

        ListTag plotList = tag.getList("plots", Tag.TAG_COMPOUND);
        for (Tag plotTag : plotList) {
            PlotEntry entry = PlotEntry.read((CompoundTag) plotTag);
            data.plots.add(entry);
            if (entry.status() == PlotStatus.GENERATING && data.generatingPlot == null) {
                data.generatingPlot = entry;
            } else if (entry.status() == PlotStatus.DELETING && data.deletingPlot == null) {
                data.deletingPlot = entry;
            }
        }

        ListTag teamList = tag.getList("teams", Tag.TAG_COMPOUND);
        for (Tag teamTag : teamList) {
            data.teams.add(Team.read((CompoundTag) teamTag));
        }

        ListTag membershipList = tag.getList("memberships", Tag.TAG_COMPOUND);
        for (Tag membershipTag : membershipList) {
            CompoundTag entry = (CompoundTag) membershipTag;
            data.memberships.put(entry.getUUID("uuid"), entry.getInt("teamId"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("anchored", anchored);
        tag.putInt("anchorX", anchorX);
        tag.putInt("anchorZ", anchorZ);
        tag.putInt("gap", gap);
        tag.putInt("nextIndex", nextIndex);
        tag.putInt("nextTeamId", nextTeamId);

        ListTag plotList = new ListTag();
        for (PlotEntry plot : plots) {
            plotList.add(plot.write(new CompoundTag()));
        }
        tag.put("plots", plotList);

        ListTag teamList = new ListTag();
        for (Team team : teams) {
            teamList.add(team.write(new CompoundTag()));
        }
        tag.put("teams", teamList);

        ListTag membershipList = new ListTag();
        for (Map.Entry<UUID, Integer> membership : memberships.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", membership.getKey());
            entry.putInt("teamId", membership.getValue());
            membershipList.add(entry);
        }
        tag.put("memberships", membershipList);
        return tag;
    }

    public List<PlotEntry> plots() {
        return Collections.unmodifiableList(plots);
    }

    public List<Team> teams() {
        return Collections.unmodifiableList(teams);
    }

    public Optional<Team> findTeam(int teamId) {
        return teams.stream().filter(team -> team.id() == teamId).findFirst();
    }

    public Optional<Team> teamOf(UUID player) {
        Integer teamId = memberships.get(player);
        return teamId == null ? Optional.empty() : findTeam(teamId);
    }

    public int allocateTeamId() {
        int id = nextTeamId++;
        setDirty();
        return id;
    }

    public void addTeam(Team team) {
        teams.add(team);
        for (TeamMember member : team.members()) {
            memberships.put(member.uuid(), team.id());
        }
        if (team.id() >= nextTeamId) {
            nextTeamId = team.id() + 1;
        }
        setDirty();
    }

    public void removeTeam(int teamId) {
        teams.removeIf(team -> team.id() == teamId);
        memberships.values().removeIf(id -> id == teamId);
        setDirty();
    }

    public void assign(UUID player, int teamId) {
        memberships.put(player, teamId);
        setDirty();
    }

    public Set<Integer> usedSlotIndices() {
        Set<Integer> used = new HashSet<>();
        for (PlotEntry plot : plots) {
            used.add(plot.index());
        }
        return used;
    }

    public void initializeAnchor(int chunkX, int chunkZ, int gap) {
        this.anchored = true;
        this.anchorX = chunkX;
        this.anchorZ = chunkZ;
        this.gap = gap;
        setDirty();
    }

    public void setNextIndex(int nextIndex) {
        this.nextIndex = nextIndex;
        setDirty();
    }

    public void addPlot(PlotEntry entry) {
        plots.add(entry);
        if (entry.status() == PlotStatus.GENERATING) {
            generatingPlot = entry;
        }
        setDirty();
    }

    public void removePlot(int index) {
        if (plots.removeIf(plot -> plot.index() == index)) {
            if (generatingPlot != null && generatingPlot.index() == index) {
                generatingPlot = null;
            }
            setDirty();
        }
    }

    /** Removes one specific entry, leaving any other plot at the same index untouched. */
    public void removePlot(PlotEntry entry) {
        if (plots.remove(entry)) {
            if (generatingPlot == entry) {
                generatingPlot = null;
            }
            if (deletingPlot == entry) {
                deletingPlot = null;
            }
            setDirty();
        }
    }

    public void finishPlot(PlotEntry entry) {
        entry.status(PlotStatus.DONE);
        if (generatingPlot == entry) {
            generatingPlot = null;
        }
        setDirty();
    }

    public void advanceChunk(PlotEntry entry, int nextChunk) {
        entry.nextChunk(nextChunk);
        setDirty();
    }

    /** Marks a finished slot for clearing; the status is persisted so a restart can resume. */
    public void startDeletion(PlotEntry entry) {
        entry.status(PlotStatus.DELETING);
        entry.nextChunk(0);
        if (generatingPlot == entry) {
            generatingPlot = null;
        }
        deletingPlot = entry;
        setDirty();
    }

    public void finishDeletion(PlotEntry entry) {
        removePlot(entry);
    }
}
