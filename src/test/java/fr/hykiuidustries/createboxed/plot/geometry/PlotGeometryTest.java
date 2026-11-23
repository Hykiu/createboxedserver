package fr.hykiuidustries.createboxed.plot.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class PlotGeometryTest {
    @Test
    void keepsPlotSizeAndVerticalRangeConsistent() {
        assertEquals(15, PlotGeometry.PLOT_CHUNKS);
        assertEquals(240, PlotGeometry.PLOT_BLOCKS);
        assertEquals(7, PlotGeometry.HALF_CHUNKS);
        assertEquals(225, PlotGeometry.CHUNKS_PER_PLOT);
        assertEquals(-64, PlotGeometry.MIN_Y);
        assertEquals(63, PlotGeometry.MAX_Y);
        assertEquals(-4, PlotGeometry.MIN_SECTION_Y);
        assertEquals(3, PlotGeometry.MAX_SECTION_Y);
    }

    @Test
    void computesPitchAndCellPlacement() {
        assertEquals(27, PlotGeometry.pitch(12));
        assertEquals(15, PlotGeometry.pitch(0));

        int centerX = PlotGeometry.centerChunk(0, 2, 27);
        int centerZ = PlotGeometry.centerChunk(10, -1, 27);
        assertEquals(54, centerX);
        assertEquals(-17, centerZ);
        assertEquals(47, PlotGeometry.originChunk(centerX));
        assertEquals(-24, PlotGeometry.originChunk(centerZ));
    }

    @Test
    void returnsCenterBlockAboveFilledVolume() {
        BlockPos center = PlotGeometry.centerBlock(54, -17, PlotGeometry.MAX_Y + 1);

        assertEquals(new BlockPos(872, 64, -264), center);
    }
}
