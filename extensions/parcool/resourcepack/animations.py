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
    # The charge: ParCool counts it down a tick at a time once let go, which played back as a slow,
    # linear unbend. A charge that is going down is let go at once instead, and eased in and out the
    # way FA eases its crouch (var.Asneak, var.sneak).
    "var.pc_ctg": "if( parcool_charge >0 && parcool_charge >=var.pc_cprev -0.001, parcool_charge, 0 )",
    "var.pc_cprev": "if( varb.fcc, var.pc_cprev, parcool_charge )",
    "var.pc_chg": "if( varb.fcc, var.pc_chg, var.pc_ctg*min(1,frame_time*12) +var.pc_chg*max(0,1-frame_time*12) )",
    "var.pc_c": "(0.5 -0.5*cos( clamp(var.pc_chg,0,1)*pi ))*(0.5 -0.5*cos( sqrt(clamp(var.pc_chg,0,1))*pi ))*(1-var.prone)",
    "var.pc_shake": "pow( clamp(var.pc_chg*1.25-0.25, 0, 1), 3 )*sin( age*2.3 )",
    # hang: weight, time since it started, feet on the wall, and the shuffle along the ledge (-1..1)
    # with its phase
    "var.pc_hw": "clamp( if( varb.fcc, var.pc_hw, parcool_hang>0, var.pc_hw +if(var.pc_plt<0.8, 2.5, 25)*frame_time, var.pc_hw -8*frame_time ), 0, 1 )",
    # time since a pole climb: climbing off the top of a chain into a hang, the hang comes in slowly
    # from FA's ladder climb instead of taking the body in a tick
    "var.pc_plt": "if( parcool_pole_climb>0, 0, var.pc_plt +if(varb.fcc, 0, frame_time) )",
    "var.pc_ht": "if( parcool_hang>0, var.pc_ht +if(varb.fcc, 0, frame_time), 0 )",
    # the catch: the mod's body offset (pixels, down positive) - the body arrives a little high, drops
    # under the hands and springs back; the IK arms already allow for it. The legs trail it a little.
    "var.pc_cy": "parcool_hang_catch",
    "var.pc_cyl": "if( varb.fcc, var.pc_cyl, var.pc_cy*min(1,frame_time*18) +var.pc_cyl*max(0,1-frame_time*18) )",
    # the head exactly where the player looks: no easing, which read as lagging or leading the camera
    "var.pc_hhy": "torad( parcool_head_yaw )",
    "var.pc_hhx": "torad( parcool_head_pitch )",
    "var.pc_hwall": "if( varb.fcc, var.pc_hwall, parcool_hang_wall*min(1,frame_time*6) +var.pc_hwall*max(0,1-frame_time*6) )",
    "var.pc_hfree": "1-var.pc_hwall",
    # how much each leg follows the foot IK: on a wall to stand on, eased in as it is found
    "var.pc_rfk": "if( varb.fcc, var.pc_rfk, parcool_rleg_ik*min(1,frame_time*8) +var.pc_rfk*max(0,1-frame_time*8) )",
    "var.pc_lfk": "if( varb.fcc, var.pc_lfk, parcool_lleg_ik*min(1,frame_time*8) +var.pc_lfk*max(0,1-frame_time*8) )",
    "var.pc_rfw": "var.pc_rfk*var.pc_hwall",
    "var.pc_lfw": "var.pc_lfk*var.pc_hwall",
    # climb up: weight, progress, and what the pose owns overall (hang and climb together)
    "var.pc_had": "if( parcool_climb>0, 1, var.pc_lt>1.5, 0, var.pc_had )",
    "var.pc_lt": "if( parcool_climb>0, 0, var.pc_lt +if(varb.fcc, 0, frame_time) )",
    "var.pc_cw": "clamp( if( varb.fcc, var.pc_cw, parcool_climb>0 || (var.pc_had>0.5 && var.pc_lt<0.3), var.pc_cw +25*frame_time, var.pc_cw -2.5*frame_time ), 0, 1 )",
    "var.pc_cp": "if( parcool_climb>0, parcool_climb, var.pc_cp )",
    # hanging under a bar: weight, and the legs trailing the swing - they chase the body's swing
    # speed a beat late, so they lag behind it on the way out and swing through on the way back
    "var.pc_bw": "clamp( if( varb.fcc, var.pc_bw, parcool_bar>0, var.pc_bw +12*frame_time, var.pc_bw -8*frame_time ), 0, 1 )",
    "var.pc_btr": "if( varb.fcc, var.pc_btr, clamp(parcool_bar_swing_speed*260, -38, 38)*min(1,frame_time*5) +var.pc_btr*max(0,1-frame_time*5) )",
    # Along the bar, as monkeys go: a hand is thrown far ahead with the body turning after it and the
    # opposite leg flung back. The body follows the hands a beat late and the legs later still, so it
    # rolls through the move instead of moving in step with them.
    "var.pc_bsw": "if( varb.fcc, var.pc_bsw, var.pc_sw*min(1,frame_time*4) +var.pc_bsw*max(0,1-frame_time*4) )",
    "var.pc_blr": "if( varb.fcc, var.pc_blr, var.pc_rr*min(1,frame_time*3.5) +var.pc_blr*max(0,1-frame_time*3.5) )",
    "var.pc_bll": "if( varb.fcc, var.pc_bll, var.pc_lr*min(1,frame_time*3.5) +var.pc_bll*max(0,1-frame_time*3.5) )",
    "var.pc_balong": "1 -parcool_bar_across",
    "var.pc_own": "1 -(1-var.pc_hw)*(1-var.pc_cw)*(1-var.pc_bw)",
    # Inertia: the lean, the knees and the head chase their targets instead of taking them, the left
    # knee a little behind the right; then a short settle once the feet are up on the ledge.
    # The climb in two beats: pull up and throw the torso over the edge with the knees tucked under it,
    # as if about to kneel on the ledge; then stand up out of the tuck.
    "var.pc_ctk": "pow( clamp((var.pc_cp-0.2)/0.35,0,1), 2 )*(3-2*clamp((var.pc_cp-0.2)/0.35,0,1)) *(1 -pow( clamp((var.pc_cp-0.78)/0.22,0,1), 2 )*(3-2*clamp((var.pc_cp-0.78)/0.22,0,1)))",
    "var.pc_clean": "if( varb.fcc, var.pc_clean, (26*var.pc_ctk +8*sin( pi*clamp(var.pc_cp*1.1,0,1) ))*min(1,frame_time*10) +var.pc_clean*max(0,1-frame_time*10) )",
    "var.pc_ckr": "if( varb.fcc, var.pc_ckr, var.pc_ctk*min(1,frame_time*16) +var.pc_ckr*max(0,1-frame_time*16) )",
    "var.pc_ckl": "if( varb.fcc, var.pc_ckl, var.pc_ckr*min(1,frame_time*9) +var.pc_ckl*max(0,1-frame_time*9) )",
    "var.pc_chd": "if( varb.fcc, var.pc_chd, (-16*(1-clamp(var.pc_cp/0.4,0,1)) -10*var.pc_ctk +9*sin( pi*clamp((var.pc_cp-0.55)/0.4,0,1) ))*min(1,frame_time*8) +var.pc_chd*max(0,1-frame_time*8) )",
    "var.pc_land": "exp( -var.pc_lt*7 )*sin( var.pc_lt*15 )*var.pc_had",
    # Shuffling: the mod moves the hands hand over hand and says which one is reaching (0..1 each).
    # The body answers it a beat late: it hangs off the hand that holds and lifts as the other reaches.
    "var.pc_rr": "if( varb.fcc, var.pc_rr, (parcool_rarm_reach +parcool_rarm_bar_reach)*min(1,frame_time*11) +var.pc_rr*max(0,1-frame_time*11) )",
    "var.pc_lr": "if( varb.fcc, var.pc_lr, (parcool_larm_reach +parcool_larm_bar_reach)*min(1,frame_time*11) +var.pc_lr*max(0,1-frame_time*11) )",
    "var.pc_sw": "var.pc_rr -var.pc_lr",
    "var.pc_shimA": "clamp( var.pc_rr +var.pc_lr, 0, 1 )",
    # The wave: hanging from the hand that holds, the body swings its feet forward as the other hand
    # is thrown ahead and swings back through once it takes hold. A damped spring (stiffness 30,
    # damping 7) chases that swing, so it overshoots and settles instead of following the reach
    # exactly; the velocity is stepped before the position, both once a frame (varb.fcc).
    "var.pc_bwt": "-22*max(var.pc_rr, var.pc_lr)*var.pc_balong",
    "var.pc_bwv": "if( varb.fcc, var.pc_bwv, var.pc_bwv +(30*(var.pc_bwt -var.pc_bwp) -7*var.pc_bwv)*min(frame_time, 0.05) )",
    "var.pc_bwp": "if( varb.fcc, var.pc_bwp, var.pc_bwp +var.pc_bwv*min(frame_time, 0.05) )",
    # How much the legs are kept on the hips (see build_pack.leg_attach): fully as soon as the hang or
    # the climb starts to show, whatever its weight - a hang easing in slowly (off the top of a chain)
    # otherwise left the legs half on FA's place and half on the hips; eased out after
    "var.pc_att": "clamp( if( varb.fcc, var.pc_att, var.pc_own >0.001, var.pc_att +12*frame_time, var.pc_att -1.2*frame_time ), 0, 1 )",
    # FA's own crawl and swim are kept for ParCool's: reading these is what hands them over
    "var.pc_crawl": "parcool_crawl +parcool_fast_swim",
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
    "bodyrz": "torad( 4 -9*var.pc_sw )",
    "bodytx": "1.8*var.pc_sw",
    "bodyty": "var.pc_cy +0.15*sin(var.Bt) -0.9*var.pc_shimA",
    # The head looks where the player looks, turned back from the torso ParCool faces to the wall (the
    # mod works that out: parcool_head_yaw), as far as a neck goes; it nods with the catch.
    "headrx": "var.pc_hhx +torad( 2.5*var.pc_cyl )",
    "headry": "var.pc_hhy",
    # Hands on top of the ledge. The mod aims each arm at it and blends it with hanging loose when
    # ParCool lets that hand go (looking away along the wall), as directions, so an arm never swings
    # out sideways on the way or turns round when the ledge passes behind the back; the shoulder is
    # raised where the arm is short. The arms follow the torso's position in FA, so they move with
    # the catch, and the mod aims them from there. On top: the shimmy, where the leading hand slides
    # out along the ledge, and a little sway for a loose hand.
    "rarmrx": "parcool_rarm_hang_rx +torad( 4*sin(age/17)*(1-parcool_rarm_grip) )",
    "larmrx": "parcool_larm_hang_rx +torad( 4*sin(age/17 +1.3)*(1-parcool_larm_grip) )",
    "rarmry": "parcool_rarm_hang_ry",
    "larmry": "parcool_larm_hang_ry",
    "rarmrz": "torad(  2*sin(age/13) )*(1-parcool_rarm_grip)",
    "larmrz": "torad( -2*sin(age/13 +0.7) )*(1-parcool_larm_grip)",
    "rarmty": "-parcool_rarm_hang_lift",
    "larmty": "-parcool_larm_hang_lift",
    # braced: knees to the wall, one higher; loose: a slow sway. The catch swings them in, trailing.
    # Feet braced on the wall: the mod sets each foot on the face below the hip and steps it along
    # like the hands (parcool_*leg_ik_*); without wall to stand on, or before it is found, the fixed
    # brace and the loose sway below.
    "rlegrx": ("var.pc_rfw*parcool_rleg_ik_rx +(1-var.pc_rfw)*torad( (-26 -8*var.pc_rr)*var.pc_hwall  +3*sin(age/13)*var.pc_hfree -(2 +3*var.pc_hfree)*var.pc_cyl )"),
    "llegrx": ("var.pc_lfw*parcool_lleg_ik_rx +(1-var.pc_lfw)*torad( (-16 -8*var.pc_lr)*var.pc_hwall  -3*sin(age/13)*var.pc_hfree -(1.5 +2.5*var.pc_hfree)*var.pc_cyl )"),
    # The legs are put back on the hips after FA sets them (build_pack.leg_attach), so they follow the
    # torso's swing and catch. Braced legs lean out to their feet, which spreads them as the body
    # shuffles; loose ones swing with the body.
    "rlegrz": "var.pc_rfw*parcool_rleg_ik_rz +torad(  5 -9*var.pc_sw )*(1-var.pc_rfw)",
    "llegrz": "var.pc_lfw*parcool_lleg_ik_rz +torad( -5 -9*var.pc_sw )*(1-var.pc_lfw)",
}


