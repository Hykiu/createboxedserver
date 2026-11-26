package fr.hykiuidustries.createboxed.plot.geometry;

import fr.hykiuidustries.createboxed.plot.data.PlotEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a team's teleport point to a block the player can actually stand on.
 * The saved position may have been dug out since it was set, so it is checked again
 * on every teleport and repaired automatically.
 */
@NullMarked
public final class PlotSpawn {
    /** Search one chunk around the requested point before falling back to a platform. */
    private static final int SEARCH_RADIUS = 16;

    private PlotSpawn() {
    }

    public record Resolution(
        BlockPos position,
        boolean adjusted,
        boolean platformCreated
    ) {
    }

    public static boolean isOutside(PlotEntry plot, BlockPos pos) {
        int minX = minX(plot);
        int minZ = minZ(plot);
        return pos.getX() < minX || pos.getX() >= minX + PlotGeometry.PLOT_BLOCKS
               || pos.getZ() < minZ || pos.getZ() >= minZ + PlotGeometry.PLOT_BLOCKS
               || pos.getY() < PlotGeometry.MIN_Y + 1
               || pos.getY() > PlotGeometry.MAX_Y + 1;
    }

    /** True when the block below is solid and the two blocks above are clear. */
    public static boolean isValid(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockPos above = pos.above();
        BlockState support = level.getBlockState(below);
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(above);
        return !support.getCollisionShape(level, below).isEmpty()
               && feet.getCollisionShape(level, pos).isEmpty()
               && head.getCollisionShape(level, above).isEmpty();
    }

    /**
     * Finds the closest valid standing position: the requested block first, then the
     * same column, then every nearby column in the surrounding chunk. A bedrock
     * platform is created only when no valid spot remains there at all.
     */
    public static Resolution resolve(ServerLevel level, PlotEntry plot, BlockPos desired) {
        BlockPos start = clamp(plot, desired);
        if (isValid(level, start)) {
            return new Resolution(start, !start.equals(desired), false);
        }

        BlockPos sameColumn = searchColumn(level, start.getX(), start.getZ(), start.getY());
        if (sameColumn != null) {
            return new Resolution(sameColumn, true, false);
        }

        int minX = minX(plot);
        int minZ = minZ(plot);
        int centerX = start.getX();
        int centerZ = start.getZ();
        for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
            BlockPos best = null;
            long bestDistance = Long.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (x < minX || x >= minX + PlotGeometry.PLOT_BLOCKS
                        || z < minZ || z >= minZ + PlotGeometry.PLOT_BLOCKS) {
                        continue;
                    }
                    BlockPos neighbour = searchColumn(level, x, z, start.getY());
                    if (neighbour == null) {
                        continue;
                    }
                    int dy = neighbour.getY() - start.getY();
                    long distance = (long) dx * dx + (long) dz * dz + (long) dy * dy;
                    if (distance < bestDistance) {
                        best = neighbour;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) {
                return new Resolution(best, true, false);
            }
        }

        return createPlatform(level, plot, centerX, centerZ);
    }

    private static @Nullable BlockPos searchColumn(
        ServerLevel level,
        int x,
        int z,
        int startY
    ) {
        int top = Math.min(startY, PlotGeometry.MAX_Y + 1);
        for (int y = top; y >= PlotGeometry.MIN_Y + 1; y--) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (isValid(level, candidate)) {
                return candidate;
            }
        }
        for (int y = top + 1; y <= PlotGeometry.MAX_Y + 1; y++) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (isValid(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Resolution createPlatform(
        ServerLevel level,
        PlotEntry plot,
        int centerX,
        int centerZ
    ) {
        int x = Math.clamp(centerX, minX(plot), minX(plot) + PlotGeometry.PLOT_BLOCKS - 2);
        int z = Math.clamp(centerZ, minZ(plot), minZ(plot) + PlotGeometry.PLOT_BLOCKS - 2);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                level.setBlock(
                    new BlockPos(x + dx, PlotGeometry.MIN_Y, z + dz),
                    Blocks.BEDROCK.defaultBlockState(),
                    3
                );
            }
        }
        return new Resolution(new BlockPos(x, PlotGeometry.MIN_Y + 1, z), true, true);
    }

    private static BlockPos clamp(PlotEntry plot, BlockPos desired) {
        int x = Math.clamp(desired.getX(), minX(plot), minX(plot) + PlotGeometry.PLOT_BLOCKS - 1);
        int z = Math.clamp(desired.getZ(), minZ(plot), minZ(plot) + PlotGeometry.PLOT_BLOCKS - 1);
        int y = Math.clamp(desired.getY(), PlotGeometry.MIN_Y + 1, PlotGeometry.MAX_Y + 1);
        return new BlockPos(x, y, z);
    }

    private static int minX(PlotEntry plot) {
        return plot.originX() * 16;
    }

    private static int minZ(PlotEntry plot) {
        return plot.originZ() * 16;
    }
}
