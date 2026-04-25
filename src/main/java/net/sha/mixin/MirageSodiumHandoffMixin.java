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
    
    @Inject(method = "setupTerrain", at = @At("RETURN"))
    private void updateFadingCamera(Camera camera, Viewport viewport, FogParameters fogParameters, boolean spectator, boolean updateChunksImmediately, org.joml.Matrix4f matrices, CallbackInfo ci) {
        if (SHAMirageManager.fadingManager != null) {
            net.minecraft.world.phys.Vec3 posRaw = camera.position();
            Vector3d pos = new Vector3d(
                posRaw.x - SHAMirageManager.offsetX, 
                posRaw.y + SHAMirageManager.offsetY, 
                posRaw.z - SHAMirageManager.offsetZ
            );
            SHAMirageManager.fadingManager.prepareFrame(pos);
        }
    }
    
    @Inject(method = "drawChunkLayer", at = @At("HEAD"), cancellable = true)
    private void drawMirageLayer(ChunkSectionLayerGroup group, ChunkRenderMatrices matrices, double x, double y, double z, GpuSampler terrainSampler, CallbackInfo ci) {
        if (SHAMirageManager.fadingManager != null) {
            int newWorldChunks = this.renderSectionManager.getVisibleChunkCount();
            
            if (newWorldChunks >= SHAMirageManager.minChunksRequired || !SHAMirageManager.isTransitioning) {
                SHAMirageManager.cleanUp();
            } else {
                double virtualX = x - SHAMirageManager.offsetX;
                double virtualY = y + SHAMirageManager.offsetY; 
                double virtualZ = z - SHAMirageManager.offsetZ;

                String layerName = group.name();
                if (layerName.equals("OPAQUE") || layerName.equals("SOLID") || layerName.equals("CUTOUT")) { 
                    SHAMirageManager.fadingManager.renderLayer(matrices, DefaultTerrainRenderPasses.SOLID, virtualX, virtualY, virtualZ, this.lastFogParameters, terrainSampler);
                    SHAMirageManager.fadingManager.renderLayer(matrices, DefaultTerrainRenderPasses.CUTOUT, virtualX, virtualY, virtualZ, this.lastFogParameters, terrainSampler);
                } else if (layerName.equals("TRANSLUCENT")) { 
                    SHAMirageManager.fadingManager.renderLayer(matrices, DefaultTerrainRenderPasses.TRANSLUCENT, virtualX, virtualY, virtualZ, this.lastFogParameters, terrainSampler);
                }
                
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
