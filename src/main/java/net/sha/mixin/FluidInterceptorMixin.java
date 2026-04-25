package net.sha.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.sha.api.SHAFluidConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public class FluidInterceptorMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void startFluidTick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        SHAFluidConfig.IS_FLUID_TICKING.set(true);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void endFluidTick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        SHAFluidConfig.IS_FLUID_TICKING.set(false);
    }
}
