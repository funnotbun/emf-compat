# EMF Compat: Carry On

## [Modrinth](https://modrinth.com/project/emf-compat-carry-on)

A small client-side mod that makes **[Carry On](https://modrinth.com/mod/carry-on)** carry poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Features

- Your arms stay raised naturally while carrying blocks and entities.
- Carried objects stay attached to your EMF-animated hands in both third-person camera directions.
- Carried entities keep their resource-pack model and texture; **Frozen** holds an independent pose while **Animated** keeps their own EMF animation without first-person shaking.
- First-person carry pose stays visible.
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Should work with most player animation resource packs using EMF.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — carry poses stay correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — raised arms stay visible on your body in first person.
- **ParCool 4 (NeoForge 1.21.1)** — with the ParCool addon installed, carrying keeps control of both arms during fast runs and charged jumps. Crawling, rolls, dodges/dashes, flips and two-handed hangs/climbs wait until the load is put down, because Carry On has no valid carry pose for those moves.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Carry On** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain Carry On behaviour. |
| Arm sync | **Body-follow** keeps captured poses attached to your moving torso. **Rotation-only** is the older, simpler behaviour, but in some cases it gives smoother animations. |
| Carried mob animation | **Frozen** holds a per-entity pose that cannot be overwritten by another mob of the same type. **Animated** keeps the carried mob's own EMF animation with stable render interpolation. |

## Build

```bash
./gradlew :carryon-neoforge-1.21.1:build
./gradlew :carryon-forge-1.20.1:build
./gradlew :carryon-fabric-1.21.11:build
```

enjoy ^_^
