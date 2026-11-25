package fr.hykiuidustries.createboxed.plot.data;

import fr.hykiuidustries.createboxed.plot.geometry.PlotCell;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.NullMarked;

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
    }

    public static PlotEntry read(CompoundTag tag) {
        PlotStatus status;
        try {
            status = PlotStatus.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException ignored) {
            status = PlotStatus.GENERATING;
        }
        return new PlotEntry(
            tag.getInt("index"),
            new PlotCell(tag.getInt("cellX"), tag.getInt("cellZ")),
            tag.getInt("originX"),
            tag.getInt("originZ"),
            tag.getInt("teamId"),
            status,
            tag.getInt("nextChunk")
        );
    }

    public CompoundTag write(CompoundTag tag) {
        tag.putInt("index", index);
        tag.putInt("cellX", cell.cx());
        tag.putInt("cellZ", cell.cz());
        tag.putInt("originX", originX);
        tag.putInt("originZ", originZ);
        tag.putInt("teamId", teamId);
        tag.putString("status", status.name());
        tag.putInt("nextChunk", nextChunk);
        return tag;
    }
}
