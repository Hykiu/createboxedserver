package fr.hykiuidustries.createboxed.plot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotCommandTest {
    @Test
    void redirectAliasRunsTheRootCommandAndExposesChildren() throws Exception {
        List<String> calls = new ArrayList<>();
        CommandDispatcher<String> dispatcher = new CommandDispatcher<>();
        LiteralCommandNode<String> slot = dispatcher.register(
            LiteralArgumentBuilder.<String>literal("slot")
                .executes(context -> {
                    calls.add("root");
                    return 1;
                })
                .then(LiteralArgumentBuilder.<String>literal("info")
                    .executes(context -> {
                        calls.add("info");
                        return 2;
                    })
                )
        );
        dispatcher.register(
            LiteralArgumentBuilder.<String>literal("plot")
                .executes(Objects.requireNonNull(slot.getCommand()))
                .redirect(slot)
        );

        assertEquals(1, dispatcher.execute("plot", "source"));
        assertEquals(List.of("root"), calls);

        calls.clear();
        assertEquals(2, dispatcher.execute("plot info", "source"));
        assertEquals(List.of("info"), calls);
    }
}
