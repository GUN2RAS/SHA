package net.sha.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.sha.api.entity.VirtualStructureEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;

import java.util.Map;

public class VirtualStructureRenderer extends EntityRenderer<VirtualStructureEntity, VirtualStructureRenderer.StructureRenderState> {

    public static class StructureRenderState extends EntityRenderState {
        public Quaternionf rotation = new Quaternionf();
        public boolean isRendered = false;
    }

    public VirtualStructureRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(VirtualStructureEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(VirtualStructureEntity entity) {
        return entity.getBoundingBox().inflate(256.0);
    }

    @Override
    public StructureRenderState createRenderState() {
        return new StructureRenderState();
    }

    @Override
    public void extractRenderState(VirtualStructureEntity entity, StructureRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        
        if (entity.getPhysicsRotation() != null) {
            if (entity.prevRotationQuaternion != null) {
                state.rotation.set(entity.prevRotationQuaternion).slerp(entity.getPhysicsRotation(), partialTick);
            } else {
                state.rotation.set(entity.getPhysicsRotation());
            }
        }

        if (entity.isMatrixDirtyForRender && !entity.isVboBaking) {
            entity.isMatrixDirtyForRender = false;
            entity.isVboBaking = true;
            Map<BlockPos, BlockState> copy = new java.util.HashMap<>(entity.getBlockMatrix());
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                buildCustomVbo(entity, copy);
                entity.isVboBaking = false;
            });
        }
    }

    @Override
    public void submit(StructureRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {

        super.submit(state, poseStack, collector, cameraState);
    }

    public static void buildCustomVbo(VirtualStructureEntity entity, Map<BlockPos, BlockState> blocks) {
        try {
            Minecraft client = Minecraft.getInstance();
            net.minecraft.client.renderer.block.BlockStateModelSet bsmSet = client.getModelManager().getBlockStateModelSet();
            net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
            
            com.mojang.blaze3d.vertex.ByteBufferBuilder byteBufferBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(8388608);
            com.mojang.blaze3d.vertex.BufferBuilder bufferBuilder = new com.mojang.blaze3d.vertex.BufferBuilder(byteBufferBuilder, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK);
            
            com.mojang.blaze3d.vertex.PoseStack.Pose basePose = new com.mojang.blaze3d.vertex.PoseStack().last();
            int totalIndices = 0;

            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();
                if (state.isAir()) continue;
                
                float qx = pos.getX();
                float qy = pos.getY();
                float qz = pos.getZ();
                
                net.minecraft.client.renderer.block.dispatch.BlockStateModel model = bsmSet.get(state);
                random.setSeed(42L);
                java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new java.util.ArrayList<>();
                model.collectParts(random, parts);
                com.mojang.blaze3d.vertex.QuadInstance quadInstance = new com.mojang.blaze3d.vertex.QuadInstance(); for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
                    for (net.minecraft.client.resources.model.geometry.BakedQuad q : part.getQuads(null)) {
                        bufferBuilder.putBlockBakedQuad(qx, qy, qz, q, quadInstance);
                        totalIndices += 6;
                    }
                    for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                        for (net.minecraft.client.resources.model.geometry.BakedQuad q : part.getQuads(dir)) {
                            bufferBuilder.putBlockBakedQuad(qx, qy, qz, q, quadInstance);
                            totalIndices += 6;
                        }
                    }
                }
            }

            if (totalIndices > 0) {
                com.mojang.blaze3d.vertex.MeshData mesh = bufferBuilder.buildOrThrow();
                java.nio.ByteBuffer vb = mesh.vertexBuffer();
                int size = vb.remaining();
                long pointer = org.lwjgl.system.MemoryUtil.nmemAlloc(size);
                org.lwjgl.system.MemoryUtil.memCopy(org.lwjgl.system.MemoryUtil.memAddress(vb), pointer, size);
                
                long oldPointer = entity.cachedVBOPointer;
                entity.cachedVBOPointer = pointer;
                entity.cachedVBOSize = size;
                entity.cachedDrawState = new com.mojang.blaze3d.vertex.MeshData.DrawState(
                    mesh.drawState().format(), 
                    mesh.drawState().vertexCount(), 
                    totalIndices, 
                    mesh.drawState().mode(), 
                    mesh.drawState().indexType()
                );
                
                if (oldPointer != 0) {
                    org.lwjgl.system.MemoryUtil.nmemFree(oldPointer);
                }
                mesh.close();
            } else {
                if (entity.cachedVBOPointer != 0) org.lwjgl.system.MemoryUtil.nmemFree(entity.cachedVBOPointer);
                entity.cachedVBOPointer = 0;
                entity.cachedVBOSize = 0;
                entity.cachedDrawState = null;
            }
            byteBufferBuilder.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void renderVBO(VirtualStructureEntity entity, PoseStack poseStack) {
        if (entity.isMatrixDirtyForRender && !entity.isVboBaking) {
            entity.isMatrixDirtyForRender = false;
            entity.isVboBaking = true;
            Map<BlockPos, BlockState> copy = new java.util.HashMap<>(entity.getBlockMatrix());
            System.out.println("[SHA] Baking VBO with " + copy.size() + " blocks.");
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                buildCustomVbo(entity, copy);
                entity.isVboBaking = false;
                System.out.println("[SHA] VBO Bake completed. Pointer: " + entity.cachedVBOPointer + ", Vertices: " + (entity.cachedDrawState != null ? entity.cachedDrawState.vertexCount() : 0));
            });
        }
        
        if (entity.cachedVBOPointer == 0 || entity.cachedDrawState == null) {
            return;
        }
        
        com.mojang.blaze3d.vertex.ByteBufferBuilder vBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(entity.cachedVBOSize + 1024);
        long vPtr = vBuilder.reserve(entity.cachedVBOSize);
        org.lwjgl.system.MemoryUtil.memCopy(entity.cachedVBOPointer, vPtr, entity.cachedVBOSize);
        com.mojang.blaze3d.vertex.ByteBufferBuilder.Result vResult = vBuilder.build();
        com.mojang.blaze3d.vertex.MeshData frameMesh = new com.mojang.blaze3d.vertex.MeshData(vResult, entity.cachedDrawState);

        net.minecraft.client.renderer.rendertype.RenderType type = net.minecraft.client.renderer.rendertype.RenderTypes.solidMovingBlock();
        
        com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().pushMatrix();
        com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().identity();
        com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().mul(poseStack.last().pose());
        
        type.draw(frameMesh);

        com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().popMatrix();
    }
}
