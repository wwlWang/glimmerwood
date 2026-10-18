"""Real-client first check of the character candidate; not full art acceptance."""
import sys
import time
from devtools_client import DevClient

c=DevClient(sys.argv[1])
world='orimod_dev_character_20261018'
deadline=time.monotonic()+90
while c.request()['overlay']!='none':
    assert time.monotonic()<deadline
    time.sleep(.2)
c.action('create_world',name=world,seed=8675345)
c.ready()
c.action('focus_game')
c.command('gamerule doDaylightCycle false');c.command('gamerule doWeatherCycle false')
c.command('time set 8000');c.command('weather clear')
c.command('tp @s 0.5 102 0.5 0 0');c.ticks(30)
c.command('fill -10 99 -15 10 99 60 moss_block')
c.command('fill -10 100 -15 10 119 60 air')
c.command('tp @s 0.5 100 0.5 0 0');c.ticks(10)
c.command('item replace entity @s hotbar.0 with orimod:spirit_core')
c.action('select_slot',slot=0);c.action('use_item');c.ticks(8);c.action('close_screen');c.ready()
c.action('grant_dash');c.action('grant_bash');c.action('grant_air_movement')
c.command('item replace entity @s hotbar.0 with minecraft:air')
c.command('gamemode survival');c.action('camera',mode='THIRD_PERSON_FRONT');c.ticks(20)
c.screenshot('front-idle')
c.records['steps']['idle']=c.spirit()
c.command('item replace entity @s hotbar.0 with minecraft:iron_pickaxe');c.ticks(10)
c.screenshot('front-pickaxe')
c.records['steps']['pickaxe']=c.spirit()
c.action('camera',mode='THIRD_PERSON_BACK');c.action('look',yaw=0,pitch=12)
c.action('record_video',name=c.build_id+'-movement',fps=30,seconds=12)
before=c.start()
c.action('input_script',steps=[{'at':0,'key':'key.forward','down':True},{'at':40,'key':'key.jump','down':True},
    {'at':41,'key':'key.jump','down':False},{'at':48,'key':'key.jump','down':True},{'at':49,'key':'key.jump','down':False},
    {'at':100,'key':'key.sneak','down':True},{'at':130,'key':'key.sneak','down':False},
    {'at':150,'key':'key.forward','down':False}])
c.ticks(245)
c.finish('movement',before)
c.action('save_video');c.action('release_keys');c.action('open_pause');c.ticks(3)
c.records.update(status='recorded',world=world,visual_review='pending; runtime screenshots and video must be inspected')
c.save()
print('Character smoke capture completed',flush=True)
