package gigaherz.eyes;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.function.Supplier;

public class InitiateJumpscare
        implements IMessage
{
    public double px;
    public double py;
    public double pz;

    public InitiateJumpscare()
    {
    }

    public InitiateJumpscare(double px, double py, double pz)
    {
        this.px = px;
        this.py = py;
        this.pz = pz;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        px = buf.readDouble();
        py = buf.readDouble();
        pz = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeDouble(px);
        buf.writeDouble(py);
        buf.writeDouble(pz);
    }

    public static class Handler implements IMessageHandler<InitiateJumpscare, IMessage>
    {
        @Nullable
        @Override
        public IMessage onMessage(InitiateJumpscare message, MessageContext ctx)
        {
            return ActualHandler.SUPPLIER.get().apply(message);
        }
    }

    private static class ActualHandler
    {
        public static final Supplier<Function<InitiateJumpscare, IMessage>> SUPPLIER = () -> ActualHandler::handle;

        public static IMessage handle(InitiateJumpscare message)
        {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                    Minecraft.getMinecraft().player.addVelocity(message.vx, message.vy, message.vz);
            });
            return null;
        }
    }
}
