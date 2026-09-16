# mctest — running and driving the game myself

Launches any Modrinth profile in a throwaway sandbox, with this repo's freshly built jars in place
of the installed ones, and drives it from inside: commands, key presses, camera, screenshots, and
probes into the core. Written so animation work can be checked in game without asking the user to
look at anything.

The profile is never written to. Everything happens in `run/mctest/<profile>/` (gitignored).

## Parts

| Part | What it is |
|---|---|
| `mctest.py` | The launcher: reads Modrinth's data, builds the sandbox, starts the game, talks to the driver. Also a CLI. |
| `server.py` | MCP server over `mctest.py` — the tools `mc_*` I call. Crops screenshots and lays them out on contact sheets. |
| `driver/<loader>-<mc>/` | The in-game mod, one module per target. A Gradle build of its own. |
| `scenarios/*.json` | Ready-made scripts — a step list is exactly what `steps` takes. |
| `.mcp.json` (repo root) | Registers the server for the session. |

## Using it

```
mc_profiles()                                  # what exists, and whether a driver is built for it
mc_launch("Test", world="New World2")          # sandbox + offline launch, waits for the world
mc_steps("Test", [...])                        # run a script, get results + screenshots
mc_log("Test", 80, "error")  /  mc_status(...)
mc_stop("Test")
```

Without the MCP tools loaded, the same thing from the shell:

```bash
python3 tools/mctest/mctest.py profiles
python3 tools/mctest/mctest.py launch Test --world "New World2" --fresh-world
python3 tools/mctest/mctest.py wait Test
python3 tools/mctest/mctest.py steps Test '[{"camera":"front"},{"wait":10},{"screenshot":"a"}]'
python3 tools/mctest/mctest.py stop Test
```

A saved scenario is just a step list, so it runs straight from the shell:

```bash
python3 tools/mctest/mctest.py steps Test "$(cat tools/mctest/scenarios/worn-items-scene.json)"
python3 tools/mctest/mctest.py steps Test "$(cat tools/mctest/scenarios/packs-fa.json)"   # reload
python3 tools/mctest/mctest.py steps Test "$(cat tools/mctest/scenarios/worn-items-shots.json)"
```

