package fr.hykiuidustries.createboxed.plot.data;

import fr.hykiuidustries.createboxed.Keys.Nbt;
import fr.hykiuidustries.createboxed.plot.geometry.PlotCell;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlotEntryTest {
    @Test
    void roundTripsAllPersistentFields() {
        int teamId = 17;
        PlotEntry entry = new PlotEntry(10, new PlotCell(2, -1), 47, -24, teamId);
        entry.nextChunk(128);
        entry.spawnPoint(new BlockPos(100, 64, -200));

        PlotEntry loaded = PlotEntry.read(entry.write(new CompoundTag()));

        assertEquals(10, loaded.index());
        assertEquals(new PlotCell(2, -1), loaded.cell());
        assertEquals(47, loaded.originX());
        assertEquals(-24, loaded.originZ());
        assertEquals(teamId, loaded.teamId());
        assertEquals(PlotStatus.GENERATING, loaded.status());
        assertEquals(128, loaded.nextChunk());
        assertEquals(new BlockPos(100, 64, -200), loaded.spawnPoint());
    }

    @Test
    void hasNoCustomSpawnPointByDefault() {
        PlotEntry entry = new PlotEntry(1, new PlotCell(0, 0), 0, 0, 4);
        assertNull(entry.spawnPoint());
        assertNull(PlotEntry.read(entry.write(new CompoundTag())).spawnPoint());
    }

    @Test
    void keepsDoneStatusAndProgress() {
        PlotEntry entry = new PlotEntry(3, new PlotCell(0, 0), 0, 0, 4);
        entry.nextChunk(225);
        entry.status(PlotStatus.DONE);

        PlotEntry loaded = PlotEntry.read(entry.write(new CompoundTag()));

        assertEquals(PlotStatus.DONE, loaded.status());
        assertEquals(225, loaded.nextChunk());
    }

    @Test
    void roundTripsDeletingStatus() {
        PlotEntry entry = new PlotEntry(6, new PlotCell(1, 1), 16, 32, 9);
        entry.status(PlotStatus.DELETING);
        entry.nextChunk(70);

        PlotEntry loaded = PlotEntry.read(entry.write(new CompoundTag()));

        assertEquals(PlotStatus.DELETING, loaded.status());
        assertEquals(70, loaded.nextChunk());
    }

    @Test
    void fallsBackToGeneratingForUnknownStatus() {
        PlotEntry entry = new PlotEntry(1, new PlotCell(0, 0), 0, 0, 4);
        CompoundTag tag = entry.write(new CompoundTag());
        tag.putString(Nbt.STATUS, "CORRUPTED");

        assertEquals(PlotStatus.GENERATING, PlotEntry.read(tag).status());
    }
}
