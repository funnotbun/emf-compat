# EMF Compat: Blåhaj

A small client-side addon that keeps Blåhaj's cuddle pose visible on EMF-animated player models.

## Features

- Preserves the finished two-arm cuddle pose for every item in Blåhaj's `blahaj:plushies` tag.
- Works with the main hand, off hand, or both.
- Leaves the head, body and legs under the resource pack's animation.
- Yields the arms to higher-priority action poses such as attacks and aiming.

## Dependencies

- Blåhaj 1.0.0+
- Entity Model Features 3.3.2+
- EMF Compat Core 2.0.0+

## Config

Open **Mods → EMF Compat Core → Config** and choose the **Blåhaj** tab to enable or disable the addon.

## Build

```bash
./gradlew :blahaj-neoforge-1.21.1:build
```
