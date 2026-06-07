package net.sha.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.sha.api.HologramProvider;
import net.sha.api.SHARenderBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerMixin {

    @Shadow
    protected abstract RenderSection getRenderSection(int x, int y, int z);

    @Shadow
    protected net.caffeinemc.mods.sodium.client.render.chunk.tree.RemovableMultiForest renderableSectionTree;

    @Shadow
    private long lastFrameAtTime;

    @Shadow
    public abstract void markGraphDirty();

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void captureConstructor(CallbackInfo ci) {
        SHARenderBridge.capture((RenderSectionManager)(Object)this);
    }

    @Inject(method = "onSectionAdded", at = @At("RETURN"), remap = false)
    private void forceHologramInitialBuild(int x, int y, int z, CallbackInfo ci) {
        SHARenderBridge.capture((RenderSectionManager)(Object)this);
        forceHologramRebuildIfNeeded(x, y, z);
    }

    @Unique
    private void forceHologramRebuildIfNeeded(int x, int y, int z) {
        RenderSection section = this.getRenderSection(x, y, z);
        if (section == null || section.getPendingUpdate() != 0) {
            return;
        }
        java.util.List<HologramProvider> intersecting = net.sha.api.SHAHologramManager.getIntersectingProviders(
                new net.minecraft.world.phys.AABB(x * 16, y * 16, z * 16, x * 16 + 16, y * 16 + 16, z * 16 + 16)
        );
        for (HologramProvider p : intersecting) {
            if (p.forcesEmptyChunkRendering()) {
                this.renderableSectionTree.add(section);
                section.setPendingUpdate(6, this.lastFrameAtTime);
                this.markGraphDirty();
                return;
            }
        }
    }
}
