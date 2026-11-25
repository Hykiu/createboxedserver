package fr.hykiuidustries.createboxed.team.data;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Pure team rules, free of Minecraft classes so they can be unit tested directly.
 */
@UtilityClass
public final class TeamRules {
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 16;

    public boolean isValidName(@Nullable String name) {
        if (name == null || name.length() < MIN_LENGTH || name.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds a valid unique name from a player name, appending a numeric suffix on collision.
     */
    public String uniqueName(String base, Set<String> usedNames) {
        String sanitized = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (sanitized.length() < MIN_LENGTH) {
            sanitized += "_".repeat(MIN_LENGTH - sanitized.length());
        }
        sanitized = sanitized.substring(0, Math.min(sanitized.length(), MAX_LENGTH));

        String candidate = sanitized;
        for (int suffix = 2; ; suffix++) {
            boolean taken = false;
            for (String used : usedNames) {
                if (used.equalsIgnoreCase(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
            String number = Integer.toString(suffix);
            int limit = MAX_LENGTH - number.length() - 1;
            String stem = sanitized.substring(0, Math.min(sanitized.length(), limit));
            candidate = stem + "-" + number;
        }
    }

    /**
     * A player may join another team unless leaving would strand a slot with no members.
     */
    public boolean canAccept(boolean currentHasSlot, int currentMemberCount, int targetSize, int maxTeamSize) {
        if (targetSize >= maxTeamSize) {
            return false;
        }
        return !currentHasSlot || currentMemberCount > 1;
    }

    /**
     * Reuses the lowest free index. A stored "next index" can be stale, so the
     * used indices are the only source of truth here.
     */
    public int firstFreeIndex(Collection<Integer> usedIndices) {
        int candidate = 0;
        while (usedIndices.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }
}
