package fr.hykiuidustries.createboxed.plot.data;

import fr.hykiuidustries.createboxed.plot.geometry.PlotCell;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Persistent record of one plot. */
public final class PlotEntry {
    private final int index;
    private final PlotCell cell;
    private final int originX;
    private final int originZ;
    private final UUID creator;
    private PlotStatus status;
    private int nextChunk;

    public PlotEntry(int index, PlotCell cell, int originX, int originZ, UUID creator) {
        this(index, cell, originX, originZ, creator, PlotStatus.GENERATING, 0);
    }

    private PlotEntry(
        int index, PlotCell cell, int originX, int originZ, UUID creator, PlotStatus status, int nextChunk
    ) {
        this.index = index;
        this.cell = cell;
        this.originX = originX;
        this.originZ = originZ;
        this.creator = creator;
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
            tag.getUUID("creator"),
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
        tag.putUUID("creator", creator);
        tag.putString("status", status.name());
        tag.putInt("nextChunk", nextChunk);
        return tag;
    }

    public int index() {
        return index;
    }

    public PlotCell cell() {
        return cell;
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public UUID creator() {
        return creator;
    }

    public PlotStatus status() {
        return status;
    }

    public void setStatus(PlotStatus status) {
        this.status = status;
    }

    public int nextChunk() {
        return nextChunk;
    }

    public void setNextChunk(int nextChunk) {
        this.nextChunk = nextChunk;
    }
}
