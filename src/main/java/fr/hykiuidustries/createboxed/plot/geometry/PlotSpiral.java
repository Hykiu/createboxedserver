package fr.hykiuidustries.createboxed.plot.geometry;

import lombok.experimental.UtilityClass;

/**
 * Pure placement maths: no Minecraft classes are used here.
 */
@UtilityClass
public final class PlotSpiral {
    private static final int HALF_PLOT = PlotGeometry.PLOT_CHUNKS / 2;

    /**
     * Maps a spiral index to a grid cell: 0 = (0,0), 1 = (1,0), 2 = (1,1),
     * 3 = (0,1), 4 = (-1,1), ... Skipped cells still consume an index.
     */
    public static PlotCell cellFromIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Spiral index must not be negative: " + index);
        }
        if (index == 0) {
            return new PlotCell(0, 0);
        }

        long ring = Math.max(1, (long) Math.floor((Math.sqrt(index) + 1.0) / 2.0));
        while (square(2 * ring + 1) <= index) {
            ring++;
        }
        while (ring > 1 && square(2 * ring - 1) > index) {
            ring--;
        }

        long offset = index - square(2 * ring - 1);
        long x;
        long z;
        if (offset < 2 * ring) {
            x = ring;
            z = -ring + 1 + offset;
        } else if (offset < 4 * ring) {
            x = ring - (offset - 2 * ring + 1);
            z = ring;
        } else if (offset < 6 * ring) {
            x = -ring;
            z = ring - (offset - 4 * ring + 1);
        } else {
            x = -ring + (offset - 6 * ring + 1);
            z = -ring;
        }
        return new PlotCell((int) x, (int) z);
    }

    /**
     * A cell is valid when its nearest edge is far enough from the anchor.
     */
    public static boolean isValid(PlotCell cell, int pitch, int minSpawnDistance) {
        long chebyshevDistance = Math.max(Math.abs((long) cell.cx()), Math.abs((long) cell.cz()));
        return chebyshevDistance * pitch - HALF_PLOT >= minSpawnDistance;
    }

    private static long square(long value) {
        return value * value;
    }
}
