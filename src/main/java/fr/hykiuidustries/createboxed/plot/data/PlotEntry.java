package fr.hykiuidustries.createboxed.plot.data;

import fr.hykiuidustries.createboxed.Keys.Nbt;
import fr.hykiuidustries.createboxed.plot.geometry.PlotCell;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Persistent record of one plot (a slot in user-facing messages).
 */
@Getter
@Accessors(fluent = true)
@NullMarked
public final class PlotEntry {
    private final int index;
    private final PlotCell cell;
    private final int originX;
    private final int originZ;
    private final int teamId;
    @Setter
    private PlotStatus status;
    @Setter
    private int nextChunk;
    @Setter
    private @Nullable BlockPos spawnPoint;

    public PlotEntry(int index, PlotCell cell, int originX, int originZ, int teamId) {
        this(index, cell, originX, originZ, teamId, PlotStatus.GENERATING, 0);
    }

    private PlotEntry(
        int index,
        PlotCell cell,
        int originX,
        int originZ,
        int teamId,
        PlotStatus status,
        int nextChunk
    ) {
        this.index = index;
        this.cell = cell;
        this.originX = originX;
        this.originZ = originZ;
        this.teamId = teamId;
        this.status = status;
        this.nextChunk = nextChunk;
        this.spawnPoint = null;
    }

    public static PlotEntry read(CompoundTag tag) {
        PlotStatus status;
        try {
            status = PlotStatus.valueOf(tag.getString(Nbt.STATUS));
        } catch (IllegalArgumentException ignored) {
            status = PlotStatus.GENERATING;
        }
        PlotEntry entry = new PlotEntry(
            tag.getInt(Nbt.INDEX),
            new PlotCell(tag.getInt(Nbt.CELL_X), tag.getInt(Nbt.CELL_Z)),
            tag.getInt(Nbt.ORIGIN_X),
            tag.getInt(Nbt.ORIGIN_Z),
            tag.getInt(Nbt.TEAM_ID),
            status,
            tag.getInt(Nbt.NEXT_CHUNK)
        );
        if (tag.contains(Nbt.SPAWN_POINT, Tag.TAG_LONG)) {
            entry.spawnPoint(BlockPos.of(tag.getLong(Nbt.SPAWN_POINT)));
        }
        return entry;
    }

    public CompoundTag write(CompoundTag tag) {
        tag.putInt(Nbt.INDEX, index);
        tag.putInt(Nbt.CELL_X, cell.cx());
        tag.putInt(Nbt.CELL_Z, cell.cz());
        tag.putInt(Nbt.ORIGIN_X, originX);
        tag.putInt(Nbt.ORIGIN_Z, originZ);
        tag.putInt(Nbt.TEAM_ID, teamId);
        tag.putString(Nbt.STATUS, status.name());
        tag.putInt(Nbt.NEXT_CHUNK, nextChunk);
        if (spawnPoint != null) {
            tag.putLong(Nbt.SPAWN_POINT, spawnPoint.asLong());
        }
        return tag;
    }
}
