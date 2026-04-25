package net.sha.mixin.physics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.sha.api.entity.VirtualStructureEntity;
import net.sha.api.network.C2S_VirtualBlockActionPayload;
import net.sha.api.network.C2S_VirtualInteractBlockPayload;
import net.sha.api.util.VirtualBlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.spongepowered.asm.mixin.Unique;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Unique
    private int destroyTicks = 0;
    
    @Unique
    private BlockPos destroyPos = null;

    @Shadow public HitResult hitResult;
    @Shadow public ClientLevel level;
    @Shadow public LocalPlayer player;
    @Shadow private int rightClickDelay;
    @Shadow private int missTime;
    @Shadow public abstract ClientPacketListener getConnection();

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (this.hitResult != null) {
            System.out.println("[SHA DEBUG] startAttack HIT CLASS: " + this.hitResult.getClass().getName());
        }
        if (this.hitResult instanceof VirtualBlockHitResult vhit) {
            if (this.level != null && this.player != null && this.missTime <= 0) {
                net.minecraft.world.entity.Entity entity = this.level.getEntity(vhit.entityId);
                if (entity instanceof VirtualStructureEntity vse) {
                    BlockPos pos = vhit.getBlockPos();
                    BlockState state = vse.blockMatrix.get(pos);
                    if (state != null && !state.isAir()) {
                        this.player.swing(InteractionHand.MAIN_HAND);
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new C2S_VirtualBlockActionPayload(vse.getId(), ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, vhit.getDirection(), 0));
                        if (this.player.getAbilities().instabuild) {
                            this.missTime = 5;
                        }
                        cir.setReturnValue(true);
                    } else {
                        cir.setReturnValue(false);
                    }
                }
            }
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void onStartUseItem(CallbackInfo ci) {
        if (this.hitResult instanceof VirtualBlockHitResult vhit) {
            if (this.level != null && this.player != null) {
                net.minecraft.world.entity.Entity entity = this.level.getEntity(vhit.entityId);
                if (entity instanceof VirtualStructureEntity vse) {
                    for (InteractionHand hand : InteractionHand.values()) {
                        ItemStack stack = this.player.getItemInHand(hand);

                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new C2S_VirtualInteractBlockPayload(vse.getId(), hand, vhit, 0));
                        this.player.swing(hand);
                        this.rightClickDelay = 4;
                        ci.cancel();
                        return; 
                    }
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void onContinueAttack(boolean breaking, CallbackInfo ci) {
        if (this.hitResult instanceof VirtualBlockHitResult vhit) {
            if (breaking && this.missTime <= 0) {
                if (this.level != null && this.player != null) {
                    net.minecraft.world.entity.Entity entity = this.level.getEntity(vhit.entityId);
                    if (entity instanceof VirtualStructureEntity vse) {
                        BlockPos localPos = vhit.getBlockPos();
                        BlockState state = vse.blockMatrix.get(localPos);
                        if (state != null && !state.isAir()) {
                            if (state.shouldSpawnTerrainParticles()) {
                                net.minecraft.world.phys.Vec3 hitLoc = vhit.getLocation();
                                Direction dir = vhit.getDirection();
                                double px = hitLoc.x + (this.level.getRandom().nextDouble() - 0.5) * 0.2 + dir.getStepX() * 0.1;
                                double py = hitLoc.y + (this.level.getRandom().nextDouble() - 0.5) * 0.2 + dir.getStepY() * 0.1;
                                double pz = hitLoc.z + (this.level.getRandom().nextDouble() - 0.5) * 0.2 + dir.getStepZ() * 0.1;
                                this.level.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state), px, py, pz, 0, 0, 0);
                            }
                            
                            if (!localPos.equals(destroyPos)) {
                                destroyTicks = 0;
                                destroyPos = localPos;
                            }
                            destroyTicks++;
                            
                            if (destroyTicks >= 4 || this.player.getAbilities().instabuild) {
                                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new C2S_VirtualBlockActionPayload(vse.getId(), ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, localPos, vhit.getDirection(), 0));
                                destroyTicks = 0;
                                this.missTime = 5;
                            }
                        }
                    }
                }
            }
            ci.cancel();
        }
    }
}
