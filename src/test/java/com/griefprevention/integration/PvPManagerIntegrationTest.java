package com.griefprevention.integration;

import com.griefprevention.test.ServerMocks;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PvPManagerIntegrationTest
{
    private static PluginManager pluginManager;
    private PvPManagerIntegration integration;
    private Logger logger;

    @BeforeAll
    static void setUpServer()
    {
        Server server = ServerMocks.newServer();
        pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        Bukkit.setServer(server);
    }

    @AfterAll
    static void tearDownServer()
    {
        ServerMocks.unsetBukkitServer();
    }

    @BeforeEach
    void setUp()
    {
        ApiFixture.missing = false;
        reset(pluginManager);
        when(pluginManager.isPluginEnabled("PvPManager")).thenReturn(true);
        GriefPrevention owner = mock(GriefPrevention.class);
        logger = mock(Logger.class);
        when(owner.getLogger()).thenReturn(logger);
        integration = new PvPManagerIntegration(owner);
    }

    @Test
    void hookCollisionWithPlayerIsCancelledBeforeImpact()
    {
        FishHook hook = mock(FishHook.class);
        ProjectileHitEvent event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn(hook);
        when(event.getHitEntity()).thenReturn(mock(Player.class));

        integration.onProjectileHit(event);

        verify(event).setCancelled(true);
        verify(hook).remove();
    }

    @Test
    void reelingPlayerIsCancelledAndHookDetached()
    {
        FishHook hook = mock(FishHook.class);
        PlayerFishEvent event = mock(PlayerFishEvent.class);
        when(event.getCaught()).thenReturn(mock(Player.class));
        when(event.getHook()).thenReturn(hook);

        integration.onPlayerFish(event);

        verify(event).setCancelled(true);
        verify(hook).setHookedEntity(null);
        verify(hook).remove();
    }

    @Test
    void hookDamageIsCancelledEvenWhenDamageIsPositive()
    {
        FishHook hook = mock(FishHook.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(hook);
        when(event.getEntity()).thenReturn(mock(Player.class));

        integration.onEntityDamage(event);

        verify(event).setCancelled(true);
        verify(hook).remove();
    }

    @Test
    void normalFishingIsUnaffected()
    {
        PlayerFishEvent event = mock(PlayerFishEvent.class);
        when(event.getCaught()).thenReturn(mock(Item.class));

        integration.onPlayerFish(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void castingWithoutCatchIsUnaffected()
    {
        PlayerFishEvent event = mock(PlayerFishEvent.class);
        integration.onPlayerFish(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void arrowsAndMeleeAreUnaffected()
    {
        ProjectileHitEvent arrowHit = mock(ProjectileHitEvent.class);
        when(arrowHit.getEntity()).thenReturn(mock(Arrow.class));
        when(arrowHit.getHitEntity()).thenReturn(mock(Player.class));
        integration.onProjectileHit(arrowHit);
        verify(arrowHit, never()).setCancelled(anyBoolean());

        EntityDamageByEntityEvent melee = mock(EntityDamageByEntityEvent.class);
        when(melee.getDamager()).thenReturn(mock(Player.class));
        integration.onEntityDamage(melee);
        verify(melee, never()).setCancelled(anyBoolean());
    }

    @Test
    void hookCollisionWithNonPlayerIsUnaffected()
    {
        FishHook hook = mock(FishHook.class);
        ProjectileHitEvent event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn(hook);
        when(event.getHitEntity()).thenReturn(mock(Item.class));

        integration.onProjectileHit(event);

        verify(event, never()).setCancelled(anyBoolean());
        verify(hook, never()).remove();
    }

    @Test
    void withoutPvpManagerPlayerHooksAreUnaffected()
    {
        when(pluginManager.isPluginEnabled("PvPManager")).thenReturn(false);
        PlayerFishEvent fish = mock(PlayerFishEvent.class);
        ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
        EntityDamageByEntityEvent damage = mock(EntityDamageByEntityEvent.class);

        integration.onPlayerFish(fish);
        integration.onProjectileHit(hit);
        integration.onEntityDamage(damage);

        verify(fish, never()).setCancelled(anyBoolean());
        verify(hit, never()).setCancelled(anyBoolean());
        verify(damage, never()).setCancelled(anyBoolean());
    }

    @Test
    void absentOrDisabledPluginUsesNativeTracking()
    {
        assertNull(integration.isInCombat(UUID.randomUUID()));
        Plugin disabled = mock(Plugin.class);
        when(pluginManager.getPlugin("PvPManager")).thenReturn(disabled);
        assertNull(integration.isInCombat(UUID.randomUUID()));
        verify(logger, never()).warning(anyString());
    }

    @Test
    void incompatibleApiFallsBackAndWarnsOnlyOnce()
    {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(pluginManager.getPlugin("PvPManager")).thenReturn(plugin);

        assertNull(integration.isInCombat(UUID.randomUUID()));
        assertNull(integration.isInCombat(UUID.randomUUID()));

        verify(logger, times(1)).warning(anyString());
    }

    @Test
    void publicApiLookupReadsBothCombatStates() throws ReflectiveOperationException
    {
        PvPManagerIntegration.CombatLookup lookup = new PvPManagerIntegration.CombatLookup(ApiFixture.class);
        Player player = mock(Player.class);

        ApiFixture.tagged = true;
        assertTrue(lookup.isInCombat(player));
        assertSame(player, ApiFixture.lastPlayer);

        ApiFixture.tagged = false;
        assertFalse(lookup.isInCombat(player));
    }

    public static final class ApiFixture
    {
        static boolean missing;
        static boolean tagged;
        static Player lastPlayer;

        public static ApiFixture get(Player player)
        {
            lastPlayer = player;
            return missing ? null : new ApiFixture();
        }

        public boolean isInCombat()
        {
            return tagged;
        }
    }

    @Test
    void missingCombatPlayerIsNotTagged() throws ReflectiveOperationException
    {
        ApiFixture.missing = true;
        PvPManagerIntegration.CombatLookup lookup = new PvPManagerIntegration.CombatLookup(ApiFixture.class);
        assertFalse(lookup.isInCombat(mock(Player.class)));
    }
}
