"""Writes the ParCool cross-mod scenarios: who owns the arms when two addons want them.

The stand is the sky course from `parcool-all-scene.json` (world `New World2`, y=200) — run that
first, it unlocks the skill tree and builds every prop these scripts teleport to.

Each file pairs ParCool *carriers* (a move that takes the arms: fast run, crawl, charge jump, the
bar hang, the ledge hang, the zipline) with one addon's *action* (a swing, a swap, a camera, a
carried block). Every pairing is marked `X <addon> <carrier>/<action>` in the log and shot as a
burst with the `fade` probe, so the answer is readable without watching: `fade` lists the pose
sources on the player and each part's weight, `parcool` lists ParCool's own running animations and
the parts it drives. Two sources on the same arm in the same frame is the finding.

    parcool-x-nea.json          swap hands / hotbar change (empty -> filled map) / eating
    parcool-x-create.json       the skyhook on a chain conveyor, both orders
    parcool-x-tacz.json         a gun held and aimed
    parcool-x-carryon.json      a carried chest, incl. the known charge-jump conflict
    parcool-x-misc.json         Exposure camera, Immersive Melodies flute
    parcool-x-bettercombat.json needs enable=["bettercombat-neoforge", "emf_compat_better_combat"]
    parcool-x-hns.json          needs enable=["hackersandslashers-2.0", "emf_compat_hackers_and_slashers"]
                                (Better Combat does not load next to it)

Run one with `mctest.py steps Test <file>`; the long ones outlive an MCP call, so drive them from
`run_steps`.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

# Marks on the course. Names match parcool-all-scene.json's props.
RUNWAY = "-595.5 200 410.5 180 0"      # long clear lane, north
FLAT = "-595.5 200 380.5 180 0"        # same lane, short of the props
VAULT_RUN = "-584.5 200 377.5 180 0"   # into the 2-high wall at z 322
LEDGE_RUN = "-584.5 200 326.5 180 0"   # the 3-high ledge, hang and climb
WALL_RUN = "-574.5 200 325.5 180 -20"  # the 9-high wall
BAR = "-554.5 200 360.5 180 0"         # under the end-rod bar at y 203
POOL = "-546.5 197 409.5 180 10"       # the water


def mark(name):
    return [{"releaseAll": True}, {"log": f"X {name}"}]


def probe(name, count=6, every=3):
    """A burst with the fade weights, plus what ParCool thinks it is doing."""
    return [{"parcool": True}, {"burst": {"count": count, "every": every, "name": name, "fade": True}},
            {"parcool": True}, {"state": True}]


def tp(where):
    yaw, pitch = where.split()[3:5]
    return [{"cmd": f"tp @s {where}"}, {"look": [float(yaw), float(pitch)]}, {"wait": 8}]


def setup(extra=()):
    """Quiet world, the player fed and safe, the camera side-on."""
    steps = [{"hideGui": True}, {"releaseAll": True},
             {"cmd": "gamerule sendCommandFeedback false"}, {"cmd": "gamerule fallDamage false"},
             {"cmd": "difficulty peaceful"}, {"cmd": "gamemode survival"}, {"cmd": "time set noon"},
             {"cmd": "effect give @s saturation infinite 10 true"},
             {"cmd": "effect give @s resistance infinite 10 true"},
             {"cmd": "parcool action unlock @s all"}, {"cmd": "clear @s"},
             # A pose left registered by the file before this one would be read as this addon's
             # doing, so every script starts by saying out loud what is still holding the player.
             {"wait": 10}, {"log": "X leak-check (sources here belong to the previous script)"},
             {"fade": True}]
    steps += list(extra)
    return steps + tp(RUNWAY) + [{"orbit": [90, 10, 5]}]


# ---- the ParCool carriers, as (enter, hold, leave) ---------------------------------------------

def fast_run(ticks=22):
    """ParCool hands the arms over during a fast run - the clearest carrier there is."""
    return ([{"hold": "forward"}, {"hold": "sprint"}, {"wait": ticks}], [], [{"releaseAll": True}])


def crawl(ticks=16):
    return ([{"hold": "key.parcool.crawl"}, {"hold": "forward"}, {"wait": ticks}], [],
            [{"releaseAll": True}])


def charge(ticks=25):
    """Charge crouch, then the leap. Sneak is also Carry On's pick-up key - that is the conflict."""
    return ([{"hold": "sneak"}, {"wait": ticks}], [],
            [{"release": "sneak"}, {"hold": "jump"}, {"wait": 6}, {"releaseAll": True}, {"wait": 12}])


