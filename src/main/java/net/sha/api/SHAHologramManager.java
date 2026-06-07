package net.sha.api;


import net.minecraft.core.BlockPos;

public class SHAHologramManager {
    public interface ClientDelegate {
        void updateSpatialMapClient(java.util.Collection<Long> changedChunks);
        void removeProviderClient(HologramBounds bounds);
        void markAreaDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
        void markRadiusSectionsDirty(BlockPos center, int radiusSections);
        void markRadiusShellDirty(BlockPos center, float minRadius, float maxRadius);
    }

    private static ClientDelegate clientDelegate = null;

    public static void registerClientDelegate(ClientDelegate delegate) {
        clientDelegate = delegate;
    }

    private static final java.util.Map<Long, java.util.List<HologramProvider>> CHUNK_MAP = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.List<HologramProvider> GLOBAL_PROVIDERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static volatile HologramProvider[] FAST_GLOBAL_ARRAY = new HologramProvider[0];
    // тут COW мапа для мультипотока, чтоб содиум не подавился на рендере, писалось в 4 утра
    private static volatile it.unimi.dsi.fastutil.longs.Long2ObjectMap<HologramProvider[]> FAST_CHUNK_MAP_COW = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    public static java.util.function.Predicate<net.minecraft.world.entity.Entity> ignorePredicate = e -> false;
    public static volatile int providerVersion = 0;
    
    // хак ебаный для отключения каллинга
    public static final ThreadLocal<Boolean> verticalCullBypass = ThreadLocal.withInitial(() -> false);

    public static boolean hasHologramsInArea(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        if (!GLOBAL_PROVIDERS.isEmpty()) return true;
        it.unimi.dsi.fastutil.longs.Long2ObjectMap<HologramProvider[]> map = FAST_CHUNK_MAP_COW;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (map.containsKey(net.minecraft.world.level.ChunkPos.pack(x, z))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasHologramsInChunk(int chunkX, int chunkZ) {
        if (!GLOBAL_PROVIDERS.isEmpty()) return true;
        return FAST_CHUNK_MAP_COW.containsKey(net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ));
    }

    public static void updateSpatialMap(HologramProvider provider) {
        providerVersion++;
        
        HologramBounds bounds = provider.getBounds();
        if (bounds == null) {
            GLOBAL_PROVIDERS.remove(provider);
            GLOBAL_PROVIDERS.add(provider);
            FAST_GLOBAL_ARRAY = GLOBAL_PROVIDERS.toArray(new HologramProvider[0]);
            return;
        } else {
            GLOBAL_PROVIDERS.remove(provider);
        }
        
        int minSecX = bounds.minX >> 4;
        int maxSecX = bounds.maxX >> 4;
        int minSecZ = bounds.minZ >> 4;
        int maxSecZ = bounds.maxZ >> 4;
        
        java.util.Set<Long> newChunks = new java.util.HashSet<>();
        for (int x = minSecX; x <= maxSecX; x++) {
            for (int z = minSecZ; z <= maxSecZ; z++) {
                newChunks.add(net.minecraft.world.level.ChunkPos.pack(x, z));
            }
        }

        java.util.Set<Long> changedChunks = new java.util.HashSet<>();

        for (long pos : newChunks) {
            java.util.List<HologramProvider> list = CHUNK_MAP.computeIfAbsent(pos, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
            if (!list.contains(provider)) {
                list.add(provider);
                changedChunks.add(pos);
            }
        }

        for (java.util.Map.Entry<Long, java.util.List<HologramProvider>> entry : CHUNK_MAP.entrySet()) {
            long pos = entry.getKey();
            if (!newChunks.contains(pos) && entry.getValue().contains(provider)) {
                entry.getValue().remove(provider);
                changedChunks.add(pos);
            }
        }

        if (!changedChunks.isEmpty()) {
            rebuildFastMap();
        }

        if (!changedChunks.isEmpty() && clientDelegate != null) {
            clientDelegate.updateSpatialMapClient(changedChunks);
        }
    }

    private static void rebuildFastMap() {
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<HologramProvider[]> newMap = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        for (java.util.Map.Entry<Long, java.util.List<HologramProvider>> entry : CHUNK_MAP.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                java.util.Set<HologramProvider> merged = new java.util.HashSet<>(GLOBAL_PROVIDERS);
                merged.addAll(entry.getValue());
                newMap.put(entry.getKey().longValue(), merged.toArray(new HologramProvider[0]));
            }
        }
        FAST_CHUNK_MAP_COW = newMap;
    }

    public static HologramProvider[] getProvidersForChunk(int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        HologramProvider[] local = FAST_CHUNK_MAP_COW.get(net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ));
        if (local != null) return local;
        return FAST_GLOBAL_ARRAY;
    }

    public static net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getSpoofedBiome(int x, int y, int z) {
        HologramProvider[] providers = getProvidersForChunk(x, y, z);
        for (int i = 0; i < providers.length; i++) {
            HologramProvider p = providers[i];
            if (p.isActive()) {
                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = p.getSpoofedBiome(x, y, z);
                if (biome != null) return biome;
            }
        }
        return null;
    }

    public static void removeProvider(HologramProvider provider) {
        GLOBAL_PROVIDERS.remove(provider);
        FAST_GLOBAL_ARRAY = GLOBAL_PROVIDERS.toArray(new HologramProvider[0]);
        boolean changed = false;
        for (java.util.Map.Entry<Long, java.util.List<HologramProvider>> entry : CHUNK_MAP.entrySet()) {
            if (entry.getValue().remove(provider)) {
                changed = true;
            }
        }
        if (changed) {
            // бля убрал ебучий дебаг || true, кто это вообще тут забыл
            rebuildFastMap();
        }

        HologramBounds bounds = provider.getBounds();
        if (bounds != null && clientDelegate != null) {
            clientDelegate.removeProviderClient(bounds);
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
        if (clientDelegate != null) {
            clientDelegate.markAreaDirty(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public static void markRadiusSectionsDirty(BlockPos center, int radiusSections) {
        if (clientDelegate != null) {
            clientDelegate.markRadiusSectionsDirty(center, radiusSections);
        }
    }

    public static void markRadiusShellDirty(BlockPos center, float minRadius, float maxRadius) {
        if (clientDelegate != null) {
            clientDelegate.markRadiusShellDirty(center, minRadius, maxRadius);
        }
    }
}
