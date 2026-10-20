package com.griefprevention.integration;

import com.griefprevention.test.ServerMocks;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.index.HashMapIndex;
import com.sk89q.worldguard.protection.managers.storage.MemoryRegionDatabase;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.session.SessionManager;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldGuardHookTest
{
    private WorldGuardPlugin worldGuard;
    private WorldGuardIntegration integration;
    private WorldConfiguration worldConfig;
    private SessionManager sessions;
    private RegionContainer regions;
    private LocalPlayer localPlayer;
    private com.sk89q.worldedit.world.World adaptedWorld;
    private Claim claim;
    private Player player;
    private Logger logger;
    private MockedStatic<BukkitAdapter> adapter;

    @BeforeAll
    static void setUpServer()
    {
        Bukkit.setServer(ServerMocks.newServer());
    }

    @AfterAll
    static void tearDownServer()
    {
        ServerMocks.unsetBukkitServer();
    }

    @BeforeEach
    void setUp()
    {
        GriefPrevention owner = mock(GriefPrevention.class);
        logger = mock(Logger.class);
        when(owner.getLogger()).thenReturn(logger);
        worldGuard = mock(WorldGuardPlugin.class);
        when(worldGuard.isEnabled()).thenReturn(true);
        WorldGuardPlatform platform = mock(WorldGuardPlatform.class);
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        worldConfig = mock(WorldConfiguration.class);
        worldConfig.useRegions = true;
        when(platform.getGlobalStateManager()).thenReturn(configuration);
        sessions = mock(SessionManager.class);
        when(platform.getSessionManager()).thenReturn(sessions);
        regions = mock(RegionContainer.class);
        when(platform.getRegionContainer()).thenReturn(regions);
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        adaptedWorld = mock(com.sk89q.worldedit.world.World.class);
        when(adaptedWorld.getName()).thenReturn("world");
        adapter = mockStatic(BukkitAdapter.class);
        adapter.when(() -> BukkitAdapter.adapt(world)).thenReturn(adaptedWorld);
        when(configuration.get(adaptedWorld)).thenReturn(worldConfig);
        player = mock(Player.class);
        localPlayer = mock(LocalPlayer.class);
        when(worldGuard.wrapPlayer(player)).thenReturn(localPlayer);
        claim = new Claim(new Location(world, 0, 60, 0), new Location(world, 10, 80, 10),
                UUID.randomUUID(), List.of(), List.of(), List.of(), List.of(), null);
        integration = new WorldGuardIntegration(owner, worldGuard, platform);
    }

    @AfterEach
    void tearDown()
    {
        adapter.close();
    }

    @Test
    void disabledWorldGuardDoesNotBlockClaiming()
    {
        when(worldGuard.isEnabled()).thenReturn(false);
        assertTrue(integration.test(claim, player));
        verifyNoInteractions(regions);
    }

    @Test
    void disabledRegionProtectionDoesNotBlockClaiming()
    {
        worldConfig.useRegions = false;
        assertTrue(integration.test(claim, player));
        verifyNoInteractions(regions);
    }

    @Test
    void worldGuardBypassAllowsAdministratorClaim()
    {
        when(sessions.hasBypass(localPlayer, adaptedWorld)).thenReturn(true);
        assertTrue(integration.test(claim, player));
        verifyNoInteractions(regions);
    }

    @Test
    void unavailableRegionDataBlocksClaimAndWarnsOnce()
    {
        assertFalse(integration.test(claim, player));
        assertFalse(integration.test(claim, player));
        verify(logger, times(1)).severe(anyString());
    }

    @Test
    void queryFailureCannotSilentlyPermitClaiming()
    {
        when(regions.get(adaptedWorld)).thenThrow(new IllegalStateException("regions not loaded"));
        assertFalse(integration.test(claim, player));
        verify(logger).severe(anyString());
    }

    @Test
    void workingHookAllowsWilderness()
    {
        when(regions.get(adaptedWorld)).thenReturn(new RegionManager(
                new MemoryRegionDatabase(), new HashMapIndex.Factory(), new SimpleFlagRegistry()));
        assertTrue(integration.test(claim, player));
        verifyNoInteractions(logger);
    }
}
