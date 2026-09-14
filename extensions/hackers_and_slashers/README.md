# EMF Compat: Hackers 'n Slashers

A small client-side mod that makes **Hackers 'n Slashers** combat poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Without it, swinging a weapon looks like nothing is happening — the resource-pack animation keeps your arms in their idle motion while the attack plays.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Attacks, blocks, rolls and other actions | Both arms, and the legs while standing still |
| Weapon stance (off by default) | Both arms |

The head and body always stay under EMF's control, so resource-pack animations keep playing while you fight.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Hackers 'n Slashers** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain Hackers 'n Slashers behaviour. |
| Arm sync | **Body-follow** keeps the pose attached to your moving torso. **Rotation-only** is the older, simpler behaviour, but in some cases it gives smoother animations. |
| Action legs | Holds the legs too while you stand still, so a lunge or a roll keeps its stance. Moving always keeps the pack's walk cycle. |
| Weapon stances | Holds the stance a carried weapon puts you in. Off by default: it takes both arms for as long as the weapon is held. |

## Notes

Third person only. Hackers 'n Slashers keeps its own first-person pose layer and item-in-hand
renderers, so your own hands are already its business and nothing here touches them.

Hackers 'n Slashers declares Better Combat incompatible, so this addon and **EMF Compat: Better
Combat** are never useful at the same time.

## Dependencies

- Hackers 'n Slashers 2.0-beta2.5+
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.3.2+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 2.1.0+

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Build

```bash
./gradlew :hackers-and-slashers-neoforge-1.21.1:build
```

enjoy ^_^
