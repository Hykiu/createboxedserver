package fr.hykiuidustries.createboxed.plot.generation;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

/** Prepared dirt states, one per filled section, indexed from MIN_SECTION_Y. */
public record DirtChunkData(List<PalettedContainer<BlockState>> states) implements PlotChunkFiller.Prepared {
}
