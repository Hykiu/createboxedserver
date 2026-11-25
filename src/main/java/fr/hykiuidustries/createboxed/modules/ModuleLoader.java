package fr.hykiuidustries.createboxed.modules;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ModuleLoader {
    private final ServiceRegistry services;
    private final Logger logger;
    private final Path baseFolder;

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final List<Module> enabled = new ArrayList<>();
    private final Map<String, ModConfigSpec.BooleanValue> enabledFlags = new HashMap<>();

    public ModuleLoader(ServiceRegistry services, Logger logger, Path baseFolder) {
        this.services = services;
        this.logger = logger;
        this.baseFolder = baseFolder;
    }

    public void register(Module module) {
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalStateException("Module en double : " + module.id());
        }
    }

    public ModConfigSpec buildConfig() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Active ou désactive chaque module").push("modules");
        for (Module m : modules.values())
            enabledFlags.put(m.id(), b.define(m.id(), true));
        b.pop();

        // Chaque module déclare ses propres options
        for (Module m : modules.values())
            m.defineConfig(b);

        return b.build();
    }

    private boolean isEnabled(Module m) {
        ModConfigSpec.BooleanValue flag = enabledFlags.get(m.id());
        if (flag == null) {
            throw new IllegalStateException(
                "buildConfig() n'a pas été appelé pour le module " + m.id());
        }
        return flag.get();
    }

    public void enableAll() {
        for (Module m : resolveOrder()) {
            if (!isEnabled(m)) {
                logger.info("Module désactivé par la config : {}", m.id());
                continue;
            }

            try {
                m.enable(contextFor(m));
                enabled.add(m);
                logger.info("Module activé : {}", m.id());
            } catch (Exception e) {
                logger.error("Échec d'activation de {}", m.id(), e);
                disableAll();   // rollback propre
                throw new RuntimeException("Impossible d'activer le module " + m.id(), e);
            }
        }
    }

    public void disableAll() {
        for (int i = enabled.size() - 1; i >= 0; i--) {
            Module m = enabled.get(i);
            try {
                m.disable();
                logger.info("Module désactivé : {}", m.id());
            } catch (Exception e) {
                logger.error("Erreur lors de la désactivation de {}", m.id(), e);
            }
        }
        enabled.clear();
        services.clear();
    }

    private ModuleContext contextFor(Module m) {
        Path folder = baseFolder.resolve(m.id());
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer " + folder, e);
        }
        return new ModuleContext(services, logger, folder);
    }

    private List<Module> resolveOrder() {
        Map<String, Set<String>> pending = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();

        for (Module m : modules.values()) {
            Set<String> deps = new HashSet<>();

            for (String dep : m.dependencies()) {
                Module depModule = modules.get(dep);
                if (depModule == null) {
                    throw new IllegalStateException(
                        m.id() + " requiert le module absent : " + dep);
                }
                if (isEnabled(m) && !isEnabled(depModule)) {
                    throw new IllegalStateException(
                        m.id() + " est activé mais dépend de '" + dep
                            + "' qui est désactivé dans la config");
                }
                deps.add(dep);
            }

            for (String soft : m.softDependencies())
                if (modules.containsKey(soft)) deps.add(soft);

            pending.put(m.id(), deps);
            for (String dep : deps)
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(m.id());
        }

        Deque<String> ready = new ArrayDeque<>();
        for (String id : modules.keySet())
            if (pending.get(id).isEmpty()) ready.add(id);

        List<Module> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            order.add(modules.get(id));

            for (String child : dependents.getOrDefault(id, List.of())) {
                Set<String> remaining = pending.get(child);
                remaining.remove(id);
                if (remaining.isEmpty()) ready.add(child);
            }
        }

        if (order.size() != modules.size()) {
            Set<String> cyclic = new LinkedHashSet<>(modules.keySet());
            order.forEach(m -> cyclic.remove(m.id()));
            throw new IllegalStateException("Dépendance circulaire entre : " + cyclic);
        }
        return order;
    }
}