def bar_hang():
    """Onto the end-rod bar: hold the hang key, then be put under it."""
    return ([{"hold": "key.parcool.hang"}, {"cmd": "tp @s -554.5 201.1 360.5 180 0"}, {"wait": 12}],
            [], [{"releaseAll": True}, {"wait": 12}])


def ledge_hang():
    return ([{"hold": "key.parcool.hang"}, {"cmd": "tp @s -584.5 201.2 323.35 180 0"}, {"wait": 12}],
            [], [{"releaseAll": True}, {"wait": 12}])


CARRIERS = {"fast_run": (RUNWAY, fast_run), "crawl": (RUNWAY, crawl), "charge_jump": (FLAT, charge),
            "bar_hang": (BAR, bar_hang), "ledge_hang": (LEDGE_RUN, ledge_hang)}


def carried(addon, carrier, action_during, name=None, orbit=None):
    """One pairing: get into the move, fire the addon's action inside it, watch the hand-back."""
    where, build = CARRIERS[carrier]
    enter, _, leave = build()
    label = name or f"{carrier}/{action_during[0]}"
    steps = mark(f"{addon} {label}") + tp(where)
    steps += [{"orbit": orbit or [90, 10, 5]}]
    steps += enter
    steps += probe(f"{addon}_{carrier}_in", 3, 2)
    steps += action_during[1]
    steps += probe(f"{addon}_{carrier}_act", 8, 2)
    steps += leave
    steps += probe(f"{addon}_{carrier}_out", 4, 3)
    return steps


# ---- NEA: swap hands, hotbar change (empty hand -> a filled map), eating ------------------------
# The map has to be *filled*: an empty map renders as a blank sheet and NEA's two-handed map pose
# only applies to the filled one, which is also the item whose swap the user asked about.
nea = setup([
    {"slot": 2}, {"cmd": "item replace entity @s hotbar.2 with minecraft:map"}, {"wait": 10},
    {"click": "use"}, {"wait": 25}, {"state": True},        # empty map -> filled map, in hand
    # The offhand copy is what the swap test needs, but it must not exist yet: a map in the left
    # hand already puts NEA into its two-handed map pose, and "empty hand" would not be empty.
    {"slot": 0},
])
# 1. Standing: what the empty-hand -> map change looks like on its own, as the baseline for the fade.
nea += mark("nea baseline/slot_change_standing") + tp(FLAT) + [{"orbit": [140, 5, 4]}]
nea += probe("nea_base_empty", 3, 2) + [{"slot": 2}] + probe("nea_base_map", 8, 2)
nea += [{"slot": 0}] + probe("nea_base_back", 6, 2)
# Now the left hand gets its copy, for the swap that follows.
nea += [{"cmd": "item replace entity @s weapon.offhand from entity @s hotbar.2"}, {"wait": 10},
        {"state": True}]
# 2. The same change during a fast run - ParCool owns the arms, NEA wants them for the swap.
nea += carried("nea", "fast_run", ("slot_change", [{"slot": 2}]))
nea += mark("nea fast_run/slot_change_back") + tp(RUNWAY)
nea += [{"hold": "forward"}, {"hold": "sprint"}, {"wait": 20}, {"slot": 0}] + \
       probe("nea_run_slot_back", 8, 2) + [{"releaseAll": True}]
