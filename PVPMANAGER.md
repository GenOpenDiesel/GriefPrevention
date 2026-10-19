# PvPManager / PvPManagerX fork

Target: Paper/Spigot 26.3, Java 25. Base: upstream `master` (18.0.0-SNAPSHOT, API 26.2).
Minecraft 26.3 runtime verification still required. Upstream labels this development branch experimental.

Install `GriefPrevention.jar` alongside ChanceSD PvPManager 4.x (free or premium).
Integration detects Bukkit plugin name `PvPManager`; no additional addon or configuration required.

- GP reads combat status through `CombatPlayer.get(player).isInCombat()`.
  PvPManager owns combat timeout, exemptions and manual untagging.
- PvPManager owns combat logout punishment while enabled. GP does not apply a second punishment.
- Fishing hooks cannot hit or reel in players while PvPManager is enabled, anywhere in the world.
  Hook collision, hook damage and player reeling are cancelled before PvPManager processes them.
  Rods therefore cannot start or refresh combat; existing combat is never cleared to achieve this.
- Normal fishing and non-player catches keep their existing GP protections.
- Without PvPManager, GP retains its native combat tracking. Fishing-hook damage never starts GP combat.

Only public PvPManager API methods are used. API lookup uses PvPManager's class loader because
PvPManager already depends on GP loading first. No circular soft dependency is added.
An incompatible API logs a warning and falls back to GP combat tracking.

Build and automated tests: `mvn --batch-mode verify` with JDK 25.

Server acceptance checks with two players:

1. Turn PvPManager's `Other Settings.Ignore No Damage Hits` off to exercise rod tagging.
2. Hit and reel in another player with a rod, both in wilderness and near a protected claim.
   Neither player should enter combat; the player must not be pulled.
3. Start combat with a sword, note the remaining time, then use a rod.
   Rod use must not reset the timer or clear the existing tag.
4. Sword and arrow attacks in wilderness must still tag normally. Protected claims must still reject attacks.
5. Run PvPManager's untag command. GP combat restrictions should end immediately.
6. Catch fish and reel in non-player entities; existing claim permissions must still apply.
7. Test logout punishment, then restart without PvPManager and verify native GP combat handling.
