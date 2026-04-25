package net.sha.api;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class SHAHologramManager {
    private static final java.util.Map<Long, java.util.List<HologramProvider>> CHUNK_MAP = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.List<HologramProvider> GLOBAL_PROVIDERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static volatile HologramProvider[] FAST_GLOBAL_ARRAY = new HologramProvider[0];
    private static final java.util.Map<Long, HologramProvider[]> FAST_CHUNK_MAP = new java.util.concurrent.ConcurrentHashMap<>();

    public static java.util.function.Predicate<net.minecraft.world.entity.Entity> ignorePredicate = e -> false;
    public static volatile int providerVersion = 0;

    public static void updateSpatialMap(HologramProvider provider) {
        providerVersion++;
        GLOBAL_PROVIDERS.remove(provider);

        for (java.util.List<HologramProvider> list : CHUNK_MAP.values()) {
            list.remove(provider);
        }
        
        HologramBounds bounds = provider.getBounds();
        if (bounds == null) {
            GLOBAL_PROVIDERS.add(provider);
            FAST_GLOBAL_ARRAY = GLOBAL_PROVIDERS.toArray(new HologramProvider[0]);
            return;
        }
        
        int minSecX = bounds.minX >> 4;
        int maxSecX = bounds.maxX >> 4;
        int minSecZ = bounds.minZ >> 4;
        int maxSecZ = bounds.maxZ >> 4;
        for (int x = minSecX; x <= maxSecX; x++) {
            for (int z = minSecZ; z <= maxSecZ; z++) {
                long pos = net.minecraft.world.level.ChunkPos.pack(x, z);
                java.util.List<HologramProvider> list = CHUNK_MAP.computeIfAbsent(pos, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
                if (!list.contains(provider)) list.add(provider);
                rebuildFastChunk(pos, list);
            }
        }
    }

    private static void rebuildFastChunk(long pos, java.util.List<HologramProvider> list) {
        if (list.isEmpty()) {
            FAST_CHUNK_MAP.remove(pos);
            return;
        }
        java.util.Set<HologramProvider> merged = new java.util.HashSet<>(GLOBAL_PROVIDERS);
        merged.addAll(list);
        FAST_CHUNK_MAP.put(pos, merged.toArray(new HologramProvider[0]));
    }

    public static HologramProvider[] getProvidersForChunk(int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        HologramProvider[] local = FAST_CHUNK_MAP.get(net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ));
        if (local != null) return local;
        return FAST_GLOBAL_ARRAY;
    }

    public static void removeProvider(HologramProvider provider) {
        GLOBAL_PROVIDERS.remove(provider);
        FAST_GLOBAL_ARRAY = GLOBAL_PROVIDERS.toArray(new HologramProvider[0]);
        for (java.util.Map.Entry<Long, java.util.List<HologramProvider>> entry : CHUNK_MAP.entrySet()) {
            if (entry.getValue().remove(provider)) {
                rebuildFastChunk(entry.getKey(), entry.getValue());
            }
        }
    }

    public static java.util.List<HologramProvider> getIntersectingProviders(net.minecraft.world.phys.AABB aabb) {
        int minX = net.minecraft.util.Mth.floor(aabb.minX - 1.0E-7) >> 4;
        int maxX = net.minecraft.util.Mth.floor(aabb.maxX + 1.0E-7) >> 4;
        int minZ = net.minecraft.util.Mth.floor(aabb.minZ - 1.0E-7) >> 4;
        int maxZ = net.minecraft.util.Mth.floor(aabb.maxZ + 1.0E-7) >> 4;
        
        boolean hasLocal = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (CHUNK_MAP.containsKey(net.minecraft.world.level.ChunkPos.pack(x, z))) {
                    hasLocal = true;
                    break;
                }
            }
            if (hasLocal) break;
        }

        if (GLOBAL_PROVIDERS.isEmpty() && !hasLocal) {
            return java.util.Collections.emptyList();
        }

        java.util.Set<HologramProvider> active = new java.util.HashSet<>();
        if (!GLOBAL_PROVIDERS.isEmpty()) {
            active.addAll(GLOBAL_PROVIDERS);
        }
        
        if (hasLocal) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    long pos = net.minecraft.world.level.ChunkPos.pack(x, z);
                    java.util.List<HologramProvider> list = CHUNK_MAP.get(pos);
                    if (list != null) {
                        active.addAll(list);
                    }
                }
            }
        }
        return new java.util.ArrayList<>(active);
    }

    public static void markAreaDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null || client.player == null) return;
        
        int minSecX = minX >> 4;
        int maxSecX = maxX >> 4;
        int minSecY = Math.max((minY >> 4), -4);
        int maxSecY = Math.min((maxY >> 4), 20);
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

    public static void markRadiusSectionsDirty(BlockPos center, int radiusSections) {
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
        int minSecY = Math.max(centerSecY - radiusSections, -4);
        int maxSecY = Math.min(centerSecY + radiusSections, 20);
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

    public static void markRadiusShellDirty(BlockPos center, float minRadius, float maxRadius) {
        if (center == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null || client.player == null) return;

        int minSecX = (int) Math.floor((center.getX() - maxRadius) / 16.0);
        int maxSecX = (int) Math.ceil((center.getX() + maxRadius) / 16.0);
        int minSecY = (int) Math.floor((center.getY() - maxRadius) / 16.0);
        int maxSecY = (int) Math.ceil((center.getY() + maxRadius) / 16.0);
        int minSecZ = (int) Math.floor((center.getZ() - maxRadius) / 16.0);
        int maxSecZ = (int) Math.ceil((center.getZ() + maxRadius) / 16.0);

        minSecY = Math.max(minSecY, -4);
        maxSecY = Math.min(maxSecY, 20);

        int renderDist = client.options.renderDistance().get();
        int playerSecX = client.player.getBlockX() >> 4;
        int playerSecZ = client.player.getBlockZ() >> 4;

        minSecX = Math.max(minSecX, playerSecX - renderDist);
        maxSecX = Math.min(maxSecX, playerSecX + renderDist);
        minSecZ = Math.max(minSecZ, playerSecZ - renderDist);
        maxSecZ = Math.min(maxSecZ, playerSecZ + renderDist);

        float minRSq = minRadius * minRadius;
        float maxRSq = maxRadius * maxRadius;

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

                    if (center.getX() < chunkMinX) closestDistSq += Math.pow(chunkMinX - center.getX(), 2);
                    else if (center.getX() > chunkMaxX) closestDistSq += Math.pow(center.getX() - chunkMaxX, 2);
                    
                    if (center.getY() < chunkMinY) closestDistSq += Math.pow(chunkMinY - center.getY(), 2);
                    else if (center.getY() > chunkMaxY) closestDistSq += Math.pow(center.getY() - chunkMaxY, 2);
                    
                    if (center.getZ() < chunkMinZ) closestDistSq += Math.pow(chunkMinZ - center.getZ(), 2);
                    else if (center.getZ() > chunkMaxZ) closestDistSq += Math.pow(center.getZ() - chunkMaxZ, 2);

                    double dxMin = Math.pow(chunkMinX - center.getX(), 2);
                    double dxMax = Math.pow(chunkMaxX - center.getX(), 2);
                    furthestDistSq += Math.max(dxMin, dxMax);
                    
                    double dyMin = Math.pow(chunkMinY - center.getY(), 2);
                    double dyMax = Math.pow(chunkMaxY - center.getY(), 2);
                    furthestDistSq += Math.max(dyMin, dyMax);
                    
                    double dzMin = Math.pow(chunkMinZ - center.getZ(), 2);
                    double dzMax = Math.pow(chunkMaxZ - center.getZ(), 2);
                    furthestDistSq += Math.max(dzMin, dzMax);

                    if (closestDistSq <= maxRSq && furthestDistSq >= minRSq) {
                        client.levelRenderer.setSectionDirtyWithNeighbors(x, y, z);
                    }
                }
            }
        }
    }
}
