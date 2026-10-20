package com.griefprevention.protection;

import com.griefprevention.test.ServerMocks;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM;
import static org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL;
import static org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER;
import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnderPearlProtectionHandlerTest
{
    private GriefPrevention plugin;
    private DataStore dataStore;
    private EnderPearlProtectionHandler handler;
    private Player player;
    private EnderPearl pearl;
    private Endermite endermite;
    private World world;
    private Location claimed;
    private Location wilderness;

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
        plugin.config_claims_blockEnderPearls = true;
        GriefPrevention.instance = plugin;
        dataStore = mock(DataStore.class);
        plugin.dataStore = dataStore;
        when(dataStore.getMessage(Messages.EnderPearlsBlockedInClaims)).thenReturn("Pearls blocked");
        world = mock(World.class);
        when(plugin.claimsEnabledForWorld(world)).thenReturn(true);
        claimed = new Location(world, 5, 64, 5);
        wilderness = new Location(world, 100, 64, 100);
        player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getLocation()).thenReturn(claimed);
        Claim claim = mock(Claim.class);
        when(claim.getOwnerID()).thenReturn(playerId);
        when(dataStore.getClaimAt(claimed, false, null)).thenReturn(claim);
        pearl = mock(EnderPearl.class);
        when(pearl.getShooter()).thenReturn(player);
        when(pearl.getLocation()).thenReturn(claimed);
        endermite = mock(Endermite.class);
        when(endermite.getType()).thenReturn(EntityType.ENDERMITE);
        when(endermite.getLocation()).thenReturn(claimed);
        handler = new EnderPearlProtectionHandler(plugin);
    }

    @Test
    void launchInOwnClaimIsBlockedEvenWithAdministrativePermissions()
    {
        when(player.isOp()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(pearl);
        handler.onPearlLaunch(event);
        assertTrue(event.isCancelled());
        verify(player).sendMessage(contains("Pearls blocked"));
        verify(player, never()).getInventory();
    }

    @Test
    void shooterInsideClaimIsBlockedEvenIfProjectileOriginIsOutside()
    {
        when(pearl.getLocation()).thenReturn(wilderness);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(pearl);
        handler.onPearlLaunch(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void pearlWithoutPlayerShooterIsStillBlockedInsideClaim()
    {
        when(pearl.getShooter()).thenReturn(null);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(pearl);
        handler.onPearlLaunch(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void wildernessLaunchIsAllowed()
    {
        when(player.getLocation()).thenReturn(wilderness);
        when(pearl.getLocation()).thenReturn(wilderness);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(pearl);
        handler.onPearlLaunch(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void otherProjectilesAreUnaffected()
    {
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(mock(Arrow.class));
        handler.onPearlLaunch(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void pearlTeleportIntoClaimFromOutsideIsBlocked()
    {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, wilderness, claimed, ENDER_PEARL);
        handler.onPearlTeleport(event);
        assertTrue(event.isCancelled());
        verify(player, never()).getInventory();
    }

    @Test
    void enteringClaimBeforePreviouslyThrownPearlLandsCannotBypassProtection()
    {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, claimed, wilderness, ENDER_PEARL);
        handler.onPearlTeleport(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void pearlTeleportWithinClaimIsBlocked()
    {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, claimed, claimed, ENDER_PEARL);
        handler.onPearlTeleport(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void wildernessTeleportIsAllowed()
    {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, wilderness, wilderness, ENDER_PEARL);
        handler.onPearlTeleport(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void otherTeleportCausesAreUnaffected()
    {
        for (PlayerTeleportEvent.TeleportCause cause : new PlayerTeleportEvent.TeleportCause[]{COMMAND, PLUGIN, CHORUS_FRUIT})
        {
            PlayerTeleportEvent event = new PlayerTeleportEvent(player, claimed, claimed, cause);
            handler.onPearlTeleport(event);
            assertFalse(event.isCancelled());
        }
    }

    @Test
    void unavailableTeleportDestinationDoesNotThrow()
    {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, wilderness, null, ENDER_PEARL);
        handler.onPearlTeleport(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void claimInAnotherWorldIsCheckedAtDestination()
    {
        World otherWorld = mock(World.class);
        when(plugin.claimsEnabledForWorld(otherWorld)).thenReturn(true);
        Location target = new Location(otherWorld, 5, 64, 5);
        when(dataStore.getClaimAt(target, false, null)).thenReturn(mock(Claim.class));
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, wilderness, target, ENDER_PEARL);
        handler.onPearlTeleport(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void pearlEndermiteSpawnInClaimIsBlockedIndependentlyOfTeleport()
    {
        CreatureSpawnEvent event = new CreatureSpawnEvent(endermite, CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
        handler.onEndermiteSpawn(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void pearlEndermiteOutsideClaimCanSpawn()
    {
        when(endermite.getLocation()).thenReturn(wilderness);
        CreatureSpawnEvent event = new CreatureSpawnEvent(endermite, CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
        handler.onEndermiteSpawn(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void commandAndSpawnerEndermitesAreNotRemoved()
    {
        for (CreatureSpawnEvent.SpawnReason reason : new CreatureSpawnEvent.SpawnReason[]{CreatureSpawnEvent.SpawnReason.COMMAND, SPAWNER, CUSTOM})
        {
            CreatureSpawnEvent event = new CreatureSpawnEvent(endermite, reason);
            handler.onEndermiteSpawn(event);
            assertFalse(event.isCancelled());
        }
    }

    @Test
    void otherMobSpawnsAreUnaffected()
    {
        Zombie zombie = mock(Zombie.class);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        CreatureSpawnEvent event = new CreatureSpawnEvent(zombie, NATURAL);
        handler.onEndermiteSpawn(event);
        assertFalse(event.isCancelled());
    }

    private EntityDamageByEntityEvent attack(org.bukkit.entity.Entity damager, org.bukkit.entity.Entity target)
    {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(target);
        return event;
    }

    @Test
    void existingOrLuredEndermiteCannotDamageAfkPlayerInsideClaim()
    {
        when(endermite.getLocation()).thenReturn(wilderness);
        EntityDamageByEntityEvent event = attack(endermite, player);
        handler.onEndermiteDamage(event);
        verify(event).setCancelled(true);
    }

    @Test
    void endermiteCanStillDamagePlayersInWilderness()
    {
        when(player.getLocation()).thenReturn(wilderness);
        EntityDamageByEntityEvent event = attack(endermite, player);
        handler.onEndermiteDamage(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void otherHostileMobsKeepNormalClaimBehaviour()
    {
        EntityDamageByEntityEvent event = attack(mock(Zombie.class), player);
        handler.onEndermiteDamage(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void endermiteDamageAgainstNonPlayersIsUnaffected()
    {
        EntityDamageByEntityEvent event = attack(endermite, mock(Zombie.class));
        handler.onEndermiteDamage(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    private void assertAllProtectionsInactive()
    {
        ProjectileLaunchEvent launch = new ProjectileLaunchEvent(pearl);
        PlayerTeleportEvent teleport = new PlayerTeleportEvent(player, claimed, claimed, ENDER_PEARL);
        CreatureSpawnEvent spawn = new CreatureSpawnEvent(endermite, CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
        EntityDamageByEntityEvent damage = attack(endermite, player);
        handler.onPearlLaunch(launch);
        handler.onPearlTeleport(teleport);
        handler.onEndermiteSpawn(spawn);
        handler.onEndermiteDamage(damage);
        assertFalse(launch.isCancelled());
        assertFalse(teleport.isCancelled());
        assertFalse(spawn.isCancelled());
        verify(damage, never()).setCancelled(anyBoolean());
    }

    @Test
    void configurationOptOutIsRespected()
    {
        plugin.config_claims_blockEnderPearls = false;
        assertAllProtectionsInactive();
    }

    @Test
    void worldsWithoutClaimsAreUnaffected()
    {
        when(plugin.claimsEnabledForWorld(world)).thenReturn(false);
        assertAllProtectionsInactive();
    }
}
