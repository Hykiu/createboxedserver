package fr.hykiuidustries.createboxed.plot.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.hykiuidustries.createboxed.plot.geometry.PlotCell;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PlotEntryTest {
    @Test
    void roundTripsAllPersistentFields() {
        UUID creator = UUID.fromString("2d1c442e-5a2b-4c8f-9f5a-1c0d3b7e6a44");
        PlotEntry entry = new PlotEntry(10, new PlotCell(2, -1), 47, -24, creator);
        entry.setNextChunk(128);

        PlotEntry loaded = PlotEntry.read(entry.write(new CompoundTag()));

        assertEquals(10, loaded.index());
        assertEquals(new PlotCell(2, -1), loaded.cell());
        assertEquals(47, loaded.originX());
        assertEquals(-24, loaded.originZ());
        assertEquals(creator, loaded.creator());
        assertEquals(PlotStatus.GENERATING, loaded.status());
        assertEquals(128, loaded.nextChunk());
    }

    @Test
    void keepsDoneStatusAndProgress() {
        PlotEntry entry = new PlotEntry(3, new PlotCell(0, 0), 0, 0, UUID.randomUUID());
        entry.setNextChunk(225);
        entry.setStatus(PlotStatus.DONE);

        PlotEntry loaded = PlotEntry.read(entry.write(new CompoundTag()));

        assertEquals(PlotStatus.DONE, loaded.status());
        assertEquals(225, loaded.nextChunk());
    }

    @Test
    void fallsBackToGeneratingForUnknownStatus() {
        PlotEntry entry = new PlotEntry(1, new PlotCell(0, 0), 0, 0, UUID.randomUUID());
        CompoundTag tag = entry.write(new CompoundTag());
        tag.putString("status", "CORRUPTED");

        assertEquals(PlotStatus.GENERATING, PlotEntry.read(tag).status());
    }
}
