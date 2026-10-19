package com.griefprevention.integration;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PvPManagerIntegration implements Listener
{
    private final GriefPrevention owner;
    private Plugin cachedPlugin;
    private CombatLookup combatLookup;

    public PvPManagerIntegration(GriefPrevention owner)
    {
        this.owner = owner;
    }

    public static boolean isEnabled()
    {
        return Bukkit.getPluginManager().isPluginEnabled("PvPManager");
    }

    public @Nullable Boolean isInCombat(UUID playerId)
    {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PvPManager");
        if (plugin == null || !plugin.isEnabled()) return null;

        if (cachedPlugin != plugin)
        {
            cachedPlugin = plugin;
            try
            {
                // PvPManager loads after GP; resolve its optional API through its own class loader.
                Class<?> combatPlayer = plugin.getClass().getClassLoader()
                        .loadClass("me.chancesd.pvpmanager.player.CombatPlayer");
                combatLookup = new CombatLookup(combatPlayer);
            }
            catch (ReflectiveOperationException | LinkageError e)
            {
                disableLookup(e);
            }
        }

        if (combatLookup == null) return null;
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (player == null) return false;

        try
        {
            return combatLookup.isInCombat(player);
        }
        catch (ReflectiveOperationException | LinkageError e)
        {
            disableLookup(e);
            return null;
        }
    }

    private void disableLookup(Throwable error)
    {
        combatLookup = null;
        owner.getLogger().warning("PvPManager combat API unavailable; using GriefPrevention combat tracking: " + error);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onProjectileHit(ProjectileHitEvent event)
    {
        if (!isEnabled() || !(event.getEntity() instanceof FishHook hook)
                || !(event.getHitEntity() instanceof Player)) return;
        event.setCancelled(true);
        hook.remove();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerFish(PlayerFishEvent event)
    {
        if (!isEnabled() || !(event.getCaught() instanceof Player)) return;
        event.setCancelled(true);
        event.getHook().setHookedEntity(null);
        event.getHook().remove();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event)
    {
        if (!isEnabled() || !(event.getDamager() instanceof FishHook hook)
                || !(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
        hook.remove();
    }

    record CombatLookup(Method getPlayer, Method inCombat)
    {
        CombatLookup(Class<?> combatPlayer) throws NoSuchMethodException
        {
            this(combatPlayer.getMethod("get", Player.class), combatPlayer.getMethod("isInCombat"));
        }

        boolean isInCombat(Player player) throws ReflectiveOperationException
        {
            Object combatPlayer = getPlayer.invoke(null, player);
            return combatPlayer != null && (boolean) inCombat.invoke(combatPlayer);
        }
    }
}
