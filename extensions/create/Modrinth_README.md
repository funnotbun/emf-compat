# EMF Compat: Create

A small client-side mod that makes **[Create](https://modrinth.com/mod/create)** — and many of its add-ons — work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** models.

Tested with **[Fresh Animations](https://modrinth.com/resourcepack/fresh-animations)**, **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any animation resource pack.

<details>
<summary>Video</summary>

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/072A-CVufho" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/YHm2dS88qz8" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

</details>

## Features

- You keep the Skyhook hanging pose while riding chains and ropes, instead of half your body sliding back into the resource-pack animation.
- Your character's expressions and idle motion keep playing while you hang — the model is no longer frozen or swapped for the vanilla one.
- The engineer's and logistics hat sits on top of the head again. With Fresh Animations it used to sink to the neck on villagers and zombies, and hang in the air beside animals, far too big.
- Grappling hook poses stay correct while you swing and hang.
- Holding an Aeronautics handle keeps your hands on the handle, following your moving body.
- Grabbed physics objects and ragdoll grabs no longer fight with EMF animations.
- Jetpack flight (Cosmonautics and Create S&A) can play your resource pack's flying animation (currently works only with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**).
- Works for other players too.

## Supported Create add-ons

Everything below is optional — install what you like, the matching feature turns itself on. Each one can also be toggled off in the config.

| Add-on | What it covers |
|---|---|
| **[Create Aeronautics](https://modrinth.com/mod/create-aeronautics)** | Handle grip pose |
| **[Climbable Ropes](https://modrinth.com/mod/create-aeronautics-climbable-rope)** | Rope climbing |
| **[Create Grappling Hooks](https://modrinth.com/mod/create-grappling-hooks)** | Grapple and cable-trolley poses |
| **[Sable Ragdolls](https://modrinth.com/mod/sable-ragdolls)** | Grabbing and being grabbed |
| **[Barehanded](https://modrinth.com/mod/barehanded)** | Holding a grabbed structure in third person |
| **[Create Cosmonautics](https://modrinth.com/mod/create-cosmonatics)** | Jetpack flight animation |
| **[Create Stuff 'N Additions](https://modrinth.com/mod/create-stuff-additions)** | Jetpack flight, grappling whisk, block picker |

## Loaders

- **NeoForge 1.21.1** — everything above.
- **Forge 1.20.1** — Skyhook, hats, Create Stuff 'N Additions and the Not Enough Animations fix. The other add-ons have no 1.20.1 release.
- **Fabric 1.21.11, 26.1.2, 26.2** — for **[Create Fly](https://modrinth.com/mod/create-fly)**: Skyhook, hats and the Not Enough Animations fix.

## Compatibility

- **[Not Enough Animations](https://modrinth.com/mod/not-enough-animations)** — its item-swap animation is suppressed while you're skyhooking or grappling.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — Skyhook poses stay visible on your body in first person.
- **[Freecam](https://modrinth.com/mod/freecam)** — Skyhook poses stay correct even when the camera is detached.

enjoy ^_^
