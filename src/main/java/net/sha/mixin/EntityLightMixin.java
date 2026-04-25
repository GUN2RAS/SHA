package net.sha.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityLightMixin {
    @Inject(method = "getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I", at = @At("HEAD"), cancellable = true)
    private void forceMaxEntityLight(Entity entity, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        java.util.List<net.sha.api.HologramProvider> providers = net.sha.api.SHAHologramManager.getIntersectingProviders(entity.getBoundingBox());
        if (!providers.isEmpty()) {
            for (int i = 0; i < providers.size(); i++) {
                net.sha.api.HologramProvider provider = providers.get(i);
                if (provider.isActive() && provider.providesCollision()) {
                    cir.setReturnValue(15728880);
                    return;
                }
            }
        }
    }
}
