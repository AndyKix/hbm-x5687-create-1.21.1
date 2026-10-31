#!/usr/bin/env python3
"""Extend the existing slice with X5687's natural solid resources and reachable mineral forms.

Run AFTER import_materials.py, import_iron_furnace.py and import_crates.py.
Recipes are selected by reachability, not by adding the entire legacy item catalogue.
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
COMMIT = '23ef33447280d83d44c1f7bcd3853fe59c0ec1fc'
BLOCKS, ITEMS, VEINS, RECIPES = {}, {}, [], {}
PROVENANCE = {}


def alexandrite_frames(source):
    """Losslessly unpack the original RGBA8 sprite strip for native item-model overrides.

    This changes the resource representation only; no pixel is redrawn/recoloured.
    The original used a custom atlas ticker driven by the player's block light.
    """
    png = source.read_bytes()
    width, height, depth, color, compression, filtering, interlace = struct.unpack('>IIBBBBB', png[16:29])
    assert (width, height, depth, color, compression, filtering, interlace) == (16, 256, 8, 6, 0, 0, 0)
    packed = bytearray(); offset = 8
    while offset < len(png):
        size = struct.unpack('>I', png[offset:offset + 4])[0]
        if png[offset + 4:offset + 8] == b'IDAT': packed.extend(png[offset + 8:offset + 8 + size])
        offset += size + 12
    raw = zlib.decompress(packed); rows = []; stride = width * 4; previous = bytes(stride)
    def paeth(a, b, c):
        p = a + b - c; da, db, dc = abs(p - a), abs(p - b), abs(p - c)
        return a if da <= db and da <= dc else b if db <= dc else c
    for y in range(height):
        start = y * (stride + 1); method = raw[start]; row = bytearray(raw[start + 1:start + 1 + stride])
        for x in range(stride):
            left, up, upper_left = (row[x - 4] if x >= 4 else 0), previous[x], (previous[x - 4] if x >= 4 else 0)
            prediction = (0, left, up, (left + up) // 2, paeth(left, up, upper_left))[method]
            row[x] = (row[x] + prediction) & 255
        rows.append(bytes(row)); previous = row
    def chunk(kind, data): return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    for frame in range(16):
        pixels = b''.join(b'\x00' + row for row in rows[frame * 16:(frame + 1) * 16])
        encoded = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels, 9)) + chunk(b'IEND', b'')
        target = RES / f'assets/hbm/textures/item/gem_alexandrite_{frame}.png'; target.write_bytes(encoded)
        PROVENANCE[str(target.relative_to(ROOT))] = dict(source='src/main/resources/assets/hbm/textures/items/gem_alexandrite.png',
            source_sha256=hashlib.sha256(png).hexdigest(), sha256=hashlib.sha256(encoded).hexdigest(), transformation=f'lossless 16x16 frame {frame} of 16')
        write(f'assets/hbm/models/item/gem_alexandrite_light_{frame}.json', dict(parent='minecraft:item/generated', textures={'layer0': f'hbm:item/gem_alexandrite_{frame}'}))
    write('assets/hbm/models/item/gem_alexandrite.json', dict(parent='hbm:item/gem_alexandrite_light_0', overrides=[
        dict(predicate={'hbm:block_light': frame / 15}, model=f'hbm:item/gem_alexandrite_light_{frame}') for frame in range(1,16)]))


def write(path, data):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def tag(registry, name, values):
    path = f'data/{name.split(":")[0]}/tags/{registry}/{name.split(":")[1]}.json'
    old = json.loads((RES / path).read_text()) if (RES / path).exists() else {'replace': False, 'values': []}
    old['values'] = list(dict.fromkeys(old['values'] + values))
    write(path, old)


def item(identifier, **kwargs):
    if not identifier.startswith('minecraft:'):
        ITEMS.setdefault(identifier.removeprefix('hbm:'), {}).update(kwargs)


def block(identifier, hardness=5, resistance=10, **kwargs):
    BLOCKS[identifier] = dict(hardness=hardness, resistance=resistance, **kwargs)


def vein(identifier, count, size, low, high, target='minecraft:stone', dimension='overworld', rarity=None):
    VEINS.append(dict(id=identifier, count=count, size=size, min_y=low, max_y=high,
                      target=target, dimension=dimension, rarity=rarity))


def material_recipe(key, output, count, inputs, pattern=None, source='crafting/MineralRecipes.java'):
    if output.startswith('hbm:'):
        output = output[4:]
    result = {'id': output if ':' in output else 'hbm:' + output, 'count': count}
    if pattern:
        data = dict(type='minecraft:crafting_shaped', category='misc', pattern=pattern,
                    key={'#': {'item': inputs[0]}}, result=result)
    else:
        data = dict(type='minecraft:crafting_shapeless', category='misc',
                    ingredients=[{'item': x} for x in inputs], result=result)
    RECIPES[key] = dict(data=data, inputs=inputs, output=result['id'], source=source)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('upstream', type=Path)
    upstream = parser.parse_args().upstream.resolve()
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=upstream, text=True).strip() == COMMIT
    java = upstream / 'src/main/java/com/hbm'
    assets = upstream / 'src/main/resources/assets/hbm'
    definitions = {kind: (java / path).read_text() for kind, path in
                   [('block', 'blocks/ModBlocks.java'), ('item', 'items/ModItems.java')]}
    # Effective defaults from WorldConfig.loadConfig (not merely field initializers).
    for name, count, size, lo, hi in [
        ('uranium', 6, 5, 5, 24), ('thorium', 7, 5, 5, 29), ('titanium', 8, 6, 5, 34),
        ('sulfur', 5, 8, 5, 34), ('aluminium', 7, 6, 5, 44), ('copper', 12, 6, 5, 49),
        ('fluorite', 6, 4, 5, 49), ('niter', 6, 6, 5, 34), ('tungsten', 10, 8, 5, 34),
        ('lead', 6, 9, 5, 34), ('beryllium', 6, 4, 5, 34), ('rare', 6, 5, 5, 24),
        ('lignite', 2, 24, 35, 59), ('asbestos', 2, 4, 16, 31), ('cinnebar', 1, 4, 8, 23),
        ('cobalt', 2, 4, 4, 11)]:
        identifier = 'ore_' + name
        block(identifier, resistance=15 if name in ('beryllium', 'lignite', 'asbestos') else 10)
        vein(identifier, count, size, lo, hi)
    for name, count, hi in [('iron', 4, 59), ('titanium', 2, 44), ('aluminium', 3, 49), ('copper', 4, 34)]:
        identifier = 'cluster_' + name
        block(identifier, resistance=15, drop='crystal_' + name)
        vein(identifier, count, 6, 15, hi)
    for name, count in [('iron', 25), ('gold', 10), ('uranium', 18), ('copper', 36),
                        ('asbestos', 6), ('lithium', 6), ('rare', 6)]:
        identifier = 'ore_gneiss_' + name
        block(identifier, 1.5)
        vein(identifier, count, 6, 30, 39, 'hbm:stone_gneiss')
    for name, count, size, lo, hi in [('uranium', 8, 6, 0, 126), ('tungsten', 10, 10, 0, 126),
                                     ('sulfur', 26, 12, 0, 126), ('fire', 24, 6, 0, 126),
                                     ('coal', 8, 32, 16, 111), ('cobalt', 2, 6, 100, 125)]:
        identifier = 'ore_nether_' + name
        block(identifier, .4, light=10 if name == 'coal' else 0, burning=name == 'coal')
        vein(identifier, count, size, lo, hi, 'minecraft:netherrack', 'nether')
    block('ore_tikite', dragon_proof=True)
    vein('ore_tikite', 8, 6, 0, 126, 'minecraft:end_stone', 'end')
    block('ore_coltan', 15, drop='fragment_coltan')
    block('ore_australium')
    block('ore_alexandrite', -1, depth=True, drop='gem_alexandrite')
    vein('ore_alexandrite', 1, 3, 10, 14, rarity=100)
    for name, drop in [('cluster_depth_iron', 'crystal_iron'), ('cluster_depth_titanium', 'crystal_titanium'),
                       ('cluster_depth_tungsten', 'crystal_tungsten'), ('ore_depth_cinnebar', 'cinnebar'),
                       ('ore_depth_zirconium', 'nugget_zirconium'), ('ore_depth_borax', 'powder_borax'),
                       ('ore_depth_nether_neodymium', 'fragment_neodymium')]:
        block(name, -1, depth=True, drop=drop)
    block('stone_gneiss', 1.5, texture='stone_gneiss_var', resource=True)
    for identifier in ('stone_depth', 'stone_depth_nether'):
        block(identifier, -1, depth=True, resource=True)
    for material in ('sulfur', 'asbestos', 'hematite', 'malachite', 'limestone', 'bauxite'):
        block('stone_resource_' + material, resource=True, legacy='stone_resource', variant=material)
    vein('stone_resource_limestone', 1, 16, 25, 54)
    for kind in ('stalagmite', 'stalactite'):
        block(kind + '_asbestos', .5, 2, resource=True, decoration=True, legacy=kind, variant='asbestos', drop='powder_asbestos')
    # Item/quantity tables are from BlockOre, BlockCluster, BlockDepthOre and BlockResourceStone.
    for identifier, definition in BLOCKS.items():
        if identifier.endswith(('_sulfur', '_niter', '_fluorite')) and not definition.get('resource'):
            definition.update(drop=identifier.rsplit('_', 1)[1], min_drop=2, max_drop=4, fortune=True)
        if identifier in ('ore_asbestos', 'ore_gneiss_asbestos'):
            definition.update(drop='ingot_asbestos', fortune=True)
        if identifier in ('ore_rare', 'ore_gneiss_rare'):
            definition.update(drop='chunk_ore_rare', fortune=True)
        if identifier == 'ore_lignite': definition.update(drop='lignite', fortune=True)
        if identifier == 'ore_cinnebar': definition.update(drop='cinnebar', fortune=True)
        if identifier.endswith('_cobalt'):
            definition.update(drop='fragment_cobalt', min_drop=5 if 'nether' in identifier else 4,
                              max_drop=12 if 'nether' in identifier else 9, fortune=True)
        if identifier == 'ore_nether_coal': definition.update(drop='coal_infernal')
        if identifier == 'ore_nether_fire': definition.update(drop='powder_fire', phosphorus_chance=.1, fortune=True)
        if identifier == 'ore_coltan': definition['fortune'] = True
        if definition.get('depth') and definition.get('drop'): definition['fortune'] = True
        if identifier == 'ore_depth_cinnebar': definition.update(min_drop=2, max_drop=4)
        if identifier in ('ore_depth_zirconium', 'ore_depth_nether_neodymium'): definition.update(min_drop=2, max_drop=3)
        if identifier == 'stone_resource_malachite': definition.update(drop='chunk_ore_malachite', malachite=True)
    item('ingot_phosphorus')
    for definition in BLOCKS.values():
        if definition.get('drop'): item(definition['drop'])
    # Only natural crystals and direct ore products enter the reachable material set.
    available = {'hbm:' + x for x in [*BLOCKS, *ITEMS, 'ingot_steel', 'ingot_titanium', 'ingot_lead']}
    available |= {'minecraft:' + x for x in ('iron_ingot', 'gold_ingot', 'copper_ingot', 'coal', 'charcoal', 'redstone')}
    mineral = (java / 'crafting/MineralRecipes.java').read_text()
    ref = r'(?:Item\.getItemFromBlock\(ModBlocks\.\w+\)|Mod(?:Items|Blocks)\.\w+)'
    def identifier(expression):
        return 'hbm:' + re.search(r'Mod(?:Items|Blocks)\.(\w+)', expression)[1]
    candidates = []
    for line_number, line in enumerate(mineral.splitlines(), 1):
        if line.lstrip().startswith('//'): continue
        origin = f'crafting/MineralRecipes.java:{line_number}'
        # Simple single-symbol shaped recipes, preserving both output count and shape.
        match = re.search(r'GameRegistry.addRecipe\(new ItemStack\((' + ref + r')(?:,\s*(\d+))?\), new Object\[\] \{ (.+) \}\);', line)
        if match:
            out, count, body = match.groups()
            shape = re.findall(r'"([# ]+)"', body)
            tail = re.search(r"'#',\s*(" + ref + r')\s*$', body)
            if shape and tail and len(re.findall(ref, body)) == 1:
                candidates.append((identifier(out), int(count or 1), [identifier(tail[1])], shape, origin))
        match = re.search(r'add1To9Pair\((Mod(?:Items|Blocks)\.\w+),\s*(Mod(?:Items|Blocks)\.\w+)\);', line)
        if match:
            one, nine = map(identifier, match.groups())
            candidates.extend([(one, 1, [nine], ['###'] * 3, origin), (nine, 9, [one], ['#'], origin)])
        match = re.search(r'addMineralSet\((ModItems\.\w+),\s*(ModItems\.\w+),\s*(ModBlocks\.\w+)\);', line)
        if match:
            nugget, ingot, storage = map(identifier, match.groups())
            candidates.extend([(ingot, 1, [nugget], ['###'] * 3, origin), (nugget, 9, [ingot], ['#'], origin),
                               (storage, 1, [ingot], ['###'] * 3, origin), (ingot, 9, [storage], ['#'], origin)])
        match = re.search(r'addBillet\((ModItems\.\w+),\s*(ModItems\.\w+),\s*(ModItems\.\w+)', line)
        if match:
            billet, ingot, nugget = map(identifier, match.groups())
            candidates.extend([(billet, 1, [nugget], ['###'] * 2, origin), (nugget, 6, [billet], None, origin),
                               (billet, 3, [ingot], ['##'], origin), (ingot, 2, [billet] * 3, None, origin)])
    # Smelting is likewise selected from the original source; no invented ore-to-ingot conversions.
    smelting = []
    for line_number, line in enumerate((java / 'crafting/SmeltingRecipes.java').read_text().splitlines(), 1):
        if line.lstrip().startswith('//'): continue
        match = re.search(r'GameRegistry.addSmelting\((' + ref + r'), new ItemStack\((ModItems\.\w+|Items\.\w+)(?:,\s*(\d+))?\), ([\d.]+)F\);', line)
        if match:
            source, target, count, xp = match.groups()
            target_id = identifier(target) if target.startswith('Mod') else 'minecraft:' + target.split('.')[1]
            smelting.append((identifier(source), target_id, int(count or 1), float(xp), line_number))
    smelting.append(('hbm:ore_aluminium', 'hbm:chunk_ore_cryolite', 1, 2.5, 40))
    # Forms with machines/hot-item/chemical behaviour are explicitly outside this closure.
    excluded = {'hbm:ingot_chainsteel', 'hbm:ingot_meteorite', 'hbm:ingot_steel_dusted'}
    for _ in range(20):
        before = set(available)
        for source, target, count, xp, line_number in smelting:
            if source in available and target not in excluded:
                available.add(target)
        for out, count, inputs, shape, origin in candidates:
            if all(x in available for x in inputs) and out not in excluded:
                available.add(out)
        if before == available: break
    for out, count, inputs, shape, origin in candidates:
        if out in available and all(x in available for x in inputs):
            key = out[4:] + '_from_' + inputs[0][4:] + ('_x' + str(len(inputs)) if len(inputs) > 1 else '')
            material_recipe(key, out, count, inputs, shape, origin)
    for source, target, count, xp, line_number in smelting:
        if source in available and target in available:
            for method, ticks in [('smelting', 200), ('blasting', 100)]:
                write(f'data/hbm/recipe/{method}/raw/{source[4:]}.json', dict(type='minecraft:' + method, category='misc',
                    ingredient={'item': source}, result={'id': target, 'count': count}, experience=xp, cookingtime=ticks))
    # Register only the reached forms; the remaining isotope and chemistry catalogue stays with its machines.
    for resource in sorted(available):
        if not resource.startswith('hbm:'): continue
        name = resource[4:]
        if name in BLOCKS: continue
        if re.search(r'^\s*' + re.escape(name) + r'\s*=.*new ', definitions['block'], re.M):
            block(name, resource=True, storage=True)
        else: item(name)
    # Extend pressing without conflicting with Create's iron/gold/copper sheet recipes.
    for metal in ('aluminium',):
        item('plate_' + metal)
        write(f'data/hbm/recipe/create/pressing/plate_{metal}.json', dict(type='create:pressing',
            ingredients=[{'tag': f'c:ingots/{metal}'}], results=[{'id': 'hbm:plate_' + metal}]))
    # Original hand-crafting of gunpowder is a direct raw-resource recipe (no new explosives added).
    for carbon in ('coal', 'charcoal'):
        material_recipe('gunpowder_from_' + carbon, 'minecraft:gunpowder', 3,
                        ['hbm:sulfur', 'hbm:niter', 'minecraft:' + carbon], source='crafting/PowderRecipes.java:51-54')
    for key, recipe in RECIPES.items(): write('data/hbm/recipe/minerals/' + key + '.json', recipe['data'])
    translations = {}
    for language, legacy_language in [('en_us', 'en_US'), ('ru_ru', 'ru_RU')]:
        translations[language] = dict(line.split('=', 1) for line in (assets / f'lang/{legacy_language}.lang').read_text(errors='replace').splitlines()
                                      if '=' in line and not line.startswith('#'))
    merged_langs = {lang: json.loads((RES / f'assets/hbm/lang/{lang}.json').read_text()) for lang in translations}
    merged_langs['en_us']['tooltip.hbm.depth_rock'] = 'Requires a special depth-rock tool.'
    merged_langs['ru_ru']['tooltip.hbm.depth_rock'] = 'Требуется специальный инструмент для глубинной породы.'
    def texture(kind, name, definition):
        old_kind = 'blocks' if kind == 'block' else 'items'
        old_name = definition.get('legacy', name)
        line = re.search(r'^\s*' + re.escape(old_name) + r'\s*=.*$', definitions[kind], re.M)
        tex = definition.get('texture')
        if not tex and not definition.get('variant'):
            match = re.search(r'set(?:Block)?TextureName\(RefStrings.MODID \+ ":([^"\n]+)"', line[0]) if line else None
            tex = match[1] if match else name
        if definition.get('variant'): tex = old_name + '.' + definition['variant']
        if name.startswith('chunk_ore_'): tex = name.replace('chunk_ore_', 'chunk_ore.')
        source = assets / f'textures/{old_kind}/{tex}.png'
        assert source.exists(), f'Missing original texture {source}'
        target = RES / f'assets/hbm/textures/{kind}/{name}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        PROVENANCE[str(target.relative_to(ROOT))] = dict(source=str(source.relative_to(upstream)), sha256=hashlib.sha256(source.read_bytes()).hexdigest())
        animation = source.with_suffix('.png.mcmeta')
        if animation.exists():
            meta_target = target.with_suffix('.png.mcmeta')
            shutil.copyfile(animation, meta_target)
            PROVENANCE[str(meta_target.relative_to(ROOT))] = dict(source=str(animation.relative_to(upstream)), sha256=hashlib.sha256(animation.read_bytes()).hexdigest())
        for lang, entries in translations.items():
            legacy_key = ('tile.' if kind == 'block' else 'item.') + old_name
            if definition.get('variant'): legacy_key += '.' + definition['variant']
            if name.startswith('chunk_ore_'): legacy_key = 'item.chunk_ore.' + name.removeprefix('chunk_ore_')
            key = f'{kind}.hbm.{name}'
            translated = entries.get(legacy_key + '.name')
            assert translated, f'Missing original {lang} translation for {legacy_key}'
            merged_langs[lang][key] = translated
    existing_items = {x for m in ('steel', 'titanium', 'lead') for x in ('ingot_' + m, 'plate_' + m, 'block_' + m)} | {'ore_titanium', 'ore_lead'}
    for identifier, definition in ITEMS.items():
        if identifier in existing_items: continue
        texture('item', identifier, definition)
        write(f'assets/hbm/models/item/{identifier}.json', dict(parent='minecraft:item/generated', textures={'layer0': 'hbm:item/' + identifier}))
    for identifier, definition in BLOCKS.items():
        if identifier in existing_items: continue
        # Preserve hardness/resistance of compression blocks from their actual definitions.
        if definition.get('storage'):
            line = re.search(r'^\s*' + re.escape(identifier) + r'\s*=.*$', definitions['block'], re.M)[0]
            for field, setter in [('hardness', 'Hardness'), ('resistance', 'Resistance')]:
                match = re.search(r'set' + setter + r'\(([\d.]+)F\)', line)
                if match: definition[field] = float(match[1])
            definition['beacon'] = 'makeBeaconable' in line or 'BlockBeaconable' in line or identifier == 'block_lithium'
        texture('block', identifier, definition)
        if definition.get('decoration'):
            write(f'assets/hbm/models/block/{identifier}.json', dict(parent='minecraft:block/cross', render_type='minecraft:cutout', textures={'cross': 'hbm:block/' + identifier}))
        else:
            write(f'assets/hbm/models/block/{identifier}.json', dict(parent='minecraft:block/cube_all', textures={'all': 'hbm:block/' + identifier}))
        write(f'assets/hbm/models/item/{identifier}.json', {'parent': 'hbm:block/' + identifier})
        write(f'assets/hbm/blockstates/{identifier}.json', {'variants': {'': {'model': 'hbm:block/' + identifier}}})
        drop = definition.get('drop', identifier)
        entry = dict(type='minecraft:item', name='hbm:' + drop)
        functions = []
        if 'min_drop' in definition:
            functions.append(dict(function='minecraft:set_count', count={'type': 'minecraft:uniform', 'min': definition['min_drop'], 'max': definition['max_drop']}))
        if definition.get('fortune'):
            functions.append(dict(function='minecraft:apply_bonus', enchantment='minecraft:fortune', formula='minecraft:ore_drops'))
        if functions: entry['functions'] = functions
        if definition.get('phosphorus_chance') or definition.get('malachite') or identifier == 'ore_alexandrite':
            # Exact nonstandard distributions are handled by a registered loot function.
            entry['functions'] = [{'function': 'hbm:raw_ore_yield', 'ore': identifier}]
        if drop != identifier and not definition.get('decoration'):
            entry = dict(type='minecraft:alternatives', children=[dict(type='minecraft:item', name='hbm:' + identifier,
                conditions=[dict(condition='minecraft:match_tool', predicate={'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}})]), entry])
        write(f'data/hbm/loot_table/blocks/{identifier}.json', dict(type='minecraft:block', pools=[dict(rolls=1, entries=[entry],
            conditions=[{'condition': 'minecraft:survives_explosion'}])]))
    for lang, entries in merged_langs.items(): write(f'assets/hbm/lang/{lang}.json', entries)
    alexandrite_frames(assets / 'textures/items/gem_alexandrite.png')
    new_blocks = ['hbm:' + x for x in BLOCKS if x not in existing_items]
    tag('block', 'minecraft:mineable/pickaxe', new_blocks)
    tag('block', 'minecraft:dragon_immune', ['hbm:ore_tikite'])
    tag('block', 'create:non_movable', ['hbm:' + x for x, d in BLOCKS.items() if d.get('depth')])
    tag('block', 'minecraft:beacon_base_blocks', ['hbm:' + x for x, d in BLOCKS.items() if d.get('beacon')])
    # Mineral aliases follow OreDictManager (thorium's item name is th232).
    for identifier in [*ITEMS, *[x for x, d in BLOCKS.items() if d.get('storage')]]:
        category, _, material = identifier.partition('_')
        category = {'ingot': 'ingots', 'nugget': 'nuggets', 'plate': 'plates', 'block': 'storage_blocks', 'billet': 'billets'}.get(category)
        if category:
            if material == 'th232': material = 'thorium'
            if material == 'phosphorus': material = 'white_phosphorus'
            if material == 'niter': material = 'saltpeter'
            tag('item', 'c:' + category + '/' + material, ['hbm:' + identifier])
            tag('item', 'c:' + category, ['#c:' + category + '/' + material])
            if category == 'storage_blocks': tag('block', 'c:storage_blocks/' + material, ['hbm:' + identifier])
            if category == 'storage_blocks': tag('block', 'c:storage_blocks', ['#c:storage_blocks/' + material])
    # Non-prefix-shaped materials and aliases from OreDictManager, not guessed from filenames.
    for name, category, material in [('sulfur', 'dusts', 'sulfur'), ('niter', 'dusts', 'saltpeter'),
            ('fluorite', 'dusts', 'fluorite'), ('powder_borax', 'dusts', 'borax'), ('powder_fire', 'dusts', 'red_phosphorus'),
            ('powder_asbestos', 'dusts', 'asbestos'), ('lithium', 'ingots', 'lithium'), ('lignite', 'gems', 'lignite'),
            ('fragment_coltan', 'ingots', 'coltan'), ('fragment_cobalt', 'nuggets', 'cobalt'),
            ('fragment_neodymium', 'nuggets', 'neodymium'), ('chunk_ore_rare', 'ingots', 'rare_earth'),
            ('chunk_ore_malachite', 'ingots', 'malachite'), ('chunk_ore_cryolite', 'crystals', 'cryolite'),
            ('cinnebar', 'gems', 'cinnabar'), ('cinnebar', 'crystals', 'cinnabar'), ('stone_resource_bauxite', 'gems', 'bauxite')]:
        tag('item', f'c:{category}/{material}', ['hbm:' + name]); tag('item', 'c:' + category, [f'#c:{category}/{material}'])
    for v in VEINS:
        identifier = v['id']
        write(f'data/hbm/worldgen/configured_feature/{identifier}.json', dict(type='minecraft:ore', config=dict(size=v['size'],
            discard_chance_on_air_exposure=0, targets=[dict(target={'predicate_type': 'minecraft:block_match', 'block': v['target']}, state={'Name': 'hbm:' + identifier})])))
        placement = [{'type': 'minecraft:rarity_filter', 'chance': v['rarity']}] if v['rarity'] else [{'type': 'minecraft:count', 'count': v['count']}]
        placement += [{'type': 'minecraft:in_square'}, {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform',
            'min_inclusive': {'absolute': v['min_y']}, 'max_inclusive': {'absolute': v['max_y']}}}, {'type': 'minecraft:biome'}]
        write(f'data/hbm/worldgen/placed_feature/{identifier}.json', dict(feature='hbm:' + identifier, placement=placement))
    ore_families = {name: ['ore_' + name] for name in ('uranium', 'thorium', 'titanium', 'aluminium', 'copper', 'tungsten', 'lead', 'beryllium', 'cobalt', 'fluorite', 'lignite', 'coltan', 'australium')}
    ore_families.update(sulfur=['ore_sulfur', 'ore_nether_sulfur', 'stone_resource_sulfur'], saltpeter=['ore_niter'],
        asbestos=['ore_asbestos', 'ore_gneiss_asbestos', 'stone_resource_asbestos'],
        rare_earth=['ore_rare', 'ore_gneiss_rare'], cinnabar=['ore_cinnebar', 'ore_depth_cinnebar'],
        borax=['ore_depth_borax'], zirconium=['ore_depth_zirconium'], neodymium=['ore_depth_nether_neodymium'],
        lithium=['ore_gneiss_lithium'], iron=['ore_gneiss_iron'], gold=['ore_gneiss_gold'],
        hematite=['stone_resource_hematite'], malachite=['stone_resource_malachite'], limestone=['stone_resource_limestone'])
    for material in ('uranium', 'copper'): ore_families[material].append('ore_gneiss_' + material)
    for material in ('uranium', 'tungsten', 'cobalt'): ore_families[material].append('ore_nether_' + material)
    # Infernal coal, phosphorus and tikite were NOT registered as generic coal/phosphorus ores.
    # Avoid silently enabling foreign generic-ore processing routes for those special resources.
    for material, names in ore_families.items():
        for registry in ('block', 'item'):
            tag(registry, 'c:ores/' + material, ['hbm:' + name for name in names]); tag(registry, 'c:ores', ['#c:ores/' + material])
            for name in names:
                ground = 'netherrack' if name.startswith('ore_nether_') else 'gneiss' if name.startswith('ore_gneiss_') else 'depth_rock' if name.startswith('ore_depth_') else 'stone'
                tag(registry, 'c:ores_in_ground/' + ground, ['hbm:' + name])
    for dimension in ('overworld', 'nether', 'end'):
        features = ['hbm:' + x['id'] for x in VEINS if x['dimension'] == dimension]
        features += ['hbm:depth_' + dimension] if dimension != 'end' else []
        if dimension == 'overworld': features += ['hbm:regional_resources']
        write(f'data/hbm/neoforge/biome_modifier/{"add_ores" if dimension == "overworld" else "add_ores_" + dimension}.json',
              dict(type='neoforge:add_features', biomes='#minecraft:is_' + dimension, features=features, step='underground_ores'))
    for name in ('depth_overworld', 'depth_nether', 'regional_resources', 'geological_layers'):
        write(f'data/hbm/worldgen/configured_feature/{name}.json', {'type': 'hbm:' + name, 'config': {}})
        write(f'data/hbm/worldgen/placed_feature/{name}.json', dict(feature='hbm:' + name, placement=[{'type': 'minecraft:biome'}]))
    write('data/hbm/neoforge/biome_modifier/geological_layers.json', dict(type='neoforge:add_features', biomes='#minecraft:is_overworld',
          features=['hbm:geological_layers'], step='underground_decoration'))
    # Separate earlier generation step, before both vanilla and HBM ores.
    geology_path = RES / 'data/hbm/neoforge/biome_modifier/geological_layers.json'
    geology = json.loads(geology_path.read_text()); geology['step'] = 'local_modifications'; write(str(geology_path.relative_to(RES)), geology)
    manifest = dict(upstream_commit=COMMIT, blocks=BLOCKS, items=ITEMS, veins=VEINS,
                    fuel_ticks={'lignite': 1200, 'coal_infernal': 4800, 'powder_fire': 6400},
                    direct_recipes={k: {x: v[x] for x in ('inputs', 'output', 'source')} for k, v in RECIPES.items()})
    write('assets/hbm/raw-materials.json', manifest)
    (ROOT / 'raw-material-provenance.json').write_text(json.dumps(dict(upstream_commit=COMMIT, assets=PROVENANCE), indent=2) + '\n')
    print(f'Generated {len(BLOCKS)} resource/mineral blocks, {len(ITEMS)} items, {len(VEINS)} veins, {len(RECIPES)} direct crafting recipes.')


if __name__ == '__main__': main()
