package net.sha.mixin;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.sha.api.SHARaycastConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockGetter.class)
public interface HologramRaycastMixin {

    @Inject(method = "clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;", at = @At("HEAD"))
    default void startRaycast(ClipContext context, CallbackInfoReturnable<BlockHitResult> cir) {
        SHARaycastConfig.IS_RAYCASTING.set(true);
    }

    @Inject(method = "clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;", at = @At("RETURN"))
    default void endRaycast(ClipContext context, CallbackInfoReturnable<BlockHitResult> cir) {
        SHARaycastConfig.IS_RAYCASTING.set(false);
    }
}
