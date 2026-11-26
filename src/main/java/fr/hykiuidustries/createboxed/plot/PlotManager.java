package fr.hykiuidustries.createboxed.plot;

import com.mojang.logging.LogUtils;
import fr.hykiuidustries.createboxed.plot.data.PlotData;
import fr.hykiuidustries.createboxed.plot.data.PlotEntry;
import fr.hykiuidustries.createboxed.plot.data.PlotStatus;
import fr.hykiuidustries.createboxed.plot.generation.AirChunkFiller;
import fr.hykiuidustries.createboxed.plot.generation.AsyncPlotGenerator;
import fr.hykiuidustries.createboxed.plot.generation.DirtChunkFiller;
import fr.hykiuidustries.createboxed.plot.generation.PlotChunkFiller;
import fr.hykiuidustries.createboxed.plot.generation.ReadyChunk;
import fr.hykiuidustries.createboxed.plot.geometry.PlotCell;
import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import fr.hykiuidustries.createboxed.plot.geometry.PlotSpiral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Set;

/**
 * Server-side orchestration: placement, chunk-by-chunk filling/clearing and progress reporting.
 * One instance lives in the module {@link fr.hykiuidustries.createboxed.modules.ServiceRegistry}
 * and is injected into its consumers. The world-dependent state is bound on {@link #start(ServerLevel)}.
 */
