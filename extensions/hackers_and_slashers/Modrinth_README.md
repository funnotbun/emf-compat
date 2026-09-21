# EMF Compat: Hackers 'n Slashers

![Hackers 'n Slashers](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/hackers-and-slashers.webp?raw=true)

A small client-side mod that makes **Hackers 'n Slashers** combat poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any player animation resource pack.

Without it, swinging a weapon looks like nothing is happening — the resource-pack animation keeps your arms in their idle motion while the attack plays.

The same combat animation and correctly attached held items are preserved in first person.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Attacks, blocks, rolls and other actions | Both arms, and the legs while standing still |
| Weapon stance (off by default) | Both arms |

## Features

- Attacks, blocks, rolls and every other action pose stay visible in third person.
- Your stance holds while you stand still.
- Optional weapon stances for weapons that have one — off by default, since they take both arms for as long as the weapon is held.
- Works for other players too.
- First-person attacks, blocks and weapon poses use Hackers 'n Slashers' animated arms and correctly attached held items instead of EMF's idle custom hands.
- Compatible with **[First Person Model](https://modrinth.com/mod/first-person-model)** — its body is hidden while H&S owns the first-person animation, preventing duplicated arms and held items.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Hackers 'n Slashers** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain Hackers 'n Slashers behaviour. |
| Arm sync | **Body-follow** keeps the pose attached to your moving torso. **Rotation-only** is the older, simpler behaviour, which in some cases gives smoother animations. |
| Action legs | Holds the legs too while you stand still, so a lunge or a roll keeps its stance. Moving always keeps the pack's walk cycle. |
| Weapon stances | Holds the stance a carried weapon puts you in. Off by default: it takes both arms for as long as the weapon is held. |

## Notes

Hackers 'n Slashers declares **[Better Combat](https://modrinth.com/mod/better-combat)** incompatible, so this addon and **[EMF Compat: Better Combat](https://modrinth.com/mod/emf-compat-better-combat)** are never useful at the same time — install the one that matches your combat mod.

enjoy ^_^