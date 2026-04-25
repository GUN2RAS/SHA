package net.sha.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.BitSet;
import java.util.List;

public class SHACataclysm {

    public interface BlockProvider {
        
        BlockState getTargetState(BlockPos pos);
    }

    public static void injectChunk(ServerLevel level, ChunkPos cp, BlockProvider blockProvider) {
        LevelChunk chunk = level.getChunk(cp.x(), cp.z());
        ThreadedLevelLightEngine lightEngine = (ThreadedLevelLightEngine) level.getLightEngine();
        
        int minBuildHeight = level.getMinY();
        int maxBuildHeight = level.getMaxY();
        
        boolean modified = false;
        BitSet modifiedSections = new BitSet();

        for (int sectionY = minBuildHeight >> 4; sectionY < maxBuildHeight >> 4; sectionY++) {
            int sectionIndex = chunk.getSectionIndex(sectionY * 16);
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;
            
            LevelChunkSection section = chunk.getSection(sectionIndex);
            boolean sectionModified = false;

            for (int localY = 0; localY < 16; localY++) {
                int worldY = sectionY * 16 + localY;
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int worldX = cp.getMinBlockX() + localX;
                        int worldZ = cp.getMinBlockZ() + localZ;
                        
                        BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                        BlockState targetState = blockProvider.getTargetState(pos);
                        
                        if (targetState == null) continue;
                        
                        BlockState oldState = section.getBlockState(localX, localY, localZ);
                        if (oldState == targetState) continue;

                        if (oldState.hasBlockEntity()) {
                            BlockEntity be = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
                            if (be != null) {
                                chunk.removeBlockEntity(pos);
                            }
                        }
                        
                        if (targetState.hasBlockEntity()) {
                            if (targetState.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock) {
                                BlockEntity newBe = entityBlock.newBlockEntity(pos, targetState);
                                if (newBe != null) {
                                    chunk.setBlockEntity(newBe);
                                }
                            }
                        }

                        section.setBlockState(localX, localY, localZ, targetState, false);

                        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING).update(localX, worldY, localZ, targetState);
                        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).update(localX, worldY, localZ, targetState);
                        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR).update(localX, worldY, localZ, targetState);
                        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).update(localX, worldY, localZ, targetState);

                        if (!targetState.getFluidState().isEmpty()) {
                            level.scheduleTick(pos, targetState.getFluidState().getType(), targetState.getFluidState().getType().getTickDelay(level));
                        }
                        
                        sectionModified = true;
                        modified = true;
                    }
                }
            }
            
            if (sectionModified) {
                modifiedSections.set(sectionIndex);
                lightEngine.updateSectionStatus(SectionPos.of(cp, sectionY), section.hasOnlyAir());
            }
        }
        
        if (modified) {
            chunk.markUnsaved();

            lightEngine.lightChunk(chunk, false).thenAcceptAsync(litChunk -> {
                
                ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket((LevelChunk)litChunk, lightEngine, null, null);
                
                List<ServerPlayer> trackingPlayers = level.getChunkSource().chunkMap.getPlayers(cp, false);
                for (ServerPlayer player : trackingPlayers) {
                    player.connection.send(packet);
                }
            }, level.getServer());

        }
    }
}
