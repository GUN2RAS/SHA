package net.sha.api.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record C2S_VirtualBlockActionPayload(int entityId, ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction direction, int sequence) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<C2S_VirtualBlockActionPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("sha", "c2s_virtual_block_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_VirtualBlockActionPayload> CODEC = StreamCodec.ofMember(C2S_VirtualBlockActionPayload::write, C2S_VirtualBlockActionPayload::new);

    private C2S_VirtualBlockActionPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readEnum(ServerboundPlayerActionPacket.Action.class), buf.readBlockPos(), Direction.from3DDataValue(buf.readUnsignedByte()), buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeEnum(this.action);
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.direction.get3DDataValue());
        buf.writeVarInt(this.sequence);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
