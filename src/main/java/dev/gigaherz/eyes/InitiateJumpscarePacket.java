package dev.gigaherz.eyes;

import dev.gigaherz.eyes.client.ClientMessageHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;

public class InitiateJumpscarePacket implements CustomPacketPayload
{
    public static final ResourceLocation ID = EyesInTheDarkness.location("server_hello");

    public double px;
    public double py;
    public double pz;

    public InitiateJumpscarePacket(double px, double py, double pz)
    {
        this.px = px;
        this.py = py;
        this.pz = pz;
    }

    public InitiateJumpscarePacket(FriendlyByteBuf buf)
    {
        this.px = buf.readDouble();
        this.py = buf.readDouble();
        this.pz = buf.readDouble();
    }

    public void write(FriendlyByteBuf buf)
    {
        buf.writeDouble(px);
        buf.writeDouble(py);
        buf.writeDouble(pz);
    }

    @Override
    public ResourceLocation id()
    {
        return ID;
    }

    public void handle(PlayPayloadContext context)
    {
        context.workHandler().execute(() -> ClientMessageHandler.handleInitiateJumpscare(this));
    }
}