| Scenario | What it sets up |
|---|---|
| `worn-items-scene.json` | Create seats + stock tickers with a villager, a zombie and a parrot sitting on them (they get Create's logistics hat), a villager in a carved pumpkin as the vanilla reference, and Artifacts on the player. Needs `enable=["artifacts"]` at launch. |
| | The parrot is the telling one: its head box is 2×2, so the scale Create takes from it is 0.25 and a hat that misses it comes out four times too big. A parrot never *falls onto* a seat, so the scene seats a chicken first (that is what spawns Create's seat entity), mounts the parrot onto it with `/ride`, and kills the chicken. |
| `worn-items-scene-1.21.11.json` | The same without curios/Artifacts, with 1.21.5+ `equipment:` NBT. |
| `worn-items-shots.json` | Camera positions, screenshots and the `model` probe for that scene. |
| `instruments-scene.json` | A zombie, skeleton, pillager, vindicator, evoker and piglin playing Immersive Melodies instruments, under a lit roof so the undead do not burn. |
| `instruments-shots.json` | Camera positions and screenshots for that scene. Toggle `immersivemelodies.mobs` to compare. |
| `worn-items-scene-1.20.1.json` / `-26.json` | The worn-items scene for Forge 1.20.1 (`Count`/`tag` NBT, no curios) and for 26.x (renamed gamerules, a pause for the chunks before the fill). |
| `chain-conveyor-scene.json` / `-26.json` | Two Create chain conveyors joined by a chain, the player under it with a wrench. Connections are written again once both blocks exist — Create drops a connection to a block that is not there yet. |
| `chain-conveyor-shots.json` | Grabs the chain (the crosshair must be on the strand itself: z 337.3, straight up) and shoots the hang from front and back. |
| `packs-vanilla / packs-fa / packs-fa-player.json` | The three pack configurations to shoot it in. |
| `parcool-course.json` | NeoForge 1.21.1 `Test`, world `New World2`: unlocks every ParCool 4 action, builds a runway with a low wall (vault), a 4-high wall (hang, climb up) and a long wall. Launch with `enable=["ParCool-1.21.1", "emf_compat_parcool"]` and `disable=["hackersandslashers-2.0", "emf_compat_hackers_and_slashers"]` (Better Combat does not load next to Hackers 'n Slashers). |
| `parcool-moves.json` | Fast run in/hold/out with fade probes, vault, hang and climb up, crawl, slide, dodges, charge jump — each with the `parcool` probe. |
| `parcool-pack.json` | The ParCool animation pack's moves with the pack on: fast run and charge jump (`fade` shows no pose sources while the pack animates them), a vault that stays ParCool's pose, then the hang - a drop onto the wall (ParCool slides down first, on the same key), a shuffle, and a free ledge. Build the pack first (`extensions/parcool/resourcepack/build_pack.py`), launch with WATUT disabled, hide the GUI and enable it above FA+Player with a `packs` step. |
| `parcool-combos.json` | The same course with other addons: a Better Combat swing during a fast run (add `bettercombat-neoforge`, `emf_compat_better_combat` to `enable`), an Immersive Melodies flute while running and crawling, NEA eating into a run, WATUT typing, the JustExpressions face during a charge jump. |

For contact sheets and images outside MCP, import `server.py`:
`uv run --with "mcp<2" --with pillow python -c "import sys; sys.path.insert(0,'tools/mctest'); import server; ..."`.

**Build first.** The sandbox takes our jars from `upload/`, which is filled by `./gradlew build`.
A driver change needs `./gradlew -p tools/mctest/driver build`. Neither is run automatically.

## Steps

A script is a list of steps, run in order on the client thread. `wait` counts client ticks (20/s).

```
{"cmd": "time set noon"}        run a command      {"chat": "hi"}
{"hold": "sneak"} {"release": "sneak"} {"releaseAll": true}
{"click": "attack"}             one press          {"slot": 0}
{"look": [yaw, pitch]}          pitch > 0 is down  {"camera": "first|back|front"}
{"hideGui": true}               (not on 26.2)      {"closeScreen": true}
{"wait": 10}                    ticks              {"log": "marker into latest.log"}
{"state": true}                 pose, hands, screen, crosshair target, held keys
{"screenshot": "name"}          the LAST RENDERED frame
{"fade": true}                  pose sources + each part's fade weight
{"config": {"core.smoothPoseTransitions": false}}   core options, in memory only
{"packs": ["FreshAnimations", "FA+Player"]}        resource packs, in order; the rest off
{"model": "villager"} {"model": {"entity": "player", "depth": 3}}   the renderer's model tree
{"burst": {"count": 8, "every": 1, "name": "atk", "fade": true}}   expanded by server.py and the CLI
{"orbit": [90, 10, 5]}          camera at [yaw offset, pitch, distance] around the player; false = off
{"orbit": [0, 80, 3, true]}     the same, pinned in the world where the player is now (stops following)
{"parcool": true}               ParCool 4: running animations, overwriting/blend factor, driven parts
```

`orbit` and `parcool` exist in the NeoForge 1.21.1 driver only (the orbit is a `Camera.setup` mixin,
the driver's only one). The yaw offset is relative to where the player faces - `0` is behind it, `180` in front - so `90` stays side-on
while it runs and turns — the only way to see a lean, since `front`/`back` look along the movement.

The Create hat fix has its own switch, so one run can shoot both states without a reload:
`{"config": {"create.hats": false}}` → screenshot → `{"config": {"create.hats": true}}` → screenshot.

`packs` is how one run shoots the same scene with and without a pack: a name is matched against the
pack ids exactly or as a substring, `vanilla` is always kept, and the reload starts *after* the
script answers — so put it last in its own call and let the next call be the wait (a reload of a
large profile takes tens of seconds; `mc_status` goes stale meanwhile, which is the signal).

`model` is the probe for "the worn thing sits in the wrong place": it prints every `ModelPart` field
of the model the renderer will use — the class it really is, how many cubes it still has, and its
transform. Under EMF a part the pack replaced reports `cubes: 0` (the geometry moved into a custom
child), which is what mods measuring the model fall back from.

Keys: `forward back left right jump sneak sprint attack use drop swap inventory`, or any mapping by
its translation key (`key.carry.desc`).

## How it works

**Launch.** Profile → loader and version from the Modrinth App database
(`~/Library/Application Support/ModrinthApp/app.db`, tables `instances` + `instance_content_sets`;
`instance_launch_overrides` holds memory and extra JVM args as SQLite JSONB — decode with `json()`
from Python's sqlite3, the system `sqlite3` CLI is too old). The flattened version JSON lives in
`~/Modrinth/meta/versions/<id>/`; the classpath is its libraries minus `include_in_classpath: false`
(installer-only jars that break NeoForge/Forge if included), plus the version jar. Java comes from
`~/Modrinth/meta/java_versions/`. The game starts offline as `Dev` with `--accessToken 0` and
`--quickPlaySingleplayer <world>`: **no account token is ever read.** Everything the profile had
already downloaded is reused, so nothing is fetched.

**Sandbox.** Mods are symlinked from the profile, except our `emf_compat_*` jars, which are copied
from `upload/` by matching `emf_compat_<addon>_<mcversion>_`. The driver jar is copied in. `config/`
is copied, `resourcepacks/` and `shaderpacks/` linked, `options.txt` copied and patched (no pause on
lost focus, sound off, chat hidden, no toggle crouch/sprint). One world is copied on first use;
`fresh_world=True` copies it again. `.disabled` mods stay disabled, unless `enable=["artifacts"]`
names them — then they are linked into the sandbox under their enabled name, and the profile still
keeps its `.disabled` file. `disable=["punchy"]` (CLI `--disable punchy`) does the reverse for one run:
Punchy takes over first-person hand rendering, so nothing hooked into `PlayerRenderer.renderHand`
runs while it is installed.

**Driving.** The driver polls `<sandbox>/mctest/inbox/*.json` every client tick, runs the steps and
writes the answer to `outbox/`; both sides write to a temp file and rename, so half-written files are
never read. `status.json` is refreshed every 10 ticks (`inWorld`, `screen`, `fps`) — that is how
`wait_ready` knows the world is up, and how it spots a loader error screen instead of timing out.
Held keys are re-asserted every tick, because opening a screen releases every mapping.

**Screenshots** go through vanilla `Screenshot.grab` into the sandbox's `screenshots/`, so no screen
recording permission is involved. They show the frame rendered *before* the step, so leave a tick
between changing something and shooting it. `server.py` crops around the player and, for several
frames, builds one contact sheet — one image instead of eight.

**The `model` probe** reads the model the renderer holds. It finds the model by field *type*, the
parts by walking the root's children, and `cubes`/`children` by their generic type — names, whether
of fields or methods, are only readable where the game runs on official mappings (NeoForge 1.21.1),
and would be `field_3661` on Fabric and `field_78116_c` on Forge.

**Core probes** (`fade`, `config`) reach the core by reflection, so the driver compiles against
nothing of ours and works with any core version. `fade` reports the pose sources on the player and
each part's interpolator weight as `"0.62 in"` / `"0.35 out"`. Fade in and out take 4–5 ticks; with
smoothing off the sources are still there but no weights appear at all.

## Adding things

**A step.** Add a `case` to `run(...)` in `driver/*/src/main/java/strm/mctest/Driver.java` — all five
copies; they differ only in the version-specific lines listed below. Document it in the `mc_steps`
docstring in `server.py` and in the table above. Rebuild the driver.

**A probe into our own code.** Use reflection (see `fade`/`config`), never a compile dependency:
the driver has to load next to whatever core version is installed in the profile.

**A target.** Create `driver/<loader>-<mc>/` with `build.gradle`, the loader entry point and
metadata, and a copy of `Driver.java`. `mctest.py` finds it by the folder name
`<loader>-<gameversion>`, so it must match what `mc_profiles` prints. Known differences:

| Version | Difference |
|---|---|
| 1.21.11+ | `Inventory.get/setSelectedSlot`, `ResourceKey.identifier()`, `Screenshot.grab(..., int downscale, ...)` |
| 26.x | `Level.getOverworldClockTime()` instead of `getDayTime()` |
| 26.2 | `mc.gui.screen()/setScreen`, `gameRenderer.mainRenderTarget()`, no `Options.hideGui` |
| Forge 1.20.1 | needs `pack.mcmeta` in the jar, or a warning screen blocks quick play |
| everywhere | `KeyMapping.getKey()` is gone on new versions — use `InputConstants.getKey(mapping.saveString())` |

## Traps worth remembering

- **Left click on a block in reach is mining, not attacking.** Better Combat plays nothing and
  vanilla just swings. Check `state.target`; aim up (`{"look": [180, -60]}`) or clear the area
  (`fill ~-5 ~ ~-5 ~5 ~3 ~5 air`). Crouching lowers the eyes onto grass.
- **ParCool 4 needs its skill tree unlocked** (`parcool action unlock @s all`), or no action starts
  and nothing tells you why. Keys: crawl `key.parcool.crawl` (C), hang `key.parcool.hang` (right
  mouse), dodge/breakfall/side wall run `key.parcool.dodge` (R). A hang only catches when the top of
  the hitbox is within ~0.2 of the ledge and the wall within 0.15: for a wall topping out at y=-56,
  `tp @s <x> -57.8 <wall face + 0.35>` while holding the hang key. A chat screen releases ParCool's
  own key state, so a hang ends when chat opens.
- **A pack that fails EMF's ASM compile is dropped whole**, and `latest.log` only says
  "Failure parsing ASM". The reason goes to stdout: read `run/mctest/<profile>/mctest/launcher.out`
  (e.g. "a variable was used both as a number and a boolean" for `!var.x`). `launch --emf-log`
  also turns on EMF's model-creation and ASM logs in the sandbox's config copy.
- **WATUT marks a scripted player as AFK** ("zZ") and bows its head; shoot animations with
  `disable=["watut", "emf_compat_watut"]`. A pack switch while the chat renders can crash vanilla's
  font upload - hide the GUI first.
- **The `front` camera** puts the camera in the direction the player looks, so on screen the player
  faces you and looks at whatever is *behind* them. The camera also collides with a block it runs
  into — a chest two blocks ahead fills the frame with a close-up.
- **Carry On** picks up with its own mapping (`key.carry.desc`) on shift, so `hold` presses every
  mapping bound to that physical key, as a real press would. Its `maxDistance` is 2.5 from the feet;
  put the chest right in front (`setblock ~ ~ ~-1 chest`, look ~50° down), and to place it look
  ~35° down while crouching — steeper and the target cell overlaps the player, so nothing is placed.
  A click is consumed at the start of the next tick with the crosshair it had then, so turning away
  one tick after the click keeps the pickup and still gives a clean camera.
- **Chat must not be set to "hidden".** With `chatVisibility:2` the server answers every `cmd` step
  with "Chat disabled in client options" and runs none of them — silently, since the chat is hidden.
  The sandbox is written with `1` (system messages only) and screenshots are taken with `hideGui`.
- **`server.py` is a stdio server.** Running it by hand without a client just hangs waiting on stdin.
- The game may be closed by hand at any time; `mc_steps` then answers `game exited` with a log tail.
