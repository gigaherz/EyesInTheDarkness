package dev.gigaherz.eyes.client;

import dev.gigaherz.eyes.InitiateJumpscarePacket;

public class ClientMessageHandler
{
    public static void handleInitiateJumpscare(InitiateJumpscarePacket message)
    {
        JumpscareOverlay.INSTANCE.show(message.px, message.py, message.pz);
    }
}