# Hanging under a bar at full weight. ParCool swings and turns the body itself; over that the hands
# hold the bar (the mod aims the arms at it and moves them hand over hand along it), the legs hang
# together and trail the swing, and shuffling along the bar sways the body like on a ledge.
BAR = {
    "rarmrx": "parcool_rarm_bar_rx",
    "larmrx": "parcool_larm_bar_rx",
    "rarmry": "parcool_rarm_bar_ry",
    "larmry": "parcool_larm_bar_ry",
    # raised so the hands reach the bar; FA hangs the head and arms off the torso, the legs follow
    # through leg_attach. Raised that far the head would be inside a bar that runs across it, so the
    # body hangs a little behind one (along the bar, "behind" is just further along it: no shift).
    "bodyty": "-parcool_bar_raise",
    "bodytz": "parcool_bar_raise*parcool_bar_across",
    "rarmrz": "parcool_rarm_bar_rz",
    "larmrz": "parcool_larm_bar_rz",
    "rarmty": "-parcool_rarm_bar_lift",
    "larmty": "-parcool_larm_bar_lift",
    # Each reach hangs the body off the holding hand; along the bar it also turns the reaching
    # shoulder forward after the hand (a left reach turns the body left, bodyry +), the head holding
    # its gaze, and flings the opposite leg back while the other swings a little forward.
    "bodyrz": "torad( -13*var.pc_bsw )",
    "bodyry": "torad( -24*var.pc_bsw*var.pc_balong )",
    "headry": "torad( 14*var.pc_bsw*var.pc_balong )",
    "bodyrx": "torad( 0.2*var.pc_btr -5*var.pc_shimA*(1-var.pc_balong) +var.pc_bwp )",
    "headrx": "torad( -8 )",
    "rlegrx": "torad( var.pc_btr +2 +3*sin(age/13) +0.7*var.pc_bwp +(38*var.pc_bll -12*var.pc_blr)*var.pc_balong -20*var.pc_bll*(1-var.pc_balong) )",
    "llegrx": "torad( 0.85*var.pc_btr -2 -3*sin(age/13 +0.8) +0.7*var.pc_bwp +(38*var.pc_blr -12*var.pc_bll)*var.pc_balong -20*var.pc_blr*(1-var.pc_balong) )",
    "rlegrz": "torad(  3 -5*var.pc_sw )",
    "llegrz": "torad( -3 -5*var.pc_sw )",
}


