package net.sha.mixin;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Camera;
import net.sha.api.SHAMirageManager;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class MirageSodiumHandoffMixin {
    
    @Shadow private RenderSectionManager renderSectionManager;
    @Shadow private FogParameters lastFogParameters;
    @Shadow private Vector3d lastCameraPos;
    
    @Redirect(
        method = {"initRenderer", "unloadLevel"},
        at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;destroy()V")
    )
    private void captureVBOs(RenderSectionManager oldManager) {
        if (SHAMirageManager.isTransitioning && SHAMirageManager.fadingManager == null) {
            SHAMirageManager.fadingManager = oldManager;
            
            SHAMirageManager.hasLobotomized = true;
            oldManager.getBuilder().shutdown();
            
            if (this.lastCameraPos != null) {
                SHAMirageManager.fadingCameraX = this.lastCameraPos.x;
                SHAMirageManager.fadingCameraY = this.lastCameraPos.y;
                SHAMirageManager.fadingCameraZ = this.lastCameraPos.z;
            }
        } else {
            oldManager.destroy();
        }
    }
    
    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void triggerSetupTerrainHead(Camera camera, Viewport viewport, FogParameters fogParameters, boolean spectator, boolean updateChunksImmediately, org.joml.Matrix4f matrices, CallbackInfo ci) {
        if (SHAMirageManager.onSetupTerrainHead != null) {
            SHAMirageManager.onSetupTerrainHead.run();
        }
    }


    @Inject(method = "setupTerrain", at = @At("RETURN"))
    private void updateFadingCamera(Camera camera, Viewport viewport, FogParameters fogParameters, boolean spectator, boolean updateChunksImmediately, org.joml.Matrix4f matrices, CallbackInfo ci) {
        if (SHAMirageManager.fadingManager != null) {
            net.minecraft.world.phys.Vec3 posRaw = camera.position();
            double vx = posRaw.x - SHAMirageManager.offsetX;
            double vy = SHAMirageManager.flipY 
                ? (SHAMirageManager.flipPivotY - posRaw.y) 
                : (posRaw.y + SHAMirageManager.offsetY);
            double vz = SHAMirageManager.flipZ 
                ? (SHAMirageManager.flipPivotZ - posRaw.z) 
                : (posRaw.z - SHAMirageManager.offsetZ);
            SHAMirageManager.fadingManager.prepareFrame(new Vector3d(vx, vy, vz));
        }
    }
    
    @Inject(method = "drawChunkLayer", at = @At("HEAD"), cancellable = true)
    private void drawMirageLayer(ChunkSectionLayerGroup group, ChunkRenderMatrices matrices, double x, double y, double z, GpuSampler terrainSampler, CallbackInfo ci) {
        if (SHAMirageManager.fadingManager != null) {
            int newWorldChunks = this.renderSectionManager.getVisibleChunkCount();
            
            if ((newWorldChunks >= SHAMirageManager.minChunksRequired && SHAMirageManager.isHologramReady.get()) || !SHAMirageManager.isTransitioning) {
                SHAMirageManager.cleanUp();
            } else {

                double virtualX = x - SHAMirageManager.offsetX;
                double virtualY = SHAMirageManager.flipY 
                    ? (SHAMirageManager.flipPivotY - y)
                    : (y + SHAMirageManager.offsetY);
                double virtualZ = SHAMirageManager.flipZ 
                    ? (SHAMirageManager.flipPivotZ - z) 
                    : (z - SHAMirageManager.offsetZ);

                ChunkRenderMatrices renderMatrices = matrices;
                boolean needsCullFlip = false;

                if (SHAMirageManager.flipY || SHAMirageManager.flipZ) {
                    float sy = SHAMirageManager.flipY ? -1.0f : 1.0f;
                    float sz = SHAMirageManager.flipZ ? -1.0f : 1.0f;
                    Matrix4f flippedMV = new Matrix4f(matrices.modelView());
                    flippedMV.scale(1.0f, sy, sz);
                    renderMatrices = new ChunkRenderMatrices(matrices.projection(), flippedMV);

                    needsCullFlip = (SHAMirageManager.flipY != SHAMirageManager.flipZ);
                }

                if (needsCullFlip) org.lwjgl.opengl.GL11.glFrontFace(org.lwjgl.opengl.GL11.GL_CW);

                String layerName = group.name();
                if (layerName.equals("OPAQUE") || layerName.equals("SOLID") || layerName.equals("CUTOUT")) { 
                    SHAMirageManager.fadingManager.renderLayer(renderMatrices, DefaultTerrainRenderPasses.SOLID, virtualX, virtualY, virtualZ, this.lastFogParameters, terrainSampler);
                    SHAMirageManager.fadingManager.renderLayer(renderMatrices, DefaultTerrainRenderPasses.CUTOUT, virtualX, virtualY, virtualZ, this.lastFogParameters, terrainSampler);
                } else if (layerName.equals("TRANSLUCENT")) { 
                    SHAMirageManager.fadingManager.renderLayer(renderMatrices, DefaultTerrainRenderPasses.TRANSLUCENT, virtualX, virtualY, virtualZ, this.lastFogParameters, terrainSampler);
                }

                if (needsCullFlip) org.lwjgl.opengl.GL11.glFrontFace(org.lwjgl.opengl.GL11.GL_CCW);
                
                ci.cancel(); 
            }
        }
    }
    
    @Inject(method = "getVisibleChunkCount", at = @At("HEAD"), cancellable = true)
    private void forceVisibleChunkCountForMirageFallback(CallbackInfoReturnable<Integer> cir) {
        if (SHAMirageManager.fadingManager != null && SHAMirageManager.isTransitioning) {

            if (this.renderSectionManager != null && this.renderSectionManager.getVisibleChunkCount() == 0) {
                cir.setReturnValue(1);
            }
        }
    }
}
