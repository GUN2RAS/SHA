package net.sha.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.sha.api.SHAHologramManager;
import net.sha.api.HologramProvider;
import net.sha.api.HologramBounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BlockGetter.class)
public interface RaycastInterceptorMixin extends BlockGetter {

    @Inject(method = "clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;", at = @At("HEAD"), cancellable = true)
    default void interceptClip(ClipContext context, CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult hit = BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (ctx, pos) -> {
            BlockState state = null;

            List<HologramProvider> providers = SHAHologramManager.getIntersectingProviders(
                    new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
            );
            
            if (!providers.isEmpty()) {
                for (int i = 0; i < providers.size(); i++) {
                    HologramProvider provider = providers.get(i);
                    if (provider.isActive() && provider.providesCollision()) {
                        HologramBounds bounds = provider.getBounds();
                        if (bounds == null || bounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                            BlockState spoofed = provider.getSpoofedBlock(pos.getX(), pos.getY(), pos.getZ());
                            if (spoofed != null) {
                                state = spoofed;
                                break;
                            }
                        }
                    }
                }
            }

            if (state == null) {
                state = this.getBlockState(pos);
            }

            FluidState fluid = state.getFluidState();
            Vec3 from = ctx.getFrom();
            Vec3 to = ctx.getTo();
            
            VoxelShape blockShape = ctx.getBlockShape(state, this, pos);
            BlockHitResult bHit = this.clipWithInteractionOverride(from, to, pos, blockShape, state);
            
            VoxelShape fluidShape = ctx.getFluidShape(fluid, this, pos);
            BlockHitResult fHit = fluidShape.clip(from, to, pos);
            
            double dHit = bHit == null ? Double.MAX_VALUE : ctx.getFrom().distanceToSqr(bHit.getLocation());
            double dfHit = fHit == null ? Double.MAX_VALUE : ctx.getFrom().distanceToSqr(fHit.getLocation());
            
            return dHit <= dfHit ? bHit : fHit;
        }, ctx -> {
            Vec3 vec3 = ctx.getFrom().subtract(ctx.getTo());
            return BlockHitResult.miss(ctx.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(ctx.getTo()));
        });
        
        cir.setReturnValue(hit);
    }
}
