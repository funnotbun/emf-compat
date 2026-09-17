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
- Resource packs can animate fast runs, charge jumps, vaults, hanging and climbing themselves through new EMF variables; a Fresh Animations: Player Extension module for the fast run, charge jump, ledge hang and climb up is included
- Hanging from a ledge, the hands of the included module rest on top of the block: the addon finds the ledge and aims the arms at it, and packs can read those angles too; looking away along the wall, only the hand nearer the wall holds on and the other hangs free, as in ParCool; catching a ledge, the hands stay on it while the body swings under them; shuffling along it, the hands go hand over hand, and round a corner the body turns smoothly while the hands reach onto the next face
- With the included module, crawling and fast swimming show Fresh Animations' own crawl and swim, and climbing a chain or pole shows its ladder climb
- Hanging under a bar, the hands of the included module hold the bar; along it the free arm swings round past the body to the next grip while the body turns after it and swings, as monkeys go, and sideways the hands go hand over hand; while the module animates it, moving along a bar is a little slower so the swing can be seen
- The included module keeps Fresh Animations' cape on the back while ParCool poses the torso, such as climbing up a ledge
- Requires EMF Compat Core 2.2.0 and Entity Model Features 3.3.2
- Added a config tab with a switch for holding the head and torso too
