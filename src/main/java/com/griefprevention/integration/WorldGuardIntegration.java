package com.griefprevention.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.RegionResultSet;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery.QueryOption;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public final class WorldGuardIntegration implements BiPredicate<Claim, Player>
{
    private final GriefPrevention owner;
    private final WorldGuardPlugin worldGuard;
    private final WorldGuardPlatform platform;
    private boolean warned;

    public WorldGuardIntegration(GriefPrevention owner)
    {
        this(owner, WorldGuardPlugin.inst(), WorldGuard.getInstance().getPlatform());
    }

    WorldGuardIntegration(GriefPrevention owner, WorldGuardPlugin worldGuard, WorldGuardPlatform platform)
    {
        this.owner = owner;
        this.worldGuard = worldGuard;
        this.platform = platform;
    }

    @Override
    public boolean test(Claim claim, Player player)
    {
        try
        {
            if (!worldGuard.isEnabled()) return true;
            World bukkitWorld = claim.getLesserBoundaryCorner().getWorld();
            if (bukkitWorld == null) return false;
            com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(bukkitWorld);
            LocalPlayer localPlayer = worldGuard.wrapPlayer(player);

            if (!platform.getGlobalStateManager().get(world).useRegions
                    || platform.getSessionManager().hasBypass(localPlayer, world)) return true;

            RegionManager manager = platform.getRegionContainer().get(world);
            if (manager == null) throw new IllegalStateException("WorldGuard region data unavailable for " + world.getName());
            return permitsClaim(manager, footprint(claim, bukkitWorld), localPlayer);
        }
        catch (RuntimeException | LinkageError error)
        {
            if (!warned)
            {
                owner.getLogger().severe("WorldGuard claim check failed; player claim blocked: " + error);
                warned = true;
            }
            return false;
        }
    }

    static ProtectedCuboidRegion footprint(Claim claim, World world)
    {
        return new ProtectedCuboidRegion("gp_claim_check",
                BlockVector3.at(claim.getLesserBoundaryCorner().getBlockX(), world.getMinHeight(), claim.getLesserBoundaryCorner().getBlockZ()),
                BlockVector3.at(claim.getGreaterBoundaryCorner().getBlockX(), world.getMaxHeight() - 1, claim.getGreaterBoundaryCorner().getBlockZ()));
    }

    static boolean permitsClaim(RegionManager manager, ProtectedRegion footprint, RegionAssociable player)
    {
        ProtectedRegion global = manager.getRegion(ProtectedRegion.GLOBAL_REGION);
        RegionResultSet globalRules = new RegionResultSet(new ArrayList<>(), global);
        if (!globalRules.testState(player, Flags.BUILD, Flags.BLOCK_BREAK, Flags.BLOCK_PLACE)) return false;

        ApplicableRegionSet regions = manager.getApplicableRegions(footprint, QueryOption.SORT);
        if (!regions.testState(player, Flags.BUILD, Flags.BLOCK_BREAK, Flags.BLOCK_PLACE)) return false;

        // Every intersected region must permit claiming; unrelated higher priorities cannot hide a denial.
        for (ProtectedRegion region : regions)
        {
            RegionResultSet individual = new RegionResultSet(new ArrayList<>(List.of(region)), global);
            if (!individual.testState(player, Flags.BUILD, Flags.BLOCK_BREAK, Flags.BLOCK_PLACE)) return false;
        }
        return true;
    }
}
