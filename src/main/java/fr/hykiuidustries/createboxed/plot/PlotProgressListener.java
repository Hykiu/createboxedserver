package fr.hykiuidustries.createboxed.plot;

import fr.hykiuidustries.createboxed.plot.data.PlotEntry;

/**
 * Notified by the generation pipeline; implemented by the team layer that owns slots.
 */
public interface PlotProgressListener {
    void onProgress(PlotEntry entry, int chunksDone);

    void onComplete(PlotEntry entry);

    void onDeleteComplete(PlotEntry entry);
}
