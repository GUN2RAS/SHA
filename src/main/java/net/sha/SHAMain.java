package net.sha;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.sha.api.entity.VirtualStructureEntity;

public class SHAMain implements ModInitializer {

    public static final String MOD_ID = "sha";

    public static final java.util.Set<VirtualStructureEntity> CLIENT_STRUCTURES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static final EntityType<VirtualStructureEntity> VIRTUAL_STRUCTURE_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("sha", "virtual_structure"),
            EntityType.Builder.<VirtualStructureEntity>of(VirtualStructureEntity::new, MobCategory.MISC)
                    .sized(10.0F, 10.0F)
                    .clientTrackingRange(20)
                    .updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("sha", "virtual_structure")))
    );

    @Override
    public void onInitialize() {
        System.out.println("[SHA] Mod initialized.");

        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(net.sha.api.network.VirtualStructureSyncPayload.ID, net.sha.api.network.VirtualStructureSyncPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(net.sha.api.network.C2S_VSEPhysicsSyncPayload.ID, net.sha.api.network.C2S_VSEPhysicsSyncPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(net.sha.api.network.C2S_VSESleepPayload.ID, net.sha.api.network.C2S_VSESleepPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(net.sha.api.network.C2S_VirtualBlockActionPayload.ID, net.sha.api.network.C2S_VirtualBlockActionPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(net.sha.api.network.C2S_VirtualInteractBlockPayload.ID, net.sha.api.network.C2S_VirtualInteractBlockPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(net.sha.api.network.S2C_VirtualBlockUpdatePayload.ID, net.sha.api.network.S2C_VirtualBlockUpdatePayload.CODEC);

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(net.sha.api.network.C2S_VSEPhysicsSyncPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.world.entity.Entity entity = ((net.minecraft.server.level.ServerLevel)context.player().level()).getEntity(payload.entityId());
                if (entity instanceof VirtualStructureEntity vse) {
                    if (context.player().getStringUUID().equals(vse.getEntityData().get(VirtualStructureEntity.AUTHORITY_OWNER))) {
                        vse.setPos(payload.x(), payload.y(), payload.z());
                        vse.setDeltaMovement(payload.vx(), payload.vy(), payload.vz());
                        vse.setPhysicsRotation(payload.qx(), payload.qy(), payload.qz(), payload.qw());
                    }
                }
            });
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(net.sha.api.network.C2S_VSESleepPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.world.entity.Entity entity = ((net.minecraft.server.level.ServerLevel)context.player().level()).getEntity(payload.entityId());
                if (entity instanceof VirtualStructureEntity vse) {
                    if (context.player().getStringUUID().equals(vse.getEntityData().get(VirtualStructureEntity.AUTHORITY_OWNER))) {
                        vse.setPos(payload.x(), payload.y(), payload.z());
                        vse.setPhysicsRotation(payload.qx(), payload.qy(), payload.qz(), payload.qw());
                        vse.setSleeping(true);
                    }
                }
            });
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(net.sha.api.network.C2S_VirtualBlockActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.world.entity.Entity entity = ((net.minecraft.server.level.ServerLevel)context.player().level()).getEntity(payload.entityId());
                if (entity instanceof VirtualStructureEntity vse) {

                    if (payload.action() == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK || 
                       (payload.action() == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && context.player().getAbilities().instabuild)) {
                        
                        net.minecraft.world.level.block.state.BlockState state = vse.blockMatrix.get(payload.pos());
                        if (state != null && !state.isAir()) {
                            
                            context.player().level().levelEvent(2001, payload.pos(), net.minecraft.world.level.block.Block.getId(state)); 
                            vse.setBlock(payload.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.sha.api.entity.VirtualStructureEntity.DamageCause.PLAYER_MINED);
                        }
                    }
                }
            });
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(net.sha.api.network.C2S_VirtualInteractBlockPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.world.entity.Entity entity = ((net.minecraft.server.level.ServerLevel)context.player().level()).getEntity(payload.entityId());
                if (entity instanceof VirtualStructureEntity vse) {
                    net.minecraft.server.level.ServerPlayer player = context.player();
                    net.minecraft.world.item.ItemStack stack = player.getItemInHand(payload.hand());
                    net.minecraft.world.item.context.UseOnContext useCtx = new net.minecraft.world.item.context.UseOnContext(player, payload.hand(), payload.blockHitResult());

                    if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                        net.minecraft.core.BlockPos placePos = payload.blockHitResult().getBlockPos().relative(payload.blockHitResult().getDirection());
                        net.minecraft.world.level.block.state.BlockState placementState = blockItem.getBlock().getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(useCtx));
                        if (placementState == null) {
                            placementState = blockItem.getBlock().defaultBlockState();
                        }
                        vse.setBlock(placePos, placementState, net.sha.api.entity.VirtualStructureEntity.DamageCause.PLAYER_MINED);
                        if (!player.getAbilities().instabuild) stack.shrink(1);

                        org.joml.Vector3f localPosF = new org.joml.Vector3f(placePos.getX() + 0.5f, placePos.getY() + 0.5f, placePos.getZ() + 0.5f);
                        localPosF.rotate(vse.getPhysicsRotation());
                        localPosF.add(vse.position().toVector3f());
                        net.minecraft.core.BlockPos soundPos = net.minecraft.core.BlockPos.containing(localPosF.x, localPosF.y, localPosF.z);
                        player.level().playSound(null, soundPos, placementState.getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                        
                    } else {
                        
                        net.minecraft.world.level.block.state.BlockState state = vse.blockMatrix.get(payload.blockHitResult().getBlockPos());
                        if (state != null) {
                            
                        }
                    }
                }
            });
        });

        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (trackedEntity instanceof VirtualStructureEntity vse) {
                byte[] payloadData = vse.compressMatrixLogically();
                if (payloadData != null && payloadData.length > 0) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new net.sha.api.network.VirtualStructureSyncPayload(vse.getId(), payloadData));
                }
            }
        });
        
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                    if (entity instanceof VirtualStructureEntity vse) {
                        vse.physicsTick();
                    }
                }
            }
        });
    }
}
