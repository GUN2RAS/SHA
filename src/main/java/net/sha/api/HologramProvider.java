package net.sha.api;

import net.minecraft.world.level.block.state.BlockState;

// Ебал я рот этой системы освещения в майнкрафте, поэтому половина методов тут тупо заглушки.
public interface HologramProvider {
    
    BlockState getSpoofedBlock(int x, int y, int z);

    default net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getSpoofedBiome(int x, int y, int z) {
        return null;
    }

    boolean isActive();

    default boolean forcesEmptyChunkRendering() { 
        return true; 
    }

    default boolean providesCollision() {
        return false;
    }

    default void getSpoofedBlockRange(int originX, int originY, int originZ, BlockState[] blockArray) {
        for (int i = 0; i < 4096; i++) {
            int lx = i & 15;
            int lz = (i >> 4) & 15;
            int ly = (i >> 8) & 15;
            BlockState fakeState = getSpoofedBlock(originX + lx, originY + ly, originZ + lz);
            if (fakeState != null) {
                blockArray[i] = fakeState;
            }
        }
    }

    default HologramBounds getBounds() {
        return null;
    }
}
