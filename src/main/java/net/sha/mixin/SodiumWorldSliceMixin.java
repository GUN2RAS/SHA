package net.sha.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.sha.SHA;
import net.sha.api.HologramProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public class SodiumWorldSliceMixin {

    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true, remap = false)
    private void getSpoofedBlockStatePos(net.minecraft.core.BlockPos pos, CallbackInfoReturnable<Object> cir) {
        HologramProvider[] providers = SHA.getProvidersForChunk(pos.getX(), pos.getY(), pos.getZ());
        if (providers.length == 0) return;
        
        for (int i = 0; i < providers.length; i++) {
            HologramProvider provider = providers[i];
            if (provider.isActive()) {
                net.sha.api.HologramBounds bounds = provider.getBounds();
                if (bounds != null && !bounds.contains(pos.getX(), pos.getY(), pos.getZ())) continue;
                
                BlockState fakeState = provider.getSpoofedBlock(pos.getX(), pos.getY(), pos.getZ());
                if (fakeState != null) {
                    cir.setReturnValue(fakeState);
                    return;
                }
            }
        }
    }

    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true, remap = false)
    private void getSpoofedBlockStateInt(int x, int y, int z, CallbackInfoReturnable<Object> cir) {
        HologramProvider[] providers = SHA.getProvidersForChunk(x, y, z);
        if (providers.length == 0) return;
        
        for (int i = 0; i < providers.length; i++) {
            HologramProvider provider = providers[i];
            if (provider.isActive()) {
                net.sha.api.HologramBounds bounds = provider.getBounds();
                if (bounds != null && !bounds.contains(x, y, z)) continue;
                
                BlockState fakeState = provider.getSpoofedBlock(x, y, z);
                if (fakeState != null) {
                    cir.setReturnValue(fakeState);
                    return;
                }
            }
        }
    }

    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true, remap = false)
    private void getSpoofedBrightness(net.minecraft.world.level.LightLayer lightType, net.minecraft.core.BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        HologramProvider[] providers = SHA.getProvidersForChunk(pos.getX(), pos.getY(), pos.getZ());
        if (providers.length == 0) return;
        
        for (int i = 0; i < providers.length; i++) {
            HologramProvider provider = providers[i];
            if (provider.isActive()) {
                net.sha.api.HologramBounds bounds = provider.getBounds();
                if (bounds != null && !bounds.contains(pos.getX(), pos.getY(), pos.getZ())) continue;
                
                BlockState fakeState = provider.getSpoofedBlock(pos.getX(), pos.getY(), pos.getZ());
                if (fakeState != null) {
                    cir.setReturnValue(15);
                    return;
                }
            }
        }
    }

    @Inject(method = "getRawBrightness", at = @At("HEAD"), cancellable = true, remap = false)
    private void getSpoofedRawBrightness(net.minecraft.core.BlockPos pos, int ambientDarkness, CallbackInfoReturnable<Integer> cir) {
        HologramProvider[] providers = SHA.getProvidersForChunk(pos.getX(), pos.getY(), pos.getZ());
        if (providers.length == 0) return;
        
        for (int i = 0; i < providers.length; i++) {
            HologramProvider provider = providers[i];
            if (provider.isActive()) {
                net.sha.api.HologramBounds bounds = provider.getBounds();
                if (bounds != null && !bounds.contains(pos.getX(), pos.getY(), pos.getZ())) continue;

                BlockState fakeState = provider.getSpoofedBlock(pos.getX(), pos.getY(), pos.getZ());
                if (fakeState != null) {
                    cir.setReturnValue(15);
                    return;
                }
            }
        }
    }
}
