package fr.hykiuidustries.createboxed.plot;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Event listeners owned by {@link PlotModule}. They are registered only while the
 * module is enabled, so disabling it also removes the command and the tick handler.
 */
public final class PlotEvents {
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        PlotManager.start(event.getServer().overworld());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        PlotManager.stop();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        PlotManager manager = PlotManager.get();
        if (manager != null) {
            manager.tick();
        }
    }
}
