package fr.hykiuidustries.createboxed.modules;

import org.slf4j.Logger;

import java.nio.file.Path;

public record ModuleContext(
    ServiceRegistry services,
    Logger logger,
    Path dataFolder
) {}
