"""Operate real attack/use key mappings and inspect authoritative damage, energy and timelines."""
import argparse
import json
from devtools_client import DevClient

p=argparse.ArgumentParser(); p.add_argument('build_id')
c=DevClient(p.parse_args().build_id)

def combat(): return c.spirit()['server']['combat']
def target_health():
    targets=[e for e in c.action('nearby_entities')['entities'] if e['type']=='entity.minecraft.husk' and e['alive']]
    assert len(targets)==1,targets
    return targets[0]['health']
def fixture(z=2.5):
    c.action('release_keys');c.action('close_screen')
    c.command('kill @e[tag=orimod_combat_test]')
    c.command('kill @e[type=arrow,tag=orimod_spirit_arrow]')
    c.command('fill -2 100 1 2 103 3 air')
    c.reset()
    c.command(f'summon husk .5 100 {z} {{Tags:["orimod_combat_test"],NoAI:1b,Silent:1b,PersistenceRequired:1b}}')
    for attr,val in [('max_health',100),('armor',0),('knockback_resistance',1)]:
        c.command(f'attribute @e[tag=orimod_combat_test,limit=1] minecraft:generic.{attr} base set {val}')
    c.command('data merge entity @e[tag=orimod_combat_test,limit=1] {Health:100f}')
    c.ticks(3)
    assert target_health()==100
def schedule(presses):
    steps=[]
    for at,name,duration in presses:
        steps.extend([{'at':at,'key':'key.'+name,'down':True},{'at':at+duration,'key':'key.'+name,'down':False}])
    c.action('input_script',steps=steps)
def record(name,before,health_before=None):
    after=c.finish(name,before)
    c.records['steps'][name]['targetHealth']=target_health()
    if health_before is not None:c.records['steps'][name]['targetBefore']=health_before
    c.save()
    return after

try:
    c.ready();c.action('close_screen');c.action('release_keys')
    for cmd in ['gamemode survival','difficulty normal','gamerule naturalRegeneration false','gamerule doMobSpawning false',
                'fill -12 99 -12 12 99 15 moss_block','fill -3 100 -3 3 104 12 air',
                'item replace entity @s hotbar.0 with orimod:spirit_blade','item replace entity @s hotbar.1 with orimod:spirit_bow']:
        c.command(cmd)
    if not c.spirit()['server']['form']:c.action('toggle_form');c.ticks(4)
    c.action('select_slot',slot=0);c.ticks(3)
    fixture();before=c.start();schedule([(0,'attack',1),(6,'attack',1),(19,'attack',1)]);c.ticks(50)
    after=record('three-strike',before,100)
    assert after['server']['combat']['bladeStarts']-before['server']['combat']['bladeStarts']==3,after
    assert after['server']['combat']['bladeHits']-before['server']['combat']['bladeHits']==3,after
    assert target_health()==84,target_health()
    trace=json.loads((c.out/'three-strike.json').read_text())['samples']
    stages={s['state']['combat']['stage'] for s in trace if s['side']=='server'}
    assert {1,2,3}<=stages,stages
    print('Three strikes: exactly 3 server hits, 16 damage',flush=True)

    fixture();before=c.start();schedule([(0,'attack',1),(1,'attack',1)]);c.ticks(24)
    after=record('too-early-input',before)
    assert after['server']['combat']['bladeStarts']-before['server']['combat']['bladeStarts']==1,after
    assert target_health()==96

    fixture();before=c.start();schedule([(0,'attack',1),(7,'orimod.dash',1)]);c.ticks(24)
    after=record('recovery-dash-cancel',before)
    assert after['server']['combat']['lastCancel']=='movement',after
    assert after['server']['movement']['dashes']>before['server']['movement']['dashes']

    fixture();before=c.start();schedule([(0,'attack',1),(1,'orimod.dash',1)]);c.ticks(24)
    after=record('windup-dash-blocked',before)
    assert after['server']['movement']['dashes']==before['server']['movement']['dashes'],after

    fixture(z=2.8);c.command('fill -2 100 1 2 103 1 stone');before=c.start()
    schedule([(0,'attack',1),(6,'attack',1),(19,'attack',1)]);c.ticks(50)
    after=record('blade-solid-wall',before)
    assert target_health()==100
    print('Blade buffering and recovery cancellation passed',flush=True)

    for name,duration,shots in [('bow-too-short',2,0),('bow-min-charge',7,1),('bow-full-charge',24,1)]:
        fixture(z=8.5);c.action('select_slot',slot=1);c.ticks(3);c.action('set_energy',amount=100)
        before=c.start();c.key('use',duration);c.ticks(duration+12)
        after=record(name,before)
        assert after['server']['combat']['bowShots']-before['server']['combat']['bowShots']==shots,after
        assert after['server']['combat']['energy']==100-12*shots,after
        assert target_health()<100 if shots else target_health()==100
    assert c.records['steps']['bow-full-charge']['targetHealth']<c.records['steps']['bow-min-charge']['targetHealth']

    fixture(z=8.5);c.action('set_energy',amount=0);before=c.start();c.key('use',20);c.ticks(25)
    after=record('bow-zero-energy',before)
    assert after['server']['combat']['bowShots']==before['server']['combat']['bowShots'] and target_health()==100,after

    fixture(z=8.5);c.command('fill -2 100 3 2 103 3 stone');c.action('set_energy',amount=100)
    before=c.start();c.key('use',22);c.ticks(30);after=record('bow-solid-wall',before)
    assert after['server']['combat']['bowShots']==before['server']['combat']['bowShots']+1 and target_health()==100,after
    assert after['server']['combat']['energy']==88
    print('Bow short/min/full charge, zero energy and wall collision passed',flush=True)

    for reason in ['menu','damage','form','item']:
        fixture(z=8.5);c.action('select_slot',slot=1);c.ticks(3);c.action('set_energy',amount=100)
        before=c.start();c.key('use',80);c.ticks(10)
        assert combat()['mode']=='bow',combat()
        if reason=='menu':c.action('open_inventory')
        elif reason=='damage':c.command('damage @s 1 minecraft:generic')
        elif reason=='form':c.action('toggle_form')
        else:c.action('select_slot',slot=0)
        c.ticks(5);c.action('release_keys');c.action('close_screen');c.ticks(10)
        after=record('bow-cancel-'+reason,before)
        assert after['server']['combat']['bowShots']==before['server']['combat']['bowShots'] and target_health()==100,after
        assert not after['server']['combat']['usingItem'] and after['server']['combat']['mode']=='idle',after
        if not after['server']['form']:c.action('toggle_form');c.ticks(4)
    print('Bow menu/damage/form/item cancellation passed',flush=True)
    c.records['status']='passed'
except Exception as exc:
    c.records['status']='failed';c.records['error']=repr(exc)
    raise
finally:
    c.save()
    c.action('release_keys');c.action('close_screen');c.command('kill @e[tag=orimod_combat_test]')
    c.command('fill -2 100 1 2 103 3 air');c.reset();c.command('difficulty peaceful')