# 3. Offhand swap (F) while running: the item crosses from the left hand to the right mid-stride.
nea += carried("nea", "fast_run", ("swap_hands", [{"click": "swap"}]))
nea += carried("nea", "crawl", ("swap_hands", [{"click": "swap"}]))
nea += carried("nea", "bar_hang", ("swap_hands", [{"click": "swap"}]), orbit=[140, 0, 5])
# 4. Eating into a run: NEA's eat animation is the one that used to survive into the move.
nea += mark("nea fast_run/eat") + tp(FLAT)
nea += [{"cmd": "item replace entity @s weapon.mainhand with minecraft:cooked_beef 64"},
        {"cmd": "effect clear @s saturation"}, {"cmd": "difficulty easy"},
        {"cmd": "effect give @s hunger 3 255 true"}, {"wait": 70},
        {"cmd": "effect clear @s hunger"}, {"wait": 10}, {"orbit": [60, 10, 4]},
        {"hold": "use"}, {"wait": 12}] + probe("nea_eat", 3, 2) + \
       [{"hold": "forward"}, {"hold": "sprint"}, {"wait": 16}] + probe("nea_eat_run", 8, 2) + \
       [{"releaseAll": True}, {"cmd": "difficulty peaceful"},
        {"cmd": "effect give @s saturation infinite 10 true"}]
nea += [{"releaseAll": True}, {"orbit": False}, {"camera": "first"}]

# ---- Create: the skyhook on a chain conveyor ---------------------------------------------------
# The chain goes up over the clear end of the lane, high enough that the hang is free of the floor.
create = setup([
    {"cmd": "setblock -600 204 415 create:chain_conveyor{Connections:[[8,0,0]]}"},
    {"cmd": "setblock -592 204 415 create:chain_conveyor{Connections:[[-8,0,0]]}"},
    {"wait": 5},
    {"cmd": "data merge block -600 204 415 {Connections:[[8,0,0]]}"},
    {"cmd": "data merge block -592 204 415 {Connections:[[-8,0,0]]}"},
    {"cmd": "item replace entity @s weapon.mainhand with create:wrench"},
    {"wait": 10},
])
# 1. The skyhook on its own: the reference pose, and proof the strand is where we think it is.
create += mark("create baseline/skyhook") + [{"cmd": "tp @s -596.5 200 416.3 0 -89.9"},
                                             {"look": [0, -89.9]}, {"wait": 10}, {"orbit": [90, 0, 5]}]
create += [{"hold": "use"}, {"wait": 6}, {"release": "use"}, {"wait": 20}] + probe("create_hook", 4, 3)
# 2. ParCool moves while hanging from the hook: which pose wins if a move starts under it.
create += mark("create skyhook/parcool_hang_key") + \
          [{"hold": "key.parcool.hang"}, {"wait": 10}] + probe("create_hook_hangkey", 6, 2) + \
          [{"release": "key.parcool.hang"}, {"wait": 6}]
create += mark("create skyhook/parcool_crawl_key") + \
          [{"hold": "key.parcool.crawl"}, {"wait": 10}] + probe("create_hook_crawl", 6, 2) + \
          [{"releaseAll": True}, {"wait": 10}]
create += mark("create skyhook/release") + probe("create_hook_off", 4, 3)
# 3. The other order: ParCool moves with the wrench in hand, then grabbing mid-move.
create += carried("create", "fast_run", ("wrench_in_hand", []))
create += carried("create", "bar_hang", ("wrench_in_hand", []), orbit=[140, 0, 5])
create += [{"releaseAll": True}, {"orbit": False}, {"camera": "first"}]

# ---- TACZ: a gun held and aimed ----------------------------------------------------------------
# TACZ 1.1.8 on 1.21.1 stores the gun in a data component; the `state` right after the give says
# whether the id took (an item with no gun id renders, but plays no stance).
tacz = setup([
    {"cmd": 'item replace entity @s weapon.mainhand with '
             'tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47"}]'},
    {"wait": 8}, {"state": True},
])
tacz += mark("tacz baseline/stance") + tp(FLAT) + [{"orbit": [140, 5, 4]}] + probe("tacz_stance", 4, 3)
tacz += carried("tacz", "fast_run", ("gun_held", []))
tacz += carried("tacz", "fast_run", ("aim", [{"hold": "use"}, {"wait": 6}]))
tacz += carried("tacz", "crawl", ("gun_held", []))
tacz += carried("tacz", "bar_hang", ("gun_held", []), orbit=[140, 0, 5])
tacz += carried("tacz", "charge_jump", ("gun_held", []))
tacz += [{"releaseAll": True}, {"orbit": False}, {"camera": "first"}]

