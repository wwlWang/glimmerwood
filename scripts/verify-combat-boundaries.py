"""Combat frame-rate, interruption, death and dimension trials through the integrated game."""
import argparse
import json
from devtools_client import DevClient

p=argparse.ArgumentParser();p.add_argument('build_id')
c=DevClient(p.parse_args().build_id)
def combat():return c.spirit()['server']['combat']
def health():
    targets=[e for e in c.action('nearby_entities')['entities'] if e['type']=='entity.minecraft.husk' and e['alive']]
    assert len(targets)==1,targets
    return targets[0]['health']
def fixture(slot=0):
    c.action('release_keys');c.action('close_screen');c.reset()
    if not c.spirit()['server']['form']:c.action('toggle_form');c.ticks(4)
    c.action('select_slot',slot=slot);c.ticks(3)
    c.command('kill @e[tag=orimod_combat_test]')
    c.command('summon husk .5 100 2.5 {Tags:["orimod_combat_test"],NoAI:1b,Silent:1b,PersistenceRequired:1b}')
    for attr,val in [('max_health',100),('armor',0),('knockback_resistance',1)]:
        c.command(f'attribute @e[tag=orimod_combat_test,limit=1] minecraft:generic.{attr} base set {val}')
    c.command('data merge entity @e[tag=orimod_combat_test,limit=1] {Health:100f}')
    c.ticks(2)
def key(at,name,duration=1):return [{'at':at,'key':'key.'+name,'down':True},{'at':at+duration,'key':'key.'+name,'down':False}]
def action(at,op,**kw):return {'at':at,'action':{'op':op,**kw}}
def script(steps):c.action('input_script',steps=steps)
def finish(name,before,**extra):
    after=c.finish(name,before,**extra)
    assert not c.request()['inputScriptError'],c.request()
    return after
try:
    c.ready()
    for cmd in ['gamemode survival','difficulty normal','gamerule naturalRegeneration false','gamerule doMobSpawning false',
                'gamerule keepInventory true','item replace entity @s hotbar.0 with orimod:spirit_blade','item replace entity @s hotbar.1 with orimod:spirit_bow']:
        c.command(cmd)
    for fps in [20,60,240]:
        c.action('settings',fpsLimit=fps,vsync=False);c.ticks(10);fixture();c.action('set_energy',amount=0)
        before=c.start();script(key(0,'attack')+key(6,'attack')+key(19,'attack'));c.ticks(50)
        after=finish('combo-fps-'+str(fps),before,targetHealth=health(),runtime=c.request())
        assert health()==84 and after['server']['combat']['bladeHits']-before['server']['combat']['bladeHits']==3,after
        fixture(slot=1);c.action('set_energy',amount=100);before=c.start();c.key('use',24);c.ticks(34)
        after=finish('bow-fps-'+str(fps),before,targetHealth=health(),runtime=c.request())
        assert health()==92 and after['server']['combat']['bowShots']==before['server']['combat']['bowShots']+1,after
        assert after['server']['combat']['energy']==88,after
    print('20/60/240 FPS limits: three hits and 16 damage; blade works at zero energy',flush=True)

    for reason,op in [('damage',action(8,'command',command='damage @s 1 minecraft:generic')),
                      ('item',action(8,'select_slot',slot=1)),('form',action(8,'toggle_form')),
                      ('inventory',action(8,'open_inventory')),
                      ('pause',action(8,'open_pause'))]:
        fixture();before=c.start()
        script(key(0,'attack')+key(6,'attack')+[op,action(20,'close_screen')]);c.ticks(32)
        after=finish('blade-cancel-'+reason,before,targetHealth=health())
        assert after['server']['combat']['bladeHits']-before['server']['combat']['bladeHits']==1,after
        assert after['server']['combat']['bladeStarts']-before['server']['combat']['bladeStarts']==1,after
        assert health()==96 and after['server']['combat']['mode']=='idle',after
    print('Buffered blade chain cancels on damage/item/form/inventory/pause',flush=True)

    fixture();before=c.start();script(key(0,'attack')+key(6,'attack')+key(19,'attack'));c.ticks(50)
    after=finish('combo-after-pause',before,targetHealth=health())
    assert health()==84 and after['server']['combat']['bladeHits']-before['server']['combat']['bladeHits']==3,after

    fixture(slot=1);c.action('set_energy',amount=100);before=c.start()
    script(key(0,'use',50)+[action(10,'open_pause'),action(25,'close_screen')]);c.ticks(65)
    after=finish('bow-pause-resume',before,targetHealth=health())
    assert after['server']['combat']['bowShots']==before['server']['combat']['bowShots'] and health()==100,after
    assert not after['server']['combat']['usingItem'],after

    for slot in [0,1]:
        fixture(slot);c.action('set_energy',amount=100);before=c.start()
        script((key(0,'attack')+key(6,'attack') if slot==0 else key(0,'use',80))+[action(8,'command',command='kill @s')]);c.ticks(18)
        after=finish('death-'+('blade' if slot==0 else 'bow'),before,targetHealth=health())
        assert after['server']['health']==0 and after['server']['combat']['mode']=='idle',after
        assert after['server']['combat']['bowShots']==before['server']['combat']['bowShots'],after
        c.action('release_keys');c.action('respawn');c.ticks(10)
        assert c.spirit()['server']['combat']['mode']=='idle'
    print('Death clears blade/bow actions; respawn starts idle',flush=True)

    # Prepare a safe Nether fixture. Cross-dimension commands intentionally test the lifecycle hook.
    c.command('execute in minecraft:the_nether run forceload add -3 -3 3 3');c.ticks(10)
    c.command('execute in minecraft:the_nether run fill -3 179 -3 3 179 3 stone')
    c.command('execute in minecraft:the_nether run fill -3 180 -3 3 183 3 air')
    for slot in [0,1]:
        fixture(slot);c.action('set_energy',amount=100);before=c.start()
        script((key(0,'attack')+key(6,'attack') if slot==0 else key(0,'use',80))+
               [action(8,'command',command='execute in minecraft:the_nether run tp @s .5 180 .5 0 0')]);c.ticks(35)
        c.action('release_keys');after=finish('dimension-'+('blade' if slot==0 else 'bow'),before,runtime=c.request())
        assert c.request()['player']['dimension']=='minecraft:the_nether'
        assert after['server']['combat']['mode']=='idle' and not after['server']['combat']['usingItem'],after
        assert after['server']['combat']['bowShots']==before['server']['combat']['bowShots'],after
        c.command('execute in minecraft:overworld run tp @s .5 100 .5 0 0');c.ticks(15)
    c.command('execute in minecraft:the_nether run forceload remove -3 -3 3 3')
    print('Dimension change clears blade/bow actions without firing',flush=True)
    c.records['status']='passed'
except Exception as exc:
    c.records['status']='failed';c.records['error']=repr(exc);raise
finally:
    c.save();c.action('release_keys');c.action('close_screen');c.action('settings',fpsLimit=260,vsync=False)
    c.command('gamerule keepInventory false');c.command('difficulty peaceful')
    if c.request().get('player',{}).get('health',0)>0:
        c.command('execute in minecraft:overworld run tp @s .5 100 .5 0 0');c.command('kill @e[tag=orimod_combat_test]')
