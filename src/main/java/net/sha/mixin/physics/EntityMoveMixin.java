package net.sha.mixin.physics;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.sha.api.entity.VirtualStructureEntity;
import net.sha.api.physics.RapierEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMoveMixin {

    @Inject(
        method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void injectKinematicCollision(Vec3 originalMovement, CallbackInfoReturnable<Vec3> cir) {
        Entity entity = (Entity) (Object) this;

        if (entity instanceof VirtualStructureEntity) return;

        Level world = entity.level();
        if (world == null || !world.isClientSide()) return; 

        Vec3 vanillaClampedMovement = cir.getReturnValue();
        if (vanillaClampedMovement.lengthSqr() == 0.0) return;

        AABB bounds = entity.getBoundingBox().inflate(Math.max(vanillaClampedMovement.length(), 2.0));
        boolean hasStructures = false;
        for (VirtualStructureEntity vse : net.sha.SHAMain.CLIENT_STRUCTURES) {
            if (vse.getBoundingBox().intersects(bounds)) {
                hasStructures = true;
                break;
            }
        }
        
        if (hasStructures) {
            int worldId = net.sha.SHA.CLIENT_WORLD_ID;
            
            if (worldId != -1) {
                float hw = entity.getBbWidth() / 2.0f;
                float hh = entity.getBbHeight() / 2.0f;

                float px = (float) entity.getX();
                float py = (float) entity.getY() + hh;
                float pz = (float) entity.getZ();

                float[] out = RapierEngine.moveCharacter(
                    worldId,
                    px, py, pz, hw, hh,
                    (float) vanillaClampedMovement.x, 
                    (float) vanillaClampedMovement.y, 
                    (float) vanillaClampedMovement.z,
                    entity.maxUpStep()
                );

                if (!Float.isNaN(out[0]) && !Float.isNaN(out[1]) && !Float.isNaN(out[2])) {
                    double finalX = Math.abs(out[0] - vanillaClampedMovement.x) < 1e-4 ? vanillaClampedMovement.x : out[0];
                    double finalY = Math.abs(out[1] - vanillaClampedMovement.y) < 1e-4 ? vanillaClampedMovement.y : out[1];
                    double finalZ = Math.abs(out[2] - vanillaClampedMovement.z) < 1e-4 ? vanillaClampedMovement.z : out[2];
                    cir.setReturnValue(new Vec3(finalX, finalY, finalZ));
                }
            }
        }
    }
}
