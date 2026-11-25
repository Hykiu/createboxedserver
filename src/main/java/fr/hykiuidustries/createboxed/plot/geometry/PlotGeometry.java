package fr.hykiuidustries.createboxed.plot.geometry;

import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;

/**
 * Single source of truth for plot size and vertical range.
 */
@UtilityClass
public final class PlotGeometry {
    public static final int PLOT_CHUNKS = 15;
    public static final int PLOT_BLOCKS = PLOT_CHUNKS * 16;
    public static final int HALF_CHUNKS = PLOT_CHUNKS / 2;
    public static final int CHUNKS_PER_PLOT = PLOT_CHUNKS * PLOT_CHUNKS;

    public static final int MIN_Y = -64;
    public static final int MAX_Y = 63;
    public static final int MIN_SECTION_Y = Math.floorDiv(MIN_Y, 16);
    public static final int MAX_SECTION_Y = Math.floorDiv(MAX_Y, 16);

    public static int pitch(int gap) {
        return PLOT_CHUNKS + gap;
    }

    public static int centerChunk(int anchorChunk, int cellCoordinate, int pitch) {
        return anchorChunk + cellCoordinate * pitch;
    }

    public static int originChunk(int centerChunk) {
        return centerChunk - HALF_CHUNKS;
    }

    /**
     * Returns the middle block of the plot at the given Y level.
     */
    public static BlockPos centerBlock(int centerChunkX, int centerChunkZ, int y) {
        return new BlockPos(centerChunkX * 16 + 8, y, centerChunkZ * 16 + 8);
    }
}
