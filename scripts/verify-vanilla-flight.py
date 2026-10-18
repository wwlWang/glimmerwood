"""Actual input checks for elytra handoff, jump fallback and a water-launched riptide."""
import sys
import shutil
from pathlib import Path
from devtools_client import DevClient

c=DevClient(sys.argv[1])
try:
    c.action('close_screen');c.action('focus_game');c.ready();c.action('release_keys')
    c.command('fill -10 199 -10 10 199 60 smooth_stone')
    c.command('fill -10 200 -10 10 205 60 air')
    c.action('settings',fov=45,hideGui=True)
    c.action('review_camera',enabled=True,x=2.5,y=1.1,z=-3.3,targetHeight=.6)
    def reset(chest):
        c.action('release_keys');c.command('gamemode creative')
        # Remove flowing water from the preceding riptide case, as well as its source.
        c.command('fill -10 200 -10 10 205 60 air')
        c.command('tp @s .5 200 .5 0 0');c.ticks(12)
        if not c.spirit()['server']['form']:c.action('toggle_form');c.ticks(12)
        for slot in ['armor.head','armor.legs','armor.feet','weapon.mainhand','weapon.offhand']:
            c.command(f'item replace entity @s {slot} with air')
        c.command(f'item replace entity @s armor.chest with {chest}')
        c.command('gamemode survival');c.ticks(8)
        assert c.spirit()['server']['movement']['grounded']
        assert not c.spirit()['server']['vanilla']['inWater']
    for shaders in [False,True]:
        c.action('shaders',enabled=shaders);c.ticks(20)
        prefix='on-' if shaders else 'off-'
        for label,chest,flight in [('elytra','minecraft:elytra',True),('broken-elytra','minecraft:elytra[minecraft:damage=431]',False),('no-elytra','air',False)]:
            reset(chest);before=c.start()
            c.key('jump',1);c.ticks(6)
            first=c.spirit()
            c.records['steps'][prefix+label]={'before':before,'first_jump':first};c.save()
            assert first['server']['movement']['jumps']==before['server']['movement']['jumps']+1, 'Ground jump was not accepted once'
            assert not first['server']['vanilla']['fallFlying']
            c.key('jump',20);c.ticks(3)
            second=c.spirit();c.screenshot(prefix+label)
            c.ticks(12);c.action('release_keys')
            after=c.finish(prefix+label,before,first_jump=first,second_jump=second)
            assert second['client']['vanilla']['fallFlying']==flight
            assert second['server']['vanilla']['fallFlying']==flight
            assert second['server']['movement']['jumps']-before['server']['movement']['jumps']==(1 if flight else 2)
            assert after['server']['movement']['jumps']==second['server']['movement']['jumps']
            if flight:
                c.action('look',yaw=0,pitch=75);c.ticks(40)
                landed=c.spirit();c.records['steps'][prefix+label]['landed']=landed
                assert landed['server']['movement']['grounded'] and not landed['server']['vanilla']['fallFlying']
                c.key('jump',1);c.ticks(5)
                assert c.spirit()['server']['movement']['jumps']==second['server']['movement']['jumps']+1
        reset('air')
        c.command('fill -2 200 -2 2 201 2 water');c.command('tp @s .5 200 .5 0 -40');c.ticks(15)
        c.command('item replace entity @s hotbar.0 with minecraft:trident[minecraft:enchantments={levels:{"minecraft:riptide":3}}]')
        c.action('select_slot',slot=0);c.ticks(5)
        before=c.start();assert before['server']['vanilla']['wet']
        c.key('use',35);c.ticks(25)
        charge=c.spirit();c.screenshot(prefix+'riptide-charged')
        assert charge['server']['combat']['usingItem']
        c.action('record_video',name=c.build_id+'-'+prefix+'riptide',fps=30,seconds=3)
        c.action('release_keys');c.ticks(3)
        release=c.spirit();c.screenshot(prefix+'riptide-spin')
        c.ticks(65);c.finish(prefix+'riptide',before,charge=charge,release=release)
        video=c.action('save_video');c.records['steps'][prefix+'riptide']['video']=video
        manifest=Path('run-devtools')/video['manifest']
        shutil.copy2(manifest,c.out/(prefix+'riptide-frames.json'))
        shutil.copy2(manifest.parent/'capture.mp4',c.out/(prefix+'riptide.mp4'))
        assert release['server']['vanilla']['spinAttack'] and release['client']['vanilla']['spinAttack']
        c.command('fill -2 200 -2 2 201 2 air')
    c.records.update(status='passed',visual_review='pending')
except Exception as error:
    c.records.update(status='failed',error=str(error));raise
finally:
    c.action('release_keys');c.action('review_camera',enabled=False)
    c.action('settings',fov=70,hideGui=False);c.action('camera',mode='FIRST_PERSON');c.action('open_pause');c.save()
