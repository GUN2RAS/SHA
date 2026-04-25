package net.sha.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.sha.SHA;
import net.sha.api.HologramProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerMixin {

    @Shadow protected abstract RenderSection getRenderSection(int x, int y, int z);
    @Shadow(remap = false) protected net.caffeinemc.mods.sodium.client.render.chunk.tree.RemovableMultiForest renderableSectionTree;
    @Shadow(remap = false) private long lastFrameAtTime;
    @Shadow(remap = false) public abstract void markGraphDirty();

    @Inject(method = "onSectionAdded", at = @At("RETURN"), remap = false)
    private void forceHologramInitialBuild(int x, int y, int z, CallbackInfo ci) {
        RenderSection section = this.getRenderSection(x, y, z);
        if (section != null && section.getPendingUpdate() == 0) {
            java.util.List<HologramProvider> intersecting = net.sha.api.SHAHologramManager.getIntersectingProviders(
                    new net.minecraft.world.phys.AABB(x * 16, y * 16, z * 16, x * 16 + 16, y * 16 + 16, z * 16 + 16)
            );
            for (HologramProvider p : intersecting) {
                if (p.forcesEmptyChunkRendering()) {
                    
                    this.renderableSectionTree.add(section);
                    section.setPendingUpdate(8, this.lastFrameAtTime); 
                    this.markGraphDirty();
                    return; 
                }
            }
        }
    }
}
