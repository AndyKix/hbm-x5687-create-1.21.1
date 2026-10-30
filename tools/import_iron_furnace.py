#!/usr/bin/env python3
"""Reproducible X5687 iron-furnace assets; vertices/UVs/normals remain unchanged."""
import argparse, hashlib, json, shutil, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
COMMIT = '23ef33447280d83d44c1f7bcd3853fe59c0ec1fc'
def write(path, data):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('upstream', type=Path)
    upstream = parser.parse_args().upstream
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=upstream, text=True).strip() == COMMIT
    assets = upstream / 'src/main/resources/assets/hbm'
    hashes = {}
    for source, target in [('textures/gui/processing/gui_furnace_iron.png', 'textures/gui/gui_furnace_iron.png'),
                           ('textures/models/machines/furnace_iron.png', 'textures/block/furnace_iron.png')]:
        dest = RES / 'assets/hbm' / target
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(assets / source, dest)
        hashes[source] = hashlib.sha256(dest.read_bytes()).hexdigest()
    source = assets / 'models/machines/furnace_iron.obj'
    hashes['models/machines/furnace_iron.obj'] = hashlib.sha256(source.read_bytes()).hexdigest()
    modeldir = RES / 'assets/hbm/models/block'
    modeldir.mkdir(parents=True, exist_ok=True)
    (modeldir / 'furnace_iron.mtl').write_text('newmtl main\nKa 0 0 0\nKd 1 1 1\nmap_Kd hbm:block/furnace_iron\n\nnewmtl fire\nKa 1 1 1\nKd 1 1 1\nmap_Kd hbm:block/furnace_iron\n')
    for active in (False, True):
        mode = 'on' if active else 'off'
        lines = ['# Adapted from official X5687; material binding and visible groups only.', 'mtllib furnace_iron.mtl']
        include = False
        for line in source.read_text().splitlines():
            parts = line.split()
            if not parts: continue
            kind = parts[0]
            if kind in ('o', 'g'):
                include = parts[1] in ('Main', 'On' if active else 'Off')
                if include: lines += [line, 'usemtl ' + ('fire' if parts[1] == 'On' else 'main')]
            elif kind in ('v', 'vt', 'vn') or include and kind == 'f': lines.append(line)
        (modeldir / f'furnace_iron_{mode}.obj').write_text('\n'.join(lines) + '\n')
        write(f'assets/hbm/models/block/furnace_iron_{mode}.json', {
            'loader': 'neoforge:obj', 'model': f'hbm:models/block/furnace_iron_{mode}.obj',
            'automatic_culling': False, 'flip_v': True, 'ambientocclusion': False,
            'textures': {'particle': 'hbm:block/furnace_iron'},
            'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, -2, 0], 'scale': [0.3, 0.3, 0.3]},
                        'ground': {'translation': [0, 3, 0], 'scale': [0.2, 0.2, 0.2]},
                        'fixed': {'scale': [0.4, 0.4, 0.4]},
                        'thirdperson_righthand': {'rotation': [75, 45, 0], 'scale': [0.25, 0.25, 0.25]},
                        'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.3, 0.3, 0.3]}}})
    variants = {}
    # Vanilla model rotations are clockwise viewed from above, opposite legacy GL rotations.
    for facing, angle in [('south', 0), ('east', 270), ('north', 180), ('west', 90)]:
        for lit in (False, True):
            variants[f'part=0,facing={facing},lit={str(lit).lower()}'] = {'model': f'hbm:block/furnace_iron_{"on" if lit else "off"}', 'y': angle}
    for part in range(1, 8): variants[f'part={part}'] = {'model': 'hbm:block/furnace_iron_empty'}
    write('assets/hbm/blockstates/furnace_iron.json', {'variants': variants})
    write('assets/hbm/models/block/furnace_iron_empty.json', {'textures': {'particle': 'hbm:block/furnace_iron'}, 'elements': []})
    write('assets/hbm/models/item/furnace_iron.json', {'parent': 'hbm:block/furnace_iron_off'})
    write('data/hbm/recipe/machine/furnace_iron.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'pattern': ['III','IFI','BBB'], 'key': {'I': {'tag': 'c:ingots/iron'}, 'F': {'item': 'minecraft:furnace'},
        'B': {'item': 'minecraft:stone_bricks'}}, 'result': {'id': 'hbm:furnace_iron'}})
    write('data/hbm/loot_table/blocks/furnace_iron.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': 'hbm:furnace_iron'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write('data/create/tags/block/non_movable.json', {'replace': False, 'values': ['hbm:furnace_iron']})
    path = RES / 'data/minecraft/tags/block/mineable/pickaxe.json'
    data = json.loads(path.read_text()); data['values'] = sorted(set(data['values'] + ['hbm:furnace_iron'])); write(path.relative_to(RES), data)
    for language, name, pending in [('en_us', 'Iron Furnace', 'Upgrades are planned for a later stage'),
                                    ('ru_ru', 'Железная печь', 'Улучшения будут добавлены на следующем этапе')]:
        path = RES / f'assets/hbm/lang/{language}.json'
        data = json.loads(path.read_text()); data.update({'block.hbm.furnace_iron': name, 'container.hbm.furnace_iron': name, 'tooltip.hbm.upgrades_pending': pending}); write(path.relative_to(RES), data)
    (ROOT / 'iron-furnace-provenance.json').write_text(json.dumps({'upstream_commit': COMMIT, 'source_assets_sha256': hashes,
        'obj_changes': 'Preserve all vertex/UV/normal lines; select Main+On or Main+Off faces; add material bindings.'}, indent=2) + '\n')
if __name__ == '__main__': main()
