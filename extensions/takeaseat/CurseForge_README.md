# EMF Compat: Take a Seat

![Take a Seat](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/take-a-seat.webp?raw=true)

A small client-side mod that makes **[Take a Seat](https://www.curseforge.com/minecraft/mc-mods/take-a-seat)** sitting poses work correctly with **[Entity Model Features](https://www.curseforge.com/minecraft/mc-mods/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://www.curseforge.com/minecraft/texture-packs/fa-player-extension)** and **[Detailed Animations](https://www.curseforge.com/minecraft/texture-packs/detailed-animations)** but it should work with any player animation resource pack.

Without it, you sit down and your character keeps standing — the resource-pack animation plays right through the chair.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Sitting on a chair or bench | Whole body except the head |

The head stays under EMF's control, so you can still look around naturally while seated.

## Features

- Compatible with **[Fresh Animations: Player Extension](https://www.curseforge.com/minecraft/texture-packs/fa-player-extension)**.
- Sitting poses stay visible instead of being overwritten by the resource-pack animation.
- Works for other players too, so everyone actually sits.

## On Fabric 1.21.11+

Entity Model Features already pauses its own animations while Take a Seat plays its pose, so sitting itself looks right without this addon. What it still fixes there is the **armour** — chestplate, leggings and boots follow the seated pose instead of staying in a standing position.
On top of that, EMF pauses animations entirely, which means Facial Expression also stops working while sitting. With this addon, everything will work just fine :)))

enjoy ^_^