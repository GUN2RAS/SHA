package net.sha.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.sha.api.SHAHologramManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

@Mixin(value = LevelSlice.class, remap = false)
public class ClientChunkCacheBiomeMixin {

    // Адекватно: подменяем биомы для содиума через LevelSlice. Если этого не сделать, вода в голограммах будет рендериться как моча, ебаная ты магия.
    @Inject(method = "getBlockTint", at = @At("HEAD"), cancellable = true, remap = false)
    private void interceptGetBlockTint(BlockPos pos, ColorResolver resolver, CallbackInfoReturnable<Integer> cir) {
        Holder<Biome> spoofed = SHAHologramManager.getSpoofedBiome(pos.getX(), pos.getY(), pos.getZ());
        if (spoofed != null) {
            cir.setReturnValue(resolver.getColor(spoofed.value(), pos.getX(), pos.getZ()));
        }
    }
}