# Climb-up pose at full weight. ParCool still leans and lifts the whole model on the pose stack; this
# is the parts over it: a little more lean in the torso, the hands pushing on the ledge until they let
# go and swing back (the mod's IK carries on through the climb), the knees driven up one after the
# other, and the head looking at the ledge then nodding over it - all a beat behind, not snapped.
CLIMB = {
    "bodyrx": "torad( 4 +var.pc_clean )",
    "headrx": "torad( var.pc_chd )",
    "rarmrx": ("parcool_rarm_hang_rx"
               " +torad( 26*sin( pi*clamp((var.pc_cp-0.5)/0.45,0,1) )*(1-parcool_rarm_grip) )"),
    "larmrx": ("parcool_larm_hang_rx"
               " +torad( 22*sin( pi*clamp((var.pc_cp-0.55)/0.42,0,1) )*(1-parcool_larm_grip) )"),
    # a loose arm points almost straight down, where its y rotation means little: let it go with the grip
    "rarmry": "parcool_rarm_hang_ry*parcool_rarm_grip",
    "larmry": "parcool_larm_hang_ry*parcool_larm_grip",
    "rarmty": "-parcool_rarm_hang_lift",
    "larmty": "-parcool_larm_hang_lift",
    # the arms lead: the mod draws the body up to the ledge a little first, then trails ParCool's lift
    # (pixels, down positive), and aims the arms from there; the legs hang off the torso with it
    "bodyty": "parcool_hang_catch",
    "rlegty": "parcool_hang_catch",
    "llegty": "parcool_hang_catch",
    # both legs tuck up under the torso, the left a beat behind after a push off the wall; then they
    # straighten as the body stands, with a small flick back as the weight comes onto the feet
    "rlegrx": "torad( -24*(1-clamp(var.pc_cp/0.15,0,1)) -86*var.pc_ckr +10*sin( pi*clamp((var.pc_cp-0.8)/0.2,0,1) ) )",
    "llegrx": ("torad( -15*(1-clamp(var.pc_cp/0.15,0,1)) +18*sin( pi*clamp((var.pc_cp-0.02)/0.25,0,1) )"
               " -80*var.pc_ckl +8*sin( pi*clamp((var.pc_cp-0.84)/0.16,0,1) ) )"),
    # knees apart in the tuck
    "rlegrz": "torad(  11*var.pc_ckr )",
    "llegrz": "torad( -9*var.pc_ckl )",
}

