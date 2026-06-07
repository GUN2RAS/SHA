package net.sha.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.sha.api.HologramBounds;
import net.sha.api.SHAHologramManager;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.sha.mixin.render.ChunkTrackerInvoker;

@Environment(EnvType.CLIENT)
public class SHAHologramManagerClient implements SHAHologramManager.ClientDelegate {

    @Override
    public void updateSpatialMapClient(java.util.Collection<Long> changedChunks) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.isSameThread()) {
            ChunkTracker tracker = ChunkTrackerHolder.get(client.level);
            if (tracker != null) {
                for (long pos : changedChunks) {
                    int x = net.minecraft.world.level.ChunkPos.getX(pos);
                    int z = net.minecraft.world.level.ChunkPos.getZ(pos);
                    ((ChunkTrackerInvoker) tracker).invokeUpdateNeighbors(x, z);
                }
            }
        }
    }

    @Override
    public void removeProviderClient(HologramBounds bounds) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.isSameThread()) {
            int minSecX = bounds.minX >> 4;
            int maxSecX = bounds.maxX >> 4;
            int minSecZ = bounds.minZ >> 4;
            int maxSecZ = bounds.maxZ >> 4;
            ChunkTracker tracker = ChunkTrackerHolder.get(client.level);
            if (tracker != null) {
                for (int x = minSecX - 1; x <= maxSecX + 1; x++) {
                    for (int z = minSecZ - 1; z <= maxSecZ + 1; z++) {
                        ((ChunkTrackerInvoker) tracker).invokeUpdateNeighbors(x, z);
                    }
                }
            }
        }
    }

    @Override
    public void markAreaDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null || client.player == null) return;

        int minSecX = minX >> 4;
        int maxSecX = maxX >> 4;
        int minSecY = minY >> 4;
        int maxSecY = maxY >> 4;
        int minSecZ = minZ >> 4;
        int maxSecZ = maxZ >> 4;

        for (int x = minSecX; x <= maxSecX; x++) {
            for (int y = minSecY; y <= maxSecY; y++) {
                for (int z = minSecZ; z <= maxSecZ; z++) {
                    client.levelRenderer.setSectionDirtyWithNeighbors(x, y, z);
                }
            }
        }
    }

    @Override
    public void markRadiusSectionsDirty(BlockPos center, int radiusSections) {
        if (center == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null || client.player == null) return;

        int centerSecX = center.getX() >> 4;
        int centerSecY = center.getY() >> 4;
        int centerSecZ = center.getZ() >> 4;

        int renderDist = client.options.renderDistance().get();
        int playerSecX = client.player.getBlockX() >> 4;
        int playerSecZ = client.player.getBlockZ() >> 4;

        int minSecX = Math.max(centerSecX - radiusSections, playerSecX - renderDist);
        int maxSecX = Math.min(centerSecX + radiusSections, playerSecX + renderDist);
        int minSecY = centerSecY - radiusSections;
        int maxSecY = centerSecY + radiusSections;
        int minSecZ = Math.max(centerSecZ - radiusSections, playerSecZ - renderDist);
        int maxSecZ = Math.min(centerSecZ + radiusSections, playerSecZ + renderDist);

        if (minSecX > maxSecX || minSecY > maxSecY || minSecZ > maxSecZ) return;

        for (int x = minSecX; x <= maxSecX; x++) {
            for (int y = minSecY; y <= maxSecY; y++) {
                for (int z = minSecZ; z <= maxSecZ; z++) {
                    if (x == centerSecX - radiusSections || x == centerSecX + radiusSections ||
                        y == centerSecY - radiusSections || y == centerSecY + radiusSections ||
                        z == centerSecZ - radiusSections || z == centerSecZ + radiusSections) {
                        client.levelRenderer.setSectionDirtyWithNeighbors(x, y, z);
                    }
                }
            }
        }
    }

    @Override
    public void markRadiusShellDirty(BlockPos center, float minRadius, float maxRadius) {
        if (center == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null || client.player == null) return;

        int minSecX = (int) Math.floor((center.getX() - maxRadius) / 16.0);
        int maxSecX = (int) Math.ceil((center.getX() + maxRadius) / 16.0);
        int minSecY = (int) Math.floor((center.getY() - maxRadius) / 16.0);
        int maxSecY = (int) Math.ceil((center.getY() + maxRadius) / 16.0);
        if (client.level != null) {
            minSecY = Math.max(minSecY, client.level.getMinY() >> 4);
            maxSecY = Math.min(maxSecY, (client.level.getMaxY() - 1) >> 4);
        }
        int minSecZ = (int) Math.floor((center.getZ() - maxRadius) / 16.0);
        int maxSecZ = (int) Math.ceil((center.getZ() + maxRadius) / 16.0);

        int renderDist = client.options.renderDistance().get();
        int playerSecX = client.player.getBlockX() >> 4;
        int playerSecZ = client.player.getBlockZ() >> 4;

        minSecX = Math.max(minSecX, playerSecX - renderDist);
        maxSecX = Math.min(maxSecX, playerSecX + renderDist);
        minSecZ = Math.max(minSecZ, playerSecZ - renderDist);
        maxSecZ = Math.min(maxSecZ, playerSecZ + renderDist);

        float minRSq = minRadius * minRadius;
        float maxRSq = maxRadius * maxRadius;

        net.sha.api.SHARenderBridge.beginBatch();
        for (int x = minSecX; x <= maxSecX; x++) {
            for (int y = minSecY; y <= maxSecY; y++) {
                for (int z = minSecZ; z <= maxSecZ; z++) {
                    int chunkMinX = x * 16;
                    int chunkMaxX = chunkMinX + 15;
                    int chunkMinY = y * 16;
                    int chunkMaxY = chunkMinY + 15;
                    int chunkMinZ = z * 16;
                    int chunkMaxZ = chunkMinZ + 15;

                    double closestDistSq = 0;
                    double furthestDistSq = 0;

                    if (center.getX() < chunkMinX) {
                        double dx = chunkMinX - center.getX();
                        closestDistSq += dx * dx;
                    } else if (center.getX() > chunkMaxX) {
                        double dx = center.getX() - chunkMaxX;
                        closestDistSq += dx * dx;
                    }

                    if (center.getY() < chunkMinY) {
                        double dy = chunkMinY - center.getY();
                        closestDistSq += dy * dy;
                    } else if (center.getY() > chunkMaxY) {
                        double dy = center.getY() - chunkMaxY;
                        closestDistSq += dy * dy;
                    }

                    if (center.getZ() < chunkMinZ) {
                        double dz = chunkMinZ - center.getZ();
                        closestDistSq += dz * dz;
                    } else if (center.getZ() > chunkMaxZ) {
                        double dz = center.getZ() - chunkMaxZ;
                        closestDistSq += dz * dz;
                    }

                    double dx1 = chunkMinX - center.getX();
                    double dx2 = chunkMaxX - center.getX();
                    furthestDistSq += Math.max(dx1 * dx1, dx2 * dx2);

                    double dy1 = chunkMinY - center.getY();
                    double dy2 = chunkMaxY - center.getY();
                    furthestDistSq += Math.max(dy1 * dy1, dy2 * dy2);

                    double dz1 = chunkMinZ - center.getZ();
                    double dz2 = chunkMaxZ - center.getZ();
                    furthestDistSq += Math.max(dz1 * dz1, dz2 * dz2);

                    if (closestDistSq <= maxRSq && furthestDistSq >= minRSq) {
                        client.levelRenderer.setSectionDirtyWithNeighbors(x, y, z);
                        net.sha.api.SHARenderBridge.forceRebuildSection(x, y, z);
                    }
                }
            }
        }
        net.sha.api.SHARenderBridge.endBatch();
    }
}
