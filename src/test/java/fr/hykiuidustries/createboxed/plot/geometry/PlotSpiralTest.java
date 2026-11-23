package fr.hykiuidustries.createboxed.plot.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotSpiralTest {
    @Test
    void mapsIndicesToOutwardSquareSpiral() {
        int[][] expected = {
            {0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1},
            {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {2, -1},
            {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2},
            {-1, 2}, {-2, 2}, {-2, 1}, {-2, 0}, {-2, -1},
            {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2},
            {3, -2}, {3, -1}, {3, 0}
        };

        for (int index = 0; index < expected.length; index++) {
            PlotCell cell = PlotSpiral.cellFromIndex(index);
            assertEquals(expected[index][0], cell.cx(), "x at index " + index);
            assertEquals(expected[index][1], cell.cz(), "z at index " + index);
        }
    }

    @Test
    void rejectsNegativeIndices() {
        assertThrows(IllegalArgumentException.class, () -> PlotSpiral.cellFromIndex(-1));
    }

    @Test
    void appliesMinimumSpawnDistanceToNearestEdge() {
        int pitch = PlotGeometry.pitch(12);

        assertFalse(PlotSpiral.isValid(new PlotCell(0, 0), pitch, 32));
        assertFalse(PlotSpiral.isValid(new PlotCell(1, 0), pitch, 32));
        assertFalse(PlotSpiral.isValid(new PlotCell(1, 1), pitch, 32));
        assertTrue(PlotSpiral.isValid(new PlotCell(2, 0), pitch, 32));
        assertTrue(PlotSpiral.isValid(new PlotCell(0, 2), pitch, 32));
        assertTrue(PlotSpiral.isValid(new PlotCell(0, -2), pitch, 32));
        assertTrue(PlotSpiral.isValid(new PlotCell(1, 0), pitch, 0));
    }

    @Test
    void usesLongArithmeticForLargeCoordinates() {
        assertTrue(PlotSpiral.isValid(new PlotCell(1_000_000, 0), 27, 32));
    }
}
