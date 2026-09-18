"""Writes parcool-obstacle-scene.json and parcool-obstacle-run.json.

An obstacle course in one lane (x -530..-508, centre x -519.5), running north (yaw 180) from
z 445 to z 205 on a floor at y 199, next to the parcool-all-scene course. Lava fills the places
that have to be crossed by a move: the pit, the strip before the wall run, the floor along the
side wall, under the bar and under the whole zipline. At the end a bar across the lane to
swing on.

The run is one go from start to finish, no teleports: `until` steps wait for the player to reach
a spot (and shoot while they wait), so the timing does not depend on how fast the frames come.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
X = -519.5
W, E = -530, -508  # the whole floor; lava spans it so nothing can be walked round

scene = [
    {"cmd": "gamerule sendCommandFeedback false"}, {"cmd": "gamemode survival"},
    {"cmd": "difficulty peaceful"}, {"cmd": "time set noon"},
    {"cmd": "gamerule doDaylightCycle false"}, {"cmd": "gamerule doWeatherCycle false"},
    {"cmd": "gamerule doMobSpawning false"}, {"cmd": "gamerule fallDamage false"},
    {"cmd": "weather clear"}, {"cmd": "parcool action unlock @s all"},
    {"cmd": "effect give @s saturation infinite 10 true"},
    {"cmd": "effect give @s resistance infinite 10 true"}, {"cmd": "clear @s"},
]


def at(z):
    scene.extend([{"cmd": f"tp @s {X} 220 {z}.5 180 0"}, {"wait": 30}])


def fill(x1, y1, z1, x2, y2, z2, block):
    scene.append({"cmd": f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}"})


def setblock(x, y, z, block):
    scene.append({"cmd": f"setblock {x} {y} {z} {block}"})


def lava(z1, z2, y=199, x1=W, x2=E):
    """A lava strip across the floor (or a platform top at y). The outer columns stay solid so
    the lava does not pour off the sides, and barriers on them stop a walk round it."""
    fill(x1, y - 1, z1, x2, y - 1, z2, "stone")
    fill(x1 + 1, y, z1, x2 - 1, y, z2, "lava")
    fill(x1, y + 1, z1, x1, y + 3, z2, "barrier")
    fill(x2, y + 1, z1, x2, y + 3, z2, "barrier")


# floor, and air above and below it (earlier versions of the course left blocks there)
for z in range(445, 195, -40):
    at(z - 20)
    fill(W, 185, z - 39, E, 198, z, "air")
    fill(W, 199, z - 39, E, 199, z, "smooth_stone")
    fill(W, 200, z - 39, E, 215, z, "air")

HOOK = "parcool:iron_zipline_hook[facing=up]"
at(420)
fill(-524, 200, 432, -515, 200, 432, "smooth_stone")           # vault: low wall
fill(-524, 201, 424, -515, 201, 420, "stone_bricks")           # slide / crawl: a 1-high gap
lava(410, 408)                                                 # pit
# charge jump: 1.5 high. A plain jump (1.25) does not make it; ParCool's charge jump tops out
# at +2.2 and only lands from right against the step.
fill(-524, 200, 400, -515, 200, 394, "stone_bricks")
fill(-524, 201, 400, -515, 201, 394, "stone_brick_slab[type=bottom]")
at(375)
# dodges on the flat z 393..382, then a 3-high ledge: catch, shimmy, climb up
fill(-524, 200, 380, -515, 202, 371, "stone_bricks")
lava(372, 371, y=202, x1=-524, x2=-515)                                          # the ledge top before the wall
fill(-524, 200, 370, -515, 207, 363, "stone_bricks")           # wall run up, catch, climb
# the 8-block drop off its far side ends in a roll
# side wall at the lane's left. ParCool starts the side wall run only with a small gap to the wall
# (0.15 worked; touching it or 0.5 away did not), so the runner strafes to x -523.55 first.
fill(-525, 200, 356, -525, 205, 330, "stone_bricks")
lava(344, 338)
fill(-520, 203, 326, -520, 203, 321, "end_rod[facing=north]")  # bar
lava(325, 322)
at(300)
fill(-524, 200, 310, -515, 208, 304, "stone_bricks")           # chain up a wall, catch, climb
fill(-520, 200, 311, -520, 208, 311, "chain[axis=y]")
# zipline. A 4-high post keeps the rope above head height over the platform (a jump grabs it)
# and its sag off the platform edge: ParCool refuses a rope through blocks. The edge is only
# ~1.5 blocks past the grab: further on, the sagging rope drags the rider's feet onto the edge
# and the ride stops there.
fill(-520, 209, 307, -520, 212, 307, "stone_bricks")
setblock(-520, 213, 307, HOOK)
lava(303, 260)
# the rope ends over a 2-high tower: the rider hangs ~2 blocks under it and lands on top
fill(-524, 200, 259, -515, 201, 252, "stone_bricks")
fill(-520, 202, 258, -520, 203, 258, "stone_bricks")
setblock(-520, 204, 258, HOOK)
at(236)
fill(-526, 186, 251, -512, 199, 221, "glass")                  # dive off the tower, fast swim
fill(-525, 187, 250, -513, 198, 222, "water")
fill(-525, 199, 250, -513, 199, 222, "air")
fill(-525, 187, 222, -513, 198, 222, "stone_bricks")           # a step at water level
fill(-520, 200, 213, -520, 201, 213, "hay_block")              # hide in block
# swing bar across the lane: grab it with a run-up, shift right, swing, let go
fill(-526, 203, 203, -513, 203, 203, "end_rod[facing=east]")
fill(W, 199, 195, E, 199, 195, "gold_block")                   # finish line
# tie the rope. The hooks are tiny: aim from their own height (a barrier to stand on) or the
# click hits the post instead.
scene += [
    {"cmd": "item replace entity @s weapon.mainhand with parcool:zipline_rope"},
    {"cmd": "fill -520 211 304 -520 211 305 barrier"},
    {"cmd": f"tp @s {X} 212 304.5 0 10"}, {"look": [0, 10]}, {"wait": 15},
    {"click": "use"}, {"wait": 5},
    {"cmd": "fill -520 211 304 -520 211 305 air"},
    {"cmd": f"tp @s {X} 202 256.0 0 -12"}, {"look": [0, -12]}, {"wait": 15},
    {"click": "use"}, {"wait": 5},
    {"cmd": "clear @s"},
    {"cmd": "setworldspawn -520 200 444"},
    {"cmd": f"tp @s {X} 200 444.5 180 0"}, {"look": [180, 0]}, {"wait": 10},
]

# ---- the run -------------------------------------------------------------------------------
run = [{"hideGui": True}, {"releaseAll": True}, {"orbit": False},
       {"cmd": f"tp @s {X} 200 444.5 180 0"}, {"look": [180, 0]}, {"wait": 20},
       {"orbit": [90, 10, 5]}]


def until(name, timeout=120, every=2, **cond):
    """Waits for cond (z_lt=413.7 -> "z<": 413.7) and shoots every `every` ticks meanwhile."""
    c = {k.replace("_lt", "<").replace("_gt", ">"): v for k, v in cond.items()}
    return {"until": {**c, "timeout": timeout, "shots": name, "every": every}}


def log(name):
    return {"log": f"obstacle {name}"}


def hold(*keys):
    return [{"hold": k} for k in keys]


def release(*keys):
    return [{"release": k} for k in keys]


def tap(key, ticks=2):
    return [{"hold": key}, {"wait": ticks}, {"release": key}]


# fast run, vault, slide under the gap, crawl out, jump the lava pit
run += [log("run_vault"), *hold("forward", "sprint"),
        until("run", z_lt=433.5, every=3), until("vault", z_lt=427),
        log("slide"), *hold("key.parcool.crawl"), until("slide", z_lt=418.5, every=3),
        *release("key.parcool.crawl", "sprint"), {"wait": 2}, *hold("sprint"),
        until("stand", z_lt=411.8), log("pit"), *hold("jump"),
        until("pit", z_lt=407.5, onGround=True, timeout=40), *release("jump")]
# walk into the step, charge, jump onto it; walk off its far edge
run += [log("charge"), *release("sprint"), until("to_step", z_lt=401.4, timeout=80, every=4),
        {"wait": 4}, *release("forward"), *hold("sneak"), {"wait": 30},
        *release("sneak"), *hold("jump", "forward"), {"wait": 2}, *release("jump"),
        until("charge_jump", y_gt=201.4, onGround=True, timeout=40),
        until("step_off", z_lt=392.5, onGround=True, timeout=80, every=3), *release("forward")]
# dodges
run += [log("dodge"), {"orbit": [60, 10, 5]}, {"wait": 4},
        *hold("right"), {"wait": 2}, *tap("key.parcool.dodge"),
        until("dodge_right", timeout=8), *release("right"), {"wait": 8},
        *hold("left"), {"wait": 2}, *tap("key.parcool.dodge"),
        until("dodge_left", timeout=8), *release("left"), {"wait": 8}, {"look": [180, 0]}]
# catch the ledge, shimmy left and back, climb up
run += [log("ledge"), {"orbit": [25, 10, 4.5]}, *hold("forward"),
        until("to_ledge", z_lt=382.0, timeout=80, every=4),
        *hold("jump"), {"wait": 3}, *hold("key.parcool.hang"), *release("jump"),
        until("catch", y_gt=200.9, timeout=20), *release("forward"), {"wait": 6},
        *hold("left"), until("shimmy_left", timeout=16, every=4), *release("left"),
        *hold("right"), until("shimmy_right", timeout=16, every=4), *release("right"),
        {"wait": 4}, *tap("jump"), until("climb_up", y_gt=202.9, onGround=True, timeout=30, every=3),
        *release("key.parcool.hang")]
# run over the ledge top, jump before the lava onto the wall, catch its top, climb
run += [log("wall_run"), {"orbit": [90, 10, 6]}, {"look": [180, -20]},
        *hold("forward", "sprint"), until("wall_approach", z_lt=373.7, timeout=40),
        *hold("jump"), until("wall_run", timeout=6), *hold("key.parcool.hang"),
        until("wall_top", timeout=4), *release("jump"), until("wall_catch", timeout=6, every=3),
        *release("forward", "sprint"), {"wait": 3}, *tap("jump"),
        until("wall_climb", y_gt=207.9, onGround=True, timeout=30, every=3),
        *release("key.parcool.hang"), {"look": [180, 0]}]
# run off the far edge, press breakfall in the air (holding it from the rooftop does nothing)
run += [log("breakfall"), *hold("forward", "sprint"),
        until("drop_run", z_lt=362.9, onGround=False, timeout=60, every=3),
        *hold("key.parcool.breakfall"), until("drop_roll", y_lt=200.1, onGround=True, timeout=40),
        until("roll", timeout=10), *release("key.parcool.breakfall")]
# side wall run over the lava, jump off the wall past it
run += [log("side_wall"), {"orbit": [-90, 10, 6]}, *release("forward", "sprint"), *hold("left"),
        until("to_wall_side", x_lt=-523.45, timeout=40), *release("left"), {"look": [180, 0]},
        *hold("forward", "sprint"), until("to_side_wall", z_lt=348.0, timeout=60, every=3),
        *hold("jump"), {"wait": 3}, *release("jump"), *hold("key.parcool.horizontal_wall_run"),
        until("side_wall_run", z_lt=338.8, timeout=40), *tap("jump"),
        until("wall_jump", onGround=True, timeout=30), *release("key.parcool.horizontal_wall_run", "sprint"),
        {"look": [180, 0]}, *release("forward"), *hold("right"),
        until("back_to_lane", x_gt=-519.7, timeout=40), *release("right")]
# bar over the lava
run += [log("bar"), {"orbit": [90, 10, 5]}, *hold("forward"),
        until("to_bar", z_lt=327.8, timeout=80, every=4),
        *hold("jump"), {"wait": 3}, *hold("key.parcool.hang"), *release("jump", "forward"),
        {"wait": 6}, *hold("forward"), until("bar", z_lt=321.6, timeout=200, every=4),
        *release("forward"), {"wait": 4}, *release("key.parcool.hang"),
        until("bar_drop", onGround=True, timeout=20)]
# chain up the wall; at its top ParCool hands over to the ledge hang; jump climbs up
run += [log("chain"), {"orbit": [90, 10, 6]}, *hold("forward"),
        until("to_chain", z_lt=312.5, timeout=60, every=4), {"wait": 3},
        *hold("key.parcool.hang"), {"wait": 4}, *release("forward"), {"wait": 6}, *hold("forward"),
        until("chain", y_gt=207.5, timeout=140, every=6), until("chain_top", timeout=12, every=4),
        *release("forward"), *tap("jump"),
        until("chain_climb", y_gt=208.9, onGround=True, timeout=30, every=3),
        *release("key.parcool.hang")]
# round the post, jump + hang under the rope, ride over the lava to the tower
run += [log("zipline"), {"orbit": [90, 10, 8]}, {"wait": 6}, *hold("forward"),
        until("off_edge", z_lt=309.8, timeout=30), *release("forward"), {"wait": 4}, *hold("left"),
        until("post_left", x_lt=-521.0, timeout=20), *release("left"), *hold("forward"),
        until("post_pass", z_lt=306.0, timeout=40), *release("forward"), *hold("right"),
        until("post_right", x_gt=-519.6, timeout=20), *release("right"), {"wait": 10},
        *hold("key.parcool.hang", "jump"), {"wait": 3}, *release("jump"),
        until("zipline", z_lt=259.6, timeout=260, every=8),
        until("zipline_end", timeout=10), *release("key.parcool.hang"),
        until("tower", onGround=True, timeout=20)]
# round the second post, dive off the tower, swim, climb out on the step
run += [log("dive"), {"orbit": [90, 25, 7]}, *hold("left"),
        until("post2_left", x_lt=-521.2, timeout=20), *release("left"),
        *hold("forward", "sprint"), until("dive_run", z_lt=253.4, timeout=40),
        *tap("jump"), until("dive", y_lt=198.5, timeout=30),
        {"orbit": [90, 60, 6]}, until("swim", z_lt=226.0, timeout=200, every=6),
        log("pool_exit"), {"orbit": [90, 25, 6]}, *release("sprint"), *hold("jump"),
        until("pool_exit", y_gt=199.9, z_lt=221.9, onGround=True, timeout=80, every=4),
        *release("jump")]
# back to the middle, hide in the hay, round it, over the finish line
run += [log("hide"), {"orbit": [90, 10, 5]}, *hold("right"),
        until("hay_line", x_gt=-519.7, timeout=20), *release("right"),
        until("to_hay", z_lt=214.7, timeout=60, every=4), *release("forward"),
        {"look": [180, 40]}, *hold("sneak"), {"wait": 3}, *hold("key.parcool.hide_in_block"),
        until("hide", timeout=40, every=4), *release("key.parcool.hide_in_block", "sneak"),
        until("hide_out", timeout=20, every=5), {"look": [180, 0]},
        *hold("left"), until("hay_left", x_lt=-521.0, timeout=20), *release("left"),
        *hold("forward"), until("hay_pass", z_lt=211.5, timeout=40), *release("forward"),
        *hold("right"), until("swing_line", x_gt=-519.7, timeout=20), *release("right")]
# swing bar: run up and grab it, shift right, swing forward-back twice, let go
def half_swing(key, name):
    """Holds a direction through one half of the swing: the speed swings its way, then back
    through zero."""
    toward = {"swing_gt": 0.05} if key == "forward" else {"swing_lt": -0.05}
    back = {"swing_lt": 0.0} if key == "forward" else {"swing_gt": 0.0}
    return [*hold(key), until(name + "_out", timeout=30, every=3, **toward),
            until(name + "_back", timeout=30, every=3, **back), *release(key)]


run += [log("swing_bar"), {"orbit": [60, 10, 7]}, *hold("forward", "sprint"),
        until("swing_run", z_lt=205.6, timeout=40), *tap("jump"), *hold("key.parcool.hang"),
        *release("forward", "sprint"), until("swing_grab", timeout=10, every=3),
        *hold("right"), until("swing_shift", x_gt=-517.8, timeout=40, every=4), *release("right")]
# swinging is a pendulum ParCool pushes with the forward/back input, so each key is held for
# half a swing. (Jumping off a swing was dropped: ParCool takes it only at a narrow angle and
# speed, and a scripted press lands there only now and then.)
run += [*half_swing("forward", "swing_f1"), *half_swing("back", "swing_b1"),
        *half_swing("forward", "swing_f2"), *half_swing("back", "swing_b2"),
        until("swing_settle", swing_lt=0.02, swing_gt=-0.02, timeout=30, every=3),
        *release("key.parcool.hang"), until("swing_drop", onGround=True, timeout=30),
        log("finish"), {"look": [180, 0]}, *hold("forward", "sprint"),
        until("finish", z_lt=194.5, timeout=60, every=3),
        {"releaseAll": True}, {"orbit": False}, {"state": True}]

json.dump(scene, open(os.path.join(HERE, "parcool-obstacle-scene.json"), "w"))
json.dump(run, open(os.path.join(HERE, "parcool-obstacle-run.json"), "w"))
old = os.path.join(HERE, "parcool-obstacle-sections.json")
if os.path.exists(old):
    os.remove(old)
