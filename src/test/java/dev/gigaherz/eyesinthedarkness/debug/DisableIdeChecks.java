package dev.gigaherz.eyesinthedarkness.debug;

import net.minecraft.SharedConstants;
import net.neoforged.fml.common.Mod;

@Mod("disable_ide_checks")
public class DisableIdeChecks
{
    public DisableIdeChecks()
    {
        // Avoid crash with AMD gpu
        SharedConstants.IS_RUNNING_IN_IDE = false;
    }
}
