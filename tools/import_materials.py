#!/usr/bin/env python3
"""Import a reviewed material slice from the official X5687 source checkout."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

UPSTREAM_COMMIT = "23ef33447280d83d44c1f7bcd3853fe59c0ec1fc"
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
METALS = {"steel": ("Steel", "Сталь"), "titanium": ("Titanium", "Титан"), "lead": ("Lead", "Свинец")}


def write(relative, value):
    path = RES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream", type=Path)
    args = parser.parse_args()
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=args.upstream, text=True).strip()
    if commit != UPSTREAM_COMMIT:
        raise SystemExit("Expected the official 1.0.27_X5687 commit: " + UPSTREAM_COMMIT)
    assets = args.upstream / "src/main/resources/assets/hbm"
    provenance = {}
    langs = {"en_us": {"itemGroup.hbm.materials": "HBM — Port Prototype"},
             "ru_ru": {"itemGroup.hbm.materials": "HBM — Прототип переноса"}}
    for metal, (english, russian) in METALS.items():
        ingot, plate, block = (f"{kind}_{metal}" for kind in ("ingot", "plate", "block"))
        for identifier, old_dir, new_dir in ((ingot, "items", "item"), (plate, "items", "item"), (block, "blocks", "block")):
            source = assets / f"textures/{old_dir}/{identifier}.png"
            target = RES / f"assets/hbm/textures/{new_dir}/{identifier}.png"
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            provenance[str(target.relative_to(ROOT))] = hashlib.sha256(source.read_bytes()).hexdigest()
        for identifier in (ingot, plate):
            write(f"assets/hbm/models/item/{identifier}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"hbm:item/{identifier}"}})
        write(f"assets/hbm/models/block/{block}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"hbm:block/{block}"}})
        write(f"assets/hbm/models/item/{block}.json", {"parent": f"hbm:block/{block}"})
        write(f"assets/hbm/blockstates/{block}.json", {"variants": {"": {"model": f"hbm:block/{block}"}}})
        write(f"data/hbm/loot_table/blocks/{block}.json", {
            "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"hbm:{block}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        write(f"data/hbm/recipe/storage/{block}.json", {"type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": ["III", "III", "III"], "key": {"I": {"item": f"hbm:{ingot}"}}, "result": {"id": f"hbm:{block}"}})
        write(f"data/hbm/recipe/storage/{ingot}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": f"hbm:{block}"}], "result": {"id": f"hbm:{ingot}", "count": 9}})
        write(f"data/hbm/recipe/create/pressing/{plate}.json", {"type": "create:pressing",
            "ingredients": [{"tag": f"c:ingots/{metal}"}], "results": [{"id": f"hbm:{plate}"}]})
        for category, identifier in (("ingots", ingot), ("plates", plate), ("storage_blocks", block)):
            write(f"data/c/tags/item/{category}/{metal}.json", {"replace": False, "values": [f"hbm:{identifier}"]})
        write(f"data/c/tags/block/storage_blocks/{metal}.json", {"replace": False, "values": [f"hbm:{block}"]})
        langs["en_us"].update({f"item.hbm.{ingot}": english + " Ingot", f"item.hbm.{plate}": english + " Plate", f"block.hbm.{block}": english + " Block"})
        # Preserve the original Russian translations, rather than guessing inflections.
        legacy_ru = (assets / "lang/ru_RU.lang").read_text(errors="replace")
        entries = dict(line.split("=", 1) for line in legacy_ru.splitlines() if "=" in line and not line.startswith("#"))
        for kind, identifier in (("item", ingot), ("item", plate), ("tile", block)):
            key = ("block" if kind == "tile" else kind) + ".hbm." + identifier
            langs["ru_ru"][key] = entries.get(f"{kind}.{identifier}.name", russian)
    blocks = ["hbm:block_" + metal for metal in METALS]
    for path in ("mineable/pickaxe", "needs_iron_tool", "beacon_base_blocks"):
        write(f"data/minecraft/tags/block/{path}.json", {"replace": False, "values": blocks})
    for category in ("ingots", "plates", "storage_blocks"):
        write(f"data/c/tags/item/{category}.json", {"replace": False, "values": [f"#c:{category}/{metal}" for metal in METALS]})
    write("data/c/tags/block/storage_blocks.json", {"replace": False, "values": [f"#c:storage_blocks/{metal}" for metal in METALS]})
    for lang, values in langs.items():
        write(f"assets/hbm/lang/{lang}.json", values)
    (ROOT / "provenance.json").write_text(json.dumps({"upstream_commit": commit, "textures_sha256": provenance}, indent=2) + "\n")
    print(f"Imported {len(provenance)} textures from official X5687; generated recipes, models, tags and translations.")


if __name__ == "__main__":
    main()
