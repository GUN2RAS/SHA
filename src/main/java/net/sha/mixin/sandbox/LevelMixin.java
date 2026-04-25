package net.sha.mixin.sandbox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.sha.api.entity.VirtualStructureEntity;
import net.sha.api.sandbox.VirtualSandbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public class LevelMixin {

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void sandboxGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (VirtualSandbox.isActive()) {
            VirtualStructureEntity sandbox = VirtualSandbox.current();
            BlockState state = sandbox.blockMatrix.get(pos);
            cir.setReturnValue(state != null ? state : Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void sandboxGetFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (VirtualSandbox.isActive()) {
            VirtualStructureEntity sandbox = VirtualSandbox.current();
            BlockState state = sandbox.blockMatrix.get(pos);
            cir.setReturnValue(state != null ? state.getFluidState() : Fluids.EMPTY.defaultFluidState());
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void sandboxSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (VirtualSandbox.isActive()) {
            VirtualStructureEntity sandbox = VirtualSandbox.current();
            sandbox.setBlock(pos.immutable(), state);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void sandboxGetBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (VirtualSandbox.isActive()) {

            cir.setReturnValue(null);
        }
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"), cancellable = true)
    private void sandboxRemoveBlockEntity(BlockPos pos, CallbackInfo ci) {
        if (VirtualSandbox.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "setBlockEntity", at = @At("HEAD"), cancellable = true)
    private void sandboxSetBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        if (VirtualSandbox.isActive()) {
            ci.cancel();
        }
    }
}
