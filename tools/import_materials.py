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
    ores = []
    for metal, count, size in (("titanium", 8, 6), ("lead", 6, 9)):
        identifier = "ore_" + metal
        ores.append("hbm:" + identifier)
        source = assets / f"textures/blocks/{identifier}.png"
        target = RES / f"assets/hbm/textures/block/{identifier}.png"
        shutil.copyfile(source, target)
        provenance[str(target.relative_to(ROOT))] = hashlib.sha256(source.read_bytes()).hexdigest()
        write(f"assets/hbm/models/block/{identifier}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"hbm:block/{identifier}"}})
        write(f"assets/hbm/models/item/{identifier}.json", {"parent": f"hbm:block/{identifier}"})
        write(f"assets/hbm/blockstates/{identifier}.json", {"variants": {"": {"model": f"hbm:block/{identifier}"}}})
        # X5687 drops the ore block itself; do not introduce raw-metal items or Fortune multiplication.
        write(f"data/hbm/loot_table/blocks/{identifier}.json", {"type": "minecraft:block", "pools": [{"rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"hbm:{identifier}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        for method, ticks in (("smelting", 200), ("blasting", 100)):
            write(f"data/hbm/recipe/{method}/ingot_{metal}_from_ore.json", {"type": "minecraft:" + method, "category": "misc",
                "ingredient": {"item": f"hbm:{identifier}"}, "result": {"id": f"hbm:ingot_{metal}"}, "experience": 3.0, "cookingtime": ticks})
        for registry in ("item", "block"):
            write(f"data/c/tags/{registry}/ores/{metal}.json", {"replace": False, "values": [f"hbm:{identifier}"]})
        # HbmWorldGen + DungeonToolbox: minHeight 5 + nextInt(30), i.e. 5..34 inclusive.
        write(f"data/hbm/worldgen/configured_feature/{identifier}.json", {"type": "minecraft:ore", "config": {
            "size": size, "discard_chance_on_air_exposure": 0.0,
            "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:stone"},
                         "state": {"Name": f"hbm:{identifier}"}}]}})
        write(f"data/hbm/worldgen/placed_feature/{identifier}.json", {"feature": f"hbm:{identifier}", "placement": [
            {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 5}, "max_inclusive": {"absolute": 34}}},
            {"type": "minecraft:biome"}]})
        langs["en_us"][f"block.hbm.{identifier}"] = METALS[metal][0] + " Ore"
        langs["ru_ru"][f"block.hbm.{identifier}"] = entries[f"tile.{identifier}.name"]
    write("data/hbm/neoforge/biome_modifier/add_ores.json", {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": ores, "step": "underground_ores"})
    for registry in ("item", "block"):
        write(f"data/c/tags/{registry}/ores.json", {"replace": False, "values": ["#c:ores/titanium", "#c:ores/lead"]})
        write(f"data/c/tags/{registry}/ores_in_ground/stone.json", {"replace": False, "values": ores})
    # Create integration of BlastFurnaceRecipes' IRON + COAL -> 1 steel recipe.
    # The original blast furnace and its other recipes remain separate pending ports.
    write("data/hbm/recipe/create/mixing/steel_from_iron_and_coal.json", {"type": "create:mixing", "heat_requirement": "heated",
        "ingredients": [{"tag": "c:ingots/iron"}, {"item": "minecraft:coal"}], "results": [{"id": "hbm:ingot_steel"}]})
    write("data/minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": blocks + ores})
    for path in ("needs_iron_tool", "beacon_base_blocks"):
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
