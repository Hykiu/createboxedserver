package fr.hykiuidustries.createboxed.team;

import fr.hykiuidustries.createboxed.modules.Module;
import fr.hykiuidustries.createboxed.modules.ModuleContext;
import fr.hykiuidustries.createboxed.plot.PlotModule;
import fr.hykiuidustries.createboxed.plot.PlotManager;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;

/**
 * Integrates teams and the /slot command into the module lifecycle.
 */
public final class TeamsModule implements Module {
    public static final String ID = "teams";

    private static ModConfigSpec.IntValue inviteExpirySeconds;
    private static ModConfigSpec.IntValue maxTeamSize;
    private static ModConfigSpec.BooleanValue allowVisitors;

    private TeamManager manager;
    private TeamsEvents events;
    private ModuleContext context;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of(PlotModule.ID);
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder builder) {
        builder.push("teams");
        inviteExpirySeconds = builder.comment(
            "Number of seconds a team invitation stays valid."
        ).defineInRange("inviteExpirySeconds", 60, 1, 3600);
        maxTeamSize = builder.comment(
            "Maximum number of members in a team."
        ).defineInRange("maxTeamSize", 8, 2, 64);
        allowVisitors = builder.comment(
            "Whether players can visit another team's finished slot with /slot visit."
        ).define("allowVisitors", true);
        builder.pop();
    }

    @Override
    public void enable(ModuleContext ctx) {
        this.context = ctx;
        manager = new TeamManager(ctx.logger(), ctx.services().require(PlotManager.class));
        ctx.services().register(TeamManager.class, manager);
        events = new TeamsEvents(manager);
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
        }
        if (context != null) {
            context.services().unregister(TeamManager.class);
            context = null;
        }
        manager = null;
    }

    public static int inviteExpirySeconds() {
        return require(inviteExpirySeconds, "inviteExpirySeconds").get();
    }

    public static int maxTeamSize() {
        return require(maxTeamSize, "maxTeamSize").get();
    }

    public static boolean allowVisitors() {
        return require(allowVisitors).get();
    }

    public static boolean visitorsDisabled() {
        return !allowVisitors();
    }

    private static ModConfigSpec.BooleanValue require(ModConfigSpec.BooleanValue value) {
        if (value == null) {
            throw new IllegalStateException("Teams config was not built: allowVisitors");
        }
        return value;
    }

    private static ModConfigSpec.IntValue require(ModConfigSpec.IntValue value, String name) {
        if (value == null) {
            throw new IllegalStateException("Teams config was not built: " + name);
        }
        return value;
    }
}
