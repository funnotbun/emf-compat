# EMF Compat: ParCool — Changelog

## 1.0.0

- First release
- Vaults, wall runs, rolls, climbing and every other parkour move stay visible instead of being overwritten by your resource pack
- Works with both ParCool 3.4.x and ParCool 4
- On ParCool 4 only the limbs an action actually moves are held, so the rest of your pack's animation keeps playing
- On ParCool 4 moves ease in and out of your resource pack's animation instead of snapping, at ParCool's own pace
- Moves that ParCool starts at full weight, like vaults, now ease in too, and a vault out of a pack-animated fast run no longer jumps to ParCool's run pose first
- Fast runs no longer jitter at the legs
- Attack with Better Combat, play an Immersive Melodies instrument or eat while fast running, crawling or charging a jump: the arms do the action, the legs keep running
- Resource packs can animate fast runs, charge jumps, vaults, hanging and climbing themselves through new EMF variables; a Fresh Animations: Player Extension module for the fast run, charge jump and ledge hang is included
- Hanging from a ledge, the hands of the included module rest on top of the block: the addon finds the ledge and aims the arms at it, and packs can read those angles too; looking away along the wall, only the hand nearer the wall holds on and the other hangs free, as in ParCool; catching a ledge, the hands stay on it while the body swings under them
- Requires EMF Compat Core 2.2.0 and Entity Model Features 3.3.2
- Added a config tab with a switch for holding the head and torso too
