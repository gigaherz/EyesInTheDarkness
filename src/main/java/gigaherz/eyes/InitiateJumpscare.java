package gigaherz.eyes;

import gigaherz.eyes.client.JumpscareOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class InitiateJumpscare
{
    public double px;
    public double py;
    public double pz;

    public InitiateJumpscare(double px, double py, double pz)
    {
        this.px = px;
        this.py = py;
        this.pz = pz;
    }

    public InitiateJumpscare(PacketBuffer buf)
    {
        this.px = buf.readDouble();
        this.py = buf.readDouble();
        this.pz = buf.readDouble();
    }

    public void encode(PacketBuffer buf)
    {
        buf.writeDouble(px);
        buf.writeDouble(py);
        buf.writeDouble(pz);
    }

    public void handle(Supplier<NetworkEvent.Context> context)
    {
        ActualHandler.handle(this);
        context.get().setPacketHandled(true);
    }

    private static class ActualHandler
    {
        public static void handle(InitiateJumpscare message)
        {
            Minecraft.getInstance().execute(() -> JumpscareOverlay.INSTANCE.show(message.px, message.py, message.pz));

        }
    }
}
