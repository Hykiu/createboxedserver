package fr.hykiuidustries.createboxed.plot;

import com.mojang.logging.LogUtils;
import fr.hykiuidustries.createboxed.Config;
import fr.hykiuidustries.createboxed.plot.data.PlotData;
import fr.hykiuidustries.createboxed.plot.data.PlotEntry;
import fr.hykiuidustries.createboxed.plot.generation.AsyncPlotGenerator;
import fr.hykiuidustries.createboxed.plot.generation.DirtChunkFiller;
import fr.hykiuidustries.createboxed.plot.generation.PlotChunkFiller;
import fr.hykiuidustries.createboxed.plot.generation.ReadyChunk;
import fr.hykiuidustries.createboxed.plot.geometry.PlotCell;
import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import fr.hykiuidustries.createboxed.plot.geometry.PlotSpiral;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

/** Server-side orchestration: placement, chunk-by-chunk filling, progress and teleport. */
public final class PlotManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROGRESS_INTERVAL = 16;
    private static PlotManager instance;

    private final ServerLevel overworld;
    private final PlotData data;
    private final PlotChunkFiller filler = new DirtChunkFiller();
    private final AsyncPlotGenerator generator;

    private PlotManager(ServerLevel overworld) {
        this.overworld = overworld;
        this.data = PlotData.get(overworld);
        this.generator = new AsyncPlotGenerator(overworld, filler);
    }

    public static synchronized void start(ServerLevel overworld) {
        instance = new PlotManager(overworld);
        PlotEntry pending = instance.data.generatingPlot();
        if (pending != null) {
            LOGGER.info("Resuming unfinished plot {}", pending.index());
            instance.generator.start(pending.nextChunk(), pending.originX(), pending.originZ());
        }
    }

    public static synchronized void stop() {
        if (instance != null) {
            instance.generator.stop();
        }
        instance = null;
    }

    public static synchronized PlotManager get() {
        return instance;
    }

    public boolean isGenerating() {
        return data.generatingPlot() != null;
    }

    /** Creates a new plot; returns null on success or a ready-to-send error component. */
    public Component createPlot(CommandSourceStack source) {
        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayer player)) {
            return Component.translatable("createboxed.plot.player_only");
        }
        if (isGenerating()) {
            return Component.translatable("createboxed.plot.busy");
        }

        int gap = data.isAnchored() ? data.gap() : Config.GAP.get();
        int pitch = PlotGeometry.pitch(gap);
        int minSpawnDistance = Config.MIN_SPAWN_DISTANCE.get();

        int index = data.nextIndex();
        PlotCell cell;
        do {
            cell = PlotSpiral.cellFromIndex(index);
            index++;
        } while (!PlotSpiral.isValid(cell, pitch, minSpawnDistance));

        if (!data.isAnchored()) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            data.initializeAnchor(
                SectionPos.blockToSectionCoord(spawn.getX()),
                SectionPos.blockToSectionCoord(spawn.getZ()),
                gap
            );
        } else if (Config.GAP.get() != data.gap()) {
            LOGGER.warn(
                "Configured gap ({}) differs from the frozen gap ({}) of existing plots; keeping the stored value.",
                Config.GAP.get(), data.gap()
            );
        }

        int centerX = PlotGeometry.centerChunk(data.anchorX(), cell.cx(), pitch);
        int centerZ = PlotGeometry.centerChunk(data.anchorZ(), cell.cz(), pitch);
        PlotEntry entry = new PlotEntry(
            index - 1,
            cell,
            PlotGeometry.originChunk(centerX),
            PlotGeometry.originChunk(centerZ),
            player.getUUID()
        );
        data.addPlot(entry);
        data.setNextIndex(index);
        generator.start(entry.nextChunk(), entry.originX(), entry.originZ());

        Component message = Component.translatable(
            "createboxed.plot.started", entry.index(), PlotGeometry.CHUNKS_PER_PLOT
        );
        source.sendSuccess(() -> message, true);
        return null;
    }

    public void tick() {
        PlotEntry entry = data.generatingPlot();
        if (entry == null) {
            return;
        }

        int limit = Config.CHUNKS_PER_TICK.get();
        for (int done = 0; done < limit && data.generatingPlot() == entry; done++) {
            ReadyChunk ready = generator.poll();
            if (ready == null || ready.index() != entry.nextChunk()) {
                // Nothing ready yet: never block the main thread waiting for a chunk.
                break;
            }
            if (ready.error() != null) {
                LOGGER.error("Retrying plot chunk {} after a preparation failure", ready.index(), ready.error());
                generator.start(entry.nextChunk(), entry.originX(), entry.originZ());
                break;
            }
            try {
                applyReady(ready);
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to fill chunk {} of plot {}", ready.index(), entry.index(), exception);
                generator.start(entry.nextChunk(), entry.originX(), entry.originZ());
                break;
            }
            data.advanceChunk(entry, ready.index() + 1);
            sendProgress(entry);

            if (ready.index() + 1 >= PlotGeometry.CHUNKS_PER_PLOT) {
                complete(entry);
            }
        }
    }

    private void applyReady(ReadyChunk ready) {
        LevelChunk chunk = java.util.Objects.requireNonNull(ready.chunk());
        try {
            filler.fill(overworld, chunk, ready.plotX(), ready.plotZ(), ready.prepared());
        } finally {
            generator.release();
        }
    }

    private void sendProgress(PlotEntry entry) {
        int done = entry.nextChunk();
        if (done % PROGRESS_INTERVAL != 0 && done != PlotGeometry.CHUNKS_PER_PLOT) {
            return;
        }
        ServerPlayer player = overworld.getServer().getPlayerList().getPlayer(entry.creator());
        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                "createboxed.plot.progress", done, PlotGeometry.CHUNKS_PER_PLOT
            ));
        }
    }

    private void complete(PlotEntry entry) {
        data.finishPlot(entry);
        generator.stop();
        LOGGER.info("Plot {} is done", entry.index());

        ServerPlayer player = overworld.getServer().getPlayerList().getPlayer(entry.creator());
        if (player == null) {
            LOGGER.info("Plot creator {} is offline; no teleport performed", entry.creator());
            return;
        }

        BlockPos center = PlotGeometry.centerBlock(
            entry.originX() + PlotGeometry.HALF_CHUNKS,
            entry.originZ() + PlotGeometry.HALF_CHUNKS,
            PlotGeometry.MAX_Y + 1
        );
        player.teleportTo(
            overworld,
            center.getX() + 0.5,
            center.getY(),
            center.getZ() + 0.5,
            player.getYRot(),
            player.getXRot()
        );
        player.sendSystemMessage(Component.translatable("createboxed.plot.done", entry.index()));
    }
}
