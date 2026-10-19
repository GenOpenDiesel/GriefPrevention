package me.ryanhamshire.GriefPrevention;

import com.griefprevention.integration.PvPManagerIntegration;
import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PvpCombatIntegrationTest
{
    private GriefPrevention plugin;
    private DataStore dataStore;
    private Player attacker;
    private Player defender;
    private PlayerData attackerData;
    private PlayerData defenderData;

    @BeforeAll
    static void setUpServer()
    {
        Server server = ServerMocks.newServer();
        doAnswer(invocation ->
        {
            Registry<PotionEffectType> registry = mock();
            doAnswer(entry -> mock(PotionEffectType.class)).when(registry).getOrThrow(any());
            return registry;
        }).when(server).getRegistry(PotionEffectType.class);
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
        plugin.config_pvp_combatTimeoutSeconds = 15;
        GriefPrevention.instance = plugin;
        dataStore = mock(DataStore.class);
        attacker = mock(Player.class);
        defender = mock(Player.class);
        attackerData = new PlayerData();
        defenderData = new PlayerData();
        attackerData.playerID = UUID.randomUUID();
        defenderData.playerID = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(attackerData.playerID);
        when(defender.getUniqueId()).thenReturn(defenderData.playerID);
        when(attacker.getName()).thenReturn("attacker");
        when(defender.getName()).thenReturn("defender");
        when(dataStore.getPlayerData(attackerData.playerID)).thenReturn(attackerData);
        when(dataStore.getPlayerData(defenderData.playerID)).thenReturn(defenderData);
        World world = mock(World.class);
        when(defender.getWorld()).thenReturn(world);
        when(plugin.pvpRulesApply(world)).thenReturn(true);
    }

    private void damage(Entity source, double amount)
    {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamage()).thenReturn(amount);
        when(event.getDamager()).thenReturn(source);
        when(event.getEntity()).thenReturn(defender);
        new EntityDamageHandler(dataStore, plugin).onEntityDamageByEntityMonitor(event);
    }

    @Test
    void positiveHookDamageCannotStartNativeCombat()
    {
        FishHook hook = mock(FishHook.class);
        when(hook.getShooter()).thenReturn(attacker);

        damage(hook, 1);

        assertEquals(0, attackerData.lastPvpTimestamp);
        assertEquals(0, defenderData.lastPvpTimestamp);
        verifyNoInteractions(dataStore);
    }

    @Test
    void hookCannotRefreshExistingNativeCombat()
    {
        attackerData.lastPvpTimestamp = 123;
        defenderData.lastPvpTimestamp = 456;
        damage(mock(FishHook.class), 1);
        assertEquals(123, attackerData.lastPvpTimestamp);
        assertEquals(456, defenderData.lastPvpTimestamp);
    }

    @Test
    void meleeStillTagsBothPlayers()
    {
        damage(attacker, 1);
        assertTrue(attackerData.inPvpCombat());
        assertTrue(defenderData.inPvpCombat());
        assertEquals("defender", attackerData.lastPvpPlayer);
        assertEquals("attacker", defenderData.lastPvpPlayer);
    }

    @Test
    void arrowsStillTagBothPlayers()
    {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(attacker);
        damage(arrow, 1);
        assertTrue(attackerData.inPvpCombat());
        assertTrue(defenderData.inPvpCombat());
    }

    @Test
    void externalTagAppliesWithoutNativeTimestamp()
    {
        PvPManagerIntegration integration = mock(PvPManagerIntegration.class);
        when(plugin.getPvpManagerIntegration()).thenReturn(integration);
        when(integration.isInCombat(attackerData.playerID)).thenReturn(true);
        assertTrue(attackerData.inPvpCombat());
    }

    @Test
    void externalUntagOverridesRecentNativeTimestamp()
    {
        attackerData.lastPvpTimestamp = System.currentTimeMillis();
        PvPManagerIntegration integration = mock(PvPManagerIntegration.class);
        when(plugin.getPvpManagerIntegration()).thenReturn(integration);
        when(integration.isInCombat(attackerData.playerID)).thenReturn(false);
        assertFalse(attackerData.inPvpCombat());
    }

    @Test
    void unavailableApiFallsBackToNativeTimeout()
    {
        PvPManagerIntegration integration = mock(PvPManagerIntegration.class);
        when(plugin.getPvpManagerIntegration()).thenReturn(integration);
        when(integration.isInCombat(attackerData.playerID)).thenReturn(null);
        attackerData.lastPvpTimestamp = System.currentTimeMillis();
        assertTrue(attackerData.inPvpCombat());
        attackerData.lastPvpTimestamp -= 60_000;
        assertFalse(attackerData.inPvpCombat());
    }

    @Test
    void emptyPlayerDataBeforePluginStartupIsNotInCombat()
    {
        GriefPrevention.instance = null;
        assertFalse(new PlayerData().inPvpCombat());
    }
}
