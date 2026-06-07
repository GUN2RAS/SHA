package net.sha.mixin.render;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.world.level.ChunkPos;
import net.sha.api.SHAHologramManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkTracker.class)
public abstract class ChunkTrackerMixin {

    @Redirect(
        method = "updateMerged",
        at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2IntOpenHashMap;get(J)I", remap = false)
    )
    private int sha$overrideChunkFlags(Long2IntOpenHashMap instance, long key) {
        // форсим флаг 3 (видимый чанк) если там есть голограмма, иначе содиум тупо не рендерит нихуя в пустых чанках.
        int originalFlags = instance.get(key);
        if (originalFlags != 3) {
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            if (SHAHologramManager.hasHologramsInChunk(x, z)) {
                return 3;
            }
        }
        return originalFlags;
    }
}
