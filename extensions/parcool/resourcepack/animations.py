"""ParCool's fast run and charge jump in Fresh Animations: Player Extension's procedural style.

FA+Player builds every channel out of layers that player.jem sums per part; the movement layer is
``var.mvmnt_*``, a state weight times a smooth function of the stride phase ``var.ls``, breathing
``var.Bt`` and ``frame_time``-eased timers. This module runs right after a_player_movement.jpm and
adds to that layer, so both moves ride on FA's own run and crouch instead of replacing them:

- fast run: a harder lean that bobs with the stride, more torso twist, bigger arm drive and stride
- hang: a catch that dips on the arms and swings out, stronger the faster the fall; feet braced on
  the wall or legs hanging loose; an uneven grip; hand over hand along the ledge
- charge jump: a crouch that deepens with the charge - torso forward, hips back, legs tilted off
  the feet, legs wide, arms swung back, the cape lifted off the hips - breathing, and trembling
  once fully charged; out of it, a leap
  with the arms thrown up

Reads the EMF variables EMF Compat: ParCool registers (ParCoolPackVariables). Reading them is also
how the addon learns that this pack animates these moves and stops replaying ParCool's poses; the
moves the pack does not read (vaults, hanging...) keep ParCool's.

Sign conventions, measured in game: body rx + leans forward, ty + moves down, tz + moves back;
arm rx + swings back, - forward; leg rx - forward; rz + is outward for the right side.
"""

STATE = {
    "var.pc_frun": "clamp( if( varb.fcc, var.pc_frun, parcool_fast_run>0 && is_on_ground, var.pc_frun +5*frame_time, var.pc_frun -5*frame_time ), 0, 1 )",
    "var.pc_f": "var.pc_frun*sqrt(limb_speed)*(1-var.in_air/1.5)*(1-var.prone)",
    "var.pc_chg": "if( varb.fcc, var.pc_chg, parcool_charge*min(1,frame_time*12) +var.pc_chg*max(0,1-frame_time*12) )",
    "var.pc_c": "sin( clamp(var.pc_chg,0,1)*pi/2 )*(1-var.prone)",
    "var.pc_shake": "pow( clamp(var.pc_chg*1.25-0.25, 0, 1), 3 )*sin( age*2.3 )",
    # hang: weight, time since it started, feet on the wall, and the shuffle along the ledge (-1..1)
    # with its phase
    "var.pc_hw": "clamp( if( varb.fcc, var.pc_hw, parcool_hang>0, var.pc_hw +25*frame_time, var.pc_hw -8*frame_time ), 0, 1 )",
    "var.pc_ht": "if( parcool_hang>0, var.pc_ht +if(varb.fcc, 0, frame_time), 0 )",
    # the catch: the mod's body offset (pixels, down positive) - the body arrives a little high, drops
    # under the hands and springs back; the IK arms already allow for it. The legs trail it a little.
    "var.pc_cy": "parcool_hang_catch",
    "var.pc_cyl": "if( varb.fcc, var.pc_cyl, var.pc_cy*min(1,frame_time*18) +var.pc_cyl*max(0,1-frame_time*18) )",
    "var.pc_hwall": "if( varb.fcc, var.pc_hwall, parcool_hang_wall*min(1,frame_time*6) +var.pc_hwall*max(0,1-frame_time*6) )",
    "var.pc_hfree": "1-var.pc_hwall",
    "var.pc_shim": "if( varb.fcc, var.pc_shim, if(parcool_hang>0, -move_strafing, 0)*min(1,frame_time*6) +var.pc_shim*max(0,1-frame_time*6) )",
    "var.pc_shimA": "abs(var.pc_shim)",
    "var.pc_sph": "if( varb.fcc, var.pc_sph, var.pc_sph +var.pc_shimA*frame_time*1.4 )",
    "var.pc_sw": "sin( var.pc_sph*pi*2 )",
    "var.pc_reach": "0.5+0.5*var.pc_sw",
    "var.pc_leap": "clamp( if( varb.fcc, var.pc_leap, parcool_charge_jump>0 && !is_on_ground, var.pc_leap +8*frame_time, var.pc_leap -4*frame_time ), 0, 1 )",
}

