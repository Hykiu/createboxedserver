package fr.hykiuidustries.createboxed.modules;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Set;

public interface Module {
    String id();

    /**
     * Modules obligatoires : erreur au démarrage s'ils manquent.
     */
    default Set<String> dependencies() {
        return Set.of();
    }

    /**
     * Modules optionnels : activés avant celui-ci s'ils existent.
     */
    default Set<String> softDependencies() {
        return Set.of();
    }

    default void defineConfig(ModConfigSpec.Builder builder) {}

    void enable(ModuleContext ctx);

    default void disable() {}
}
