package fr.hykiuidustries.createboxed.team.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamRulesTest {
    @Test
    void acceptsOnlySimpleNamesBetweenThreeAndSixteenCharacters() {
        assertTrue(TeamRules.isValidName("abc"));
        assertTrue(TeamRules.isValidName("A_1-2_345678901"));
        assertFalse(TeamRules.isValidName("ab"));
        assertFalse(TeamRules.isValidName("a.b"));
        assertFalse(TeamRules.isValidName("12345678901234567"));
        assertFalse(TeamRules.isValidName(null));
    }

    @Test
    void uniqueNameAvoidsCaseInsensitiveCollisions() {
        Set<String> used = Set.of("alex", "sam", "sam-2");

        assertEquals("alex-2", TeamRules.uniqueName("Alex", used));
        assertEquals("sam-3", TeamRules.uniqueName("SAM", used));
        assertEquals("bo_", TeamRules.uniqueName("bo", used));
    }

    @Test
    void uniqueNameStaysWithinTheLengthLimit() {
        String name = TeamRules.uniqueName("abcdefghijklmnop", Set.of("abcdefghijklmnop"));

        assertTrue(TeamRules.isValidName(name));
        assertEquals(16, name.length());
    }

    @Test
    void acceptRequiresEitherNoSlotOrRemainingMembers() {
        assertTrue(TeamRules.canAccept(false, 1, 2, 8));
        assertTrue(TeamRules.canAccept(true, 2, 2, 8));
        assertFalse(TeamRules.canAccept(true, 1, 2, 8));
        assertFalse(TeamRules.canAccept(false, 1, 8, 8));
    }

    @Test
    void firstFreeIndexUsesTheLowestGapBeforeGrowing() {
        assertEquals(1, TeamRules.firstFreeIndex(List.of(0, 2, 3)));
        assertEquals(4, TeamRules.firstFreeIndex(List.of(0, 1, 2, 3)));
        assertEquals(0, TeamRules.firstFreeIndex(List.of()));
    }

    @Test
    void firstFreeIndexNeverReturnsAnAlreadyUsedIndex() {
        assertEquals(1, TeamRules.firstFreeIndex(List.of(0)));
    }
}