# Hang pose at full weight, per channel. FA's own layers are faded out under it (see build), so
# these are the whole pose of the parts, not an adjustment. Kept to modest angles.
HANG = {
    # an uneven grip: right hand lower and wider, the torso turned towards it; leaning out from a
    # wall the feet are braced on; the catch drops the body under the hands, hips swinging in and the
    # head nodding, then breathing. Shuffling, the torso
    # shifts its weight under whichever hand has just caught.
    "bodyrx": "torad( 6*var.pc_hwall +2*var.pc_hfree -1.5*var.pc_cy )",
    "bodyry": "torad( 8 )",
    "bodyrz": "torad( 4 +7*(var.pc_reach-0.5)*var.pc_shim )",
    "bodytx": "1.2*(var.pc_reach-0.5)*var.pc_shim",
    "bodyty": "var.pc_cy +0.15*sin(var.Bt) +0.4*sin(var.pc_reach*pi)*var.pc_shimA",
    "headrx": "torad( -16 +2.5*var.pc_cyl )",
    "headry": "torad( -10*(var.pc_reach-0.5)*var.pc_shim )",
    # Hands on top of the ledge. The mod aims each arm at it and blends it with hanging loose when
    # ParCool lets that hand go (looking away along the wall), as directions, so an arm never swings
    # out sideways on the way or turns round when the ledge passes behind the back; the shoulder is
    # raised where the arm is short. The arms follow the torso's position in FA, so they move with
    # the catch, and the mod aims them from there. On top: the shimmy, where the leading hand slides
    # out along the ledge, and a little sway for a loose hand.
    "rarmrx": "parcool_rarm_hang_rx +torad( 4*sin(age/17)*(1-parcool_rarm_grip) )",
    "larmrx": "parcool_larm_hang_rx +torad( 4*sin(age/17 +1.3)*(1-parcool_larm_grip) )",
    "rarmry": ("parcool_rarm_hang_ry"
               " +torad( 30*var.pc_reach*max(0,-var.pc_shim) -10*(1-var.pc_reach)*max(0, var.pc_shim) )*parcool_rarm_grip"),
    "larmry": ("parcool_larm_hang_ry"
               " +torad( -30*var.pc_reach*max(0, var.pc_shim) +10*(1-var.pc_reach)*max(0,-var.pc_shim) )*parcool_larm_grip"),
    "rarmrz": "torad(  2*sin(age/13) )*(1-parcool_rarm_grip)",
    "larmrz": "torad( -2*sin(age/13 +0.7) )*(1-parcool_larm_grip)",
    "rarmty": "-parcool_rarm_hang_lift",
    "larmty": "-parcool_larm_hang_lift",
    # braced: knees to the wall, one higher; loose: a slow sway. The catch swings them in, trailing.
    "rlegrx": ("torad( (-26 -7*max(0, var.pc_sw)*var.pc_shimA)*var.pc_hwall"
               " +3*sin(age/13)*var.pc_hfree -(2 +3*var.pc_hfree)*var.pc_cyl )"),
    "llegrx": ("torad( (-16 -7*max(0,-var.pc_sw)*var.pc_shimA)*var.pc_hwall"
               " -3*sin(age/13)*var.pc_hfree -(1.5 +2.5*var.pc_hfree)*var.pc_cyl )"),
    # legs are not parented to the torso in FA, so they take the catch's offset themselves
    "rlegty": "var.pc_cy",
    "llegty": "var.pc_cy",
    "rlegrz": "torad(  5 +6*(var.pc_reach-0.5)*var.pc_shim*var.pc_hfree )",
    "llegrz": "torad( -5 +6*(var.pc_reach-0.5)*var.pc_shim*var.pc_hfree )",
}

# The FA layers faded out under the hang; FA's walk, jump and idle swings would fight the pose.
FADED_LAYERS = ("idl", "mvmnt", "vrtcl")

