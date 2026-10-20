package com.griefprevention.protection;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.Nullable;

public final class EnderPearlProtectionHandler implements Listener
{
    private final GriefPrevention plugin;
    private final DataStore dataStore;

    public EnderPearlProtectionHandler(GriefPrevention plugin)
    {
        this.plugin = plugin;
        this.dataStore = plugin.dataStore;
    }

    private boolean isProtected(@Nullable Location location)
    {
        return location != null && location.getWorld() != null
                && plugin.claimsEnabledForWorld(location.getWorld())
                && dataStore.getClaimAt(location, false, null) != null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPearlLaunch(ProjectileLaunchEvent event)
    {
        if (!plugin.config_claims_blockEnderPearls || !(event.getEntity() instanceof EnderPearl pearl)) return;
        Player player = pearl.getShooter() instanceof Player shooter ? shooter : null;
        if (!isProtected(pearl.getLocation()) && (player == null || !isProtected(player.getLocation()))) return;

        event.setCancelled(true);
        if (player != null) GriefPrevention.sendMessage(player, TextMode.Err, Messages.EnderPearlsBlockedInClaims);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPearlTeleport(PlayerTeleportEvent event)
    {
        if (!plugin.config_claims_blockEnderPearls || event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        if (!isProtected(event.getFrom()) && !isProtected(event.getTo())) return;

        event.setCancelled(true);
        GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, Messages.EnderPearlsBlockedInClaims);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEndermiteSpawn(CreatureSpawnEvent event)
    {
        if (!plugin.config_claims_blockEnderPearls || event.getEntityType() != EntityType.ENDERMITE
                || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.ENDER_PEARL) return;
        if (isProtected(event.getLocation())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEndermiteDamage(EntityDamageByEntityEvent event)
    {
        if (!plugin.config_claims_blockEnderPearls || !(event.getDamager() instanceof Endermite)
                || !(event.getEntity() instanceof Player player)) return;
        // Also protect against endermites lured into claims or spawned before this protection was enabled.
        if (isProtected(player.getLocation())) event.setCancelled(true);
    }
}
