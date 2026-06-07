package net.sha.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "ensureHasSentCarriedItem", at = @At("HEAD"), cancellable = true)
    private void sha$guardEnsureHasSentCarriedItem(CallbackInfo ci) {
        // защита от краша если плеер нулл, ванильный баг который вылезает при мираже. спасибо моджанг за спагетти.
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            ci.cancel();
        }
    }
}
