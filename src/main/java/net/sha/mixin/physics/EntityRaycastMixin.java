package net.sha.mixin.physics;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.sha.SHAMain;
import net.sha.api.entity.VirtualStructureEntity;
import net.sha.api.util.VirtualBlockHitResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityRaycastMixin {

    @Inject(method = "pick", at = @At("RETURN"), cancellable = true)
    private void raycastVirtualStructures(double maxDistance, float tickProgress, boolean includeFluids, CallbackInfoReturnable<HitResult> cir) {
        Entity self = (Entity) (Object) this;
        Vec3 startPos = self.getEyePosition(tickProgress);
        Vec3 rotationVec = self.getViewVector(tickProgress);
        Vec3 endPos = startPos.add(rotationVec.x * maxDistance, rotationVec.y * maxDistance, rotationVec.z * maxDistance);

        HitResult currentHit = cir.getReturnValue();
        double closestDistance = currentHit != null && currentHit.getType() != HitResult.Type.MISS
                ? currentHit.getLocation().distanceToSqr(startPos)
                : maxDistance * maxDistance;

        VirtualBlockHitResult closestVirtualHit = null;

        for (VirtualStructureEntity structure : SHAMain.CLIENT_STRUCTURES) {
            
            Vector3f localStart = startPos.toVector3f();
            Vector3f localEnd = endPos.toVector3f();

            Vector3f structurePos = structure.position().toVector3f();
            localStart.sub(structurePos);
            localEnd.sub(structurePos);

            Quaternionf invQuat = new Quaternionf(structure.getPhysicsRotation()).invert();
            localStart.rotate(invQuat);
            localEnd.rotate(invQuat);

            Vec3 localStartD = new Vec3(localStart.x(), localStart.y(), localStart.z());
            Vec3 localEndD = new Vec3(localEnd.x(), localEnd.y(), localEnd.z());

            BlockHitResult localHit = structure.raycastLocalBlocks(localStartD, localEndD);
            if (localHit != null && localHit.getType() != HitResult.Type.MISS) {
                
                Vector3f globalHit = localHit.getLocation().toVector3f();
                globalHit.rotate(structure.getPhysicsRotation());
                globalHit.add(structurePos);
                
                Vec3 globalHitD = new Vec3(globalHit.x(), globalHit.y(), globalHit.z());
                double distSq = globalHitD.distanceToSqr(startPos);
                
                if (distSq < closestDistance) {
                    closestDistance = distSq;
                    closestVirtualHit = new VirtualBlockHitResult(
                            structure, globalHitD, localHit.getDirection(), localHit.getBlockPos(), localHit.isInside()
                    );
                }
            }
        }

        if (closestVirtualHit != null) {
            cir.setReturnValue(closestVirtualHit);
        }
    }
}
