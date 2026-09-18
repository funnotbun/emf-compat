"""Writes the demo-pad scenarios: short takes for the GIFs on the store pages.

The pad is the clearing in world `test` at -2 79 0 (flower forest, a tree behind the player) —
the background the published GIFs already use. Every take starts from the same spot and stance,
the camera is third person *front* (the player faces the lens, as in those GIFs), and the takes
are separated by a still second so the recording can be cut between them.

Each addon gets its own file, because the mods behind them cannot all be loaded at once
(Hackers 'n Slashers refuses to load next to Better Combat, ParCool wants its own props):

    demo-pad-carryon.json     lift a chest, carry it, set it down
    demo-pad-parcool.json     fast run, vault, wall run and climb, side-on (see below)
    demo-pad-hns.json         weapon stance and an attack combo

Run one with `mctest.py steps Test <file>` while the recorder is on; the log lines
(`[mctest] demo <take>`) mark where each take starts.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

# Where the player stands, and the way they face: the F3 stance of the published GIFs.
X, Y, Z = -2.5, 79, 0.5
YAW, PITCH = 114, 10


def setup(*items):
    """Same start for every take: quiet, noon, no weather, the player on his mark."""
    steps = [
        {"hideGui": True}, {"releaseAll": True},
        {"cmd": "gamerule sendCommandFeedback false"}, {"cmd": "gamerule doDaylightCycle false"},
        {"cmd": "gamerule doWeatherCycle false"}, {"cmd": "gamerule doMobSpawning false"},
        {"cmd": "gamerule fallDamage false"}, {"cmd": "weather clear"}, {"cmd": "time set noon"},
        {"cmd": "difficulty peaceful"}, {"cmd": "gamemode survival"},
        {"cmd": "effect give @s saturation infinite 10 true"},
        {"cmd": "effect give @s resistance infinite 10 true"},
        {"cmd": "clear @s"},
    ]
    for slot, item in enumerate(items):
        if item:
            steps.append({"cmd": f"item replace entity @s hotbar.{slot} with {item}"})
    # The ground west of the mark is levelled: that is where the props go and where the player
    # walks, and it is behind the lens (the camera stands in front of a player facing west), so
    # nothing of the background in frame changes.
    steps += [{"cmd": "fill -16 78 -4 -1 78 4 grass_block"},
              {"cmd": "fill -16 79 -4 -3 86 4 air"},
              {"cmd": f"tp @s {X} {Y} {Z} {YAW} {PITCH}"}, {"look": [YAW, PITCH]},
              {"camera": "front"}, {"slot": 0}, {"wait": 20}]
    return steps


def take(name):
    """Marks a take in the log and leaves a still second in front of it to cut on."""
    return [{"releaseAll": True}, {"orbit": False}, {"cmd": f"tp @s {X} {Y} {Z} {YAW} {PITCH}"},
            {"look": [YAW, PITCH]}, {"camera": "front"}, {"wait": 20}, {"log": f"demo {name}"}]


def hold(*keys):
    return [{"hold": k} for k in keys]


def release(*keys):
    return [{"release": k} for k in keys]


def tap(key, ticks=2):
    return [{"hold": key}, {"wait": ticks}, {"release": key}]


def shots(name, count, every=3):
    """Screenshots at a steady beat, to check the framing without a recorder."""
    out = []
    for i in range(count):
        out += [{"wait": every}, {"screenshot": f"{name}_{i:02d}"}]
    return out


# ---- Carry On: lift a chest, carry it, set it down -------------------------------------------
carryon = setup()
# The chest goes one block in front of where the player stands, on the pad.
carryon += [{"cmd": "setblock -5 79 0 chest[facing=east]"},
            {"cmd": "data merge block -5 79 0 {Items:[{Slot:13b,id:\"minecraft:golden_apple\",Count:5b}]}"},
            {"wait": 10}]
carryon += take("carryon_lift")
# Aimed at the chest: the crosshair has to be on it, so the state probe prints the target.
carryon += [{"look": [90, 32]}, {"wait": 10}, {"state": True}]
# Carry On lifts a block with a GUI on sneak + use; plain use would just open the chest.
carryon += [{"hold": "sneak"}, {"wait": 3}, {"click": "use"}, {"wait": 6},
            {"release": "sneak"}, {"closeScreen": True}, {"wait": 6}] + shots("carry_lift", 6)
carryon += [{"log": "demo carryon_walk"}, {"look": [YAW, PITCH]}, *hold("forward"), {"wait": 14},
            *release("forward")] + shots("carry_walk", 6)
# Back on the mark for the last take: walking west takes the player off it, and the camera, which
# stands in front, ends up pressed against the raised ground there.
carryon += take("carryon_place") + [{"look": [90, 30]}, {"wait": 10},
            {"click": "use"}, {"wait": 10}] + shots("carry_place", 6) + [{"state": True}]
carryon += [{"releaseAll": True}, {"camera": "first"}]

# ---- ParCool: the moves, from the side ---------------------------------------------------------
# Front camera does not work for these: it stands where the player is running, so it ends up inside
# the props. ParCool's takes use the orbit camera side-on (the run crosses the frame), and the lane
# is cleared wide enough for the camera to travel outside it.
parcool = setup()
parcool += [{"cmd": "parcool action unlock @s all"},
            {"cmd": "fill -18 78 -8 -1 78 8 grass_block"},
            {"cmd": "fill -18 79 -8 -1 86 8 air"},
            # Narrow props: side-on the camera looks along z, and a wide wall would stand in front
            # of the player instead of beside him.
            {"cmd": "fill -8 79 -1 -8 79 1 smooth_stone"},        # vault wall, 1 high
            {"cmd": "fill -14 79 -1 -14 85 1 stone_bricks"},      # wall run / climb wall
            {"wait": 10}]
parcool += take("parcool_run") + [{"orbit": [90, 6, 4]}]
parcool += [{"look": [90, 5]}, *hold("forward", "sprint"), {"wait": 25}] + shots("pc_run", 5) + \
           [{"log": "demo parcool_vault"}] + shots("pc_vault", 6) + [*release("forward", "sprint")]
parcool += take("parcool_wallrun") + [{"orbit": [90, 6, 4.5]}]
parcool += [{"cmd": f"tp @s -11.5 79 {Z} 90 0"}, {"look": [90, 0]}, {"wait": 15},
            *hold("forward", "sprint"), {"wait": 8}, *hold("jump"), {"wait": 3},
            *hold("key.parcool.hang"), {"wait": 6}, *release("jump")] + shots("pc_wall", 8) + \
           [*release("forward", "sprint"), {"wait": 4}, *tap("jump"), {"wait": 20}] + \
           shots("pc_climb", 6) + [*release("key.parcool.hang")]
parcool += [{"releaseAll": True}, {"orbit": False}, {"camera": "first"}]

# ---- Hackers 'n Slashers: stance and a combo --------------------------------------------------
hns = setup("netherite_sword", "shield")
hns += take("hns_stance")
hns += shots("hns_stance", 6)
hns += [{"log": "demo hns_combo"}]
for i in range(4):
    hns += [{"click": "attack"}, {"wait": 6}] + shots(f"hns_hit{i}", 3, 2)
hns += [{"log": "demo hns_block"}, {"hold": "use"}, {"wait": 20}] + shots("hns_block", 4) + \
       [{"release": "use"}]
hns += [{"releaseAll": True}, {"camera": "first"}]

for name, steps in (("carryon", carryon), ("parcool", parcool), ("hns", hns)):
    with open(os.path.join(HERE, f"demo-pad-{name}.json"), "w") as f:
        json.dump(steps, f)
    print(f"demo-pad-{name}.json  {len(steps)} steps")
