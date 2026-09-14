# EMF Compat Core — Changelog

## 2.2.0

- EMF no longer freezes a player's pack animation while another mod is playing an animation through Player Animation Library. Every addon is covered, not just Better Combat — sitting down, carrying a block or playing an instrument used to leave the whole model frozen
- Poses now blend with your resource pack's animation instead of switching to it in one frame. A mod taking a limb, letting it go, or changing what it does with it eases in and out over about a fifth of a second, and armour follows the blend
- Every addon is covered without needing an update of its own, because the blending happens where the core puts poses back
- Added a Core setting, "Smooth pose transitions", to turn it off
- Fixed crouching while a mod animates the player. The model could sink into the ground, sit far lower than a normal crouch, and jump a quarter block up and back with every attack from a crouch — Hackers 'n Slashers, Better Combat and TACZ all showed it. Setting: "Crouch fix"
- The Better Combat, Iron's Spells and Create addons now need this version: they handed their own copies of the animation-pause fix over to the core
- Added a Fabric 1.21.1 build

## 2.0.0

- Now requires Entity Model Features 3.3.2 or newer
- Rebuilt on EMF's own animation hooks instead of patching its internals, so pose restoring no longer breaks every time EMF moves something around
- Poses are restored once per rendered player instead of once per body part, which takes work off the render thread

## 1.2.0

- Added support for Entity Model Features 3.3, which moved the state the core reads to restore poses — without this the game crashes the moment a player is drawn
- Still works with EMF 3.2.x, so nothing has to be updated in lockstep

## 1.1.2

- Fixed a frame rate drop in first person — the check for First Person Model ran over and over while drawing the player instead of once, and got slower the more mods were installed (thanks to YEETSKIE1 for the profiling)

## 1.1.1

- Fixed a crash on startup with several addons installed — they registered their settings tabs at the same moment and tripped over each other

## 1.1.0

- Added an in-game settings screen with a tab for every installed addon
- Added a global switch in the Core tab: turn off every addon
- Added body-based arm restore: a held or raised pose keeps its exact shape while you walk, crouch and turn, instead of only its rotation
- Added armour support for full-body poses, so sitting down no longer leaves your armour standing up
- Added limb priority: when two mods reach for the same limb the more specific one wins — an attack takes the arms while a riding pose keeps the legs
- Poses no longer flicker when several mods are active at once