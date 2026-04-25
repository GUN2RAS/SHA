package net.sha.api.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;

public record C2S_VirtualInteractBlockPayload(int entityId, InteractionHand hand, BlockHitResult blockHitResult, int sequence) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<C2S_VirtualInteractBlockPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("sha", "c2s_virtual_interact_block"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2S_VirtualInteractBlockPayload> CODEC = StreamCodec.ofMember(C2S_VirtualInteractBlockPayload::write, C2S_VirtualInteractBlockPayload::new);

    private C2S_VirtualInteractBlockPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readEnum(InteractionHand.class), buf.readBlockHitResult(), buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeEnum(this.hand);
        buf.writeBlockHitResult(this.blockHitResult);
        buf.writeVarInt(this.sequence);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
