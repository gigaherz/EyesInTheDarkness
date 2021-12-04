package gigaherz.eyes;

import gigaherz.eyes.client.ClientMessageHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class InitiateJumpscarePacket
{
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

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeDouble(px);
        buf.writeDouble(py);
        buf.writeDouble(pz);
    }

    public boolean handle(Supplier<NetworkEvent.Context> context)
    {
        context.get().enqueueWork(() -> ClientMessageHandler.handleInitiateJumpscare(this));
        return true;
    }
}
