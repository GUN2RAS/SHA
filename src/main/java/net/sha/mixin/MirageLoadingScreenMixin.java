package net.sha.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.sha.api.SHAMirageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MirageLoadingScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void suppressDimensionLoadingScreens(Screen screen, CallbackInfo ci) {
        if (SHAMirageManager.isTransitioning) {
            if (screen instanceof LevelLoadingScreen) {
                ci.cancel();
            }
        }
    }
}
