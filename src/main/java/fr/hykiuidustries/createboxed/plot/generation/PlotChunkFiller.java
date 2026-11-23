package fr.hykiuidustries.createboxed.plot.generation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Fills exactly one chunk of a plot. Coordinates are local to the plot (0..14).
 * Preparation happens off the main thread; application happens on it.
 */
public interface PlotChunkFiller {
    /** Builds the data needed for one chunk. Must not touch the level or the chunk. */
    Prepared prepare(int plotX, int plotZ);

    /** Applies the prepared data to a loaded chunk. Called on the main thread. */
    void fill(ServerLevel level, LevelChunk chunk, int plotX, int plotZ, Prepared prepared);

    /** Opaque data computed off-thread and reused when the chunk is applied. */
    interface Prepared {
    }
}
