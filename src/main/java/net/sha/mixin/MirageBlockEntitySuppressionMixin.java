package net.sha.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.sha.api.SHAMirageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class MirageBlockEntitySuppressionMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void suppressBlockEntityDuringMirage(
        net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state, 
        com.mojang.blaze3d.vertex.PoseStack poseStack, 
        net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, 
        net.minecraft.client.renderer.state.level.CameraRenderState camera, 
        CallbackInfo ci
    ) {
        if (SHAMirageManager.fadingManager != null && SHAMirageManager.isTransitioning) {
            ci.cancel();
        }
    }
}
