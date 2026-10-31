#!/usr/bin/env python3
"""Import base iron/steel storage crates from the pinned official X5687 source."""
import argparse, hashlib, json, shutil, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
COMMIT = '23ef33447280d83d44c1f7bcd3853fe59c0ec1fc'
def write(path, value):
    p = RES / path; p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('upstream',type=Path); upstream=parser.parse_args().upstream
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=upstream,text=True).strip()==COMMIT
    assets=upstream/'src/main/resources/assets/hbm'; hashes={}
    for metal in ['iron','steel']:
        for source,target in [(f'textures/blocks/crate_{metal}_top.png',f'textures/block/crate_{metal}_top.png'),
                              (f'textures/blocks/crate_{metal}_side.png',f'textures/block/crate_{metal}_side.png'),
                              (f'textures/gui/storage/gui_crate_{metal}.png',f'textures/gui/gui_crate_{metal}.png')]:
            dest=RES/'assets/hbm'/target;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(assets/source,dest)
            hashes[source]=hashlib.sha256(dest.read_bytes()).hexdigest()
        block=f'crate_{metal}'
        write(f'assets/hbm/models/block/{block}.json',{'parent':'minecraft:block/cube_column','textures':{'end':f'hbm:block/{block}_top','side':f'hbm:block/{block}_side'}})
        write(f'assets/hbm/models/item/{block}.json',{'parent':f'hbm:block/{block}'})
        write(f'assets/hbm/blockstates/{block}.json',{'variants':{'':{'model':f'hbm:block/{block}'}}})
        write(f'data/hbm/recipe/storage/{block}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['PPP','I I','III'],
              'key':{'P':{'tag':f'c:plates/{metal}'},'I':{'tag':f'c:ingots/{metal}'}},'result':{'id':f'hbm:{block}'}})
        write(f'data/hbm/loot_table/blocks/{block}.json',{'type':'minecraft:block','pools':[{'rolls':1,
            'entries':[{'type':'minecraft:item','name':f'hbm:{block}','functions':[{'function':'minecraft:copy_components','source':'block_entity',
                'include':['minecraft:custom_name','minecraft:container','minecraft:lock','minecraft:max_stack_size']}]}]}]})
    for name in ['Open','Close']:
        source=f'sounds/block/crate{name}.ogg';dest=RES/'assets/hbm'/f'sounds/block/crate_{name.lower()}.ogg';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(assets/source,dest)
        hashes[source]=hashlib.sha256(dest.read_bytes()).hexdigest()
    write('assets/hbm/sounds.json',{'block.crate_open':{'sounds':['hbm:block/crate_open']},'block.crate_close':{'sounds':['hbm:block/crate_close']}})
    path=RES/'data/minecraft/tags/block/mineable/pickaxe.json'; data=json.loads(path.read_text());data['values']=sorted(set(data['values']+['hbm:crate_iron','hbm:crate_steel']));write(path.relative_to(RES),data)
    for lang in ['en_us','ru_ru']:
        p=RES/f'assets/hbm/lang/{lang}.json';data=json.loads(p.read_text())
        data.update({'block.hbm.crate_iron':'Iron Crate' if lang=='en_us' else 'Железный ящик',
            'block.hbm.crate_steel':'Steel Crate' if lang=='en_us' else 'Стальной ящик',
            'tooltip.hbm.crate_slots':'%s inventory slots' if lang=='en_us' else 'Слотов: %s'})
        write(p.relative_to(RES),data)
    (ROOT/'crate-provenance.json').write_text(json.dumps({'upstream_commit':COMMIT,'source_assets_sha256':hashes,'changes':'Textures, GUI and sounds copied byte-for-byte; new native block models and recipes.'},indent=2)+'\n')
if __name__=='__main__':main()
