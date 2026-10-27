package com.griefprevention.platform.cushion;

import com.griefprevention.platform.PlatformDetection;
import com.griefprevention.platform.PlatformListener;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class CushionProtectionListener implements PlatformListener
{
    @Override
    public boolean isSupported()
    {
        return PlatformDetection.classExists("io.papermc.paper.event.entity.EntityBreakEvent");
    }

    @Override
    public @NotNull Listener create()
    {
        return new PaperCushionProtectionHandler();
    }
}
