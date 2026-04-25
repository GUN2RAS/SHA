package net.sha.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.sha.api.SHAHologramManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class LevelRendererLightMixin {

    @Inject(method = "getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void interceptHologramLight(BlockAndLightGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        java.util.List<net.sha.api.HologramProvider> providers = SHAHologramManager.getIntersectingProviders(
            new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
        );
        if (!providers.isEmpty()) {
            for (int i = 0; i < providers.size(); i++) {
                net.sha.api.HologramProvider provider = providers.get(i);
                if (provider.isActive()) {
                    net.sha.api.HologramBounds bounds = provider.getBounds();
                    if (bounds == null || bounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                        cir.setReturnValue(15728880); 
                        return;
                    }
                }
            }
        }
    }
}
