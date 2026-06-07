package net.sha.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = RenderSectionManager.class, remap = false)
public interface RenderSectionManagerAccessor {
    @Invoker("getRenderSection")
    RenderSection invokeGetRenderSection(int x, int y, int z);

    @Accessor("renderableSectionTree")
    net.caffeinemc.mods.sodium.client.render.chunk.tree.RemovableMultiForest getRenderableSectionTree();

    @Accessor("lastFrameAtTime")
    long getLastFrameAtTime();
}
