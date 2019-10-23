package gigaherz.eyes.client;

import gigaherz.eyes.InitiateJumpscarePacket;

public class ClientMessageHandler
{
    public static void handleInitiateJumpscare(InitiateJumpscarePacket message)
    {
        JumpscareOverlay.INSTANCE.show(message.px, message.py, message.pz);
    }
}
