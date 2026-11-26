package fr.hykiuidustries.createboxed.plot;

import fr.hykiuidustries.createboxed.modules.Module;
import fr.hykiuidustries.createboxed.modules.ModuleContext;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Integrates the plot feature into the module lifecycle: config, events and commands
 * only exist while this module is enabled.
 */
public final class PlotModule implements Module {
    public static final String ID = "plot";

    private static ModConfigSpec.IntValue gap;
    private static ModConfigSpec.IntValue minSpawnDistance;
    private static ModConfigSpec.IntValue chunksPerTick;

    private PlotEvents events;
    private PlotManager manager;
    private ModuleContext context;

    public static int gap() {
        return require(gap, "gap").get();
    }

    public static int minSpawnDistance() {
        return require(minSpawnDistance, "minSpawnDistance").get();
    }

    public static int chunksPerTick() {
        return require(chunksPerTick, "chunksPerTick").get();
    }

    private static ModConfigSpec.IntValue require(ModConfigSpec.IntValue value, String name) {
        if (value == null) {
            throw new IllegalStateException("Plot config was not built: " + name);
        }
        return value;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder builder) {
        gap = builder.comment(
            "Number of chunks between the edges of two neighbouring plots. Frozen on first plot creation."
        ).defineInRange("gap", 12, 0, Integer.MAX_VALUE);

        minSpawnDistance = builder.comment(
            "Minimum distance in chunks from the spawn anchor to the nearest edge of a new plot."
        ).defineInRange("minSpawnDistance", 32, 0, Integer.MAX_VALUE);

        chunksPerTick = builder.comment(
            "Maximum number of plot chunks filled per server tick."
        ).defineInRange("chunksPerTick", 8, 1, 64);
    }

    @Override
    public void enable(ModuleContext ctx) {
        this.context = ctx;
        manager = new PlotManager();
        ctx.services().register(PlotManager.class, manager);
        events = new PlotEvents(manager);
        NeoForge.EVENT_BUS.register(events);
    }

    @Override
    public void disable() {
        if (events != null) {
            NeoForge.EVENT_BUS.unregister(events);
            events = null;
        }
        if (manager != null) {
            manager.stop();
            manager = null;
        }
        if (context != null) {
            context.services().unregister(PlotManager.class);
            context = null;
        }
    }
}
