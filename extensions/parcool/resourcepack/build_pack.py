#!/usr/bin/env python3
"""Builds the ParCool animation pack on top of an installed Fresh Animations: Player Extension.

The pack animates ParCool moves in FA+Player's procedural style (a_player_parcool.jpm, generated
by animations.py against the installed FA+Player's layer variables). It
has to be listed in player.jem to run, and player.jem is FreshLX's, whose terms forbid sharing
their assets unedited - so this repository ships only our module and patches the player models
from the FA+Player the user already has:

    python3 extensions/parcool/resourcepack/build_pack.py <FA+Player zip or folder> <resourcepacks dir>

The cape is patched too, to follow the torso while ParCool poses it (see patch_cape).

The result is a folder pack, "EMF Compat ParCool Animations", to be placed above FA+Player.
"""
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
PACK_NAME = "EMF Compat ParCool Animations"
CEM = "assets/minecraft/emf/cem"
MODULE = "a_player_parcool.jpm"
AFTER = "a_player_movement.jpm"


def read_fa(source: Path, name: str) -> str:
    if source.is_dir():
        return (source / CEM / name).read_text(encoding="utf-8")
    with zipfile.ZipFile(source) as z:
        return z.read(f"{CEM}/{name}").decode("utf-8")


def fa_layer_vars(source: Path) -> set[str]:
    names = set()
    for jpm in ("a_player_idle.jpm", "a_player_movement.jpm"):
        for block in json.loads(read_fa(source, jpm))["animations"]:
            names.update(k for k in block if re.match(r"var\.(idl|mvmnt|vrtcl|fly)_", k))
    return names


def patch(jem_text: str) -> str:
    jem = json.loads(jem_text)
    models = jem["models"]
    if any(m.get("model") == MODULE for m in models):
        return json.dumps(jem, indent=1)
    index = next(i for i, m in enumerate(models) if m.get("model") == AFTER)
    models.insert(index + 1, {"part": "root", "id": "root", "invertAxis": "xy",
                              "translate": [0, 0, 0], "model": MODULE})
    jem["credit"] = jem.get("credit", "") + " | ParCool module: EMF Compat"
    return json.dumps(jem, indent=1)


CAPE = "player_cape.jem"


def patch_cape(jem_text: str) -> str:
    """FA hangs the cape off its own torso variables, not the torso part, so while ParCool holds the
    torso in its pose the cape stays where FA's torso would be and tears off the back. The torso's
    share in those variables is eased out by how much ParCool holds it (``parcool_body_held``)."""
    jem = json.loads(jem_text)
    held = "(1-parcool_body_held)"
    count = 0
    for model in jem["models"]:
        for block in model.get("animations", []):
            for key, expr in block.items():
                if not re.match(r"cloak2?\.", key):
                    continue
                new = re.sub(r"var\.(body_(?:rx|ry|rz|tx|ty|tz)|idl_bodyrx)\b",
                             lambda m: f"(var.{m.group(1)}*{held})", expr)
                if new != expr:
                    block[key] = new
                    count += 1
    if count == 0:
        raise SystemExit("FA+Player's cape does not read the torso variables it used to; the cape patch is out of date")
    return json.dumps(jem, indent=1)


def main(argv: list[str]) -> None:
    if len(argv) != 2:
        raise SystemExit(__doc__)
    source, target_dir = Path(argv[0]), Path(argv[1])
    out = target_dir / PACK_NAME
    if out.exists():
        shutil.rmtree(out)
    (out / CEM).mkdir(parents=True)
    for jem in ("player.jem", "player_slim.jem"):
        (out / CEM / jem).write_text(patch(read_fa(source, jem)), encoding="utf-8")
    (out / CEM / CAPE).write_text(patch_cape(read_fa(source, CAPE)), encoding="utf-8")
    sys.path.insert(0, str(HERE))
    import animations
    (out / CEM / MODULE).write_text(json.dumps(animations.build(fa_layer_vars(source)), indent=2),
                                    encoding="utf-8")
    (out / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 34, "supported_formats": {"min_inclusive": 15, "max_inclusive": 999},
        "description": "ParCool moves for FA+Player - needs EMF Compat: ParCool. Place above FA+Player."}},
        indent=2), encoding="utf-8")
    (out / "credits.txt").write_text(
        "Player models edited from Fresh Animations: Player Extension by FreshLX\n"
        "https://modrinth.com/resourcepack/fa-player-extension\n"
        "ParCool animation module: EMF Compat (STRadaT)\n", encoding="utf-8")
    print(out)


if __name__ == "__main__":
    main(sys.argv[1:])
