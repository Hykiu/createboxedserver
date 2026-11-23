package fr.hykiuidustries.createboxed.plot;

import fr.hykiuidustries.createboxed.CreateBoxed;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CreateBoxed.MODID)
public final class PlotEvents {
    private PlotEvents() {
    }

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        PlotManager.start(event.getServer().overworld());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PlotManager.stop();
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        PlotManager manager = PlotManager.get();
        if (manager != null) {
            manager.tick();
        }
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        PlotCommand.register(event.getDispatcher());
    }
}
