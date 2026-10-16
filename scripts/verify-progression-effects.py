"""Measure actual shard effects, hostile drops and death persistence in the growth fixture."""
import argparse
from devtools_client import DevClient

parser=argparse.ArgumentParser()
parser.add_argument('build_id')
c=DevClient(parser.parse_args().build_id)
bits={'capacity':1,'recovery':2,'gathering':4,'resilience':8,'air_control':16}

def p(): return c.action('progress_state')['server']
def equip(*names):
    mask=sum(bits[name] for name in names)
    current=p()['progress']['equippedShards']
    for name,bit in bits.items():
        if current&bit and not mask&bit:
            c.action('progress_action',action='unequip',target=name);c.ticks(3)
    current=p()['progress']['equippedShards']
    for name,bit in bits.items():
        if mask&bit and not current&bit:
            c.action('progress_action',action='equip',target=name);c.ticks(3)
    assert p()['progress']['equippedShards']==mask
def record(name,**data):
    c.records['steps'][name]={'progress':p(),'spirit':c.spirit(),**data}
    c.save()

try:
    c.ready()
    c.action('release_keys')
    for command in ['difficulty normal','gamerule naturalRegeneration false','gamerule doMobSpawning false',
                    'kill @e[tag=orimod_growth_test]']:
        c.command(command)
    rates=[]
    for names in [(),('recovery',)]:
        equip(*names)
        c.action('set_energy',amount=0)
        before=p()
        c.ticks(80)
        after=p()
        rates.append(after['energy'])
        record('regen-'+('boosted' if names else 'base'),before=before,after=after)
    assert 8<=rates[0]<=13 and 1.3<=rates[1]/rates[0]<=1.8,rates
    print('Energy recovery measured: '+str(rates),flush=True)

    equip('resilience')
    c.reset()
    c.action('set_energy',amount=20)
    c.command('damage @s 4 minecraft:generic')
    c.ticks(85)
    assert c.spirit()['server']['health']==17,c.spirit()
    record('resilience-heal')
    c.command('damage @s 4 minecraft:generic')
    c.ticks(85)
    assert c.spirit()['server']['health']==13,c.spirit()
    record('resilience-cooldown')
    c.ticks(120)
    c.command('effect give @s instant_health 1 5 true')
    c.ticks(3)
    c.command('damage @s 4 minecraft:generic')
    assert c.spirit()['server']['health']==16,c.spirit()
    c.ticks(76)
    c.action('set_energy',amount=0)
    c.ticks(10)
    assert c.spirit()['server']['health']==16,c.spirit()
    record('resilience-no-energy')
    print('Resilience heals once, respects cooldown and requires energy',flush=True)

    trajectories=[]
    for names in [(),('air_control',)]:
        equip(*names)
        c.reset(y=113)
        before=c.start()
        c.key('forward',8)
        c.ticks(8)
        after=c.finish('air-steering-'+('shard' if names else 'base'),before)
        distance=after['client']['position'][2]-before['client']['position'][2]
        trajectories.append(distance)
        c.reset()
    assert 1.12<=trajectories[1]/trajectories[0]<=1.45,trajectories
    print('Air steering measured: '+str(trajectories),flush=True)

    equip()
    c.reset()
    c.action('select_slot',slot=1)
    c.command('summon husk .5 100 2.3 {Tags:["orimod_growth_test"],NoAI:1b,Silent:1b,PersistenceRequired:1b}')
    c.command('attribute @e[tag=orimod_growth_test,type=husk,limit=1] minecraft:generic.knockback_resistance base set 1')
    c.ticks(3)
    before=p()
    attacks=0
    for _ in range(8):
        targets=[e for e in c.action('nearby_entities')['entities'] if e['type']=='entity.minecraft.husk' and e['alive']]
        if not targets: break
        c.action('attack_entity',id=targets[0]['id']);attacks+=1
        c.ticks(15)
    assert attacks>0
    c.key('forward',18)
    c.ticks(22)
    after=p()
    assert after['progress']['light']==before['progress']['light']+2,(before,after)
    record('hostile-light-drop',before=before,after=after,attacks=attacks)
    print('Actual melee kill drops two light; pickup converts it with a full inventory',flush=True)

    c.reset()
    before=p()
    c.command('summon item .5 100.3 3.8 {Tags:["orimod_growth_test"],Item:{id:"orimod:spirit_light",count:3},PickupDelay:0s}')
    c.ticks(22)
    assert p()['progress']['light']==before['progress']['light']
    equip('gathering')
    c.ticks(35)
    assert p()['progress']['light']==before['progress']['light']+3,p()
    assert abs(c.spirit()['server']['position'][2]-.5)<.01
    record('gathering-radius',before=before)
    print('Gathering attracts light while the player stays still',flush=True)

    equip('capacity','recovery','air_control')
    before=p()
    c.command('spawnpoint @s 8 100 8')
    c.command('gamerule keepInventory false')
    c.command('kill @s')
    c.ticks(6)
    assert c.spirit()['server']['health']==0
    c.action('respawn')
    c.ticks(12)
    after=p()
    assert before['progress']==after['progress'] and before['abilities']==after['abilities'],(before,after)
    assert not c.spirit()['server']['form'] and not after['inventory'],after
    c.action('progress_action',action='claim',target='blade')
    c.ticks(3)
    assert not p()['inventory']
    record('death-keeps-progress-not-items',before=before,after=after)
    print('Death keeps progression and claims, restores human form, and drops ordinary inventory',flush=True)
    c.records['status']='passed'
    c.save()
except Exception as error:
    c.records['status']='failed';c.records['error']=repr(error);c.save();raise
finally:
    c.action('release_keys')
    c.command('gamerule naturalRegeneration true')
    c.command('difficulty peaceful')
print(c.out,flush=True)
