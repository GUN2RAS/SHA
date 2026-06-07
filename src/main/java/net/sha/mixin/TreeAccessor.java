package net.sha.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.tree.Tree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Tree.class, remap = false)
public interface TreeAccessor {
    // костыль для доступа к приватным полям содиума. заебали всё прятать.
    @Accessor(value = "offsetX", remap = false)
    int getOffsetX();

    @Accessor(value = "offsetZ", remap = false)
    int getOffsetZ();
}
