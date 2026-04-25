package net.sha.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.sha.api.SHAHologramManager;
import net.sha.api.HologramProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerPlayerGameMode.class)
public class BlockBreakInterceptorMixin {

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void preventHologramBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        List<HologramProvider> providers = SHAHologramManager.getIntersectingProviders(
            new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
        );
        if (!providers.isEmpty()) {
            for (int i = 0; i < providers.size(); i++) {
                HologramProvider provider = providers.get(i);
                if (provider.isActive() && provider.providesCollision()) {
                    net.sha.api.HologramBounds bounds = provider.getBounds();
                    if (bounds == null || bounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                        if (provider.getSpoofedBlock(pos.getX(), pos.getY(), pos.getZ()) != null) {
                            
                            cir.setReturnValue(false);
                            return;
                        }
                    }
                }
            }
        }
    }
}
