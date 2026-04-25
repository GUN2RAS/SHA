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

import net.caffeinemc.mods.sodium.client.world.LevelSlice;

@Mixin(value = LevelSlice.class, remap = false)
public abstract class LevelSliceMixin {

    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true, remap = false)
    private static void forcePrepareForHolograms(Level level, SectionPos pos, net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache cache, CallbackInfoReturnable<Object> cir) {
        if (!SHA.FORCE_SOLID.get()) {
            java.util.List<HologramProvider> intersectingList = net.sha.api.SHAHologramManager.getIntersectingProviders(new net.minecraft.world.phys.AABB(pos.minBlockX(), pos.minBlockY(), pos.minBlockZ(), pos.maxBlockX(), pos.maxBlockY(), pos.maxBlockZ()));
            if (!intersectingList.isEmpty()) {
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
        net.minecraft.core.SectionPos pos = section.getPosition();
        int originX = pos.minBlockX();
        int originY = pos.minBlockY();
        int originZ = pos.minBlockZ();

        HologramProvider[] providers = SHA.getProvidersForChunk(originX, originY, originZ);
        if (providers.length == 0) return;

        for (int i = 0; i < 4096; i++) {
            int lx = i & 15;
            int lz = (i >> 4) & 15;
            int ly = (i >> 8) & 15;
            int worldX = originX + lx;
            int worldY = originY + ly;
            int worldZ = originZ + lz;

            for (int pIdx = 0; pIdx < providers.length; pIdx++) {
                HologramProvider provider = providers[pIdx];
                if (provider.isActive()) {
                    net.sha.api.HologramBounds bounds = provider.getBounds();
                    if (bounds != null && !bounds.contains(worldX, worldY, worldZ)) continue;

                    net.minecraft.world.level.block.state.BlockState fakeState = provider.getSpoofedBlock(worldX, worldY, worldZ);
                    if (fakeState != null) {
                        blockArray[i] = fakeState;
                        break;
                    }
                }
            }
        }
    }
}