# ---- Carry On: the carried block, and the charge jump under it ---------------------------------
# Carry On picks up on sneak + use, and ParCool charges a jump on sneak: the same key, which is the
# conflict already on the list. The pick-up is scripted first, then sneak is pressed again.
carryon = setup()
carryon += mark("carryon pickup") + tp(FLAT)
carryon += [{"cmd": "setblock -596 200 379 chest[facing=south]"}, {"wait": 6},
            {"look": [180, 30]}, {"wait": 8}, {"state": True},
            {"hold": "sneak"}, {"wait": 3}, {"click": "use"}, {"wait": 8},
            {"release": "sneak"}, {"closeScreen": True}, {"wait": 10}]
carryon += [{"orbit": [140, 5, 4]}] + probe("carry_hold", 4, 3)
carryon += carried("carryon", "fast_run", ("carrying", []))
carryon += carried("carryon", "crawl", ("carrying", []))
# The known one: a charge crouch while carrying. Watch whether the block stays in the hands.
carryon += carried("carryon", "charge_jump", ("carrying", []))
carryon += carried("carryon", "bar_hang", ("carrying", []), orbit=[140, 0, 5])
# Put the chest down again. This is not tidiness: a carry survives `clear @s`, so leaving it held
# would register carry_on's pose on the player for every script after this one.
carryon += mark("carryon place") + tp(FLAT) + [{"look": [180, 35]}, {"wait": 8},
            {"hold": "sneak"}, {"wait": 3}, {"click": "use"}, {"wait": 10},
            {"releaseAll": True}, {"closeScreen": True}, {"wait": 15}] + probe("carry_placed", 4, 3)
carryon += [{"releaseAll": True}, {"orbit": False}, {"camera": "first"}]

# ---- Better Combat: a swing inside every carrier ------------------------------------------------
bc = setup([{"cmd": "item replace entity @s weapon.mainhand with minecraft:iron_sword"}, {"wait": 5}])
bc += mark("bc baseline/swing") + tp(FLAT) + [{"orbit": [140, 5, 4]}, {"look": [180, -40]}]
bc += [{"click": "attack"}, {"wait": 1}] + probe("bc_base_swing", 8, 1)
bc += carried("bc", "fast_run", ("swing", [{"click": "attack"}, {"wait": 1}]))
bc += carried("bc", "crawl", ("swing", [{"click": "attack"}, {"wait": 1}]))
bc += carried("bc", "charge_jump", ("swing", [{"click": "attack"}, {"wait": 1}]))
bc += carried("bc", "bar_hang", ("swing", [{"click": "attack"}, {"wait": 1}]), orbit=[140, 0, 5])
bc += carried("bc", "ledge_hang", ("swing", [{"click": "attack"}, {"wait": 1}]), orbit=[140, 0, 5])
bc += [{"releaseAll": True}, {"orbit": False}, {"camera": "first"}]

# ---- Hackers 'n Slashers: the stance layer, then a combo ----------------------------------------
hns = setup([{"cmd": "item replace entity @s weapon.mainhand with minecraft:netherite_sword"},
             {"cmd": "item replace entity @s weapon.offhand with minecraft:shield"}, {"wait": 5}])
hns += mark("hns baseline/stance") + tp(FLAT) + [{"orbit": [140, 5, 4]}] + probe("hns_stance", 4, 3)
hns += mark("hns baseline/combo") + [{"look": [180, -40]}]
for i in range(3):
    hns += [{"click": "attack"}, {"wait": 5}] + probe(f"hns_base_hit{i}", 3, 2)
hns += carried("hns", "fast_run", ("stance", []))
hns += carried("hns", "fast_run", ("attack", [{"click": "attack"}, {"wait": 1}]))
hns += carried("hns", "crawl", ("stance", []))
hns += carried("hns", "charge_jump", ("stance", []))
hns += carried("hns", "bar_hang", ("stance", []), orbit=[140, 0, 5])
# The leg gate: H&S drops the legs below limbSwingAmount 0.15, and a ParCool move moves the legs
# without swinging them. Walk slowly out of a crawl and watch the legs flip.
hns += mark("hns legs/slow_walk_after_crawl") + tp(FLAT) + [{"orbit": [90, 5, 4]}]
hns += [{"hold": "key.parcool.crawl"}, {"hold": "forward"}, {"wait": 14},
        {"release": "key.parcool.crawl"}, {"wait": 6}] + probe("hns_legs_out", 10, 2) + \
       [{"releaseAll": True}]
