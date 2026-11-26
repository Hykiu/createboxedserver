package fr.hykiuidustries.createboxed.plot.generation;

import fr.hykiuidustries.createboxed.plot.geometry.PlotGeometry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Clears one plot chunk back to air, keeping the chunk's biomes. */
@SuppressWarnings("DuplicatedCode")
public final class AirChunkFiller implements PlotChunkFiller {
    @Override
    public Prepared prepare(int plotX, int plotZ) {
        List<PalettedContainer<BlockState>> states = new ArrayList<>();
        for (int sectionY = PlotGeometry.MIN_SECTION_Y; sectionY <= PlotGeometry.MAX_SECTION_Y; sectionY++) {
            states.add(new PalettedContainer<>(
                Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES
            ));
        }
        return new AirChunkData(List.copyOf(states));
    }

    @Override
    public void fill(ServerLevel level, LevelChunk chunk, int plotX, int plotZ, Prepared prepared) {
        AirChunkData data = (AirChunkData) prepared;
        LevelChunkSection[] sections = chunk.getSections();

        int slot = 0;
        for (int sectionY = PlotGeometry.MIN_SECTION_Y; sectionY <= PlotGeometry.MAX_SECTION_Y; sectionY++) {
            int index = level.getSectionIndexFromSectionY(sectionY);
            LevelChunkSection previous = sections[index];
            sections[index] = new LevelChunkSection(data.states().get(slot++), previous.getBiomes());
            level.getChunkSource().getLightEngine().updateSectionStatus(
                SectionPos.of(chunk.getPos(), sectionY), true
            );
        }

        clearContents(level, chunk);
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        chunk.setUnsaved(true);
    }

    private static void clearContents(ServerLevel level, LevelChunk chunk) {
        for (BlockPos pos : List.copyOf(chunk.getBlockEntitiesPos())) {
            chunk.removeBlockEntity(pos);
        }

        int minBlockX = chunk.getPos().x * 16;
        int minBlockZ = chunk.getPos().z * 16;
        AABB volume = new AABB(
            minBlockX, PlotGeometry.MIN_Y, minBlockZ,
            minBlockX + 16, PlotGeometry.MAX_Y + 1, minBlockZ + 16
        );
        for (Entity entity : level.getEntities(EntityTypeTest.forClass(Entity.class), volume, entity -> !(entity instanceof Player))) {
            entity.discard();
        }
    }

    private record AirChunkData(List<PalettedContainer<BlockState>> states) implements Prepared {
    }
}
