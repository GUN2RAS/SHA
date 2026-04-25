package net.sha.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.sha.api.SHAHologramManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.world.level.BlockCollisions.class)
public class BlockCollisionsMixin {

    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.world.phys.shapes.CollisionContext context;

    @Redirect(
        method = "computeNext",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState interceptCollisionBlockState(BlockGetter instance, BlockPos pos) {
        if (instance instanceof net.minecraft.world.level.Level level && level.isClientSide()) {
            boolean hasPortal = false;
            net.sha.api.HologramProvider[] providers = net.sha.api.SHAHologramManager.getProvidersForChunk(pos.getX(), pos.getY(), pos.getZ());
            for (int i = 0; i < providers.length; i++) {
                if (providers[i].isActive() && providers[i].providesCollision() && providers[i] instanceof net.sha.api.entity.VirtualStructureEntity vse && vse.getAuthorityOwner().startsWith("portal_mirage")) {
                    hasPortal = true;
                    break;
                }
            }
            if (!hasPortal) {
                return instance.getBlockState(pos);
            }
        }
        
        if (this.context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ecc) {
            net.minecraft.world.entity.Entity entity = ecc.getEntity();
            if (entity != null && net.sha.api.SHAHologramManager.ignorePredicate.test(entity)) {
                return instance.getBlockState(pos);
            }
        }

        net.sha.api.HologramProvider[] providers = net.sha.api.SHAHologramManager.getProvidersForChunk(pos.getX(), pos.getY(), pos.getZ());
        if (providers.length > 0) {
            for (int i = 0; i < providers.length; i++) {
                net.sha.api.HologramProvider provider = providers[i];
                if (provider.isActive() && provider.providesCollision()) {
                    net.sha.api.HologramBounds bounds = provider.getBounds();
                    if (bounds == null || bounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                        BlockState spoofed = provider.getSpoofedBlock(pos.getX(), pos.getY(), pos.getZ());
                        if (spoofed != null) {
                            return spoofed;
                        }
                    }
                }
            }
        }
        return instance.getBlockState(pos);
    }
}
