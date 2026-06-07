package net.sha.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller.class, remap = false)
public class OcclusionCullerMixin {
    @Inject(method = "isWithinRenderDistance", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sha$checkHologramDistance(net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform camera, net.caffeinemc.mods.sodium.client.render.chunk.RenderSection section, float maxDistance, CallbackInfoReturnable<Boolean> cir) {
        // костыль для обхода ебаного куллинга, без него фризит как сука или отсекаются нужные чанки. Форсим тру, пусть рендерит всё
        if (net.sha.api.SHAHologramManager.hasHologramsInChunk(section.getChunkX(), section.getChunkZ())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isWithinFrustum", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sha$alwaysVisibleInFrustum(net.caffeinemc.mods.sodium.client.render.viewport.Viewport viewport, net.caffeinemc.mods.sodium.client.render.chunk.RenderSection section, CallbackInfoReturnable<Boolean> cir) {
        if (net.sha.api.SHAMirageManager.isTransitioning) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isWithinNearbySectionFrustum", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sha$alwaysVisibleInNearbyFrustum(net.caffeinemc.mods.sodium.client.render.viewport.Viewport viewport, net.caffeinemc.mods.sodium.client.render.chunk.RenderSection section, CallbackInfoReturnable<Boolean> cir) {
        if (net.sha.api.SHAMirageManager.isTransitioning) {
            cir.setReturnValue(true);
        }
    }
}
