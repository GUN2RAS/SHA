package net.sha.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.sha.api.HologramProvider;
import net.sha.api.SHAHologramManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class AbstractBlockStateMixin {

    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void overrideHologramDestroyProgress(Player player, BlockGetter world, BlockPos pos, CallbackInfoReturnable<Float> cir) {

        if (recursionGuard.get()) {
            return;
        }

        List<HologramProvider> providers = SHAHologramManager.getIntersectingProviders(
            new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
        );

        if (!providers.isEmpty()) {
            for (HologramProvider provider : providers) {
                if (provider.isActive() && provider.providesCollision()) {
                    net.sha.api.HologramBounds bounds = provider.getBounds();
                    if (bounds == null || bounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                        BlockState spoofed = provider.getSpoofedBlock(pos.getX(), pos.getY(), pos.getZ());
                        if (spoofed != null) {
                            try {
                                recursionGuard.set(true);
                                float progress = spoofed.getDestroyProgress(player, world, pos);
                                cir.setReturnValue(progress);
                            } finally {
                                recursionGuard.set(false);
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    private static final ThreadLocal<Boolean> recursionGuard = ThreadLocal.withInitial(() -> false);
}
