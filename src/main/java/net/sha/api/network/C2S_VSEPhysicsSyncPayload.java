package net.sha.api.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2S_VSEPhysicsSyncPayload(int entityId, double x, double y, double z, float qx, float qy, float qz, float qw, double vx, double vy, double vz) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<C2S_VSEPhysicsSyncPayload> ID = new CustomPacketPayload.Type<>(Identifier.tryParse("sha:c2s_vse_physics_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_VSEPhysicsSyncPayload> CODEC = StreamCodec.ofMember(
        (payload, buf) -> {
            buf.writeInt(payload.entityId());
            buf.writeDouble(payload.x());
            buf.writeDouble(payload.y());
            buf.writeDouble(payload.z());
            buf.writeFloat(payload.qx());
            buf.writeFloat(payload.qy());
            buf.writeFloat(payload.qz());
            buf.writeFloat(payload.qw());
            buf.writeDouble(payload.vx());
            buf.writeDouble(payload.vy());
            buf.writeDouble(payload.vz());
        },
        buf -> new C2S_VSEPhysicsSyncPayload(
            buf.readInt(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readDouble(), buf.readDouble(), buf.readDouble()
        )
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
