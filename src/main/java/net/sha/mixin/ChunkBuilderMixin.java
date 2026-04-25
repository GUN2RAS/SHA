package net.sha.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.sha.api.SHAMirageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkBuilder.class, remap = false)
public class ChunkBuilderMixin {
    @Inject(
        method = "shutdown",
        at = @At(value = "INVOKE", target = "Ljava/lang/IllegalStateException;<init>(Ljava/lang/String;)V"),
        cancellable = true
    )
    private void safeShutdown(CallbackInfo ci) {
        if (SHAMirageManager.hasLobotomized) {

            ci.cancel(); 
        }
    }

    @Inject(method = "shutdownThreads", at = @At("HEAD"), cancellable = true)
    private void preventDeadlockJoin(CallbackInfo ci) {
        if (SHAMirageManager.hasLobotomized) {

            ci.cancel();
        }
    }
}
