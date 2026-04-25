package net.sha.api;

import net.minecraft.world.level.block.state.BlockState;

public interface HologramProvider {
    
    BlockState getSpoofedBlock(int x, int y, int z);

    boolean isActive();

    default boolean forcesEmptyChunkRendering() { 
        return true; 
    }

    default boolean providesCollision() {
        return false;
    }

    default HologramBounds getBounds() {
        return null;
    }
}
