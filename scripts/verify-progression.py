"""Operate the Chinese growth screen and verify server-owned purchases and claim rules.

Fixtures grant light and fill inventory deliberately; this is not the command-free survival acceptance run.
"""
import argparse
import json
import time
from devtools_client import DevClient

parser=argparse.ArgumentParser()
parser.add_argument('build_id')
parser.add_argument('world')
args=parser.parse_args()
c=DevClient(args.build_id)

def progress(): return c.action('progress_state')
def p(): return progress()['server']
def gui(): return c.action('gui_state')
def click(widget):
    c.action('gui_click',x=widget['x']+widget['width']/2,y=widget['y']+widget['height']/2)
    c.ticks(3)
def tab(text):
    click(next(w for w in gui()['widgets'] if w['text']==text))
def choose(title):
    for _ in range(8):
        listing=next(w for w in gui()['widgets'] if w['type']=='ChoiceList')
        index=next(i for i,v in enumerate(listing['entries']) if v.startswith(title+'.'))
        y=listing['y']+4+index*25-listing['scroll']+12
        if listing['y']+4 <= y < listing['y']+listing['height']-4:
            c.action('gui_click',x=listing['x']+24,y=y)
            c.ticks(2)
            return
        c.action('gui_scroll',x=listing['x']+24,y=listing['y']+20,amount=-2 if y>listing['y'] else 2)
    raise AssertionError('Entry could not be made visible: '+title)
def action_button():
    state=gui()
    listing=next(w for w in state['widgets'] if w['type']=='ChoiceList')
    return next(w for w in state['widgets'] if w['type']=='SpiritButton' and w['visible']
                and w['x']>listing['x']+listing['width'] and listing['y']<=w['y']<listing['y']+listing['height'])
def act(title):
    choose(title)
    button=action_button()
    assert button['active'], (title,button,gui())
    click(button)
def request(action,target):
    c.action('progress_action',action=action,target=target)
    c.ticks(3)
def snap(name, picture=False):
    record={'progress':progress(),'state':c.request()}
    if record['state']['screen']=='SpiritScreen': record['gui']=gui()
    c.records['steps'][name]=record
    if picture: c.screenshot(name)
    c.save()
    return record['progress']['server']

try:
    deadline=time.monotonic()+90
    while c.request()['overlay']!='none':
        assert time.monotonic()<deadline
        time.sleep(.25)
    c.action('create_world',name=args.world,seed=8675310)
    c.ready()
    for command in ['gamemode survival','difficulty peaceful','gamerule doMobSpawning false',
                    'gamerule doDaylightCycle false','gamerule doWeatherCycle false','time set 9000',
                    'fill -10 99 -10 10 99 10 moss_block','fill -4 100 -4 4 110 6 air',
                    'tp @s .5 100 .5 0 0','experience add @s 5 levels']:
        c.command(command)
    c.action('language',code='zh_cn')
    c.ready()
    request('ability','dash')
    assert p()['abilities']==0
    c.command('give @s orimod:spirit_core')
    c.action('select_slot',slot=0)
    c.ticks(4)
    c.action('use_item')
    c.ticks(7)
    assert gui()['screen']=='SpiritScreen'
    snap('first-guide',True)
    tab('能力')
    choose('冲刺')
    assert not action_button()['active']
    request('ability','dash')
    assert p()['progress']['light']==0 and p()['abilities']==0
    snap('insufficient-light',True)
    c.action('grant_light',amount=500)
    request('ability','bash')
    assert p()['abilities']==0 and p()['progress']['light']==500

    for i in range(36):
        slot=f'hotbar.{i}' if i<9 else f'inventory.{i-9}'
        c.command(f'item replace entity @s {slot} with stone 64')
    act('灵光刃')
    assert p()['progress']['claimedWeapons']==0 and '背包已满' in gui()['reply']
    snap('full-inventory-claim',True)
    c.command('item replace entity @s hotbar.1 with air')
    act('灵光刃')
    assert p()['progress']['claimedWeapons']==1
    request('claim','blade')
    assert sum(v['count'] for v in p()['inventory'] if v['item']=='orimod:spirit_blade')==1
    act('冲刺')
    assert p()['abilities']==1 and p()['progress']['light']==480 and p()['experienceLevel']==5
    request('ability','dash')
    request('ability','dash')
    assert p()['progress']['light']==480

    tab('碎片')
    act('充盈'); act('聚光'); act('回流')
    act('充盈')
    choose('聚光')
    assert not action_button()['active']
    request('equip','gathering')
    assert p()['progress']['equippedShards']==1 and p()['slots']==1
    snap('one-slot-limit',True)
    tab('能力'); act('猛击')
    tab('碎片'); act('聚光')
    choose('回流')
    assert not action_button()['active']
    request('equip','recovery')
    assert p()['progress']['equippedShards']==5 and p()['slots']==2
    snap('two-slot-limit',True)
    tab('能力'); act('滑翔'); act('牵引')
    tab('碎片'); act('回流')
    assert p()['progress']['equippedShards']==7 and p()['slots']==3
    act('复苏'); act('轻灵')
    choose('复苏')
    assert not action_button()['active']
    request('equip','resilience')
    assert p()['progress']['equippedShards']==7
    snap('three-slot-limit',True)
    tab('能力'); act('下砸'); act('灵光弓')
    assert p()['abilities']==63
    c.command('item replace entity @s hotbar.2 with air')
    act('灵光弓')
    assert p()['progress']['claimedWeapons']==3
    tab('碎片')
    for _ in range(3): act('能量扩容')
    assert p()['progress']['capacityLevel']==3 and p()['progress']['light']==75 and p()['maxEnergy']==200
    assert not action_button()['active']
    c.action('set_energy',amount=200)
    act('充盈')
    assert p()['maxEnergy']==175 and p()['energy']<=175
    snap('completed-purchases',True)
    print('Purchases, prerequisites, duplicate rejection, three slot limits and capacity passed',flush=True)

    c.action('gui_key',key=256)
    c.key('orimod.panel',2)
    c.ticks(3)
    assert gui()['screen']=='SpiritScreen'
    for _ in range(4): c.action('gui_key',key=258)
    keyboard_before=gui()
    c.action('gui_key',key=264)
    c.action('gui_key',key=265)
    snap('keyboard-focus',True)
    c.action('gui_key',key=256)
    c.key('orimod.toggle_form',2)
    c.ticks(4)
    assert not c.spirit()['server']['form']
    c.key('orimod.toggle_form',2)
    c.ticks(4)
    assert c.spirit()['server']['form']
    c.records['before_rejoin']=progress()
    c.action('leave_world')
    c.ticks(8)
    c.action('open_world',name=args.world)
    c.ready()
    after=progress()
    assert after['server']['progress']==c.records['before_rejoin']['server']['progress']
    assert after['server']['abilities']==63
    snap('rejoined')
    print('Core-free form switching and saved progression reload passed',flush=True)
    c.records['status']='passed'
    c.save()
except Exception as error:
    c.records['status']='failed'
    c.records['error']=repr(error)
    c.save()
    raise
print(c.out,flush=True)
