package net.sha.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.sha.api.SHAHologramManager;
import net.sha.api.HologramProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntitySuffocationMixin {
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void preventHoloSuffocation(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;
        if (net.sha.api.SHAHologramManager.ignorePredicate.test(self)) return;
        BlockPos pos = BlockPos.containing(self.getX(), self.getEyeY(), self.getZ());
        
        List<HologramProvider> providers = SHAHologramManager.getIntersectingProviders(
            new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
        );
        
        if (!providers.isEmpty()) {
            for (int i = 0; i < providers.size(); i++) {
                HologramProvider provider = providers.get(i);
                if (provider.isActive() && provider.providesCollision()) {
                    net.sha.api.HologramBounds bounds = provider.getBounds();
                    if (bounds == null || bounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                        BlockState spoofed = provider.getSpoofedBlock(pos.getX(), pos.getY(), pos.getZ());
                        if (spoofed != null && !spoofed.isAir()) {
                            if (spoofed.isSuffocating(self.level(), pos)) {
                                cir.setReturnValue(false); 
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
