#!/usr/bin/env python3
"""Small X5687 direct-crafting slice: insulator and two existing battery types."""
import argparse,hashlib,json,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources';COMMIT='23ef33447280d83d44c1f7bcd3853fe59c0ec1fc'
def write(path,value):
 p=RES/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('upstream',type=Path);upstream=parser.parse_args().upstream
 assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=upstream,text=True).strip()==COMMIT
 source=upstream/'src/main/resources/assets/hbm/textures/items/plate_polymer.png';target=RES/'assets/hbm/textures/item/plate_polymer.png';shutil.copyfile(source,target)
 write('assets/hbm/models/item/plate_polymer.json',{'parent':'minecraft:item/generated','textures':{'layer0':'hbm:item/plate_polymer'}})
 recipes={
 'insulator_bricks':(['BB'],{'B':{'item':'minecraft:brick'}},'hbm:plate_polymer',4,109),
 'insulator_wool':(['SWS'],{'S':{'item':'minecraft:string'},'W':{'tag':'minecraft:wool'}},'hbm:plate_polymer',4,108),
 'insulator_asbestos':(['DD'],{'D':{'tag':'c:ingots/asbestos'}},'hbm:plate_polymer',16,107),
 'battery_redstone':(['IRI','PRP','IRI'],{'I':{'tag':'c:plates/iron'},'R':{'item':'minecraft:redstone_block'},'P':{'item':'hbm:plate_polymer'}},'hbm:battery_redstone',1,278),
 'capacitor_copper':(['IRI','PRP','IRI'],{'I':{'tag':'c:plates/steel'},'R':{'tag':'c:storage_blocks/copper'},'P':{'item':'hbm:plate_polymer'}},'hbm:capacitor_copper',1,279)}
 for name,(pattern,key,result,count,line) in recipes.items():write(f'data/hbm/recipe/components/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':key,'result':{'id':result,'count':count}})
 for lang,label in [('en_us','Insulator'),('ru_ru','Изолятор')]:
  path=f'assets/hbm/lang/{lang}.json';data=json.loads((RES/path).read_text());data['item.hbm.plate_polymer']=label;write(path,data)
 (ROOT/'insulator-provenance.json').write_text(json.dumps({'upstream_commit':COMMIT,'source_assets_sha256':{'textures/items/plate_polymer.png':hashlib.sha256(source.read_bytes()).hexdigest()},'recipes':{name:{'source':'CraftingManager.java:'+str(v[-1]),'output':v[2],'count':v[3]} for name,v in recipes.items()},'omissions':'Plastic/rubber/fiber inputs await their materials. The source nether-brick alternative uses ingotNetherBrick, while the canonical key is ingotBrickNether; no alias or repaired recipe is silently introduced. Batteries craft empty.'},indent=2)+'\n')
if __name__=='__main__':main()