# Added to FA's movement layer, per channel (the part after ``var.mvmnt_``).
LAYERS = {
    "bodyrx": ["torad( 16 +4*cos(var.ls*2) )*var.pc_f", "torad( 35 +2*sin(var.Bt) )*var.pc_c", "torad(-8)*var.pc_leap"],
    "bodyry": ["torad( 7*cos(var.ls) )*var.pc_f"],
    "bodytx": ["0.15*var.pc_shake*var.pc_c"],
    "bodyty": ["( 0.5 -0.9*cos(pi/4 +var.ls*2) )*var.pc_f", "( 1.6 +0.2*sin(var.Bt) )*var.pc_c"],
    "bodytz": ["6*var.pc_c"],
    "headrx": ["torad(4)*var.pc_f", "torad(-10)*var.pc_c"],
    "headry": ["-torad( 5*cos(var.ls) )*var.pc_f"],
    "rarmrx": ["torad(  35*cos(pi/7 +var.ls) -8 )*var.pc_f*(1-var.requip/2)", "torad( 45 +3*sin(var.Bt) )*var.pc_c", "torad(-60)*var.pc_leap"],
    "larmrx": ["torad( -35*cos(pi/7 +var.ls) -8 )*var.pc_f*(1-var.lequip/2)", "torad( 45 +3*sin(var.Bt) )*var.pc_c", "torad(-60)*var.pc_leap"],
    "rarmrz": ["torad(7)*var.pc_f", "torad(12)*var.pc_c", "torad(25)*var.pc_leap"],
    "larmrz": ["-torad(7)*var.pc_f", "-torad(12)*var.pc_c", "-torad(25)*var.pc_leap"],
    "rfootrx": ["torad(-30)*var.pc_c"],
    "lfootrx": ["torad(-30)*var.pc_c"],
    # feet planted wide for the leap
    "rlegrz": ["torad(16)*var.pc_c"],
    "llegrz": ["-torad(16)*var.pc_c"],
    "rlegrx": ["torad( -22*cos(var.ls +cos(var.ls)/4) -6 )*var.pc_f", "torad(20)*var.pc_leap"],
    "llegrx": ["torad(  22*cos(var.ls -cos(var.ls)/4) -6 )*var.pc_f", "torad(10)*var.pc_leap"],
}


# Moved from FA's idle layer into the movement layer: the torso ends up exactly where it was, but
# FA's cape reads the idle lean on its own - player_cape.jem swings the lower flap by
# ``-var.idl_bodyrx`` - so the flap lifts away from the hips the crouch pushes back into it.
CAPE_LIFT = "torad(30)*var.pc_c"


def build(fa_layer_vars: set[str]) -> dict:
    """The .jpm, given the layer variables the installed FA+Player defines (``var.mvmnt_bodyrx``...)."""
    missing = [c for c in [*LAYERS, *HANG] if f"var.mvmnt_{c}" not in fa_layer_vars]
    if missing:
        raise SystemExit(f"FA+Player has no movement layer for {missing}; player.jem would not sum them")
    additions: dict[str, list[str]] = {}
    # The run, charge and leap give way to the hang like FA's own layers do: ParCool keeps playing
    # the charge jump for a while after a ledge is caught, and its arms would add onto the hang's.
    for channel, terms in LAYERS.items():
        additions.setdefault(channel, []).extend(f"({t})*(1-var.pc_hw)" for t in terms)
    for channel, pose in HANG.items():
        additions.setdefault(channel, []).append(f"({pose})*var.pc_hw")
    additions.setdefault("bodyrx", []).append(CAPE_LIFT)

    layers = {}
    for var in sorted(fa_layer_vars):
        prefix, _, channel = var[len("var."):].partition("_")
        if prefix not in FADED_LAYERS:
            continue
        expr = f"{var}*(1-var.pc_hw)"
        if prefix == "mvmnt" and channel in additions:
            expr += " +" + " +".join(f"({t})" for t in additions[channel])
        if var == "var.idl_bodyrx":
            expr += f" -({CAPE_LIFT})"
        layers[var] = expr
    return {
        "credit": "EMF Compat: ParCool animations, built on Fresh Animations: Player Extension by FreshLX",
        "id": "parcool",
        "animations": [STATE, layers],
    }
