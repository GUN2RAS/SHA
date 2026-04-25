package net.sha.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.sha.api.SHARaycastConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelHologramRaycastMixin {

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void interceptRaycastState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (SHARaycastConfig.IS_RAYCASTING.get()) {
            java.util.List<net.sha.api.HologramProvider> providers = net.sha.api.SHAHologramManager.getIntersectingProviders(new net.minecraft.world.phys.AABB(pos));
            for (net.sha.api.HologramProvider provider : providers) {
                if (provider.providesCollision()) {
                    BlockState spoofed = provider.getSpoofedBlock(pos.getX(), pos.getY(), pos.getZ());
                    if (spoofed != null && !spoofed.isAir()) {
                        cir.setReturnValue(spoofed);
                        return;
                    }
                }
            }
        }
    }
}
