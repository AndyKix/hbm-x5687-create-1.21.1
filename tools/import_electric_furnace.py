#!/usr/bin/env python3
"""Import one X5687 base electric furnace; native lit state replaces the second block ID."""
import argparse,hashlib,json,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources';COMMIT='23ef33447280d83d44c1f7bcd3853fe59c0ec1fc'
def write(name,value):
 p=RES/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('upstream',type=Path);upstream=parser.parse_args().upstream
 assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=upstream,text=True).strip()==COMMIT
 assets=upstream/'src/main/resources/assets/hbm';hashes={}
 for suffix in ['front_on','front_off','side','top','bottom']:
  source=f'textures/blocks/machine_electric_furnace_{suffix}.png';target=RES/'assets/hbm'/f'textures/block/machine_electric_furnace_{suffix}.png';target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(assets/source,target);hashes[source]=hashlib.sha256(target.read_bytes()).hexdigest()
 source='textures/gui/GUIElectricFurnace.png';target=RES/'assets/hbm/textures/gui/gui_electric_furnace.png';shutil.copyfile(assets/source,target);hashes[source]=hashlib.sha256(target.read_bytes()).hexdigest()
 for active in ['off','on']:
  texture='hbm:block/machine_electric_furnace_';write(f'assets/hbm/models/block/electric_furnace_{active}.json',{'parent':'minecraft:block/orientable_with_bottom','textures':{'front':texture+'front_'+active,'side':texture+'side','top':texture+'top','bottom':texture+'bottom'}})
 variants={}
 for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
  for active in [False,True]:variants[f'facing={facing},lit={str(active).lower()}']={'model':'hbm:block/electric_furnace_'+('on' if active else 'off'),'y':angle}
 block='machine_electric_furnace_off';write(f'assets/hbm/blockstates/{block}.json',{'variants':variants});write(f'assets/hbm/models/item/{block}.json',{'parent':'hbm:block/electric_furnace_off'})
 write(f'data/hbm/loot_table/blocks/{block}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'hbm:'+block}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 for path in ['data/create/tags/block/non_movable.json','data/minecraft/tags/block/mineable/pickaxe.json']:
  data=json.loads((RES/path).read_text());data['values']=sorted(set(data['values']+['hbm:'+block]));write(path,data)
 for lang,label in [('en_us','Electric Furnace'),('ru_ru','Электропечь')]:
  path=f'assets/hbm/lang/{lang}.json';data=json.loads((RES/path).read_text());data['block.hbm.'+block]=label;data['container.hbm.electric_furnace']=label;write(path,data)
 (ROOT/'electric-furnace-provenance.json').write_text(json.dumps({'upstream_commit':COMMIT,'source_assets_sha256':hashes,'scope':'Base electric furnace only. Merge old off/on block IDs into native lit property; upgrades, soot and dependent production recipe deferred.','source_recipe':'CraftingManager.java:248: BBB / WFW / RRR; B beryllium ingot, W cast copper plate, F furnace, R tungsten coil. Do not bypass missing coil production.'},indent=2)+'\n')
if __name__=='__main__':main()
