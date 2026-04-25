package net.sha;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.sha.api.HologramProvider;
import net.sha.api.entity.VirtualStructureEntity;
import net.sha.api.network.VirtualStructureSyncPayload;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.sha.client.render.entity.VirtualStructureRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SHA implements ClientModInitializer {
    private static final Map<Identifier, HologramProvider> PROVIDERS = new ConcurrentHashMap<>();

    public static final Map<Integer, byte[]> PENDING_MATRICES = new ConcurrentHashMap<>();
    
    public static int CLIENT_WORLD_ID = -1;
    public static final java.util.Set<net.minecraft.core.BlockPos> MAPPED_TERRAIN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onInitializeClient() {
        
        CLIENT_WORLD_ID = net.sha.api.physics.RapierEngine.createWorld();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused() && CLIENT_WORLD_ID != -1) {
                try {
                    net.sha.api.physics.RapierEngine.step(CLIENT_WORLD_ID);
                    net.sha.api.physics.RapierEngine.step(CLIENT_WORLD_ID);
                    net.sha.api.physics.RapierEngine.step(CLIENT_WORLD_ID);
                } catch (Exception e) {}
            }
        });

        EntityRendererRegistry.register(SHAMain.VIRTUAL_STRUCTURE_ENTITY, VirtualStructureRenderer::new);
        
        ClientPlayNetworking.registerGlobalReceiver(VirtualStructureSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Entity entity = context.client().level.getEntity(payload.entityId());
                if (entity instanceof VirtualStructureEntity vse) {
                    vse.applyCompressedMatrix(payload.compressedMatrix());
                } else {
                    
                    PENDING_MATRICES.put(payload.entityId(), payload.compressedMatrix());
                }
            });
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(() -> {
                net.sha.api.SHAMirageManager.cleanUp();
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(net.sha.api.network.S2C_VirtualBlockUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Entity entity = context.client().level.getEntity(payload.entityId());
                if (entity instanceof VirtualStructureEntity vse) {
                    net.minecraft.world.level.block.state.BlockState newState = net.minecraft.world.level.block.Block.stateById(payload.stateId());
                    vse.setBlock(payload.pos(), newState);
                }
            });
        });

    }

    public static void attemptPendingMatrixSync(VirtualStructureEntity entity) {
        byte[] data = PENDING_MATRICES.remove(entity.getId());
        if (data != null) {
            entity.applyCompressedMatrix(data);
        }
    }

    public static void registerProvider(Identifier id, HologramProvider provider) {
        PROVIDERS.put(id, provider);
        net.sha.api.SHAHologramManager.updateSpatialMap(provider);
    }
    
    public static void unregisterProvider(Identifier id) {
        HologramProvider p = PROVIDERS.remove(id);
        if (p != null) net.sha.api.SHAHologramManager.removeProvider(p);
    }

    public static final ThreadLocal<HologramProvider[]> ACTIVE_SECTION_PROVIDERS = ThreadLocal.withInitial(() -> new HologramProvider[0]);
    public static final ThreadLocal<Long> LAST_CHUNK_POS = ThreadLocal.withInitial(() -> Long.MIN_VALUE);
    public static final ThreadLocal<Boolean> FORCE_SOLID = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Integer> LAST_GENERATION = ThreadLocal.withInitial(() -> -1);

    public static HologramProvider[] getProvidersForChunk(int x, int y, int z) {
        int cx = x >> 4;
        int cy = y >> 4;
        int cz = z >> 4;
        long chunkHash = ((long)cx & 0x3FFFFFL) | (((long)cz & 0x3FFFFFL) << 22) | (((long)cy & 0xFFFFFL) << 44);
        int currentGen = net.sha.api.SHAHologramManager.providerVersion;

        if (LAST_CHUNK_POS.get() != chunkHash || LAST_GENERATION.get() != currentGen) {
            LAST_CHUNK_POS.set(chunkHash);
            LAST_GENERATION.set(currentGen);
            java.util.List<HologramProvider> intersecting = net.sha.api.SHAHologramManager.getIntersectingProviders(
                new net.minecraft.world.phys.AABB(cx * 16, cy * 16, cz * 16, cx * 16 + 16, cy * 16 + 16, cz * 16 + 16)
            );
            ACTIVE_SECTION_PROVIDERS.set(intersecting.toArray(new HologramProvider[0]));
        }
        return ACTIVE_SECTION_PROVIDERS.get();
    }
}
