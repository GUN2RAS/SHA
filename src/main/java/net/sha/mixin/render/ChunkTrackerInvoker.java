package net.sha.mixin.render;

import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkTracker.class)
public interface ChunkTrackerInvoker {
    // инвокер потому что приватные методы в рендере. классика хуле.
    @Invoker("updateNeighbors")
    void invokeUpdateNeighbors(int x, int z);
}
