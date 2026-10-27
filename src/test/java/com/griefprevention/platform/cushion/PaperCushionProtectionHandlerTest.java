package com.griefprevention.platform.cushion;

import com.griefprevention.platform.PlatformDetection;
import com.griefprevention.test.ServerMocks;
import io.papermc.paper.event.entity.EntityBreakByEntityEvent;
import io.papermc.paper.event.entity.EntityBreakEvent;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cushion;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.papermc.paper.event.entity.EntityBreakEvent.RemoveCause.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaperCushionProtectionHandlerTest
{
    private final PaperCushionProtectionHandler handler = new PaperCushionProtectionHandler();
    private GriefPrevention plugin;
    private DataStore dataStore;
    private World world;
    private Location location;
    private Cushion cushion;
    private Player player;
    private PlayerData playerData;
    private Claim claim;

    @BeforeAll
    static void setUpServer()
    {
        Bukkit.setServer(ServerMocks.newServer());
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
        dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        world = mock(World.class);
        when(plugin.claimsEnabledForWorld(world)).thenReturn(true);
        plugin.config_claims_worldModes = new ConcurrentHashMap<>();
        plugin.config_claims_worldModes.put(world, ClaimsMode.Survival);
        location = new Location(world, 5, 64, 5);
        cushion = mock(Cushion.class);
        when(cushion.getLocation()).thenReturn(location);
        when(cushion.getWorld()).thenReturn(world);
        player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        playerData = new PlayerData();
        when(dataStore.getPlayerData(playerId)).thenReturn(playerData);
        claim = mock(Claim.class);
        when(dataStore.getClaimAt(eq(location), eq(false), any())).thenReturn(claim);
        when(claim.checkPermission(eq(player), eq(ClaimPermission.Build), any())).thenReturn(() -> "Build denied");
    }

    private EntityBreakByEntityEvent attack(Entity remover, DamageSource source)
    {
        return new EntityBreakByEntityEvent(cushion, remover, source, ENTITY);
    }

    private EntityPlaceEvent placement(Entity entity, Player placer)
    {
        return new EntityPlaceEvent(entity, placer, mock(Block.class), BlockFace.UP, EquipmentSlot.HAND);
    }

    @Test
    void untrustedPlayerCannotBreakClaimedCushion()
    {
        EntityBreakEvent event = attack(player, null);
        handler.onCushionBreak(event);
        assertTrue(event.isCancelled());
        verify(claim).checkPermission(player, ClaimPermission.Build, event);
        verify(player).sendMessage(contains("Build denied"));
    }

    @Test
    void ownerOrBuildTrustedPlayerCanBreakCushion()
    {
        when(claim.checkPermission(eq(player), eq(ClaimPermission.Build), any())).thenReturn(null);
        EntityBreakEvent event = attack(player, null);
        handler.onCushionBreak(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void ignoringClaimsAllowsBreaking()
    {
        playerData.ignoreClaims = true;
        EntityBreakEvent event = attack(player, null);
        handler.onCushionBreak(event);
        assertFalse(event.isCancelled());
        verifyNoInteractions(claim);
    }

    @Test
    void untrustedArrowShooterCannotBreakCushion()
    {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(player);
        EntityBreakEvent event = attack(arrow, null);
        handler.onCushionBreak(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void trustedArrowShooterCanBreakCushion()
    {
        when(claim.checkPermission(eq(player), eq(ClaimPermission.Build), any())).thenReturn(null);
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(player);
        EntityBreakEvent event = attack(arrow, null);
        handler.onCushionBreak(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void damageSourcePlayerCannotBypassBuildPermission()
    {
        DamageSource source = mock(DamageSource.class);
        when(source.getCausingEntity()).thenReturn(player);
        EntityBreakEvent event = attack(mock(Arrow.class), source);
        handler.onCushionBreak(event);
        assertTrue(event.isCancelled());
        verify(claim).checkPermission(player, ClaimPermission.Build, event);
    }

    @Test
    void dispenserArrowCannotBreakClaimedCushion()
    {
        EntityBreakEvent event = attack(mock(Arrow.class), null);
        handler.onCushionBreak(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void mobCannotBreakClaimedCushion()
    {
        EntityBreakEvent event = attack(mock(Zombie.class), null);
        handler.onCushionBreak(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void unknownRemoverCannotBreakClaimedCushion()
    {
        EntityBreakEvent event = attack(null, null);
        handler.onCushionBreak(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void environmentalDestructionCannotBreakClaimedCushion()
    {
        for (EntityBreakEvent.RemoveCause cause : new EntityBreakEvent.RemoveCause[]{EXPLOSION, OBSTRUCTION, DEFAULT})
        {
            EntityBreakEvent event = new EntityBreakEvent(cushion, cause);
            handler.onCushionBreak(event);
            assertTrue(event.isCancelled(), cause.name());
        }
    }

    @Test
    void playerCausedExplosionCannotBreakClaimedCushion()
    {
        when(claim.checkPermission(eq(player), eq(ClaimPermission.Build), any())).thenReturn(null);
        EntityBreakEvent event = new EntityBreakByEntityEvent(cushion, player, null, EXPLOSION);
        handler.onCushionBreak(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void removingSupportingBlockAllowsCushionToDrop()
    {
        EntityBreakEvent event = new EntityBreakEvent(cushion, PHYSICS);
        handler.onCushionBreak(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void wildernessCushionCanBeBrokenByPlayer()
    {
        when(dataStore.getClaimAt(eq(location), eq(false), any())).thenReturn(null);
        EntityBreakEvent event = attack(player, null);
        handler.onCushionBreak(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void wildernessCushionCanBreakNaturally()
    {
        when(dataStore.getClaimAt(eq(location), eq(false), any())).thenReturn(null);
        EntityBreakEvent event = new EntityBreakEvent(cushion, EXPLOSION);
        handler.onCushionBreak(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void disabledWorldIsUnaffected()
    {
        when(plugin.claimsEnabledForWorld(world)).thenReturn(false);
        EntityBreakEvent event = attack(player, null);
        handler.onCushionBreak(event);
        assertFalse(event.isCancelled());
        verifyNoInteractions(dataStore);
    }

    @Test
    void otherEntitiesAreUnaffected()
    {
        EntityBreakEvent event = new EntityBreakEvent(mock(Entity.class), EXPLOSION);
        handler.onCushionBreak(event);
        assertFalse(event.isCancelled());
        EntityPlaceEvent place = placement(mock(Entity.class), player);
        handler.onCushionPlace(place);
        assertFalse(place.isCancelled());
        verifyNoInteractions(dataStore);
    }

    @Test
    void untrustedPlayerCannotPlaceCushionInClaim()
    {
        EntityPlaceEvent event = placement(cushion, player);
        handler.onCushionPlace(event);
        assertTrue(event.isCancelled());
        verify(claim).checkPermission(player, ClaimPermission.Build, event);
    }

    @Test
    void ownerOrBuildTrustedPlayerCanPlaceCushion()
    {
        when(claim.checkPermission(eq(player), eq(ClaimPermission.Build), any())).thenReturn(null);
        EntityPlaceEvent event = placement(cushion, player);
        handler.onCushionPlace(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void placementWithoutPlayerIsUnaffected()
    {
        EntityPlaceEvent event = placement(cushion, null);
        handler.onCushionPlace(event);
        assertFalse(event.isCancelled());
        verifyNoInteractions(dataStore);
    }

    @Test
    void listenerRequiresPaperBreakEvent()
    {
        try (MockedStatic<PlatformDetection> platform = mockStatic(PlatformDetection.class))
        {
            platform.when(() -> PlatformDetection.classExists("io.papermc.paper.event.entity.EntityBreakEvent")).thenReturn(false);
            assertFalse(new CushionProtectionListener().isSupported());
            platform.when(() -> PlatformDetection.classExists("io.papermc.paper.event.entity.EntityBreakEvent")).thenReturn(true);
            assertTrue(new CushionProtectionListener().isSupported());
            assertInstanceOf(PaperCushionProtectionHandler.class, new CushionProtectionListener().create());
        }
    }
}
