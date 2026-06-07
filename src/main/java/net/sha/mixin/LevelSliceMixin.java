package net.sha.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.sha.SHA;
import net.sha.api.HologramProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.state.BlockState;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;

@Mixin(value = LevelSlice.class, remap = false)
public abstract class LevelSliceMixin {
    @Unique


    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true, remap = false)
    private static void forcePrepareForHolograms(Level level, SectionPos pos, net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache cache, CallbackInfoReturnable<Object> cir) {
        if (!SHA.FORCE_SOLID.get()) {
            java.util.List<HologramProvider> intersectingList = net.sha.api.SHAHologramManager.getIntersectingProviders(new net.minecraft.world.phys.AABB(pos.minBlockX(), pos.minBlockY(), pos.minBlockZ(), pos.maxBlockX(), pos.maxBlockY(), pos.maxBlockZ()));
            if (!intersectingList.isEmpty()) {
                // бля ну тут содиум долго выебывался, пришлось форсить флаг FORCE_SOLID через ThreadLocal чтобы пустые чанки рендерились как миленькие, иначе кэш идет по пизде
                SHA.FORCE_SOLID.set(true);
                try {
                    Object result = net.caffeinemc.mods.sodium.client.world.LevelSlice.prepare(level, pos, cache);
                    cir.setReturnValue(result); 
                } finally {
                    SHA.FORCE_SOLID.set(false);
                }
            }
        }
    }

    @Inject(method = "unpackBlockData", at = @At("RETURN"), remap = false)
    private void deepMemoryOverwrite(net.minecraft.world.level.block.state.BlockState[] blockArray, net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext context, net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection section, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        // лида навсегда шпана навсегда
        net.minecraft.core.SectionPos pos = section.getPosition();
        int originX = pos.minBlockX();
        int originY = pos.minBlockY();
        int originZ = pos.minBlockZ();

        HologramProvider[] providers = SHA.getProvidersForChunk(originX, originY, originZ);
        if (providers.length == 0) return;

        BlockState[] originalArray = null;
        if (SHA.debugMode) {
            originalArray = new BlockState[blockArray.length];
            System.arraycopy(blockArray, 0, originalArray, 0, blockArray.length);
        }

        long start = System.nanoTime();
        try {
            for (int pIdx = 0; pIdx < providers.length; pIdx++) {
                HologramProvider provider = providers[pIdx];
                if (provider.isActive()) {
                    provider.getSpoofedBlockRange(originX, originY, originZ, blockArray);
                }
            }
        } finally {
            long duration = System.nanoTime() - start;
            if (SHA.compileTimerCallback != null) {
                SHA.compileTimerCallback.accept(duration);
            }
        }

        if (SHA.debugMode && originalArray != null) {
            BlockState froglight = net.minecraft.world.level.block.Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
            for (int i = 0; i < blockArray.length; i++) {
                if (blockArray[i] != originalArray[i]) {
                    if ((blockArray[i] != null && !blockArray[i].isAir()) || (originalArray[i] != null && !originalArray[i].isAir())) {
                        blockArray[i] = froglight;
                    }
                }
            }
        }
    }
}
