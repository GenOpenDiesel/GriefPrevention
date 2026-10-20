# WorldGuard claim protection

WorldGuard 7.x integration is restored in this fork. Upstream `master` did not enforce
WorldGuard protection during claim creation, despite comments in `DataStore` describing a check.

With WorldGuard installed, new player claims and resized claim boundaries require WorldGuard
build permission. The check also respects `block-break: deny` and `block-place: deny`.
Commands, shovel claims and automatic chest claims share the same datastore check.
Resizing is checked before its dry-run result can be committed.

The full horizontal footprint is queried, across the world's full height including negative Y.
This detects partial overlaps and protected regions fully enclosed inside a larger proposed claim.
WorldGuard performs the geometry check, including polygonal regions.

Claiming requires permission in **every intersected region**. This is deliberately conservative:
a higher-priority allow region cannot hide a forbidden part of the proposed footprint.
It can also reject claims in a higher-priority buildable enclave inside a protected region.
Inherited flags, region groups and the global region are evaluated by WorldGuard.
Global deny rules also block claiming inside regional build-allow exceptions.
WorldGuard's world-specific bypass is respected; test with a regular player without bypass.

Optional setting, enabled by default:

```yaml
GriefPrevention:
  Claims:
    CreationRequiresWorldGuardBuildPermission: true
```

Existing configuration files acquire this setting when the plugin loads.
Without WorldGuard, or with the setting disabled, normal claiming remains available.
If WorldGuard protection is active but its region data/API cannot be queried, the operation is
rejected and an error is logged. Server/API operations with no player actor are unaffected.

Existing claims are not deleted or moved by this fix. Remove unwanted spawn claims separately.
Restart the server after replacing the JAR. Keep the existing WorldGuard spawn flags.

Acceptance checks on Paper 26.3:

1. Use a normal non-operator player without WorldGuard bypass.
2. Try `/claim` and a shovel claim inside spawn with `block-break` and `block-place` denied.
   Both must reject the claim without consuming claim blocks.
3. Try a claim from outside spawn whose boundary crosses into it, then a claim surrounding spawn.
4. Try expanding an existing outside claim into spawn. Its previous boundary must remain unchanged.
5. Verify automatic first claims respect the same protection, if enabled in configuration.
6. Claim in ordinary wilderness and verify normal claiming still succeeds.
7. Verify an administrator with WorldGuard bypass can still create administrative claims.

Tests use WorldGuard's real in-memory region manager and flag calculations, plus Bukkit mocks.
They do not replace acceptance testing on a running server.