# After a climb, whatever the pose: the body settles onto the feet.
SETTLE = {
    "bodyty": "0.9*var.pc_land",
    "headrx": "torad( 3*var.pc_land )",
    "rlegty": "0.9*var.pc_land",
    "llegty": "0.9*var.pc_land",
}

# The FA layers faded out under the hang and the climb; FA's walk, jump and idle swings would fight
# the pose, and it takes the top of a climb for a moment of flight.
FADED_LAYERS = ("idl", "mvmnt", "vrtcl", "fly")

# Added to FA's movement layer, per channel (the part after ``var.mvmnt_``).
LAYERS = {
    "bodyrx": ["torad( 16 +4*cos(var.ls*2) )*var.pc_f", "torad( 10 +2*sin(var.Bt) )*var.pc_c", "torad(-8)*var.pc_leap"],
    # FA twists the torso with the arms already; more turns the hips away from the legs, which are
    # not parented to the torso
    "bodyry": ["0"],
    "bodytx": ["0.15*var.pc_shake*var.pc_c"],
    # the charge is FA's crouch taken deeper: the torso sinks and sits back behind the legs, which
    # stay forward, with only a little more lean
    "bodyty": ["( 0.5 -0.9*cos(pi/4 +var.ls*2) )*var.pc_f", "( 2 +0.2*sin(var.Bt) )*var.pc_c"],
    "bodytz": ["3*var.pc_c"],
    "headrx": ["torad(4)*var.pc_f", "torad(-6)*var.pc_c"],
    "headry": ["-torad( 5*cos(var.ls) )*var.pc_f"],
    # a little more drive on FA's arm swing, on the legs' beat: FA's swing already leads them
    "rarmrx": ["torad(  12*cos(var.ls) +6 )*var.pc_f*(1-var.requip/2)", "torad( 25 +3*sin(var.Bt) )*var.pc_c", "torad(-60)*var.pc_leap"],
    "larmrx": ["torad( -12*cos(var.ls) +6 )*var.pc_f*(1-var.lequip/2)", "torad( 25 +3*sin(var.Bt) )*var.pc_c", "torad(-60)*var.pc_leap"],
    "rarmrz": ["torad(7)*var.pc_f", "torad(12)*var.pc_c", "torad(25)*var.pc_leap"],
    "larmrz": ["-torad(7)*var.pc_f", "-torad(12)*var.pc_c", "-torad(25)*var.pc_leap"],
    "rfootrx": ["torad(6)*var.pc_c"],
    "lfootrx": ["torad(6)*var.pc_c"],
    # feet planted wide for the leap
    "rlegrz": ["torad(16)*var.pc_c"],
    "llegrz": ["-torad(16)*var.pc_c"],
    # a longer stride on FA's own, in its phase, and pulled forward: FA's sprint keeps the legs behind
    # the body, and under the extra lean the back leg would kick up level
    "rlegrx": ["torad( -12*cos(var.ls +cos(var.ls)/4) -16 )*var.pc_f", "torad(20)*var.pc_leap"],
    "llegrx": ["torad(  12*cos(var.ls -cos(var.ls)/4) -16 )*var.pc_f", "torad(10)*var.pc_leap"],
    # The legs are not parented to the torso either: they bob with it - FA's run bob and the extra
    # one above - so the hips do not open and close over the tops of the legs.
    "rlegty": ["( 0.5 -0.9*cos(pi/4 +var.ls*2) -cos(pi/4 +var.ls*2 -cos(var.ls*2)/4) )*var.pc_f"],
    "llegty": ["( 0.5 -0.9*cos(pi/4 +var.ls*2) -cos(pi/4 +var.ls*2 -cos(var.ls*2)/4) )*var.pc_f"],
}


