package net.sha.api.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.sha.api.network.VirtualStructureSyncPayload;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualStructureEntity extends Entity implements net.sha.api.HologramProvider {
    
    private boolean registeredAsHologram = false;
    
    public final Map<BlockPos, BlockState> blockMatrix = new ConcurrentHashMap<>();
    public final Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> blockEntityMatrix = new ConcurrentHashMap<>();
    
    public static class HologramEntityData {
        public final int entityId;
        public final int entityTypeId;
        public double x, y, z;
        public float pitch, yaw;
        public HologramEntityData(int id, int type, double x, double y, double z, float pitch, float yaw) {
            this.entityId = id; this.entityTypeId = type; this.x = x; this.y = y; this.z = z; this.pitch = pitch; this.yaw = yaw;
        }
    }
    public final Map<Integer, HologramEntityData> hologramEntities = new ConcurrentHashMap<>();
    public final Map<Integer, Entity> dummyEntities = new ConcurrentHashMap<>();

    public Entity getDummyEntity(HologramEntityData data) {
        return dummyEntities.computeIfAbsent(data.entityId, id -> {
            EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.byId(data.entityTypeId);
            if (type != null) {
                Entity e = type.create(this.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
                if (e != null) {
                    e.setPos(this.getX() + data.x, this.getY() + data.y, this.getZ() + data.z);
                    e.setXRot(data.pitch);
                    e.setYRot(data.yaw);
                    e.setYHeadRot(data.yaw);
                }
                return e;
            }
            return null;
        });
    }

    public static final Map<BlockPos, Long> RAPIER_STATIC_COLLIDERS = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Integer> RAPIER_STATIC_REFS = new ConcurrentHashMap<>();
    
    private final java.util.Set<BlockPos> myTerrainBlocks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private BlockPos lastTerrainScanPos = null;
    private double lastRapierX = Double.NaN;
    private double lastRapierY = Double.NaN;
    private double lastRapierZ = Double.NaN;
    public volatile boolean isMatrixDirtyForRender = false;
    private boolean isMatrixDirty = true;

    public volatile long cachedVBOPointer = 0;
    public volatile int cachedVBOSize = 0;
    public volatile com.mojang.blaze3d.vertex.MeshData.DrawState cachedDrawState = null;
    public volatile boolean isVboBaking = false;

    private final Map<BlockPos, Long> blockColliderHandles = new ConcurrentHashMap<>();
    
    private int restTicks = 0;
    private int syncTicks = 0;
    private boolean hasSentSleep = false;

    public enum DamageCause { PLAYER_MINED, EXPLOSION, CRUSH, UNKNOWN }
    private transient boolean needsSplitCheck = false;
    private transient java.util.Set<DamageCause> pendingSplitCauses = java.util.EnumSet.noneOf(DamageCause.class);

    public VirtualStructureEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    private Quaternionf rotationQuaternion = new Quaternionf(0, 0, 0, 1);
    public Quaternionf prevRotationQuaternion = new Quaternionf(0, 0, 0, 1);
    private static final EntityDataAccessor<org.joml.Vector3fc> TARGET_POSITION = SynchedEntityData.defineId(VirtualStructureEntity.class, EntityDataSerializers.VECTOR3);
    public static final EntityDataAccessor<Boolean> IS_SLEEPING = SynchedEntityData.defineId(VirtualStructureEntity.class, EntityDataSerializers.BOOLEAN);
    public static final net.minecraft.network.syncher.EntityDataAccessor<String> AUTHORITY_OWNER = net.minecraft.network.syncher.SynchedEntityData.defineId(VirtualStructureEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    public static final net.minecraft.network.syncher.EntityDataAccessor<Float> SYNC_QX = net.minecraft.network.syncher.SynchedEntityData.defineId(VirtualStructureEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    public static final net.minecraft.network.syncher.EntityDataAccessor<Float> SYNC_QY = net.minecraft.network.syncher.SynchedEntityData.defineId(VirtualStructureEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    public static final net.minecraft.network.syncher.EntityDataAccessor<Float> SYNC_QZ = net.minecraft.network.syncher.SynchedEntityData.defineId(VirtualStructureEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    public static final net.minecraft.network.syncher.EntityDataAccessor<Float> SYNC_QW = net.minecraft.network.syncher.SynchedEntityData.defineId(VirtualStructureEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);

    @Override

    public void syncPacketPositionCodec(double x, double y, double z) {

        if (rapierBodyId == -1) {
            super.syncPacketPositionCodec(x, y, z);
        }
    }

    @Override
    public void tick() {
        if (this.getY() < -150) {
            this.discard();
            return;
        }
        
        if (this.level().isClientSide()) {
            
            net.sha.SHAMain.CLIENT_STRUCTURES.add(this);

            net.sha.SHA.attemptPendingMatrixSync(this);
            this.prevRotationQuaternion.set(this.rotationQuaternion);
        }
        
        super.tick(); 

        if (!this.blockMatrix.isEmpty()) {
            net.sha.api.sandbox.VirtualSandbox.enter(this);
            try {
                
                for (Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> entry : this.blockEntityMatrix.entrySet()) {
                    net.minecraft.world.level.block.entity.BlockEntity be = entry.getValue();
                    if (be != null && !be.isRemoved()) {
                        net.minecraft.world.level.block.entity.BlockEntityTicker<net.minecraft.world.level.block.entity.BlockEntity> ticker = 
                            (net.minecraft.world.level.block.entity.BlockEntityTicker<net.minecraft.world.level.block.entity.BlockEntity>) 
                            be.getBlockState().getTicker(this.level(), (net.minecraft.world.level.block.entity.BlockEntityType<net.minecraft.world.level.block.entity.BlockEntity>)be.getType());
                        if (ticker != null) {
                            ticker.tick(this.level(), entry.getKey(), be.getBlockState(), be);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[SHA] Error ticking virtual structure components: " + e.getMessage());
            } finally {
                net.sha.api.sandbox.VirtualSandbox.exit();
            }
        }
        
        if (this.level().isClientSide()) {
            this.clientRapierPhysicsTick();
            
            if (this.getAuthorityOwner() != null && this.getAuthorityOwner().startsWith("portal_mirage") && !registeredAsHologram) {
                net.sha.api.SHAHologramManager.updateSpatialMap(this);
                registeredAsHologram = true;
            }
        }
    }

    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        super.remove(reason);
        if (this.level().isClientSide()) {
            net.sha.SHAMain.CLIENT_STRUCTURES.remove(this);
            if (net.sha.SHA.CLIENT_WORLD_ID != -1) {
                
                if (rapierBodyId != -1) {
                    net.sha.api.physics.RapierEngine.removeDynamicStructure(net.sha.SHA.CLIENT_WORLD_ID, rapierBodyId);
                    rapierBodyId = -1;
                }
                
                if (!myTerrainBlocks.isEmpty()) {
                    for (BlockPos pos : myTerrainBlocks) {
                        releaseCollider(pos);
                    }
                    myTerrainBlocks.clear();
                }
            }
            if (cachedVBOPointer != 0) {
                org.lwjgl.system.MemoryUtil.nmemFree(cachedVBOPointer);
                cachedVBOPointer = 0;
            }
        }
    }
    private void updateTerrainColliders(double vx, double vy, double vz) {
        if (net.sha.SHA.CLIENT_WORLD_ID == -1 || (this.getAuthorityOwner() != null && this.getAuthorityOwner().startsWith("portal_mirage"))) return;
        this.refreshDimensions();
        
        BlockPos currentPos = this.blockPosition();
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);

        if (lastTerrainScanPos != null && currentPos.distManhattan(lastTerrainScanPos) == 0 && speed < 0.01) {
            
            var restIterator = myTerrainBlocks.iterator();
            while (restIterator.hasNext()) {
                BlockPos pos = restIterator.next();
                if (this.level().getBlockState(pos).isAir()) {
                    releaseCollider(pos);
                    restIterator.remove();
                }
            }
            return;
        }
        lastTerrainScanPos = currentPos;

        double r = this.getDimensions(net.minecraft.world.entity.Pose.STANDING).width() / 2.0;
        net.minecraft.world.phys.AABB baseBounds = new net.minecraft.world.phys.AABB(
            this.getX() - r, this.getY() - r, this.getZ() - r,
            this.getX() + r, this.getY() + r, this.getZ() + r
        );

        net.minecraft.world.phys.AABB bounds = baseBounds.expandTowards(vx * 3.0, vy * 3.0, vz * 3.0).inflate(0.5f);
        
        int minX = net.minecraft.util.Mth.floor(bounds.minX);
        int minY = net.minecraft.util.Mth.floor(bounds.minY);
        int minZ = net.minecraft.util.Mth.floor(bounds.minZ);
        int maxX = net.minecraft.util.Mth.floor(bounds.maxX);
        int maxY = net.minecraft.util.Mth.floor(bounds.maxY);
        int maxZ = net.minecraft.util.Mth.floor(bounds.maxZ);

        var iterator = myTerrainBlocks.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!bounds.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                releaseCollider(pos);
                iterator.remove();
            }
        }

        java.util.Set<BlockPos> currentVisibleBlocks = new java.util.HashSet<>();
        
        BlockPos.MutableBlockPos pos_offset = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor_offset = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos_offset.set(x, y, z);
                    BlockState state = this.level().getBlockState(pos_offset);
                    if (!state.isAir() && !state.getCollisionShape(this.level(), pos_offset).isEmpty()) {

                        boolean exposed = false;
                        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                            neighbor_offset.setWithOffset(pos_offset, dir);
                            if (this.level().getBlockState(neighbor_offset).getCollisionShape(this.level(), neighbor_offset).isEmpty()) {
                                exposed = true;
                                break;
                            }
                        }
                        if (exposed) {
                            currentVisibleBlocks.add(pos_offset.immutable());
                        }
                    }
                }
            }
        }

        for (BlockPos pos : currentVisibleBlocks) {
            if (!myTerrainBlocks.contains(pos)) {
                acquireCollider(pos, this.level().getBlockState(pos));
                myTerrainBlocks.add(pos);
            }
        }
    }

    private void acquireCollider(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        int refs = RAPIER_STATIC_REFS.getOrDefault(pos, 0);
        if (refs == 0) {
            net.minecraft.world.phys.AABB voxelBox = state.getCollisionShape(this.level(), pos).bounds();
            float cx = (float)(pos.getX() + voxelBox.minX + (voxelBox.maxX - voxelBox.minX) / 2.0);
            float cy = (float)(pos.getY() + voxelBox.minY + (voxelBox.maxY - voxelBox.minY) / 2.0);
            float cz = (float)(pos.getZ() + voxelBox.minZ + (voxelBox.maxZ - voxelBox.minZ) / 2.0);
            float hx = (float)((voxelBox.maxX - voxelBox.minX) / 2.0);
            float hy = (float)((voxelBox.maxY - voxelBox.minY) / 2.0);
            float hz = (float)((voxelBox.maxZ - voxelBox.minZ) / 2.0);
            long handle = net.sha.api.physics.RapierEngine.addStaticBox(net.sha.SHA.CLIENT_WORLD_ID, cx, cy, cz, hx, hy, hz);
            RAPIER_STATIC_COLLIDERS.put(pos, handle);
        }
        RAPIER_STATIC_REFS.put(pos, refs + 1);
    }

    private void releaseCollider(BlockPos pos) {
        int refs = RAPIER_STATIC_REFS.getOrDefault(pos, 0);
        if (refs <= 1) {
            Long handle = RAPIER_STATIC_COLLIDERS.remove(pos);
            if (handle != null) {
                try { net.sha.api.physics.RapierEngine.removeCollider(net.sha.SHA.CLIENT_WORLD_ID, handle); } catch (Exception ignored) {}
            }
            RAPIER_STATIC_REFS.remove(pos);
        } else {
            RAPIER_STATIC_REFS.put(pos, refs - 1);
        }
    }

    public void setAuthorityOwner(String uuid) {
        if (uuid == null) uuid = "";
        this.entityData.set(AUTHORITY_OWNER, uuid);
    }

    public String getAuthorityOwner() {
        return this.entityData.get(AUTHORITY_OWNER);
    }

    private void clientRapierPhysicsTick() {
        if (net.sha.SHA.CLIENT_WORLD_ID == -1) return;
        if (this.blockMatrix.isEmpty()) return;
        
        if (this.getAuthorityOwner() != null && this.getAuthorityOwner().startsWith("portal_mirage")) return;

        net.minecraft.client.player.LocalPlayer clientPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (clientPlayer == null) return;
        
        String ownerUuid = this.entityData.get(AUTHORITY_OWNER);
        if (!clientPlayer.getStringUUID().equals(ownerUuid)) {

            return;
        }
        
        if (this.entityData.get(IS_SLEEPING)) {

        }

        if (this.getX() == 0.0 && this.getY() == 0.0 && this.getZ() == 0.0) return;

        rapierWorldId = net.sha.SHA.CLIENT_WORLD_ID;

        if (rapierBodyId == -1) {
            
            this.updateTerrainColliders(0, 0, 0);
            try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
                java.util.List<BlockPos> solidBlocks = new java.util.ArrayList<>();
                for (java.util.Map.Entry<BlockPos, BlockState> entry : this.blockMatrix.entrySet()) {
                    if (!entry.getValue().getCollisionShape(this.level(), entry.getKey()).isEmpty()) {
                        solidBlocks.add(entry.getKey());
                    }
                }
                int blockCount = solidBlocks.size();
                if (blockCount > 0) {
                    java.lang.foreign.MemorySegment blocksPtr = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT, blockCount * 3L);
                    java.lang.foreign.MemorySegment outHandlesPtr = arena.allocate(java.lang.foreign.ValueLayout.JAVA_LONG, blockCount);
                    int idx = 0;
                    BlockPos[] keys = solidBlocks.toArray(new BlockPos[0]);
                    for (BlockPos localPos : keys) {
                        blocksPtr.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, idx * 3L, localPos.getX());
                        blocksPtr.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, idx * 3L + 1, localPos.getY());
                        blocksPtr.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, idx * 3L + 2, localPos.getZ());
                        idx++;
                    }
                    rapierBodyId = net.sha.api.physics.RapierEngine.addDynamicStructure(rapierWorldId, (float)this.getX(), (float)this.getY(), (float)this.getZ(), blocksPtr, blockCount, outHandlesPtr);
                    
                    this.rotationQuaternion.normalize();
                    net.sha.api.physics.RapierEngine.setRotation(rapierWorldId, rapierBodyId, this.rotationQuaternion.x(), this.rotationQuaternion.y(), this.rotationQuaternion.z(), this.rotationQuaternion.w());
                    net.minecraft.world.phys.Vec3 vel = this.getDeltaMovement();
                    net.sha.api.physics.RapierEngine.setLinearVelocity(rapierWorldId, rapierBodyId, (float)vel.x, (float)vel.y, (float)vel.z);
                    
                    for (int i = 0; i < blockCount; i++) {
                        long handle = outHandlesPtr.getAtIndex(java.lang.foreign.ValueLayout.JAVA_LONG, i);
                        this.blockColliderHandles.put(keys[i], handle);
                    }
                    
                    lastRapierX = this.getX();
                    lastRapierY = this.getY();
                    lastRapierZ = this.getZ();
                    
                }
            }
            return;
        }
        
        if (rapierWorldId != -1 && rapierBodyId != -1) {
            
            float[] pos = net.sha.api.physics.RapierEngine.getPosition(rapierWorldId, rapierBodyId);
            float[] rot = net.sha.api.physics.RapierEngine.getRotation(rapierWorldId, rapierBodyId);
            
            if (Float.isNaN(pos[0]) || Float.isNaN(pos[1]) || Float.isNaN(pos[2])) {
                System.err.println("[SHA] CRITICAL: Rapier returned NaN position! Rescuing entity.");
                return;
            }

            double newX = pos[0], newY = pos[1], newZ = pos[2];
            double vx = Double.isNaN(lastRapierX) ? 0.0 : (newX - lastRapierX);
            double vy = Double.isNaN(lastRapierY) ? 0.0 : (newY - lastRapierY);
            double vz = Double.isNaN(lastRapierZ) ? 0.0 : (newZ - lastRapierZ);
            
            this.setDeltaMovement(vx, vy, vz);

            this.isRapierUpdating = true;
            this.setPos(newX, newY, newZ);
            this.isRapierUpdating = false;
            lastRapierX = newX;
            lastRapierY = newY;
            lastRapierZ = newZ;
            this.setRotationFromRapier(rot);

            double speedSq = vx * vx + vy * vy + vz * vz;
            if (speedSq < 0.0025) { 
                restTicks++;
            } else {
                restTicks = 0;
            }

            if (restTicks >= 20 && !hasSentSleep) {
                
                hasSentSleep = true;
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new net.sha.api.network.C2S_VSESleepPayload(this.getId(), newX, newY, newZ, rot[0], rot[1], rot[2], rot[3])
                );
            } else if (!hasSentSleep) {
                
                syncTicks++;
                if (syncTicks >= 2) {
                    syncTicks = 0;
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new net.sha.api.network.C2S_VSEPhysicsSyncPayload(this.getId(), newX, newY, newZ, rot[0], rot[1], rot[2], rot[3], vx, vy, vz)
                    );
                }
            }

            this.updateTerrainColliders(vx, vy, vz);
        }
    }
    
    public int getRapierWorldId() {
        return this.rapierWorldId;
    }

    public void applyMatrix(Map<BlockPos, BlockState> blocks) {
        if (this.level().isClientSide() && rapierBodyId != -1) {
            net.sha.api.physics.RapierEngine.removeDynamicStructure(rapierWorldId, rapierBodyId);
            rapierBodyId = -1;
            this.blockColliderHandles.clear();
        }
        this.blockMatrix.clear();
        this.blockMatrix.putAll(blocks);
        this.isMatrixDirty = true;
        this.isMatrixDirtyForRender = true;
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(TARGET_POSITION, new org.joml.Vector3f(0f, 0f, 0f));
        builder.define(IS_SLEEPING, false);
        builder.define(AUTHORITY_OWNER, "");
        builder.define(SYNC_QX, 0.0f);
        builder.define(SYNC_QY, 0.0f);
        builder.define(SYNC_QZ, 0.0f);
        builder.define(SYNC_QW, 1.0f);
    }
    
    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> pKey) {
        super.onSyncedDataUpdated(pKey);
        if (this.level().isClientSide() && rapierBodyId == -1) {
            
            if (SYNC_QX.equals(pKey) || SYNC_QY.equals(pKey) || SYNC_QZ.equals(pKey) || SYNC_QW.equals(pKey)) {
                this.rotationQuaternion.set(
                    this.entityData.get(SYNC_QX),
                    this.entityData.get(SYNC_QY),
                    this.entityData.get(SYNC_QZ),
                    this.entityData.get(SYNC_QW)
                );
                if (this.rotationQuaternion.lengthSquared() > 0.0001f) {
                    this.rotationQuaternion.normalize();
                } else {
                    this.rotationQuaternion.set(0.0f, 0.0f, 0.0f, 1.0f);
                }
            }
        }
    }

    public boolean isSleeping() {
        return this.entityData.get(IS_SLEEPING);
    }
    public void setSleeping(boolean sleeping) {
        this.entityData.set(IS_SLEEPING, sleeping);
    }

    public void setRotationFromRapier(float[] quat) {
        this.rotationQuaternion.set(quat[0], quat[1], quat[2], quat[3]);
    }

    public Quaternionfc getPhysicsRotation() {
        return this.rotationQuaternion;
    }

    public void setBlock(BlockPos pos, BlockState state) {
        setBlock(pos, state, DamageCause.UNKNOWN);
    }
    
    public void setBlock(BlockPos pos, BlockState state, DamageCause cause) {
        if (state == null || state.isAir()) {
            this.blockMatrix.remove(pos);
            net.minecraft.world.level.block.entity.BlockEntity oldBe = this.blockEntityMatrix.remove(pos);
            if (oldBe != null) oldBe.setRemoved();
            
            if (this.level().isClientSide() && rapierBodyId != -1) {
                Long handle = this.blockColliderHandles.remove(pos);
                if (handle != null) {
                    try { net.sha.api.physics.RapierEngine.removeCollider(rapierWorldId, handle); } catch(Exception ignored) {}
                }
            }
        } else {
            this.blockMatrix.put(pos, state);
            if (state.hasBlockEntity()) {
                net.minecraft.world.level.block.entity.BlockEntity be = ((net.minecraft.world.level.block.EntityBlock)state.getBlock()).newBlockEntity(pos, state);
                if (be != null) {
                    be.setLevel(this.level());
                    this.blockEntityMatrix.put(pos, be);
                }
            } else {
                net.minecraft.world.level.block.entity.BlockEntity oldBe = this.blockEntityMatrix.remove(pos);
                if (oldBe != null) oldBe.setRemoved();
            }
            
            if (this.level().isClientSide() && rapierBodyId != -1) {
                Long oldHandle = this.blockColliderHandles.remove(pos);
                if (oldHandle != null) {
                    try { net.sha.api.physics.RapierEngine.removeCollider(rapierWorldId, oldHandle); } catch(Exception ignored) {}
                }
                if (!state.getCollisionShape(this.level(), pos).isEmpty()) {
                    long newHandle = net.sha.api.physics.RapierEngine.addBlockToStructure(rapierWorldId, rapierBodyId, pos.getX(), pos.getY(), pos.getZ());
                    if (newHandle != -1) {
                        this.blockColliderHandles.put(pos.immutable(), newHandle);
                    }
                }
            }
        }
        
        if (!this.level().isClientSide() && (state == null || state.isAir())) {
            this.needsSplitCheck = true;
            this.pendingSplitCauses.add(cause);
            this.setSleeping(false);
        }
        
        if (!this.level().isClientSide()) {
            int stateId = (state == null || state.isAir()) ? 0 : net.minecraft.world.level.block.Block.getId(state);
            net.sha.api.network.S2C_VirtualBlockUpdatePayload payload = new net.sha.api.network.S2C_VirtualBlockUpdatePayload(this.getId(), pos.immutable(), stateId);
            for (net.minecraft.server.level.ServerPlayer p : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(this)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, payload);
            }
            
        } else {
            this.isMatrixDirtyForRender = true;
            this.refreshDimensions();
        }
    }

    public byte[] compressMatrixLogically() {
        if (this.blockMatrix.isEmpty() && this.hologramEntities.isEmpty()) return new byte[0];
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(baos);
            DataOutputStream dos = new DataOutputStream(gzip);
            dos.writeInt(this.blockMatrix.size());
            for (Map.Entry<BlockPos, BlockState> entry : this.blockMatrix.entrySet()) {
                dos.writeLong(entry.getKey().asLong());
                dos.writeInt(net.minecraft.world.level.block.Block.getId(entry.getValue()));
            }
            dos.writeInt(this.hologramEntities.size());
            for (HologramEntityData hed : this.hologramEntities.values()) {
                dos.writeInt(hed.entityId);
                dos.writeInt(hed.entityTypeId);
                dos.writeDouble(hed.x);
                dos.writeDouble(hed.y);
                dos.writeDouble(hed.z);
                dos.writeFloat(hed.pitch);
                dos.writeFloat(hed.yaw);
            }
            dos.flush();
            gzip.finish();
            gzip.flush();
            dos.close();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public void applyCompressedMatrix(byte[] data) {
        if (data == null || data.length == 0) return;
        if (this.level().isClientSide() && rapierBodyId != -1) {
            net.sha.api.physics.RapierEngine.removeDynamicStructure(rapierWorldId, rapierBodyId);
            rapierBodyId = -1;
            this.blockColliderHandles.clear();
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            GZIPInputStream gzip = new GZIPInputStream(bais);
            DataInputStream dis = new DataInputStream(gzip);
            int size = dis.readInt();
            this.blockMatrix.clear();
            for (int i = 0; i < size; i++) {
                long posLong = dis.readLong();
                int stateId = dis.readInt();
                BlockState state = net.minecraft.world.level.block.Block.stateById(stateId);
                if (state != null) {
                    this.blockMatrix.put(BlockPos.of(posLong), state);
                }
            }
            this.hologramEntities.clear();
            try {
                int entSize = dis.readInt();
                for (int i = 0; i < entSize; i++) {
                    int eid = dis.readInt();
                    int etype = dis.readInt();
                    double x = dis.readDouble();
                    double y = dis.readDouble();
                    double z = dis.readDouble();
                    float pitch = dis.readFloat();
                    float yaw = dis.readFloat();
                    this.hologramEntities.put(eid, new HologramEntityData(eid, etype, x, y, z, pitch, yaw));
                }
            } catch (java.io.EOFException ignored) {} 

            dis.close();
            this.isMatrixDirtyForRender = true;
            this.refreshDimensions();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void syncMatrixToTrackingPlayers() {
        if (this.level().isClientSide()) return;
        byte[] payloadData = this.compressMatrixLogically();
        if (payloadData != null && payloadData.length > 0) {
            if (this.level().getChunkSource() instanceof net.minecraft.server.level.ServerChunkCache scc) {
                int sent = 0;
                for (net.minecraft.server.level.ServerPlayer p : scc.chunkMap.getPlayers(this.chunkPosition(), false)) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new net.sha.api.network.VirtualStructureSyncPayload(this.getId(), payloadData));
                    sent++;
                }
            }

        }
        this.isMatrixDirty = false;
    }

    public Map<BlockPos, BlockState> getBlockMatrix() {
        return this.blockMatrix;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        CompoundTag tag = input.<CompoundTag>read("VSEData", CompoundTag.CODEC).orElse(null);
        if (tag != null) {
            this.setSleeping(tag.getBoolean("isSleeping").orElse(false));
            this.entityData.set(AUTHORITY_OWNER, tag.getString("ownerUUID").orElse(""));
            this.rotationQuaternion.set(tag.getFloat("qX").orElse(0.0f), tag.getFloat("qY").orElse(0.0f), tag.getFloat("qZ").orElse(0.0f), tag.getFloat("qW").orElse(1.0f));
            this.prevRotationQuaternion.set(this.rotationQuaternion);
            
            if (tag.contains("blocks")) {
                net.minecraft.nbt.ListTag list = tag.getList("blocks").orElse(new net.minecraft.nbt.ListTag());
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag blockTag = list.getCompound(i).orElse(new CompoundTag());
                    BlockPos pos = new BlockPos(blockTag.getInt("px").orElse(0), blockTag.getInt("py").orElse(0), blockTag.getInt("pz").orElse(0));
                    BlockState state = net.minecraft.world.level.block.Block.stateById(blockTag.getInt("st").orElse(0));
                    if (state != null) {
                        this.blockMatrix.put(pos, state);
                    }
                }
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isSleeping", this.entityData.get(IS_SLEEPING));
        tag.putString("ownerUUID", this.entityData.get(AUTHORITY_OWNER));
        tag.putFloat("qX", this.rotationQuaternion.x());
        tag.putFloat("qY", this.rotationQuaternion.y());
        tag.putFloat("qZ", this.rotationQuaternion.z());
        tag.putFloat("qW", this.rotationQuaternion.w());
        
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (Map.Entry<BlockPos, BlockState> entry : this.blockMatrix.entrySet()) {
            CompoundTag blockTag = new CompoundTag();
            blockTag.putInt("px", entry.getKey().getX());
            blockTag.putInt("py", entry.getKey().getY());
            blockTag.putInt("pz", entry.getKey().getZ());
            blockTag.putInt("st", net.minecraft.world.level.block.Block.getId(entry.getValue()));
            list.add(blockTag);
        }
        tag.put("blocks", list);
        output.store("VSEData", CompoundTag.CODEC, tag);
    }
    
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    private int rapierWorldId = -1;
    private int rapierBodyId = -1;
    private boolean isRapierUpdating = false;

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        if (this.blockMatrix == null || this.blockMatrix.isEmpty()) {
            return super.getDimensions(pose);
        }
        
        double maxDistSq = 0;
        for (net.minecraft.core.BlockPos p : this.blockMatrix.keySet()) {
            double distSq = p.getX()*p.getX() + p.getY()*p.getY() + p.getZ()*p.getZ();
            if (distSq > maxDistSq) maxDistSq = distSq;
        }
        float radius = (float) Math.sqrt(maxDistSq) + 1.0f;
        float width = radius * 2.0f;
        float height = radius * 2.0f;
        return net.minecraft.world.entity.EntityDimensions.fixed(width, height);
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (!this.level().isClientSide() || rapierWorldId == -1 || isRapierUpdating) {
            super.setPos(x, y, z);
        }
    }

    @Override
    public net.minecraft.world.entity.InterpolationHandler getInterpolation() {
        if (this.level().isClientSide() && rapierWorldId != -1) {

            return new net.minecraft.world.entity.InterpolationHandler(this) {
                @Override
                public void interpolate() {
                    
                }

                @Override
                public net.minecraft.world.phys.Vec3 position() {
                    return VirtualStructureEntity.this.position();
                }

                @Override
                public float yRot() {
                    return VirtualStructureEntity.this.getYRot();
                }

                @Override
                public float xRot() {
                    return VirtualStructureEntity.this.getXRot();
                }
            };
        }
        return super.getInterpolation();
    }

    public void physicsTick() {
        if (!this.level().isClientSide()) {
            if (this.isSleeping()) return;

            if (this.tickCount == 5 && !this.blockMatrix.isEmpty()) {
                this.syncMatrixToTrackingPlayers();
            } else if (this.isMatrixDirty) {
                this.syncMatrixToTrackingPlayers();
            }
            
            if (this.needsSplitCheck && !this.blockMatrix.isEmpty()) {
                this.needsSplitCheck = false;
                this.detectAndProcessSplits();
            }

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            this.xOld = this.getX();
            this.yOld = this.getY();
            this.zOld = this.getZ();
        }
    }

    public void setPhysicsRotation(float qx, float qy, float qz, float qw) {
        this.rotationQuaternion.set(qx, qy, qz, qw);
        if (!this.level().isClientSide()) {
            this.entityData.set(SYNC_QX, qx);
            this.entityData.set(SYNC_QY, qy);
            this.entityData.set(SYNC_QZ, qz);
            this.entityData.set(SYNC_QW, qw);
        }
    }

    private AABB rotateAABB(AABB box, Quaternionf quat) {
        org.joml.Vector3f[] corners = new org.joml.Vector3f[] {
            new org.joml.Vector3f((float)box.minX, (float)box.minY, (float)box.minZ),
            new org.joml.Vector3f((float)box.minX, (float)box.minY, (float)box.maxZ),
            new org.joml.Vector3f((float)box.minX, (float)box.maxY, (float)box.minZ),
            new org.joml.Vector3f((float)box.minX, (float)box.maxY, (float)box.maxZ),
            new org.joml.Vector3f((float)box.maxX, (float)box.minY, (float)box.minZ),
            new org.joml.Vector3f((float)box.maxX, (float)box.minY, (float)box.maxZ),
            new org.joml.Vector3f((float)box.maxX, (float)box.maxY, (float)box.minZ),
            new org.joml.Vector3f((float)box.maxX, (float)box.maxY, (float)box.maxZ)
        };
        
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        
        for (org.joml.Vector3f c : corners) {
            c.rotate(quat);
            if (c.x < minX) minX = c.x;
            if (c.y < minY) minY = c.y;
            if (c.z < minZ) minZ = c.z;
            if (c.x > maxX) maxX = c.x;
            if (c.y > maxY) maxY = c.y;
            if (c.z > maxZ) maxZ = c.z;
        }
        
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public net.minecraft.world.phys.Vec3 resolveMathematicalCollision(AABB worldAABB, net.minecraft.world.phys.Vec3 worldMovement) {
        if (this.blockMatrix.isEmpty()) return worldMovement;

        AABB localAABB = worldAABB.move(-this.getX(), -this.getY(), -this.getZ());
        
        Quaternionf invQuat = new Quaternionf(this.rotationQuaternion).invert();
        AABB searchBox = rotateAABB(localAABB, invQuat);
        
        org.joml.Vector3f localMovementVec = new org.joml.Vector3f((float)worldMovement.x, (float)worldMovement.y, (float)worldMovement.z);
        localMovementVec.rotate(invQuat);
        net.minecraft.world.phys.Vec3 localMovement = new net.minecraft.world.phys.Vec3(localMovementVec.x(), localMovementVec.y(), localMovementVec.z());

        AABB sweepBox = searchBox.expandTowards(localMovement).inflate(1.0);
        double min_x = sweepBox.minX;
        double min_y = sweepBox.minY;
        double min_z = sweepBox.minZ;
        double max_x = sweepBox.maxX;
        double max_y = sweepBox.maxY;
        double max_z = sweepBox.maxZ;

        List<VoxelShape> localShapes = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = (int)Math.floor(min_x); x <= Math.ceil(max_x); x++) {
            for (int y = (int)Math.floor(min_y); y <= Math.ceil(max_y); y++) {
                for (int z = (int)Math.floor(min_z); z <= Math.ceil(max_z); z++) {
                    mpos.set(x, y, z);
                    BlockState st = this.blockMatrix.get(mpos);
                    if (st != null && !st.isAir() && !st.getCollisionShape(this.level(), mpos).isEmpty()) {
                        VoxelShape shape = st.getCollisionShape(this.level(), mpos).move(x, y, z);
                        localShapes.add(shape);
                    }
                }
            }
        }
        
        if (localShapes.isEmpty()) return worldMovement;

        net.minecraft.world.phys.Vec3 resolvedLocalMovement = net.minecraft.world.phys.Vec3.ZERO;
        for (net.minecraft.core.Direction.Axis axis : net.minecraft.core.Direction.axisStepOrder(localMovement)) {
            double comp = localMovement.get(axis);
            if (comp != 0.0) {
                double allowed = net.minecraft.world.phys.shapes.Shapes.collide(axis, searchBox.move(resolvedLocalMovement), localShapes, comp);
                resolvedLocalMovement = resolvedLocalMovement.with(axis, allowed);
            }
        }

        org.joml.Vector3f resolvedWorldVec = new org.joml.Vector3f((float)resolvedLocalMovement.x, (float)resolvedLocalMovement.y, (float)resolvedLocalMovement.z);
        resolvedWorldVec.rotate(this.rotationQuaternion);
        
        return new net.minecraft.world.phys.Vec3(resolvedWorldVec.x(), resolvedWorldVec.y(), resolvedWorldVec.z());
    }

    public net.minecraft.world.phys.BlockHitResult raycastLocalBlocks(net.minecraft.world.phys.Vec3 start, net.minecraft.world.phys.Vec3 end) {
        return net.minecraft.world.level.BlockGetter.traverseBlocks(start, end, this, (entity, pos) -> {
            BlockState state = entity.blockMatrix.get(pos);
            if (state == null || state.isAir()) return null;
            
            VoxelShape shape = state.getShape(entity.level(), pos);
            if (shape.isEmpty()) return null;

            net.minecraft.world.phys.BlockHitResult hit = shape.clip(start, end, pos);
            if (hit != null) {
                net.minecraft.world.phys.BlockHitResult specializedHit = state.getVisualShape(entity.level(), pos, net.minecraft.world.phys.shapes.CollisionContext.empty()).clip(start, end, pos);
                if (specializedHit != null && specializedHit.getLocation().subtract(start).lengthSqr() < hit.getLocation().subtract(start).lengthSqr()) {
                    hit = hit.withDirection(specializedHit.getDirection());
                }
            }
            return hit;
        }, (entity) -> {
            net.minecraft.world.phys.Vec3 vec = start.subtract(end);
            return net.minecraft.world.phys.BlockHitResult.miss(end, net.minecraft.core.Direction.getApproximateNearest(vec), BlockPos.containing(end));
        });
    }

    public void detectAndProcessSplits() {
        if (this.blockMatrix.isEmpty()) return;
        
        java.util.Set<BlockPos> unvisited = new java.util.HashSet<>(this.blockMatrix.keySet());
        java.util.List<java.util.Set<BlockPos>> islands = new java.util.ArrayList<>();
        
        BlockPos[] DIRS = new BlockPos[]{
            BlockPos.ZERO.above(), BlockPos.ZERO.below(),
            BlockPos.ZERO.north(), BlockPos.ZERO.south(),
            BlockPos.ZERO.east(), BlockPos.ZERO.west()
        };
        
        while (!unvisited.isEmpty()) {
            BlockPos startNode = unvisited.iterator().next();
            java.util.Set<BlockPos> island = new java.util.HashSet<>();
            java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
            
            queue.add(startNode);
            unvisited.remove(startNode);
            island.add(startNode);
            
            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                for (BlockPos d : DIRS) {
                    BlockPos neighbor = current.offset(d);
                    if (unvisited.contains(neighbor)) {
                        unvisited.remove(neighbor);
                        queue.add(neighbor);
                        island.add(neighbor);
                    }
                }
            }
            islands.add(island);
        }
        
        if (islands.size() <= 1) {
            this.pendingSplitCauses.clear();
            return;
        }
        
        java.util.Set<BlockPos> largestIsland = null;
        int maxSize = -1;
        for (java.util.Set<BlockPos> is : islands) {
            if (is.size() > maxSize) {
                maxSize = is.size();
                largestIsland = is;
            }
        }
        
        boolean hasExplosion = this.pendingSplitCauses.contains(DamageCause.EXPLOSION) || this.pendingSplitCauses.contains(DamageCause.CRUSH);
        boolean hasMined = this.pendingSplitCauses.contains(DamageCause.PLAYER_MINED);
        
        for (java.util.Set<BlockPos> is : islands) {
            if (is == largestIsland) continue;
            
            boolean spawnPhysical = true;
            if (hasExplosion && !hasMined) {
                if (is.size() < 30) spawnPhysical = false;
            }
            
            if (spawnPhysical) {
                VirtualStructureEntity newVse = new VirtualStructureEntity(net.sha.SHAMain.VIRTUAL_STRUCTURE_ENTITY, this.level());
                newVse.setPos(this.getX(), this.getY(), this.getZ());
                newVse.setPhysicsRotation(this.rotationQuaternion.x(), this.rotationQuaternion.y(), this.rotationQuaternion.z(), this.rotationQuaternion.w());
                
                net.minecraft.world.phys.Vec3 parentVel = this.getDeltaMovement();
                double rx = (this.random.nextFloat() - 0.5) * 0.15;
                double ry = (this.random.nextFloat() - 0.5) * 0.15;
                double rz = (this.random.nextFloat() - 0.5) * 0.15;
                newVse.setDeltaMovement(parentVel.add(rx, ry, rz));
                
                newVse.setAuthorityOwner(this.getAuthorityOwner());
                
                for (BlockPos p : is) {
                    BlockState s = this.blockMatrix.remove(p);
                    net.minecraft.world.level.block.entity.BlockEntity be = this.blockEntityMatrix.remove(p);
                    if (s != null) newVse.blockMatrix.put(p, s);
                    if (be != null) newVse.blockEntityMatrix.put(p, be);
                }
                
                newVse.isMatrixDirty = true;
                this.level().addFreshEntity(newVse);
            } else {
                for (BlockPos p : is) {
                    this.blockMatrix.remove(p);
                    this.blockEntityMatrix.remove(p);
                    
                    org.joml.Vector3f globalPos = new org.joml.Vector3f((float)p.getX() + 0.5f, (float)p.getY() + 0.5f, (float)p.getZ() + 0.5f);
                    globalPos.rotate(this.rotationQuaternion);
                    globalPos.add((float)this.getX(), (float)this.getY(), (float)this.getZ());
                    ((net.minecraft.server.level.ServerLevel)this.level()).sendParticles(
                        net.minecraft.core.particles.ParticleTypes.SMOKE, globalPos.x, globalPos.y, globalPos.z, 5, 0.2, 0.2, 0.2, 0.05
                    );
                }
            }
        }
        
        this.pendingSplitCauses.clear();
        this.isMatrixDirty = true;
        this.refreshDimensions();
    }

    @Override
    public BlockState getSpoofedBlock(int x, int y, int z) {

        if (this.getAuthorityOwner() != null && this.getAuthorityOwner().startsWith("portal_mirage")) return null;
        
        BlockPos local = new BlockPos(x, y, z).subtract(this.blockPosition());
        return this.blockMatrix.get(local);
    }

    @Override
    public boolean isActive() {
        return this.getAuthorityOwner() != null && this.getAuthorityOwner().startsWith("portal_mirage") && !this.isRemoved();
    }

    @Override
    public boolean forcesEmptyChunkRendering() {
        return false; 
    }

    @Override
    public boolean providesCollision() {
        return true; 
    }

    @Override
    public net.sha.api.HologramBounds getBounds() {
        net.minecraft.world.phys.AABB box = this.getBoundingBox();
        return new net.sha.api.HologramBounds((int)box.minX, (int)box.minY, (int)box.minZ, (int)box.maxX, (int)box.maxY, (int)box.maxZ);
    }
}
