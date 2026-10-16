"""Live impact, liquid, low-clearance and repeat-target boundary checks."""
import argparse
from devtools_client import DevClient

parser = argparse.ArgumentParser()
parser.add_argument('build_id')
c = DevClient(parser.parse_args().build_id)

def stomp():
    c.action('input_script', steps=[{'at':0,'key':'key.sneak','down':True},
        {'at':1,'key':'key.orimod.dash','down':True}, {'at':3,'key':'key.orimod.dash','down':False},
        {'at':4,'key':'key.sneak','down':False}])

def entities():
    return c.action('nearby_entities')['entities']

try:
    c.ready()
    for command in ['difficulty normal', 'gamerule doMobSpawning false', 'gamerule naturalRegeneration false', 'gamerule fallDamage true',
                    'fill -4 100 -4 4 130 8 air', 'fill -6 99 -6 6 99 9 moss_block',
                    'kill @e[tag=orimod_surface_test]']:
        c.command(command)
    c.command('summon husk 2 100 .5 {Tags:["orimod_surface_test"],NoAI:1b,Silent:1b,PersistenceRequired:1b}')
    c.command('summon husk 5 100 .5 {Tags:["orimod_surface_test"],NoAI:1b,Silent:1b,PersistenceRequired:1b}')
    c.command('summon cow -1.5 100 .5 {Tags:["orimod_surface_test"],NoAI:1b,Silent:1b,PersistenceRequired:1b}')
    c.reset(y=112)
    before = c.start()
    original = entities()
    floor = c.action('inspect_block',x=0,y=99,z=0)
    stomp()
    c.ticks(16)
    impacted = entities()
    floor_after = c.action('inspect_block',x=0,y=99,z=0)
    after = c.finish('stomp-area',before,original=original,impacted=impacted,floor=floor,floor_after=floor_after)
    near = next(e for e in original if e['type']=='entity.minecraft.husk' and e['position'][0]<3)
    actual = next(e for e in impacted if e['id']==near['id'])
    assert 13 <= actual['health'] < 20, actual
    for e in impacted:
        if e['id'] != near['id']:
            initial = next((v for v in original if v['id']==e['id']), None)
            if initial: assert e['health']==initial['health'], e
    assert floor==floor_after, (floor,floor_after)
    assert after['server']['movement']['stompImpacts']-before['server']['movement']['stompImpacts']==1, after
    print('Stomp impact: nearby hostile damaged once; friendly, distant target and terrain preserved',flush=True)
    c.command('kill @e[tag=orimod_surface_test]')

    for block in ['stone_slab[type=bottom]', 'stone_stairs[facing=north]']:
        c.command('setblock 0 100 0 '+block)
        c.reset(y=110)
        before=c.start()
        stomp()
        c.ticks(18)
        after=c.finish('stomp-'+block.split('[')[0],before)
        assert after['server']['health']==20 and 100.5<=after['server']['position'][1]<=101,after
        assert after['server']['movement']['stompImpacts']-before['server']['movement']['stompImpacts']==1,after
        c.command('setblock 0 100 0 air')
        print('Stomp collision: '+block,flush=True)

    c.reset()
    c.command('tp @s .5 101.8 .5')
    before=c.start()
    assert before['server']['position'][1]>100.3,before
    stomp()
    c.ticks(10)
    after=c.finish('stomp-too-low',before)
    assert after['server']['movement']['stomps']==before['server']['movement']['stomps'],after
    print('Stomp below minimum clearance rejected',flush=True)

    c.command('fill -2 100 -2 2 102 2 water')
    c.reset(y=113)
    before=c.start()
    stomp()
    c.ticks(35)
    after=c.finish('stomp-water',before)
    assert after['server']['movement']['stomps']>before['server']['movement']['stomps'],after
    assert after['server']['movement']['stompImpacts']==before['server']['movement']['stompImpacts'],after
    assert after['server']['movement']['mode']!='stomp' and after['server']['health']==20,after
    c.command('fill -2 100 -2 2 102 2 air')
    print('Water cancels stomp without ground impact',flush=True)

    c.reset()
    c.command('setblock 0 101 4 orimod:bash_anchor')
    before=c.start()
    c.key('orimod.bash',20)
    c.ticks(2)
    held=c.spirit()
    assert held['server']['movement']['mode']=='bash_aim',held
    c.key('sneak',2)
    c.ticks(3)
    c.action('release_keys')
    c.ticks(1)
    c.key('orimod.bash',8)
    c.ticks(3)
    after=c.finish('bash-cancel-repeat',before,held=held)
    assert after['server']['movement']['mode']!='bash_aim',after
    assert after['server']['movement']['cancelledBashes']-before['server']['movement']['cancelledBashes']==1,after
    c.action('release_keys')
    c.command('setblock 0 101 4 air')
    print('Cancelled Bash target cannot immediately be reused',flush=True)
    c.records['status']='passed'
    c.save()
except Exception as error:
    c.records['status']='failed'
    c.records['error']=repr(error)
    c.save()
    raise
finally:
    c.action('release_keys')
    c.command('tp @s .5 100 .5')
    c.command('gamerule fallDamage true')
    c.command('gamerule naturalRegeneration true')
    c.command('kill @e[tag=orimod_surface_test]')
    c.command('difficulty peaceful')
print(c.out,flush=True)
