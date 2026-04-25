package net.sha.api.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VirtualStructureSyncPayload(int entityId, byte[] compressedMatrix) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VirtualStructureSyncPayload> ID = new CustomPacketPayload.Type<>(Identifier.tryParse("sha:sync_matrix"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VirtualStructureSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, VirtualStructureSyncPayload::entityId,
            ByteBufCodecs.BYTE_ARRAY, VirtualStructureSyncPayload::compressedMatrix,
            VirtualStructureSyncPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