# Moved from FA's idle layer into the movement layer: the torso ends up exactly where it was, but
# FA's cape reads the idle lean on its own - player_cape.jem swings the lower flap by
# ``-var.idl_bodyrx`` - so the flap lifts away from the hips the crouch pushes back into it.
CAPE_LIFT = "torad(30)*var.pc_c"


def build(fa_layer_vars: set[str]) -> dict:
    """The .jpm, given the layer variables the installed FA+Player defines (``var.mvmnt_bodyrx``...)."""
    missing = [c for c in [*LAYERS, *HANG, *BAR, *CLIMB, *SETTLE] if f"var.mvmnt_{c}" not in fa_layer_vars]
    if missing:
        raise SystemExit(f"FA+Player has no movement layer for {missing}; player.jem would not sum them")
    additions: dict[str, list[str]] = {}
    # The run, charge and leap give way to the hang like FA's own layers do: ParCool keeps playing
    # the charge jump for a while after a ledge is caught, and its arms would add onto the hang's.
    for channel, terms in LAYERS.items():
        additions.setdefault(channel, []).extend(f"({t})*(1-var.pc_own)" for t in terms)
    # The hang hands over to the climb: its share goes as the climb's comes in.
    for channel, pose in HANG.items():
        additions.setdefault(channel, []).append(f"({pose})*var.pc_hw*(1-var.pc_cw)")
    for channel, pose in BAR.items():
        additions.setdefault(channel, []).append(f"({pose})*var.pc_bw")
    for channel, pose in CLIMB.items():
        additions.setdefault(channel, []).append(f"({pose})*var.pc_cw")
    for channel, pose in SETTLE.items():
        additions.setdefault(channel, []).append(pose)
    additions.setdefault("bodyrx", []).append(CAPE_LIFT)

    layers = {}
    for var in sorted(fa_layer_vars):
        prefix, _, channel = var[len("var."):].partition("_")
        if prefix not in FADED_LAYERS:
            continue
        expr = f"{var}*(1-var.pc_own)"
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
