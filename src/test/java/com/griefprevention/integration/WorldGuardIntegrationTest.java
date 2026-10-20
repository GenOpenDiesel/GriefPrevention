package com.griefprevention.integration;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.index.HashMapIndex;
import com.sk89q.worldguard.protection.managers.storage.MemoryRegionDatabase;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.sk89q.worldguard.protection.flags.StateFlag.State.ALLOW;
import static com.sk89q.worldguard.protection.flags.StateFlag.State.DENY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldGuardIntegrationTest
{
    private static final RegionAssociable VISITOR = regions -> Association.NON_MEMBER;
    private static final RegionAssociable MEMBER = regions -> Association.MEMBER;
    private RegionManager manager;

    @BeforeEach
    void setUp()
    {
        manager = new RegionManager(new MemoryRegionDatabase(), new HashMapIndex.Factory(), new SimpleFlagRegistry());
    }

    private ProtectedCuboidRegion region(String id, int minX, int minZ, int maxX, int maxZ)
    {
        return new ProtectedCuboidRegion(id, BlockVector3.at(minX, -64, minZ), BlockVector3.at(maxX, 319, maxZ));
    }

    private boolean canClaim(ProtectedRegion claim)
    {
        return WorldGuardIntegration.permitsClaim(manager, claim, VISITOR);
    }

    @Test
    void wildernessPermitsClaims()
    {
        assertTrue(canClaim(region("claim", 0, 0, 10, 10)));
    }

    static Stream<StateFlag> forbiddenFlags()
    {
        return Stream.of(Flags.BUILD, Flags.BLOCK_BREAK, Flags.BLOCK_PLACE);
    }

    @ParameterizedTest
    @MethodSource("forbiddenFlags")
    void eachDeniedBuildFlagBlocksClaim(StateFlag flag)
    {
        ProtectedRegion spawn = region("spawn", 0, 0, 30, 30);
        spawn.setFlag(Flags.BUILD, ALLOW);
        spawn.setFlag(flag, DENY);
        manager.addRegion(spawn);

        assertFalse(canClaim(region("claim", 5, 5, 15, 15)));
    }

    @Test
    void nonMemberCannotClaimRegionWithDefaultBuildProtection()
    {
        manager.addRegion(region("spawn", 0, 0, 30, 30));
        assertFalse(canClaim(region("claim", 5, 5, 15, 15)));
    }

    @Test
    void partialOverlapFromOutsideSpawnIsRejected()
    {
        manager.addRegion(region("spawn", 0, 0, 30, 30));
        assertFalse(canClaim(region("claim", -10, -10, 5, 5)));
    }

    @Test
    void enclosedRegionIsDetectedWhenAllClaimCornersAreOutside()
    {
        manager.addRegion(region("spawn", 10, 10, 20, 20));
        assertFalse(canClaim(region("claim", 0, 0, 30, 30)));
    }

    @Test
    void neighbouringClaimOutsideRegionIsAllowed()
    {
        manager.addRegion(region("spawn", 0, 0, 30, 30));
        assertTrue(canClaim(region("claim", 31, 0, 41, 10)));
    }

    @Test
    void polygonIntersectionUsesWorldGuardGeometry()
    {
        ProtectedPolygonalRegion spawn = new ProtectedPolygonalRegion("spawn", List.of(
                BlockVector2.at(0, 0), BlockVector2.at(30, 0), BlockVector2.at(0, 30)), -64, 319);
        manager.addRegion(spawn);
        assertFalse(canClaim(region("claim", 1, 1, 5, 5)));
        assertTrue(canClaim(region("claim", 25, 25, 30, 30)));
    }

    @Test
    void permittedMemberCanClaimButExplicitDenyStillApplies()
    {
        ProtectedRegion spawn = region("spawn", 0, 0, 30, 30);
        manager.addRegion(spawn);
        ProtectedRegion claim = region("claim", 5, 5, 15, 15);
        assertTrue(WorldGuardIntegration.permitsClaim(manager, claim, MEMBER));
        spawn.setFlag(Flags.BLOCK_BREAK, DENY);
        assertFalse(WorldGuardIntegration.permitsClaim(manager, claim, MEMBER));
    }

    @Test
    void regionFlagGroupsAreRespected()
    {
        ProtectedRegion spawn = region("spawn", 0, 0, 30, 30);
        spawn.setFlag(Flags.BLOCK_PLACE, DENY);
        spawn.setFlag(Flags.BLOCK_PLACE.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);
        manager.addRegion(spawn);
        ProtectedRegion claim = region("claim", 5, 5, 15, 15);
        assertTrue(WorldGuardIntegration.permitsClaim(manager, claim, MEMBER));
        assertFalse(canClaim(claim));
    }

    @Test
    void inheritedDenyAppliesWhenParentIsOutsideClaim() throws ProtectedRegion.CircularInheritanceException
    {
        ProtectedRegion parent = region("parent", 100, 100, 110, 110);
        parent.setFlag(Flags.BUILD, ALLOW);
        parent.setFlag(Flags.BLOCK_PLACE, DENY);
        ProtectedRegion child = region("child", 0, 0, 30, 30);
        child.setParent(parent);
        manager.addRegion(parent);
        manager.addRegion(child);
        assertFalse(canClaim(region("claim", 5, 5, 15, 15)));
    }

    @Test
    void globalDenyProtectsWilderness()
    {
        ProtectedRegion global = new GlobalProtectedRegion("__global__");
        global.setFlag(Flags.BLOCK_PLACE, DENY);
        manager.addRegion(global);
        assertFalse(canClaim(region("claim", 0, 0, 10, 10)));
    }

    @Test
    void explicitRegionalAllowDoesNotOverrideGlobalClaimRestriction()
    {
        ProtectedRegion global = new GlobalProtectedRegion("__global__");
        global.setFlag(Flags.BUILD, DENY);
        manager.addRegion(global);
        ProtectedRegion allowed = region("allowed", 0, 0, 30, 30);
        allowed.setFlag(Flags.BUILD, ALLOW);
        manager.addRegion(allowed);
        assertFalse(canClaim(region("claim", 5, 5, 15, 15)));
        assertFalse(canClaim(region("large_claim", -10, -10, 40, 40)));
    }

    @Test
    void unrelatedHigherPriorityAllowCannotHideSpawnDenial()
    {
        ProtectedRegion spawn = region("spawn", 0, 0, 10, 10);
        spawn.setFlag(Flags.BUILD, DENY);
        ProtectedRegion allowed = region("allowed", 20, 20, 30, 30);
        allowed.setPriority(100);
        allowed.setFlag(Flags.BUILD, ALLOW);
        manager.addRegion(spawn);
        manager.addRegion(allowed);
        assertFalse(canClaim(region("claim", 0, 0, 30, 30)));
    }

    @Test
    void footprintCoversFullWorldHeightIncludingNegativeY()
    {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        Claim claim = new Claim(new Location(world, 0, 60, 0), new Location(world, 30, 70, 30),
                UUID.randomUUID(), List.of(), List.of(), List.of(), List.of(), null);
        ProtectedRegion footprint = WorldGuardIntegration.footprint(claim, world);
        assertEquals(-64, footprint.getMinimumPoint().y());
        assertEquals(319, footprint.getMaximumPoint().y());

        manager.addRegion(new ProtectedCuboidRegion("underground_spawn", BlockVector3.at(10, -50, 10), BlockVector3.at(20, -30, 20)));
        assertFalse(canClaim(footprint));
    }
}
