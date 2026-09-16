# EMF Compat: ParCool

A small client-side mod that makes **[ParCool!](https://modrinth.com/mod/parcool)** parkour animations work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

Without it, ParCool's moves are lost the moment EMF takes over the model: you vault a fence and your character keeps jogging on the spot, arms swinging to the resource pack's idle. This addon keeps the parkour pose where it belongs.

## Covered Poses

Every ParCool animation is covered — vaults, wall runs and wall jumps, rolls and breakfalls, climbing and hanging, sliding, crawling, dodges and dives.

| ParCool version | Captured parts |
|---|---|
| 4.x | Exactly the parts the running action animates |
| 3.4.x, action owns the model | Head, torso, arms and legs |
| 3.4.x, action adjusts the vanilla pose | Head, arms and legs |

ParCool 4 reports which limbs each action drives, so anything it does not touch keeps playing the resource pack's animation. ParCool 3 has no such list, so the addon falls back to the scope its two animation modes imply.

On ParCool 4 the move also fades in and out over your resource pack's animation with ParCool's own timing, so starting a fast run or landing a vault blends instead of snapping. While you only run, swim, crawl or charge a jump, your arms are free for other mods: a Better Combat swing, an instrument from Immersive Melodies or eating takes the arms and hands them back smoothly, and the legs keep running.

## Features

- Parkour moves stay visible in third person instead of falling back to the resource-pack animation.
- Works for other players too, so everyone's parkour looks right.
- Works with both ParCool generations — 3.4.x and 4.x — and picks the right path automatically.
- Smooth transitions between parkour moves and your pack's animation (ParCool 4).
- Attack, play or eat while running: Better Combat, Immersive Melodies and Not Enough Animations get the arms (ParCool 4).
- Your own first-person view is left untouched.
- Body rotation during flips and dives is ParCool's own and was never affected by EMF; it keeps working as before.

## Resource pack animations

ParCool's own animations are keyframed poses that look out of place next to a procedural pack like Fresh Animations. On ParCool 4 the addon therefore also hands ParCool's state to resource packs as EMF animation variables, so a pack can animate the moves itself:

| Variable | Value |
|---|---|
| `parcool_fast_run` | 1 while fast running |
| `parcool_charge` | charge jump charge, 0 to 1 |
| `parcool_charge_jump` | 1 during the jump out of a charge |
| `parcool_vault` | progress through a vault, 0 to 1 |
| `parcool_vault_side` | -1 vaulting left, 1 right, 0 straight over |
| `parcool_hang` | 1 while hanging from a ledge |
| `parcool_hang_wall` | 1 while the feet are against the wall |
| `parcool_hang_left_to_wall`, `parcool_hang_right_to_wall`, `parcool_hang_back_to_wall` | how far a player hanging with the feet on the wall has turned away from it, 0 to 1 |
| `parcool_rarm_grip`, `parcool_larm_grip` | 1 while that hand holds the ledge, 0 while it hangs free — turned side-on, only the hand nearer the wall holds on; eased |
| `parcool_rarm_hang_rx`, `parcool_rarm_hang_ry`, `parcool_rarm_hang_lift` (and `larm`) | the finished hanging arm: on the ledge by IK, loose, or smoothly between; `ry` never wraps |
| `parcool_rarm_ik`, `parcool_larm_ik` | 1 while hanging with a ledge top found for that hand |
| `parcool_hang_catch` | pixels to move the body down (negative: up) while catching a ledge and after, so the hands stay on it; the IK angles allow for it |
| `parcool_rarm_ik_rx`, `parcool_rarm_ik_ry`, `parcool_larm_ik_rx`, `parcool_larm_ik_ry` | arm rotations, in radians, that put each hand on top of the ledge |
| `parcool_rarm_ik_lift`, `parcool_larm_ik_lift` | pixels to raise each shoulder so the hand reaches; the rotations assume it is raised |
| `parcool_rarm_ik_reach`, `parcool_larm_ik_reach` | shoulder-to-ledge distance over the arm's length, before the lift |
| `parcool_climb` | progress climbing up from a ledge, 0 to 1 |

A pack that reads a move's variables takes that move over: the addon stops replaying ParCool's pose and its torso lean for it. Moves the pack does not read keep ParCool's poses.

An animation module for **Fresh Animations: Player Extension** lives in `resourcepack/`; for now it animates the fast run, the charge jump and hanging from a ledge; the other moves keep ParCool's poses. FreshLX's terms do not allow sharing their files unedited, so it is built on your own copy:

```bash
python3 extensions/parcool/resourcepack/build_pack.py <FA+Player zip> <resourcepacks folder>
```

Enable "EMF Compat ParCool Animations" above FA+Player.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **ParCool** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain ParCool behaviour. |
| Resource pack animations | Let a pack that animates ParCool moves itself play them instead of ParCool's poses. |
| Pose scope | **Whole pose** holds every part ParCool animates. **Limbs only** leaves the head and torso to the resource pack, so facial and idle animations keep playing during a move. |

## Dependencies

- [ParCool!](https://modrinth.com/mod/parcool) 3.4.0.0+ (3.4.x or 4.x)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 2.2.0+

## Notes

ParCool's own [compatibility addon](https://github.com/semillakan6/ParCool-CompatibilityAddon-NeoForge) solves the same clash the other way round: it asks EMF to drop to the vanilla model and pause its animation while ParCool poses the player. That works, but it costs you the pack's animation for as long as the move lasts. This addon captures ParCool's pose and replays it over the EMF model instead, so the rest of your pack keeps running. Running both at once is redundant — pick one.

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Build

```bash
./gradlew :parcool-neoforge-1.21.1:build
```

enjoy ^_^
