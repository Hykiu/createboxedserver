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
import lombok.Getter;
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
 */
@NullMarked
public final class PlotManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Getter
    private static PlotManager instance;
    private static @Nullable PlotProgressListener listener;

    private final ServerLevel overworld;
    private final PlotData data;
    private @Nullable AsyncPlotGenerator generator;
    private @Nullable PlotChunkFiller activeFiller;

    private PlotManager(ServerLevel overworld) {
        this.overworld = overworld;
        this.data = PlotData.get(overworld);
    }

    public static synchronized void start(ServerLevel overworld) {
        instance = new PlotManager(overworld);
        PlotEntry generating = instance.data.generatingPlot();
        if (generating != null) {
            LOGGER.info("Resuming unfinished slot {}", generating.index());
            instance.startWork(generating, new DirtChunkFiller());
            return;
        }
        PlotEntry deleting = instance.data.deletingPlot();
        if (deleting != null) {
            LOGGER.info("Resuming slot deletion {}", deleting.index());
            instance.startWork(deleting, new AirChunkFiller());
        }
    }

    public static synchronized void stop() {
        if (instance != null) {
            instance.stopWork();
        }
        instance = null;
    }

    public static synchronized PlotManager get() {
        return instance;
    }

    public static void setListener(@Nullable PlotProgressListener listener) {
        PlotManager.listener = listener;
    }

    public boolean isGenerating() {
        return data.generatingPlot() != null;
    }

    public boolean isDeleting() {
        return data.deletingPlot() != null;
    }

    public boolean isBusy() {
        return isGenerating() || isDeleting();
    }

    /**
     * Starts one slot for a team at the lowest usable index. Returns null if busy.
     */
    public synchronized @Nullable PlotEntry createPlot(int teamId, int preferredIndex) {
        if (isBusy()) {
            return null;
        }

        int gap = data.anchored() ? data.gap() : PlotModule.gap();
        int pitch = PlotGeometry.pitch(gap);
        int minSpawnDistance = PlotModule.minSpawnDistance();

        Set<Integer> used = data.usedSlotIndices();
        int index = Math.max(0, preferredIndex);
        PlotCell cell = PlotSpiral.cellFromIndex(index);
        while (used.contains(index) || !PlotSpiral.isValid(cell, pitch, minSpawnDistance)) {
            index++;
            cell = PlotSpiral.cellFromIndex(index);
        }

        if (!data.anchored()) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            data.initializeAnchor(
                SectionPos.blockToSectionCoord(spawn.getX()),
                SectionPos.blockToSectionCoord(spawn.getZ()),
                gap
            );
        } else if (PlotModule.gap() != data.gap()) {
            LOGGER.warn(
                "Configured gap ({}) differs from the frozen gap ({}) of existing slots; keeping the stored value.",
                PlotModule.gap(),
                data.gap()
            );
        }

        int centerX = PlotGeometry.centerChunk(data.anchorX(), cell.cx(), pitch);
        int centerZ = PlotGeometry.centerChunk(data.anchorZ(), cell.cz(), pitch);
        PlotEntry entry = new PlotEntry(
            index,
            cell,
            PlotGeometry.originChunk(centerX),
            PlotGeometry.originChunk(centerZ),
            teamId
        );
        data.addPlot(entry);
        data.setNextIndex(Math.max(data.nextIndex(), index + 1));
        startWork(entry, new DirtChunkFiller());
        return entry;
    }

    /**
     * Starts clearing a team's finished slot. The DELETING status is persisted before any chunk is touched.
     */
    public synchronized @Nullable PlotEntry deletePlot(int teamId) {
        if (isBusy()) {
            return null;
        }
        PlotEntry entry = data.plots().stream()
            .filter(plot -> plot.teamId() == teamId && plot.status() == PlotStatus.DONE)
            .findFirst()
            .orElse(null);
        if (entry == null) {
            return null;
        }
        data.startDeletion(entry);
        startWork(entry, new AirChunkFiller());
        return entry;
    }

    public void tick() {
        PlotEntry entry = data.generatingPlot();
        if (entry == null) {
            entry = data.deletingPlot();
        }
        AsyncPlotGenerator active = generator;
        if (entry == null || active == null) {
            return;
        }

        boolean deleting = entry.status() == PlotStatus.DELETING;
        int limit = PlotModule.chunksPerTick();
        for (int done = 0; done < limit; done++) {
            boolean stillActive = deleting ? data.deletingPlot() == entry : data.generatingPlot() == entry;
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
            data.advanceChunk(entry, ready.index() + 1);
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
            filler.fill(overworld, chunk, ready.plotX(), ready.plotZ(), ready.prepared());
        } finally {
            active.release();
        }
    }

    private void complete(PlotEntry entry, boolean deleting) {
        stopWork();
        if (deleting) {
            data.finishDeletion(entry);
            LOGGER.info("Slot {} deleted", entry.index());
            notifyDeletion(entry);
        } else {
            data.finishPlot(entry);
            LOGGER.info("Slot {} is done", entry.index());
            notifyCompletion(entry);
        }
    }

    private static void notifyCompletion(PlotEntry entry) {
        PlotProgressListener current = listener;
        if (current != null) {
            current.onComplete(entry);
        }
    }

    private static void notifyDeletion(PlotEntry entry) {
        PlotProgressListener current = listener;
        if (current != null) {
            current.onDeleteComplete(entry);
        }
    }

    private void startWork(PlotEntry entry, PlotChunkFiller filler) {
        stopWork();
        activeFiller = filler;
        generator = new AsyncPlotGenerator(overworld, filler);
        generator.start(entry.nextChunk(), entry.originX(), entry.originZ());
    }

    private void stopWork() {
        if (generator != null) {
            generator.stop();
        }
        generator = null;
        activeFiller = null;
    }
}
