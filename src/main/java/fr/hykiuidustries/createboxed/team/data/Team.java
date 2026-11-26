package fr.hykiuidustries.createboxed.team.data;

import fr.hykiuidustries.createboxed.Keys.Nbt;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A persistent team. Members are kept in join order; the first one becomes leader
 * when the current leader leaves.
 */
@Getter
@Accessors(fluent = true)
@NullMarked
public final class Team {
    private final int id;
    @Setter
    private String name;
    @Setter
    private UUID leader;
    private final List<TeamMember> members = new ArrayList<>();
    @Setter
    private @Nullable Integer slotIndex;

    private Team(int id, String name, UUID leader, @Nullable Integer slotIndex) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.slotIndex = slotIndex;
    }

    public static Team solo(int id, String name, TeamMember leader) {
        Team team = new Team(id, name, leader.uuid(), null);
        team.members.add(leader);
        return team;
    }

    public static Team read(CompoundTag tag) {
        Team team = new Team(
            tag.getInt(Nbt.ID),
            tag.getString(Nbt.NAME),
            tag.getUUID(Nbt.LEADER),
            tag.contains(Nbt.SLOT_INDEX, Tag.TAG_INT) ? tag.getInt(Nbt.SLOT_INDEX) : null
        );
        ListTag memberList = tag.getList(Nbt.MEMBERS, Tag.TAG_COMPOUND);
        for (Tag memberTag : memberList) {
            team.members.add(TeamMember.read((CompoundTag) memberTag));
        }
        return team;
    }

    public CompoundTag write(CompoundTag tag) {
        tag.putInt(Nbt.ID, id);
        tag.putString(Nbt.NAME, name);
        tag.putUUID(Nbt.LEADER, leader);
        if (slotIndex != null) {
            tag.putInt(Nbt.SLOT_INDEX, slotIndex);
        }
        ListTag memberList = new ListTag();
        for (TeamMember member : members) {
            memberList.add(member.write(new CompoundTag()));
        }
        tag.put(Nbt.MEMBERS, memberList);
        return tag;
    }

    public List<TeamMember> members() {
        return List.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    public boolean hasMember(UUID uuid) {
        return findMember(uuid).isPresent();
    }

    public Optional<TeamMember> findMember(UUID uuid) {
        return members.stream().filter(member -> member.uuid().equals(uuid)).findFirst();
    }

    public Optional<TeamMember> findMemberByName(String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        return members.stream()
            .filter(member -> member.name().toLowerCase(Locale.ROOT).equals(needle))
            .findFirst();
    }

    public void addMember(TeamMember member) {
        if (!hasMember(member.uuid())) {
            members.add(member);
        }
    }

    /**
     * Removes a member and promotes the longest-standing remaining one if needed.
     */
    public void removeMember(UUID uuid) {
        members.removeIf(member -> member.uuid().equals(uuid));
        if (!members.isEmpty() && leader.equals(uuid)) {
            leader = members.getFirst().uuid();
        }
    }

    public boolean updateKnownName(UUID uuid, String name) {
        for (int i = 0; i < members.size(); i++) {
            TeamMember member = members.get(i);
            if (member.uuid().equals(uuid) && !member.name().equals(name)) {
                members.set(i, new TeamMember(uuid, name));
                return true;
            }
        }
        return false;
    }
}
