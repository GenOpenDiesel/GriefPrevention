# Ender pearl and endermite protection

Enabled by default:

```yaml
GriefPrevention:
  Claims:
    BlockEnderPearls: true
```

This policy applies to all claims, including administrative claims, and to every player,
including claim owners, trusted players and operators. Claim trust and ignore-claims mode
do not bypass it. Worlds where claims are disabled are unaffected.

- Ender pearls cannot be launched from inside claims. Both the projectile position and the
  shooter's position are checked, covering throws next to claim boundaries.
- Pearl teleports are rejected if either the current position or destination is inside a claim.
  This also covers pearls thrown outside before the player enters a claim.
- Endermites created by pearls cannot spawn inside claims. This check is independent of
  teleport cancellation and of the existing `EnderPearlsRequireAccessTrust` setting.
- Endermites cannot damage players standing inside claims, including mobs spawned earlier
  or lured across a boundary. They are not deleted, so existing endermite bait remains usable.
- Other hostile mobs, non-player damage targets, non-pearl teleport causes and wilderness
  pearl use retain their existing behavior.

No item is manually refunded by the new handlers. The existing access-trust teleport handler
now skips already-cancelled teleports, avoiding a second cancellation/refund path.

Setting `BlockEnderPearls: false` disables all four protections and restores the existing
`EnderPearlsRequireAccessTrust` behavior. Keeping it true protects against this exploit even
when `EnderPearlsRequireAccessTrust: false` is present in an older configuration.

Message customization in `plugins/GriefPreventionData/messages.yml`:

```yaml
Messages:
  EnderPearlsBlockedInClaims: 'Perły Endu są zablokowane na działkach.'
```

After updating the JAR, restart the server. Config and message keys are added automatically.
Later setting/message changes can be loaded with `/gpreload`.

Acceptance checks on Paper 26.3:

1. As a visitor, an owner, a trusted player and an operator, try throwing a pearl inside a claim.
2. Throw from wilderness toward a claim. Confirm that teleporting into it is rejected.
3. Throw from wilderness, then enter a claim before impact. Confirm the teleport is rejected
   and no pearl endermite appears inside the claim.
4. Spawn or lure an existing endermite next to a player in a claim. Its attacks must not remove health.
5. Verify normal pearl travel outside claims and normal attacks from other hostile mobs.
6. Check that repeated blocked use cannot duplicate pearls. Plugin cancellation and item handling
   still require verification on the actual server together with its other plugins.

The build retains the PvPManager fishing-hook and WorldGuard claim protections from earlier updates.
