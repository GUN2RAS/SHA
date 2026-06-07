package net.sha.api.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2C_VirtualBlockUpdatePayload(int entityId, BlockPos pos, int stateId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<S2C_VirtualBlockUpdatePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("sha", "s2c_virtual_block_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2C_VirtualBlockUpdatePayload> CODEC = StreamCodec.ofMember(S2C_VirtualBlockUpdatePayload::write, S2C_VirtualBlockUpdatePayload::new);

    private S2C_VirtualBlockUpdatePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBlockPos(), buf.readInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.stateId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
