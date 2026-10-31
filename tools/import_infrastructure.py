#!/usr/bin/env python3
"""Reproduce the X5687 HE/fluid foundation. Original art; native material/state bindings."""
import argparse, hashlib, json, shutil, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; RES=ROOT/'src/main/resources'
COMMIT='23ef33447280d83d44c1f7bcd3853fe59c0ec1fc'
def write(path,data):
 p=RES/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('upstream',type=Path);upstream=parser.parse_args().upstream
 assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=upstream,text=True).strip()==COMMIT
 assets=upstream/'src/main/resources/assets/hbm'; hashes={}
 def read(path):
  content=(assets/path).read_bytes();hashes[path]=hashlib.sha256(content).hexdigest();return content.decode()
 def copy(source,target):
  p=RES/'assets/hbm'/target;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(assets/source,p);hashes[source]=hashlib.sha256(p.read_bytes()).hexdigest()
 for name in ['battery_socket','barrel']:copy(f'textures/gui/storage/gui_{name}.png',f'textures/gui/gui_{name}.png')
 packs=['battery_redstone','battery_lead','battery_lithium','capacitor_copper','capacitor_gold']
 for name in ['battery_socket']+packs:copy(f'textures/models/machines/{name}.png',f'textures/block/{name}.png')
 for name in ['barrel_steel','cable_neo','pipe_neo','pipe_neo_overlay']:copy(f'textures/blocks/{name}.png',f'textures/block/{name}.png')
 for source,name in [('battery_potato','battery_potato'),('cube_power','cube_power'),('battery_creative_new','battery_creative'),('fluid_identifier_multi','fluid_identifier_multi'),('fluid_identifier_overlay','fluid_identifier_overlay')]:copy(f'textures/items/{source}.png',f'textures/item/{name}.png')
 modeldir=RES/'assets/hbm/models/block';modeldir.mkdir(parents=True,exist_ok=True)
 def mesh(name,source,groups,transform=lambda v:v,offset=(0,0,0),overlays=None):
  lines=['# X5687 mesh: native origin/material/group adaptation only.',f'mtllib {name}.mtl'];faces=[];active=None
  for line in source.splitlines():
   fields=line.split()
   if not fields:continue
   k=fields[0]
   if k in ('v','vn'):
    xyz=transform(tuple(map(float,fields[1:4])))
    if k=='v':xyz=tuple(a+b for a,b in zip(xyz,offset))
    lines.append(k+' '+' '.join(format(v,'.8g') for v in xyz))
   elif k=='vt':lines.append(line)
   elif k in ('g','o'):active=fields[1]
   elif k=='f' and active in groups:faces.append((active,line))
  for group,face in faces:lines.extend(['usemtl '+groups[group],face])
  if overlays:
   for group,face in faces:lines.extend(['usemtl '+overlays,face])
  (modeldir/f'{name}.obj').write_text('\n'.join(lines)+'\n')
 def material(name,bindings):
  text=''
  for mat,texture,color in bindings:
   text+=f'newmtl {mat}\nKa 0 0 0\nKd '+ ' '.join(str(v) for v in color)+f'\nmap_Kd hbm:block/{texture}\n\n'
  (modeldir/f'{name}.mtl').write_text(text.rstrip()+'\n')
 def objmodel(name,particle,scale=.65):
  write(f'assets/hbm/models/block/{name}.json',{'loader':'neoforge:obj','model':f'hbm:models/block/{name}.obj','automatic_culling':False,'flip_v':True,'ambientocclusion':False,'render_type':'minecraft:cutout','textures':{'particle':f'hbm:block/{particle}'},'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[scale]*3},'ground':{'translation':[0,3,0],'scale':[.3]*3},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.5]*3},'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.4]*3}}})
 def itemparent(item,parent):write(f'assets/hbm/models/item/{item}.json',{'parent':f'hbm:block/{parent}'})
 battery=read('models/machines/battery.obj');kinds=['empty']+packs+['battery_potato','battery_creative','cube_power']
 for kind in kinds:
  name='socket_'+kind;groups={'Socket':'socket'};bindings=[('socket','battery_socket',(1,1,1))]
  if kind in packs:groups['Capacitor' if kind.startswith('capacitor') else 'Battery']='pack';bindings.append(('pack',kind,(1,1,1)))
  mesh(name,battery,groups,lambda v:(-v[2],v[1],v[0]));material(name,bindings);objmodel(name,'battery_socket',.3)
 for kind in packs:
  name='pack_'+kind;mesh(name,battery,{'Capacitor' if kind.startswith('capacitor') else 'Battery':'pack'},offset=(.5,0,.5));material(name,[('pack',kind,(1,1,1))]);objmodel(name,kind,.4);itemparent(kind,name)
 variants={}
 for kind in kinds:
  for facing,angle in [('south',0),('east',270),('north',180),('west',90)]:variants[f'part=0,facing={facing},pack={kind}']={'model':f'hbm:block/socket_{kind}','y':angle}
 for part in range(1,8):variants[f'part={part}']={'model':'hbm:block/infrastructure_empty'}
 write('assets/hbm/blockstates/machine_battery_socket.json',{'variants':variants});write('assets/hbm/models/block/infrastructure_empty.json',{'textures':{'particle':'hbm:block/battery_socket'},'elements':[]});itemparent('machine_battery_socket','socket_empty')
 barrel=read('models/blocks/barrel.obj');mesh('barrel_steel',barrel,{'Barrel':'main'},offset=(.5,0,.5));material('barrel_steel',[('main','barrel_steel',(1,1,1))]);objmodel('barrel_steel','barrel_steel');write('assets/hbm/blockstates/barrel_steel.json',{'variants':{'':{'model':'hbm:block/barrel_steel'}}});itemparent('barrel_steel','barrel_steel')
 cable=read('models/blocks/cable_neo.obj');pipe=read('models/blocks/pipe_neo.obj');colors={'none':0x888888,'water':0x3333ff,'lava':0xff3300};cvariants={};pvariants={}
 for mask in range(64):
  groups={48:['CX'],12:['CY'],3:['CZ']}.get(mask,['Core']+[g for b,g in [(32,'posX'),(16,'negX'),(8,'posY'),(4,'negY'),(2,'negZ'),(1,'posZ')] if mask&b])
  name=f'cable_{mask}';mesh(name,cable,{g:'main' for g in groups},offset=(.5,.5,.5));material(name,[('main','cable_neo',(1,1,1))]);objmodel(name,'cable_neo');cvariants[f'mask={mask}']={'model':f'hbm:block/{name}'}
  arms=[(32,'pX'),(16,'nX'),(8,'pY'),(4,'nY'),(2,'nZ'),(1,'pZ')]
  if mask==0:groups=[g for _,g in arms]
  elif mask in (32,16):groups=['pX','nX']
  elif mask in (8,4):groups=['pY','nY']
  elif mask in (2,1):groups=['pZ','nZ']
  else:
   groups=[g for b,g in arms if mask&b]
   groups += [g for b,g in [(32|8|2,'ppn'),(32|8|1,'ppp'),(16|8|2,'npn'),(16|8|1,'npp'),(32|4|2,'pnn'),(32|4|1,'pnp'),(16|4|2,'nnn'),(16|4|1,'nnp')] if not mask&b]
  for fluid,color in colors.items():
   name=f'pipe_{fluid}_{mask}';mesh(name,pipe,{g:'base' for g in groups},offset=(.5,.5,.5),overlays='overlay');material(name,[('base','pipe_neo',(1,1,1)),('overlay','pipe_neo_overlay',tuple(((color>>n)&255)/255 for n in (16,8,0)))]);objmodel(name,'pipe_neo');pvariants[f'mask={mask},fluid={fluid}']={'model':f'hbm:block/{name}'}
 write('assets/hbm/blockstates/red_cable.json',{'variants':cvariants});write('assets/hbm/blockstates/fluid_duct_neo.json',{'variants':pvariants});itemparent('red_cable','cable_51');itemparent('fluid_duct_neo','pipe_none_51')
 for name in ['battery_potato','cube_power','battery_creative','fluid_identifier_multi']:
  textures={'layer0':f'hbm:item/{name}'}
  if name=='fluid_identifier_multi':textures['layer1']='hbm:item/fluid_identifier_overlay'
  write(f'assets/hbm/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':textures})
 for block in ['machine_battery_socket','red_cable','fluid_duct_neo','barrel_steel']:
  entry={'type':'minecraft:item','name':f'hbm:{block}'}
  if block=='barrel_steel':entry['functions']=[{'function':'minecraft:copy_components','source':'block_entity','include':['hbm:barrel_state','minecraft:max_stack_size','minecraft:custom_name']}]
  write(f'data/hbm/loot_table/blocks/{block}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 for block,pattern,key,count in [('machine_battery_socket',['PRP'],{'P':'c:plates/steel','R':'c:ingots/red_copper'},1),('barrel_steel',['IPI','I I','IPI'],{'I':'c:plates/steel','P':'c:ingots/steel'},1),('fluid_duct_neo',['SAS','   ','SAS'],{'S':'c:plates/steel','A':'c:plates/aluminium'},8)]:
  # Socket recipe is original but remains gated by future red copper production.
  write(f'data/hbm/recipe/infrastructure/{block}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'tag':v} for k,v in key.items()},'result':{'id':f'hbm:{block}','count':count}})
 for path,values in [('data/minecraft/tags/block/mineable/pickaxe.json',['hbm:'+b for b in ['machine_battery_socket','red_cable','fluid_duct_neo','barrel_steel']]),('data/create/tags/block/non_movable.json',['hbm:'+b for b in ['machine_battery_socket','red_cable','fluid_duct_neo','barrel_steel']])]:
  p=RES/path;data=json.loads(p.read_text()) if p.exists() else {'replace':False,'values':[]};data['values']=sorted(set(data['values']+values));write(path,data)
 names={'machine_battery_socket':('Battery Socket','Аккумуляторная станция'),'red_cable':('Red Copper Cable','Красномедный кабель'),'fluid_duct_neo':('Fluid Duct','Жидкостная труба'),'barrel_steel':('Steel Barrel','Стальная бочка'),'battery_redstone':('Redstone Battery','Редстоуновый аккумулятор'),'battery_lead':('Lead-Acid Battery','Свинцовый аккумулятор'),'battery_lithium':('Lithium Battery','Литиевый аккумулятор'),'capacitor_copper':('Copper Capacitor','Медный конденсатор'),'capacitor_gold':('Gold Capacitor','Золотой конденсатор'),'battery_potato':('Potato Battery','Картофельная батарейка'),'battery_creative':('Creative Battery','Творческий аккумулятор'),'cube_power':('Power Cube','Энергокуб'),'fluid_identifier_multi':('Fluid Identifier','Идентификатор жидкости')}
 texts={
 'tooltip.hbm.energy_stored':('%s / %s HE','%s / %s HE'), 'tooltip.hbm.energy_rates':('Charge: %s HE/t; discharge: %s HE/t','Заряд: %s HE/т; разряд: %s HE/т'),
 'tooltip.hbm.energy_delta':('Change over 20 ticks: %s HE','Изменение за 20 тиков: %s HE'),
 'tooltip.hbm.low_mode':('Redstone LOW: %s (click)','Без сигнала: %s (нажми)'), 'tooltip.hbm.high_mode':('Redstone HIGH: %s (click)','С сигналом: %s (нажми)'), 'tooltip.hbm.priority':('Priority: %s (click)','Приоритет: %s (нажми)'),
 'mode.hbm.0':('Input','Приём'),'mode.hbm.1':('Buffer','Буфер'),'mode.hbm.2':('Output','Выдача'),'mode.hbm.3':('Off','Выключено'),
 'priority.hbm.1':('Low','Низкий'),'priority.hbm.2':('Normal','Обычный'),'priority.hbm.3':('High','Высокий'),
 'fluid.hbm.none':('None','Не выбрана'),
 'tooltip.hbm.identifier_primary':('Primary: %s','Основная: %s'),'tooltip.hbm.identifier_secondary':('Secondary: %s','Запасная: %s'),
 'tooltip.hbm.identifier_controls':('Shift + right-click air: select; right-click: swap; Shift + right-click block: apply','Shift + ПКМ в воздух: выбор; ПКМ: обмен; Shift + ПКМ по блоку: применить'),
 'tooltip.hbm.identifier_flush':('Changing a barrel fluid type discards its contents (X5687 rule)','Смена типа жидкости бочки удаляет её содержимое (правило X5687)'),
 'tooltip.hbm.barrel_contents':('%s / %s mB — %s','%s / %s мВ — %s'),'tooltip.hbm.barrel_fluids':('Set water or lava with a fluid identifier','Выбери воду или лаву идентификатором'),
 'tooltip.hbm.tikite_processing':('Trixite ore needs a centrifuge or crystallizer, not a furnace','Трикситовая руда перерабатывается в центрифуге или кристаллизаторе'),
 'tooltip.hbm.tikite_processing_pending':('These processing machines are planned for a later stage','Эти перерабатывающие машины будут перенесены на следующих этапах')}
 for index,lang in enumerate(['en_us','ru_ru']):
  path=f'assets/hbm/lang/{lang}.json';data=json.loads((RES/path).read_text())
  for name,pair in names.items():data[('block' if name in ['machine_battery_socket','red_cable','fluid_duct_neo','barrel_steel'] else 'item')+'.hbm.'+name]=pair[index]
  data.update({k:v[index] for k,v in texts.items()});data['container.hbm.battery_socket']=names['machine_battery_socket'][index];data['container.hbm.barrel_steel']=names['barrel_steel'][index];data['container.hbm.fluid_identifier']=names['fluid_identifier_multi'][index];write(path,data)
 (ROOT/'infrastructure-provenance.json').write_text(json.dumps({'upstream_commit':COMMIT,'source_assets_sha256':hashes,'mesh_adaptation':'Preserved source UVs/faces; rotated socket origin, translated block origins, selected original visible groups; bound native MTLs and per-fluid overlay colors. Creative horse/beam effects deferred.','deferred_recipes':['Cable needs red-copper fine wire and polymer plates','Battery recipes require polymers, chemical plant or assembler','Identifier requires vacuum-tube circuit','Potato battery requires fine wires'],'reachable_recipes':['Steel barrel: 6 steel plates + 2 steel ingots','8 fluid ducts: 4 steel plates + 2 aluminium plates']},ensure_ascii=False,indent=2)+'\n')
if __name__=='__main__':main()
