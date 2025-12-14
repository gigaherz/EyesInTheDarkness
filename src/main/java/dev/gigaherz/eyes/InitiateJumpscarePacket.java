package dev.gigaherz.eyes;

import dev.gigaherz.eyes.client.ClientMessageHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record InitiateJumpscarePacket(double px, double py, double pz)
        implements CustomPacketPayload
{
    public static final StreamCodec<ByteBuf, InitiateJumpscarePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, InitiateJumpscarePacket::px,
            ByteBufCodecs.DOUBLE, InitiateJumpscarePacket::py,
            ByteBufCodecs.DOUBLE, InitiateJumpscarePacket::pz,
            InitiateJumpscarePacket::new
    );

    public static final Identifier ID = EyesInTheDarkness.location("server_hello");

    public static final Type<InitiateJumpscarePacket> TYPE = new Type<>(ID);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public void handle(IPayloadContext context)
    {
        context.enqueueWork(() -> ClientMessageHandler.handleInitiateJumpscare(this));
    }
}