@NullMarked
public final class PlotManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private @Nullable ServerLevel overworld;
    private @Nullable PlotData data;
    private @Nullable AsyncPlotGenerator generator;
    private @Nullable PlotChunkFiller activeFiller;
    private @Nullable PlotProgressListener listener;

    public PlotManager() {
    }

    public synchronized void start(ServerLevel overworld) {
        stopWork();
        this.overworld = overworld;
        PlotData storage = PlotData.get(overworld);
        this.data = storage;
        PlotEntry generating = storage.generatingPlot();
        if (generating != null) {
            LOGGER.info("Resuming unfinished slot {}", generating.index());
            startWork(generating, new DirtChunkFiller());
            return;
        }
        PlotEntry deleting = storage.deletingPlot();
        if (deleting != null) {
            LOGGER.info("Resuming slot deletion {}", deleting.index());
            startWork(deleting, new AirChunkFiller());
        }
    }

    public synchronized void stop() {
        stopWork();
        listener = null;
        overworld = null;
        data = null;
    }

    public void setListener(@Nullable PlotProgressListener listener) {
        this.listener = listener;
    }

    public boolean isNotStarted() {
        return overworld == null || data == null;
    }

    private void notifyCompletion(PlotEntry entry) {
        PlotProgressListener current = listener;
        if (current != null) {
            current.onComplete(entry);
        }
    }

    private void notifyDeletion(PlotEntry entry) {
        PlotProgressListener current = listener;
        if (current != null) {
            current.onDeleteComplete(entry);
        }
    }

    public boolean isGenerating() {
        PlotData storage = data;
        return storage != null && storage.generatingPlot() != null;
    }

    public boolean isDeleting() {
        PlotData storage = data;
        return storage != null && storage.deletingPlot() != null;
    }

    public boolean isBusy() {
        return isGenerating() || isDeleting();
    }

    /**
     * Starts one slot for a team at the lowest usable index. Returns null if busy or not started.
     */
    public synchronized @Nullable PlotEntry createPlot(int teamId, int preferredIndex) {
        if (isNotStarted() || isBusy()) {
            return null;
        }
        PlotData storage = data();
        ServerLevel level = java.util.Objects.requireNonNull(overworld);

        int gap = storage.anchored() ? storage.gap() : PlotModule.gap();
        int pitch = PlotGeometry.pitch(gap);
        int minSpawnDistance = PlotModule.minSpawnDistance();

        Set<Integer> used = storage.usedSlotIndices();
        int index = Math.max(0, preferredIndex);
        PlotCell cell = PlotSpiral.cellFromIndex(index);
        while (used.contains(index) || !PlotSpiral.isValid(cell, pitch, minSpawnDistance)) {
            index++;
            cell = PlotSpiral.cellFromIndex(index);
        }

        if (!storage.anchored()) {
            BlockPos spawn = level.getSharedSpawnPos();
            storage.initializeAnchor(
                SectionPos.blockToSectionCoord(spawn.getX()),
                SectionPos.blockToSectionCoord(spawn.getZ()),
                gap
            );
        } else if (PlotModule.gap() != storage.gap()) {
            LOGGER.warn(
                "Configured gap ({}) differs from the frozen gap ({}) of existing slots; keeping the stored value.",
                PlotModule.gap(),
                storage.gap()
            );
        }

        int centerX = PlotGeometry.centerChunk(storage.anchorX(), cell.cx(), pitch);
        int centerZ = PlotGeometry.centerChunk(storage.anchorZ(), cell.cz(), pitch);
        PlotEntry entry = new PlotEntry(
            index,
            cell,
            PlotGeometry.originChunk(centerX),
            PlotGeometry.originChunk(centerZ),
            teamId
        );
        storage.addPlot(entry);
        storage.setNextIndex(Math.max(storage.nextIndex(), index + 1));
        startWork(entry, new DirtChunkFiller());
        return entry;
    }

    /**
     * Starts clearing a team's finished slot. The DELETING status is persisted before any chunk is touched.
     */
    public synchronized @Nullable PlotEntry deletePlot(int teamId) {
        if (isNotStarted() || isBusy()) {
            return null;
        }
        PlotData storage = data();
        PlotEntry entry = storage.plots().stream()
            .filter(plot -> plot.teamId() == teamId && plot.status() == PlotStatus.DONE)
            .findFirst()
            .orElse(null);
        if (entry == null) {
            return null;
        }
        storage.startDeletion(entry);
        startWork(entry, new AirChunkFiller());
        return entry;
    }

    public void tick() {
        PlotData storage = data;
        if (storage == null) {
            return;
        }
        PlotEntry entry = storage.generatingPlot();
        if (entry == null) {
            entry = storage.deletingPlot();
        }
        AsyncPlotGenerator active = generator;
        if (entry == null || active == null) {
            return;
        }

        boolean deleting = entry.status() == PlotStatus.DELETING;
        int limit = PlotModule.chunksPerTick();
        for (int done = 0; done < limit; done++) {
            boolean stillActive = deleting ?
                                  storage.deletingPlot() == entry :
                                  storage.generatingPlot() == entry;
            if (!stillActive) {
                return;
            }
            ReadyChunk ready = active.poll();
            if (ready == null || ready.index() != entry.nextChunk()) {
                // Nothing ready yet: never block the main thread waiting for a chunk.
                break;
            }
            if (ready.error() != null) {
                LOGGER.error(
                    "Retrying slot chunk {} after a preparation failure",
                    ready.index(),
                    ready.error()
                );
                active.start(entry.nextChunk(), entry.originX(), entry.originZ());
                break;
            }
            try {
                applyReady(ready, active);
            } catch (RuntimeException exception) {
                LOGGER.error(
                    "Failed to fill slot chunk {} of slot {}",
                    ready.index(),
                    entry.index(),
                    exception
                );
                active.start(entry.nextChunk(), entry.originX(), entry.originZ());
                break;
            }
            storage.advanceChunk(entry, ready.index() + 1);
            PlotProgressListener current = listener;
            if (current != null) {
                current.onProgress(entry, entry.nextChunk());
            }

            if (ready.index() + 1 >= PlotGeometry.CHUNKS_PER_PLOT) {
                complete(entry, deleting);
                return;
            }
        }
    }

    private void applyReady(ReadyChunk ready, AsyncPlotGenerator active) {
        LevelChunk chunk = java.util.Objects.requireNonNull(ready.chunk());
        PlotChunkFiller filler = java.util.Objects.requireNonNull(activeFiller);
        try {
            filler.fill(level(), chunk, ready.plotX(), ready.plotZ(), ready.prepared());
        } finally {
            active.release();
        }
    }

    private void complete(PlotEntry entry, boolean deleting) {
        stopWork();
        PlotData storage = data();
        if (deleting) {
            storage.finishDeletion(entry);
            LOGGER.info("Slot {} deleted", entry.index());
            notifyDeletion(entry);
        } else {
            storage.finishPlot(entry);
            LOGGER.info("Slot {} is done", entry.index());
            notifyCompletion(entry);
        }
    }

    private void startWork(PlotEntry entry, PlotChunkFiller filler) {
        stopWork();
        activeFiller = filler;
        generator = new AsyncPlotGenerator(level(), filler);
        generator.start(entry.nextChunk(), entry.originX(), entry.originZ());
    }

    private void stopWork() {
        if (generator != null) {
            generator.stop();
        }
        generator = null;
        activeFiller = null;
    }

    private PlotData data() {
        PlotData storage = data;
        if (storage == null) {
            throw new IllegalStateException("PlotManager was not started");
        }
        return storage;
    }

    private ServerLevel level() {
        ServerLevel current = overworld;
        if (current == null) {
            throw new IllegalStateException("PlotManager was not started");
        }
        return current;
    }
}
