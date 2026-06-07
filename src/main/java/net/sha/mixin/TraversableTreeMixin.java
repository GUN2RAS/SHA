package net.sha.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.tree.TraversableTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = TraversableTree.class, remap = false)
public abstract class TraversableTreeMixin {

    @org.spongepowered.asm.mixin.injection.Inject(method = "traverse(Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/CoordinateSectionVisitor;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;FF)V", at = @At("HEAD"), remap = false)
    private void sha$onTraverseStart(net.caffeinemc.mods.sodium.client.render.chunk.lists.CoordinateSectionVisitor visitor, net.caffeinemc.mods.sodium.client.render.viewport.Viewport viewport, float distanceLimit, float buildDistance, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        int offsetX = ((TreeAccessor) this).getOffsetX();
        int offsetZ = ((TreeAccessor) this).getOffsetZ();

        int minChunkX = offsetX >> 4;
        int maxChunkX = minChunkX + 63;
        int minChunkZ = offsetZ >> 4;
        int maxChunkZ = minChunkZ + 63;
        boolean hasHologram = net.sha.api.SHAHologramManager.hasHologramsInArea(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        // адекватно: флаг для байпасса. неадекватно: ебаный содиум отсекает чанки по вертикали, и голограммы пропадают нахуй.
        net.sha.api.SHAHologramManager.verticalCullBypass.set(hasHologram);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "cylindricalDistanceTest", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sha$bypassCylindricalTest(float dx, float dy, float dz, float distanceLimit, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        // костыль для содиума, без него фризит и режет рендер как сука.
        if (net.sha.api.SHAHologramManager.verticalCullBypass.get() == Boolean.TRUE) {
            cir.setReturnValue(true);
        }
    }
}
