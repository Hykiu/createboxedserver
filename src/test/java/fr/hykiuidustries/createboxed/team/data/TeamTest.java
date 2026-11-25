package fr.hykiuidustries.createboxed.team.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamTest {
    private static final UUID LEADER = UUID.fromString("2d1c442e-5a2b-4c8f-9f5a-1c0d3b7e6a44");
    private static final UUID SECOND = UUID.fromString("74a1e5c9-0bd3-4c16-9d0e-35b08a2ec510");
    private static final UUID THIRD = UUID.fromString("b6f9d0a2-1c47-4e5b-8a3d-90e6f1c2b704");

    @Test
    void leavingLeaderIsReplacedByLongestStandingMember() {
        Team team = Team.solo(1, "alpha", new TeamMember(LEADER, "Alice"));
        team.addMember(new TeamMember(SECOND, "Bob"));
        team.addMember(new TeamMember(THIRD, "Carol"));

        team.removeMember(LEADER);

        assertEquals(SECOND, team.leader());
        assertFalse(team.hasMember(LEADER));
        assertEquals(2, team.size());
    }

    @Test
    void offlineMembersAreFoundByLastKnownName() {
        Team team = Team.solo(2, "alpha", new TeamMember(LEADER, "Alice"));
        team.addMember(new TeamMember(SECOND, "Bob"));

        assertTrue(team.findMemberByName("bOb").isPresent());
        assertEquals(SECOND, team.findMemberByName("Bob").orElseThrow().uuid());
        assertTrue(team.findMemberByName("Nobody").isEmpty());
    }

    @Test
    void roundTripsAllPersistentFields() {
        Team team = Team.solo(7, "alpha", new TeamMember(LEADER, "Alice"));
        team.addMember(new TeamMember(SECOND, "Bob"));
        team.slotIndex(42);

        Team loaded = Team.read(team.write(new CompoundTag()));

        assertEquals(7, loaded.id());
        assertEquals("alpha", loaded.name());
        assertEquals(LEADER, loaded.leader());
        assertEquals(42, loaded.slotIndex());
        assertEquals(2, loaded.size());
        assertEquals("Bob", loaded.findMember(SECOND).orElseThrow().name());
    }

    @Test
    void keepsNoSlotWhenNoneIsAssigned() {
        Team team = Team.solo(8, "alpha", new TeamMember(LEADER, "Alice"));

        assertNull(Team.read(team.write(new CompoundTag())).slotIndex());
    }
}
