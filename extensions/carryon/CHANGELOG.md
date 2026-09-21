# EMF Compat: Carry On — Changelog

## 2.0.0

- Requires Entity Model Features 3.3.2 and EMF Compat Core 2.0.0
- Fixed a startup crash with EMF 3.3
- Added a Fabric 1.21.1 build, and the Fabric 26.2 build is back
- Fixed carried objects drifting away from the hands in the mirrored third-person camera
- Frozen now keeps a per-entity EMF pose in both first and third person, so another visible mob of the same type cannot animate the carried model
- Animated keeps the carried mob's own EMF animation while normalising Carry On's render interpolation to prevent shaking

## 1.1.0

- Added a config tab with arm sync and a toggle for the carried mob's model
- The carried block or mob stays in your hands while you move more accurately
