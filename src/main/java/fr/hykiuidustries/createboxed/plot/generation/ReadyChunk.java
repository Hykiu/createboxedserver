package fr.hykiuidustries.createboxed.plot.generation;

import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.LevelChunk;

/** A chunk loaded by the worker and waiting for its main-thread fill. */
public record ReadyChunk(
    int index,
    @Nullable LevelChunk chunk,
    int plotX,
    int plotZ,
    @Nullable PlotChunkFiller.Prepared prepared,
    @Nullable Throwable error
) {
    public static ReadyChunk failed(int index, Throwable error) {
        return new ReadyChunk(index, null, 0, 0, null, error);
    }
}
