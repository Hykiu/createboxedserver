package fr.hykiuidustries.createboxed;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue GAP = BUILDER.comment(
        "Number of chunks between the edges of two neighbouring plots. Frozen on first plot creation."
    ).defineInRange("gap", 12, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MIN_SPAWN_DISTANCE = BUILDER.comment(
        "Minimum distance in chunks from the spawn anchor to the nearest edge of a new plot."
    ).defineInRange("minSpawnDistance", 32, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CHUNKS_PER_TICK = BUILDER.comment(
        "Maximum number of plot chunks filled per server tick."
    ).defineInRange("chunksPerTick", 2, 1, 64);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
