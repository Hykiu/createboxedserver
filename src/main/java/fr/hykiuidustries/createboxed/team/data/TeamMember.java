package fr.hykiuidustries.createboxed.team.data;

import fr.hykiuidustries.createboxed.Keys.Nbt;
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
@SuppressWarnings("ClassCanBeRecord")
public final class TeamMember {
    private final UUID uuid;
    private final String name;

    public TeamMember(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public static TeamMember read(CompoundTag tag) {
        return new TeamMember(tag.getUUID(Nbt.UUID), tag.getString(Nbt.NAME));
    }

    public CompoundTag write(CompoundTag tag) {
        tag.putUUID(Nbt.UUID, uuid);
        tag.putString(Nbt.NAME, name);
        return tag;
    }
}
