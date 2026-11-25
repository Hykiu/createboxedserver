package fr.hykiuidustries.createboxed.team.data;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * A team member with the last name seen for its UUID, so offline members stay addressable.
 */
@Getter
@Accessors(fluent = true)
@NullMarked
public final class TeamMember {
    private final UUID uuid;
    private final String name;

    public TeamMember(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public static TeamMember read(CompoundTag tag) {
        return new TeamMember(tag.getUUID("uuid"), tag.getString("name"));
    }

    public CompoundTag write(CompoundTag tag) {
        tag.putUUID("uuid", uuid);
        tag.putString("name", name);
        return tag;
    }
}