hns += [{"releaseAll": True}, {"orbit": False}, {"camera": "first"}]

# ---- Misc: Exposure camera, Immersive Melodies flute --------------------------------------------
misc = setup()
misc += mark("exposure baseline/raise") + tp(FLAT) + [{"orbit": [140, 5, 4]}]
misc += [{"cmd": "item replace entity @s weapon.mainhand with exposure:camera"}, {"wait": 8},
         {"state": True}, {"hold": "use"}, {"wait": 12}] + probe("exp_raise", 4, 3)
misc += carried("exposure", "fast_run", ("camera_up", [{"hold": "use"}, {"wait": 8}]))
misc += carried("exposure", "bar_hang", ("camera_up", [{"hold": "use"}, {"wait": 8}]),
                orbit=[140, 0, 5])
misc += mark("melodies baseline/flute") + tp(FLAT) + [{"orbit": [140, 5, 4]}]
misc += [{"cmd": 'item replace entity @s weapon.mainhand with '
                 'immersive_melodies:flute[playing=true,melody="immersive_melodies:axel_f"]'},
         {"wait": 20}] + probe("im_flute", 4, 3)
misc += carried("melodies", "fast_run", ("flute", []))
misc += carried("melodies", "crawl", ("flute", []))
misc += carried("melodies", "bar_hang", ("flute", []), orbit=[140, 0, 5])
misc += mark("melodies release/flute_taken_away") + [{"closeScreen": True},
        {"cmd": "clear @s"}, {"wait": 20}] + probe("im_released", 4, 3)
misc += [{"releaseAll": True}, {"orbit": False}, {"camera": "first"}]

# ---- NEA on a hang: one hand at rest, both while moving --------------------------------------
# The check for ParCoolHangHold. A filled map is the clearest item to read: two-handed when NEA has
# both arms, one-handed and off to the side when it only has one.
neahang = setup([
    {"slot": 2}, {"cmd": "item replace entity @s hotbar.2 with minecraft:map"}, {"wait": 10},
    {"click": "use"}, {"wait": 25}, {"state": True},        # empty map -> filled map, in hand
])
neahang += mark("neahang bar/still") + [{"hold": "key.parcool.hang"},
            {"cmd": "tp @s -554.5 201.1 360.5 180 0"}, {"wait": 20}, {"orbit": [35, 0, 4]}, {"wait": 6}]
neahang += probe("nh_bar_still", 5, 3)
neahang += mark("neahang bar/shuffling") + [{"hold": "key.parcool.hang"},
            {"cmd": "tp @s -554.5 201.1 360.5 180 0"}, {"wait": 16}, {"orbit": [35, 0, 4]},
            {"hold": "right"}, {"wait": 12}]
neahang += probe("nh_bar_move", 6, 2) + [{"release": "right"}, {"wait": 16}]
neahang += probe("nh_bar_back", 5, 3) + [{"releaseAll": True}, {"wait": 12}]
neahang += mark("neahang ledge/still") + [{"hold": "key.parcool.hang"},
            {"cmd": "tp @s -584.5 201.2 323.35 180 0"}, {"wait": 20}, {"orbit": [35, 0, 4]}, {"wait": 6}]
neahang += probe("nh_ledge_still", 5, 3)
neahang += mark("neahang ledge/shimmy") + [{"hold": "key.parcool.hang"},
            {"cmd": "tp @s -584.5 201.2 323.35 180 0"}, {"wait": 16}, {"orbit": [35, 0, 4]},
            {"hold": "left"}, {"wait": 12}]
neahang += probe("nh_ledge_move", 6, 2) + [{"releaseAll": True}, {"wait": 12}]
neahang += [{"releaseAll": True}, {"orbit": False}, {"camera": "first"}]

for name, steps in (("nea-hang", neahang), ("nea", nea), ("create", create), ("tacz", tacz), ("carryon", carryon),
                    ("bettercombat", bc), ("hns", hns), ("misc", misc)):
    with open(os.path.join(HERE, f"parcool-x-{name}.json"), "w") as f:
        json.dump(steps, f)
    ticks = sum(int(s.get("wait", 0)) for s in steps)
    print(f"parcool-x-{name}.json  {len(steps)} steps  ~{ticks / 20:.0f}s of waits")
