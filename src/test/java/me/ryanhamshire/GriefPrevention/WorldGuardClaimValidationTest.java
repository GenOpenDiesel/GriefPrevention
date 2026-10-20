package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldGuardClaimValidationTest
{
    private static Server server;
    private GriefPrevention plugin;
    private DataStore dataStore;
    private World world;
    private Player player;
    private PluginManager pluginManager;

    @BeforeAll
    static void setUpServer()
    {
        server = ServerMocks.newServer();
        Bukkit.setServer(server);
    }

    @AfterAll
    static void tearDownServer()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @BeforeEach
    void setUp()
    {
        plugin = mock(GriefPrevention.class);
        GriefPrevention.instance = plugin;
        plugin.config_claims_worldModes = new ConcurrentHashMap<>();
        dataStore = mock(DataStore.class, CALLS_REAL_METHODS);
        dataStore.claims = new ArrayList<>();
        dataStore.nextClaimID = 1L;
        doNothing().when(dataStore).addClaim(any(), anyBoolean());
        pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        WorldBorder border = mock(WorldBorder.class);
        when(border.isInside(any())).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        when(plugin.getMinY(world)).thenReturn(-64);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    private CreateClaimResult create(boolean dryRun)
    {
        return dataStore.createClaim(world, 0, 10, 60, 80, 0, 10, player.getUniqueId(), null, null, player, dryRun);
    }

    @Test
    void deniedCreationDoesNotFireCreationEventOrSaveClaim()
    {
        assertFalse(create(false).succeeded);
        verify(plugin).canCreateClaim(any(), same(player));
        verifyNoInteractions(pluginManager);
        verify(dataStore, never()).addClaim(any(), anyBoolean());
        verify(dataStore, never()).incrementNextClaimID();
    }

    @Test
    void dryRunCannotSkipWorldGuardCheck()
    {
        assertFalse(create(true).succeeded);
        verify(plugin).canCreateClaim(any(), same(player));
    }

    @Test
    void rejectedResizeKeepsOriginalBoundaryAndDoesNotSave()
    {
        Claim original = new Claim(new Location(world, 20, 60, 20), new Location(world, 30, 80, 30),
                player.getUniqueId(), List.of(), List.of(), List.of(), List.of(), 1L);
        CreateClaimResult result = dataStore.resizeClaim(original, 0, 30, 60, 80, 0, 30, player);
        assertFalse(result.succeeded);
        assertEquals(20, original.getLesserBoundaryCorner().getBlockX());
        assertEquals(20, original.getLesserBoundaryCorner().getBlockZ());
        verify(dataStore, never()).saveClaim(any());
    }

    @Test
    void permittedCreationContinuesThroughNormalCreationEvent()
    {
        when(plugin.canCreateClaim(any(), same(player))).thenReturn(true);
        assertTrue(create(false).succeeded);
        verify(pluginManager).callEvent(any());
        verify(dataStore).addClaim(any(), eq(true));
    }

    @Test
    void checkReceivesNormalizedWholeClaimBoundary()
    {
        dataStore.createClaim(world, 30, -10, 60, 80, 25, -5, player.getUniqueId(), null, null, player, true);
        ArgumentCaptor<Claim> checked = ArgumentCaptor.forClass(Claim.class);
        verify(plugin).canCreateClaim(checked.capture(), same(player));
        assertEquals(-10, checked.getValue().getLesserBoundaryCorner().getBlockX());
        assertEquals(-5, checked.getValue().getLesserBoundaryCorner().getBlockZ());
        assertEquals(30, checked.getValue().getGreaterBoundaryCorner().getBlockX());
        assertEquals(25, checked.getValue().getGreaterBoundaryCorner().getBlockZ());
    }

    @Test
    void noWorldGuardPreservesNormalClaiming()
    {
        plugin.config_claims_respectWorldGuard = true;
        doCallRealMethod().when(plugin).canCreateClaim(any(), any());
        assertTrue(create(false).succeeded);
    }

    @Test
    void configurationOptOutIsRespected() throws ReflectiveOperationException
    {
        Field hook = GriefPrevention.class.getDeclaredField("worldGuardClaimCheck");
        hook.setAccessible(true);
        hook.set(plugin, (BiPredicate<Claim, Player>) (claim, creator) -> false);
        doCallRealMethod().when(plugin).canCreateClaim(any(), any());
        plugin.config_claims_respectWorldGuard = true;
        assertFalse(create(true).succeeded);
        plugin.config_claims_respectWorldGuard = false;
        assertTrue(create(true).succeeded);
    }
}
