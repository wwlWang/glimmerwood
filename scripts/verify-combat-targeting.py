"""Validate directional hits, multiple victims and energy loss during a live bow charge."""
import argparse
from devtools_client import DevClient

p=argparse.ArgumentParser();p.add_argument('build_id')
c=DevClient(p.parse_args().build_id)
def enemies():return [e for e in c.action('nearby_entities')['entities'] if e['type']=='entity.minecraft.husk' and e['alive']]
def spawn(x,y,z):
    c.command(f'summon husk {x} {y} {z} {{Tags:["orimod_combat_test","orimod_combat_new"],NoAI:1b,Silent:1b,PersistenceRequired:1b}}')
    for attr,val in [('max_health',100),('armor',0),('knockback_resistance',1)]:
        c.command(f'attribute @e[tag=orimod_combat_new,limit=1] minecraft:generic.{attr} base set {val}')
    c.command('data merge entity @e[tag=orimod_combat_new,limit=1] {Health:100f}')
    c.command('tag @e[tag=orimod_combat_new] remove orimod_combat_new')
def reset(slot=0,yaw=0,pitch=0):
    c.action('release_keys');c.command('kill @e[tag=orimod_combat_test]');c.reset(yaw=yaw,pitch=pitch)
    c.action('select_slot',slot=slot);c.ticks(3)
def record(name,before):
    after=c.finish(name,before,entities=enemies())
    return after
try:
    c.ready();c.command('difficulty normal')
    reset()
    for pos in [(-.2,100,2.3),(1.2,100,2.3),(.5,100,-1.5),(.5,100,4.5)]:spawn(*pos)
    c.ticks(3);assert all(e['health']==100 for e in enemies()),enemies()
    before=c.start();c.key('attack',1);c.ticks(18);after=record('cone-and-deduplication',before)
    targets=enemies();assert len(targets)==4,targets
    for e in targets:
        assert e['health']==(96 if 1<e['position'][2]<3 else 100),targets
    assert after['server']['combat']['bladeHits']==before['server']['combat']['bladeHits']+2,after

    for name,yaw,pitch,position in [('diagonal-left',-45,0,(6.5,100,6.5)),
                                   ('diagonal-right',45,0,(-5.5,100,6.5)),
                                   ('upward',-45,-25,(6.5,103,6.5))]:
        reset(slot=1,yaw=yaw,pitch=pitch)
        c.command(f'setblock {int(position[0])} {position[1]-1} {int(position[2])} stone')
        spawn(*position);c.ticks(3);c.action('set_energy',amount=100)
        before=c.start();c.key('use',24);c.ticks(35);after=record(name,before)
        assert enemies()[0]['health']==92,enemies()
        assert after['server']['combat']['energy']==88,after
    print('Blade cone/deduplication and diagonal/upward bow targeting passed',flush=True)

    reset(slot=1);spawn(.5,100,8.5);c.action('set_energy',amount=12)
    before=c.start();c.key('use',50);c.ticks(10);c.action('set_energy',amount=0);c.action('release_keys');c.ticks(8)
    after=record('energy-lost-during-charge',before)
    assert after['server']['combat']['bowShots']==before['server']['combat']['bowShots'] and enemies()[0]['health']==100,after
    assert after['server']['combat']['energy']==0,after

    reset();c.action('set_energy',amount=0);before=c.start()
    c.action('input_script',steps=[{'at':0,'key':'key.jump','down':True},{'at':1,'key':'key.jump','down':False},
                                  {'at':6,'key':'key.jump','down':True},{'at':7,'key':'key.jump','down':False},
                                  {'at':10,'key':'key.orimod.dash','down':True},{'at':11,'key':'key.orimod.dash','down':False}])
    c.ticks(30);after=record('zero-energy-movement',before)
    assert after['server']['movement']['jumps']==before['server']['movement']['jumps']+2,after
    assert after['server']['movement']['dashes']==before['server']['movement']['dashes']+1,after
    print('Energy loss prevents release; zero energy preserves double jump and dash',flush=True)
    c.records['status']='passed'
except Exception as exc:
    c.records['status']='failed';c.records['error']=repr(exc);raise
finally:
    c.save();c.action('release_keys');c.command('kill @e[tag=orimod_combat_test]');c.reset();c.command('difficulty peaceful')
