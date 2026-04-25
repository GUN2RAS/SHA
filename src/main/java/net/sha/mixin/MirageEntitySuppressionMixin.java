package net.sha.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.sha.api.SHAMirageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class MirageEntitySuppressionMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void suppressEntityDuringMirage(
        net.minecraft.client.renderer.entity.state.EntityRenderState renderState, 
        net.minecraft.client.renderer.state.level.CameraRenderState camera, 
        double x, double y, double z, 
        com.mojang.blaze3d.vertex.PoseStack poseStack, 
        net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, 
        CallbackInfo ci
    ) {
        if (SHAMirageManager.fadingManager != null && SHAMirageManager.isTransitioning) {
            ci.cancel();
        }
    }
}
