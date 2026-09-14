# EMF Compat: Iron's Spells 'n Spellbooks — Changelog

## 2.1.0

- Requires EMF Compat Core 2.2.0
- The fix that keeps EMF animating while casting moved into the core, where it now covers every addon instead of this one alone
- Turning the addon off in the settings now also stops it lifting EMF's pause; it used to keep doing that regardless
- Added a Forge 1.20.1 build

## 2.0.0

- Now requires Entity Model Features 3.3.2 and EMF Compat Core 2.0.0
- Fixed the player freezing during a cast: EMF 3.3 moved the decision that pauses its animations, and the addon was lifting the pause in a place that is no longer consulted

## 1.0.0

- First release
- Spellcasting poses stay visible instead of being overwritten by your resource pack
- Casting also looks right in first person
- Body, head and legs keep their resource-pack animations while you cast
- Casting takes priority over weapon poses, so a spell still looks right with a gun in your hands
