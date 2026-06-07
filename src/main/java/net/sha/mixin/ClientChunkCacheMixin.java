package net.sha.mixin;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.sha.api.SHAHologramManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {

    @Shadow @Final private LevelChunk emptyChunk;

    // Бля ну тут гемини долго ебался, но в итоге эта залупа заработала. Подсовываем пустой чанк если клиент просит то, чего у него еще нет, но голограммы там есть, иначе рендер пошлет нас нахуй с NullPointerException.
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("RETURN"), cancellable = true)
    private void sha$injectFakeHologramChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, CallbackInfoReturnable<LevelChunk> cir) {
        if (!loadOrGenerate && cir.getReturnValue() == null && SHAHologramManager.hasHologramsInChunk(x, z)) {
            cir.setReturnValue(this.emptyChunk);
        }
    }
}
