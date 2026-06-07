package net.sha.mixin;

import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
public class LevelChunkSectionAirMixin {

    @Inject(method = "hasOnlyAir", at = @At("HEAD"), cancellable = true)
    private void spoofAirForHolograms(CallbackInfoReturnable<Boolean> cir) {
        // адекватно: наебываем ванильный майнкрафт чтобы он думал что в пустом чанке есть блоки. Иначе он вообще не пустит чанк в рендер
        if (net.sha.SHA.FORCE_SOLID.get()) {
            cir.setReturnValue(false); 
        }
    }
}
