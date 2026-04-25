package net.sha.mixin.render;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sha.api.entity.VirtualStructureEntity;
import net.sha.api.util.VirtualBlockHitResult;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void renderVirtualTargetBlockOutline(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, boolean renderingTranslucent, LevelRenderState renderState, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.hitResult instanceof VirtualBlockHitResult hitResult) {
            if (renderingTranslucent && hitResult.getType() != HitResult.Type.MISS) {
                VirtualStructureEntity entity = hitResult.structure;
                BlockPos pos = hitResult.getBlockPos();

                BlockState state = entity.blockMatrix.get(pos);
                
                if (state != null && !state.isAir()) {
                    CollisionContext ctx = CollisionContext.of(client.getCameraEntity());
                    VoxelShape shape = state.getShape(entity.level(), pos, ctx);
                    
                    if (!shape.isEmpty()) {
                        poseStack.pushPose();
                        
                        Vec3 camPos = renderState.cameraRenderState.pos;
                        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

                        double ex = Mth.lerp((double)tickDelta, entity.xo, entity.getX());
                        double ey = Mth.lerp((double)tickDelta, entity.yo, entity.getY());
                        double ez = Mth.lerp((double)tickDelta, entity.zo, entity.getZ());

                        poseStack.translate(ex - camPos.x, ey - camPos.y, ez - camPos.z);

                        Quaternionf prevRot = new Quaternionf(entity.prevRotationQuaternion);
                        Quaternionf lerpedRot = prevRot.slerp(entity.getPhysicsRotation(), tickDelta);
                        poseStack.mulPose(lerpedRot);

                        int color = (102 << 24); 
                        float lineWidth = Math.max(2.5F, (float)client.getWindow().getWidth() / 1920.0f * 2.5f);
                        
                        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lines());

                        ShapeRenderer.renderShape(poseStack, consumer, shape, pos.getX(), pos.getY(), pos.getZ(), color, lineWidth);
                        
                        bufferSource.endLastBatch();
                        
                        poseStack.popPose();
                    }
                }
            }
            
            ci.cancel();
        }
    }
}
