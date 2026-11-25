package fr.hykiuidustries.createboxed.team;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Event listeners owned by {@link TeamsModule}, registered only while it is enabled.
 */
public final class TeamsEvents {
    private final TeamManager manager;

    public TeamsEvents(TeamManager manager) {
        this.manager = manager;
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        manager.start(event.getServer().overworld());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        manager.stop();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.ensureTeam(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SlotCommand.register(event.getDispatcher(), manager);
    }
}
