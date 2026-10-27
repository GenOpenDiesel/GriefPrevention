package com.griefprevention.platform.cushion;

import com.griefprevention.protection.ProtectionHelper;
import io.papermc.paper.event.entity.EntityBreakByEntityEvent;
import io.papermc.paper.event.entity.EntityBreakEvent;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.entity.Cushion;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;

import java.util.function.Supplier;

public class PaperCushionProtectionHandler implements Listener
{
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onCushionBreak(EntityBreakEvent event)
    {
        if (!(event.getEntity() instanceof Cushion cushion)) return;
        if (!GriefPrevention.instance.claimsEnabledForWorld(cushion.getWorld())) return;

        // Supporting blocks already have build protection; allow unsupported cushions to drop.
        if (event.getCause() == EntityBreakEvent.RemoveCause.PHYSICS) return;

        Player player = null;
        if (event.getCause() != EntityBreakEvent.RemoveCause.EXPLOSION
                && event instanceof EntityBreakByEntityEvent entityEvent)
        {
            Entity remover = entityEvent.getRemover();
            if (entityEvent.getDamageSource() != null
                    && entityEvent.getDamageSource().getCausingEntity() instanceof Player causingPlayer)
            {
                player = causingPlayer;
            }
            else if (remover instanceof Player removingPlayer)
            {
                player = removingPlayer;
            }
            else if (remover instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter)
            {
                player = shooter;
            }
        }

        if (player == null)
        {
            if (GriefPrevention.instance.dataStore.getClaimAt(cushion.getLocation(), false, null) != null)
                event.setCancelled(true);
            return;
        }

        Supplier<String> denial = ProtectionHelper.checkPermission(player, cushion.getLocation(), ClaimPermission.Build, event);
        if (denial != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(player, TextMode.Err, denial.get());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onCushionPlace(EntityPlaceEvent event)
    {
        if (!(event.getEntity() instanceof Cushion) || event.getPlayer() == null) return;

        Supplier<String> denial = ProtectionHelper.checkPermission(
                event.getPlayer(), event.getEntity().getLocation(), ClaimPermission.Build, event);
        if (denial != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, denial.get());
        }
    }
}
