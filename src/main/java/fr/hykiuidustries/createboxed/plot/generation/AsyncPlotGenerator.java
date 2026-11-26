package fr.hykiuidustries.createboxed.plot.generation;

import com.mojang.logging.LogUtils;
import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;

/**
 * Off-main-thread pipeline: prepares fill data and asks Minecraft to load chunks
 * ahead of time. The main thread only applies the ready chunks, in bounded batches.
 */
public final class AsyncPlotGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LOOKAHEAD = 16;
    private static final long LOAD_RETRY_MILLIS = 50L;
    /** Vanilla's UNKNOWN ticket expires after one tick; plot chunks need a durable ticket. */
    private static final TicketType<ChunkPos> PLOT_TICKET = TicketType.create(
        "createboxed_plot", Comparator.comparingLong(ChunkPos::toLong)
    );

    private final ServerLevel level;
    private final PlotChunkFiller filler;
    private final BlockingQueue<ReadyChunk> ready = new LinkedBlockingQueue<>();
    private volatile boolean running;
    private Semaphore permits;
    private ExecutorService worker;
    private int ticketOriginX;
    private int ticketOriginZ;
    private boolean ticketsHeld;

    public AsyncPlotGenerator(ServerLevel level, PlotChunkFiller filler) {
        this.level = level;
        this.filler = filler;
    }

    public synchronized void start(int startIndex, int originX, int originZ) {
        stop();
        addPlotTickets(originX, originZ);
        permits = new Semaphore(LOOKAHEAD);
        worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CreateBoxed plot generator");
            thread.setDaemon(true);
            return thread;
        });
        running = true;
        worker.execute(() -> loadAhead(startIndex, originX, originZ));
    }

    public synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
        ready.clear();
        removePlotTickets();
    }

    /** Keeps every plot chunk loaded until the fill is done, independently of tick timing. */
    private void addPlotTickets(int originX, int originZ) {
        for (int z = 0; z < PlotGeometry.PLOT_CHUNKS; z++) {
            for (int x = 0; x < PlotGeometry.PLOT_CHUNKS; x++) {
                ChunkPos pos = new ChunkPos(originX + x, originZ + z);
                level.getChunkSource().addRegionTicket(PLOT_TICKET, pos, 0, pos);
            }
        }
        ticketOriginX = originX;
        ticketOriginZ = originZ;
        ticketsHeld = true;
    }

    private void removePlotTickets() {
        if (!ticketsHeld) {
            return;
        }
        for (int z = 0; z < PlotGeometry.PLOT_CHUNKS; z++) {
            for (int x = 0; x < PlotGeometry.PLOT_CHUNKS; x++) {
                ChunkPos pos = new ChunkPos(ticketOriginX + x, ticketOriginZ + z);
                level.getChunkSource().removeRegionTicket(PLOT_TICKET, pos, 0, pos);
            }
        }
        ticketsHeld = false;
    }

    /** Non-blocking: returns the next ready chunk, or null if none is available. */
    public ReadyChunk poll() {
        return ready.poll();
    }

    /** Called on the main thread after a ready chunk has been applied. */
    public void release() {
        permits.release();
    }

    private void loadAhead(int startIndex, int originX, int originZ) {
        int index = startIndex;
        while (running && index < PlotGeometry.CHUNKS_PER_PLOT) {
            try {
                permits.acquire();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) {
                permits.release();
                return;
            }

            try {
                int plotX = index % PlotGeometry.PLOT_CHUNKS;
                int plotZ = index / PlotGeometry.PLOT_CHUNKS;
                PlotChunkFiller.Prepared prepared = filler.prepare(plotX, plotZ);
                ChunkPos pos = new ChunkPos(originX + plotX, originZ + plotZ);
                LevelChunk chunk = loadChunk(pos);
                ready.add(new ReadyChunk(index, chunk, plotX, plotZ, prepared, null));
                index++;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                permits.release();
                LOGGER.error("Failed to prepare plot chunk {}", index, exception);
                ready.add(ReadyChunk.failed(index, exception));
                return;
            }
        }
    }

    /**
     * Loads a chunk from a worker. The first request can report "Unloaded chunk"
     * while the ticket reaches the generation holder, so retry briefly instead
     * of failing the plot; the server thread is never blocked here.
     */
    @SuppressWarnings("BusyWait")
    private LevelChunk loadChunk(ChunkPos pos) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L);
        String lastError = "unknown error";
        while (running && System.nanoTime() < deadline) {
            ChunkResult<ChunkAccess> result = level.getChunkSource()
                .getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, false)
                .join();
            if (result.orElse(null) instanceof LevelChunk chunk) {
                return chunk;
            }
            lastError = String.valueOf(result.getError());
            Thread.sleep(LOAD_RETRY_MILLIS);
        }
        if (!running) {
            throw new InterruptedException("Plot generation stopped while loading " + pos);
        }
        throw new IllegalStateException("Timed out loading plot chunk " + pos + ": " + lastError);
    }
}
