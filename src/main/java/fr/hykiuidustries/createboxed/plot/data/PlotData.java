package fr.hykiuidustries.createboxed.plot.data;

import fr.hykiuidustries.createboxed.CreateBoxed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld SavedData holding the frozen layout and all created plots. */
public final class PlotData extends SavedData {
    private static final String DATA_NAME = CreateBoxed.MODID + "_plots";
    private static final SavedData.Factory<PlotData> FACTORY = new SavedData.Factory<>(PlotData::new, PlotData::read);

    private boolean anchored;
    private int anchorX;
    private int anchorZ;
    private int gap;
    private int nextIndex;
    private final List<PlotEntry> plots = new ArrayList<>();
    private PlotEntry generatingPlot;

    public static PlotData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static PlotData read(CompoundTag tag, HolderLookup.Provider registries) {
        PlotData data = new PlotData();
        data.anchored = tag.getBoolean("anchored");
        data.anchorX = tag.getInt("anchorX");
        data.anchorZ = tag.getInt("anchorZ");
        data.gap = tag.getInt("gap");
        data.nextIndex = tag.getInt("nextIndex");

        ListTag plotList = tag.getList("plots", Tag.TAG_COMPOUND);
        for (Tag plotTag : plotList) {
            PlotEntry entry = PlotEntry.read((CompoundTag) plotTag);
            data.plots.add(entry);
            if (entry.status() == PlotStatus.GENERATING && data.generatingPlot == null) {
                data.generatingPlot = entry;
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("anchored", anchored);
        tag.putInt("anchorX", anchorX);
        tag.putInt("anchorZ", anchorZ);
        tag.putInt("gap", gap);
        tag.putInt("nextIndex", nextIndex);

        ListTag plotList = new ListTag();
        for (PlotEntry plot : plots) {
            plotList.add(plot.write(new CompoundTag()));
        }
        tag.put("plots", plotList);
        return tag;
    }

    public boolean isAnchored() {
        return anchored;
    }

    public int anchorX() {
        return anchorX;
    }

    public int anchorZ() {
        return anchorZ;
    }

    public int gap() {
        return gap;
    }

    public int nextIndex() {
        return nextIndex;
    }

    public List<PlotEntry> plots() {
        return Collections.unmodifiableList(plots);
    }

    public PlotEntry generatingPlot() {
        return generatingPlot;
    }

    public void initializeAnchor(int chunkX, int chunkZ, int gap) {
        this.anchored = true;
        this.anchorX = chunkX;
        this.anchorZ = chunkZ;
        this.gap = gap;
        setDirty();
    }

    public void setNextIndex(int nextIndex) {
        this.nextIndex = nextIndex;
        setDirty();
    }

    public void addPlot(PlotEntry entry) {
        plots.add(entry);
        if (entry.status() == PlotStatus.GENERATING) {
            generatingPlot = entry;
        }
        setDirty();
    }

    public void finishPlot(PlotEntry entry) {
        entry.setStatus(PlotStatus.DONE);
        if (generatingPlot == entry) {
            generatingPlot = null;
        }
        setDirty();
    }

    public void advanceChunk(PlotEntry entry, int nextChunk) {
        entry.setNextChunk(nextChunk);
        setDirty();
    }
}
